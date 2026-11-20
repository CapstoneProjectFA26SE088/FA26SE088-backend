package com.fa26se088.filmlink.service.archive.impl;

import com.fa26se088.filmlink.client.storage.FullResolutionLinkVerifier;
import com.fa26se088.filmlink.client.storage.FullResolutionLinkVerifier.LinkVerification;
import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.archive.response.ArchiveRollResponse;
import com.fa26se088.filmlink.dto.archive.response.ArchiveRollSummaryResponse;
import com.fa26se088.filmlink.dto.archive.response.RollScanStatusResponse;
import com.fa26se088.filmlink.dto.archive.response.ScanFileResponse;
import com.fa26se088.filmlink.entity.archive.ArchiveRoll;
import com.fa26se088.filmlink.entity.archive.ScanFile;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.archive.ArchiveMapper;
import com.fa26se088.filmlink.repository.archive.ArchiveRollRepository;
import com.fa26se088.filmlink.repository.archive.ScanFileRepository;
import com.fa26se088.filmlink.repository.archive.ScanFileRepository.PreviewCountRow;
import com.fa26se088.filmlink.service.archive.ArchiveService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveServiceImpl implements ArchiveService {

  /** UC26: cạnh dài tối đa của ảnh xem trước ({@code ck_scan_files_dimensions}). */
  static final int MAX_EDGE_PX = 2048;

  /** UC26: dung lượng tối đa ({@code ck_scan_files_size}, {@link StoragePurpose#PREVIEW_SCAN}). */
  static final long MAX_SIZE_BYTES = StoragePurpose.PREVIEW_SCAN.maxSizeBytes();

  private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

  /** UC28: giao scan mới nhất trước; {@code id} để thứ tự ổn định. */
  private static final Sort NEWEST_DELIVERED_FIRST =
      Sort.by(Sort.Order.desc("deliveredAt"), Sort.Order.desc("id"));

  private final ArchiveRollRepository archiveRollRepository;
  private final ScanFileRepository scanFileRepository;
  private final StorageClient storageClient;
  private final FullResolutionLinkVerifier fullResolutionLinkVerifier;
  private final ArchiveMapper archiveMapper;
  private final Clock clock;

  /**
   * UC26, BR-08: thêm ảnh xem trước.
   *
   * <ul>
   *   <li>1–{@value ArchiveService#MAX_FILES_PER_UPLOAD} file mỗi lần; khác → 400 {@code
   *       VALIDATION_ERROR}.
   *   <li>Tổng ảnh hiện hành của cuộn sau khi thêm ≤ {@value ArchiveService#MAX_PREVIEWS_PER_ROLL};
   *       số khung tiếp theo số lớn nhất đã có (bắt đầu 1) và không vượt 100 → khác thì 422 {@code
   *       ARCHIVE_PREVIEW_LIMIT_REACHED}.
   *   <li>Kiểm <b>mọi</b> file trước khi upload file nào: magic bytes JPEG (không tin {@code
   *       Content-Type}), ≤ 5 MB, đọc kích thước bằng {@link ImageReader} (chỉ đọc header, không
   *       decode ảnh), cạnh dài ≤ {@value #MAX_EDGE_PX} px; tính SHA-256 (NFR-CAP-03).
   *   <li>Upload qua {@link StorageClient} ({@link StoragePurpose#PREVIEW_SCAN}); lỗi giữa chừng →
   *       xoá các file đã upload của lần này; transaction rollback sau đó (kể cả ở domain gọi) cũng
   *       xoá file.
   * </ul>
   */
  @Override
  @Transactional
  public List<ScanFileResponse> addPreviewScans(Long filmRollId, List<MultipartFile> files) {
    Objects.requireNonNull(filmRollId, "filmRollId");
    List<MultipartFile> fileList = files == null ? List.of() : files;
    if (fileList.isEmpty() || fileList.size() > MAX_FILES_PER_UPLOAD) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Cần 1–"
              + MAX_FILES_PER_UPLOAD
              + " ảnh xem trước mỗi lần; đã gửi "
              + fileList.size()
              + ".",
          Map.of("field", "files", "min", 1, "max", MAX_FILES_PER_UPLOAD));
    }

    long currentCount = scanFileRepository.countCurrentByFilmRollId(filmRollId);
    Short maxFrame = scanFileRepository.findMaxFrameNumber(filmRollId);
    int firstFrame = (maxFrame == null ? 0 : maxFrame) + 1;
    int lastFrame = firstFrame + fileList.size() - 1;
    if (currentCount + fileList.size() > MAX_PREVIEWS_PER_ROLL
        || lastFrame > MAX_PREVIEWS_PER_ROLL) {
      throw new BusinessException(
          ErrorCode.ARCHIVE_PREVIEW_LIMIT_REACHED,
          "Mỗi cuộn tối đa "
              + MAX_PREVIEWS_PER_ROLL
              + " ảnh xem trước; cuộn đang có "
              + currentCount
              + " ảnh, không thêm được "
              + fileList.size()
              + " ảnh.",
          Map.of("currentCount", currentCount, "max", MAX_PREVIEWS_PER_ROLL));
    }

    List<PreviewImage> images = new ArrayList<>(fileList.size());
    for (int i = 0; i < fileList.size(); i++) {
      images.add(inspect(fileList.get(i), i + 1));
    }

    List<String> blobKeys = uploadAll(fileList, images);
    registerRollbackCleanup(blobKeys);

    List<ScanFile> rows = new ArrayList<>(images.size());
    for (int i = 0; i < images.size(); i++) {
      PreviewImage image = images.get(i);
      ScanFile scan = new ScanFile();
      scan.setFilmRollId(filmRollId);
      scan.setFrameNumber((short) (firstFrame + i));
      scan.setBlobKey(blobKeys.get(i));
      scan.setSizeBytes(image.sizeBytes());
      scan.setWidthPx((short) image.width());
      scan.setHeightPx((short) image.height());
      scan.setChecksumSha256(image.checksumSha256());
      rows.add(scan);
    }
    scanFileRepository.saveAll(rows);
    return currentPreviews(filmRollId);
  }

  /**
   * UC26: link file gốc. Kiểm qua {@link FullResolutionLinkVerifier} (chống SSRF, cần 2xx); không
   * hợp lệ → 422 {@code ARCHIVE_LINK_UNREACHABLE} kèm lý do. Hợp lệ → upsert {@code archive_rolls}
   * theo {@code film_roll_id}: dòng mới có {@code delivered_at = now} tạm (cột NOT NULL), được gán
   * lại lúc giao scan ({@link #markDelivered}).
   */
  @Override
  @Transactional
  public RollScanStatusResponse setFullResolutionLink(Long filmRollId, String url) {
    Objects.requireNonNull(filmRollId, "filmRollId");
    String link = url == null ? null : url.strip();
    LinkVerification verification = fullResolutionLinkVerifier.verify(link);
    if (!verification.reachable()) {
      throw new BusinessException(
          ErrorCode.ARCHIVE_LINK_UNREACHABLE,
          "Link file gốc không hợp lệ: " + verification.reason(),
          Map.of("field", "url"));
    }

    Instant now = clock.instant();
    ArchiveRoll roll =
        archiveRollRepository
            .findByFilmRollId(filmRollId)
            .orElseGet(
                () -> {
                  ArchiveRoll created = new ArchiveRoll();
                  created.setFilmRollId(filmRollId);
                  created.setDeliveredAt(now);
                  return created;
                });
    roll.setFullResolutionLink(link);
    roll.setLinkVerifiedAt(now);
    roll = archiveRollRepository.save(roll);
    return new RollScanStatusResponse(
        filmRollId,
        roll.getId(),
        scanFileRepository.countCurrentByFilmRollId(filmRollId),
        roll.getFullResolutionLink(),
        roll.getLinkVerifiedAt());
  }

  /** UC26: hai query gộp (dòng archive, đếm ảnh hiện hành) cho mọi cuộn. */
  @Override
  @Transactional(readOnly = true)
  public Map<Long, RollScanStatusResponse> getScanStatuses(Collection<Long> filmRollIds) {
    if (filmRollIds == null || filmRollIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, ArchiveRoll> archiveByRoll =
        archiveRollRepository.findByFilmRollIdIn(filmRollIds).stream()
            .collect(Collectors.toMap(ArchiveRoll::getFilmRollId, Function.identity()));
    Map<Long, Long> previewCounts = previewCounts(filmRollIds);
    Map<Long, RollScanStatusResponse> result = new LinkedHashMap<>();
    for (Long filmRollId : filmRollIds) {
      ArchiveRoll archive = archiveByRoll.get(filmRollId);
      result.put(
          filmRollId,
          new RollScanStatusResponse(
              filmRollId,
              archive == null ? null : archive.getId(),
              previewCounts.getOrDefault(filmRollId, 0L),
              archive == null ? null : archive.getFullResolutionLink(),
              archive == null ? null : archive.getLinkVerifiedAt()));
    }
    return result;
  }

  /** UC26: {@code delivered_at = now} cho các cuộn; cuộn chưa có dòng archive là lỗi lập trình. */
  @Override
  @Transactional
  public void markDelivered(Collection<Long> filmRollIds) {
    List<ArchiveRoll> rolls = archiveRollRepository.findByFilmRollIdIn(filmRollIds);
    if (rolls.size() != filmRollIds.size()) {
      throw new IllegalStateException("Có cuộn chưa có link file gốc khi giao scan");
    }
    Instant now = clock.instant();
    rolls.forEach(roll -> roll.setDeliveredAt(now));
    archiveRollRepository.saveAll(rolls);
  }

  /**
   * UC28: một trang trong tập cuộn đã lọc; 3 query cho cả trang (dòng archive, đếm ảnh, ảnh bìa) +
   * ký link ảnh bìa.
   */
  @Override
  @Transactional(readOnly = true)
  public Page<ArchiveRollSummaryResponse> getArchiveRolls(
      Collection<Long> filmRollIds, int page, int size) {
    PageRequest pageable = PageRequest.of(page, size, NEWEST_DELIVERED_FIRST);
    if (filmRollIds == null || filmRollIds.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, 0);
    }
    Page<ArchiveRoll> rolls = archiveRollRepository.findByFilmRollIdIn(filmRollIds, pageable);
    if (rolls.isEmpty()) {
      return rolls.map(roll -> archiveMapper.toSummaryResponse(roll, 0, null));
    }
    List<Long> pageRollIds = rolls.map(ArchiveRoll::getFilmRollId).getContent();
    Map<Long, Long> previewCounts = previewCounts(pageRollIds);
    Map<Long, ScanFile> covers = new LinkedHashMap<>();
    for (ScanFile scan : scanFileRepository.findFirstCurrentByFilmRollIds(pageRollIds)) {
      covers.putIfAbsent(scan.getFilmRollId(), scan);
    }
    return rolls.map(
        roll -> {
          ScanFile cover = covers.get(roll.getFilmRollId());
          return archiveMapper.toSummaryResponse(
              roll,
              previewCounts.getOrDefault(roll.getFilmRollId(), 0L),
              cover == null ? null : storageClient.signedUrl(cover.getBlobKey()));
        });
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Long> findFilmRollId(Long archiveRollId) {
    return archiveRollRepository.findById(archiveRollId).map(ArchiveRoll::getFilmRollId);
  }

  /** UC28: ảnh hiện hành theo số khung, link có chữ ký 15 phút (NFR-PRI-02). */
  @Override
  @Transactional(readOnly = true)
  public ArchiveRollResponse getArchiveRoll(Long archiveRollId) {
    ArchiveRoll roll =
        archiveRollRepository
            .findById(archiveRollId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.ARCHIVE_ROLL_NOT_FOUND,
                        "Không tìm thấy cuộn trong kho phim số."));
    return archiveMapper.toResponse(roll, currentPreviews(roll.getFilmRollId()));
  }

  private List<ScanFileResponse> currentPreviews(Long filmRollId) {
    return scanFileRepository.findCurrentByFilmRollId(filmRollId).stream()
        .map(
            scan ->
                archiveMapper.toScanFileResponse(scan, storageClient.signedUrl(scan.getBlobKey())))
        .toList();
  }

  private Map<Long, Long> previewCounts(Collection<Long> filmRollIds) {
    return scanFileRepository.countCurrentByFilmRollIds(filmRollIds).stream()
        .collect(
            Collectors.toMap(PreviewCountRow::getFilmRollId, PreviewCountRow::getPreviewCount));
  }

  /**
   * UC26: đọc file (≤ 5 MB) một lần, kiểm JPEG + kích thước, tính SHA-256. Không giữ nội dung file
   * sau khi kiểm (upload đọc lại stream).
   *
   * @param position thứ tự file trong request (1..n), để báo lỗi rõ file nào
   */
  PreviewImage inspect(MultipartFile file, int position) {
    String label = fileLabel(file, position);
    if (file.getSize() > MAX_SIZE_BYTES) {
      throw tooLarge(label);
    }
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException ex) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Không đọc được " + label + " gửi lên.");
    }
    if (content.length > MAX_SIZE_BYTES) {
      throw tooLarge(label);
    }
    if (content.length < JPEG_MAGIC.length
        || content[0] != JPEG_MAGIC[0]
        || content[1] != JPEG_MAGIC[1]
        || content[2] != JPEG_MAGIC[2]) {
      throw new BusinessException(
          ErrorCode.STORAGE_FILE_TYPE_NOT_ALLOWED,
          "Ảnh xem trước chỉ nhận JPG; " + label + " không phải JPEG.",
          Map.of("field", "files", "position", position));
    }

    int width;
    int height;
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
    if (!readers.hasNext()) {
      throw new IllegalStateException("JVM không có ImageReader cho JPEG");
    }
    ImageReader reader = readers.next();
    try (ImageInputStream input =
        new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
      reader.setInput(input, true, true);
      width = reader.getWidth(0);
      height = reader.getHeight(0);
    } catch (IOException | RuntimeException ex) {
      throw invalidPreview(label + " không đọc được như ảnh JPEG (file hỏng).", position);
    } finally {
      reader.dispose();
    }
    if (width < 1 || height < 1 || Math.max(width, height) > MAX_EDGE_PX) {
      throw invalidPreview(
          label
              + " có kích thước "
              + width
              + "×"
              + height
              + " px; cạnh dài tối đa "
              + MAX_EDGE_PX
              + " px.",
          position);
    }
    return new PreviewImage(content.length, width, height, sha256Hex(content));
  }

  /** UC26: upload lần lượt; một file lỗi thì xoá các file đã upload của lần này rồi ném lỗi. */
  private List<String> uploadAll(List<MultipartFile> files, List<PreviewImage> images) {
    List<String> blobKeys = new ArrayList<>(files.size());
    try {
      for (int i = 0; i < files.size(); i++) {
        MultipartFile file = files.get(i);
        try (InputStream content = file.getInputStream()) {
          blobKeys.add(
              storageClient.upload(
                  StoragePurpose.PREVIEW_SCAN,
                  file.getOriginalFilename(),
                  content,
                  images.get(i).sizeBytes()));
        } catch (IOException ex) {
          throw new BusinessException(
              ErrorCode.VALIDATION_ERROR, "Không đọc được " + fileLabel(file, i + 1) + " gửi lên.");
        }
      }
    } catch (RuntimeException ex) {
      deleteQuietly(blobKeys);
      throw ex;
    }
    return blobKeys;
  }

  /** File chỉ bị xoá khi transaction rollback; commit thì giữ. */
  private void registerRollbackCleanup(List<String> blobKeys) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_ROLLED_BACK) {
                deleteQuietly(blobKeys);
              }
            }
          });
    }
  }

  private void deleteQuietly(List<String> blobKeys) {
    for (String blobKey : blobKeys) {
      try {
        storageClient.delete(blobKey);
      } catch (RuntimeException ex) {
        log.warn("Could not delete preview scan blob {}", blobKey, ex);
      }
    }
  }

  private static String sha256Hex(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("JVM không có SHA-256", ex);
    }
  }

  private static String fileLabel(MultipartFile file, int position) {
    String name = file.getOriginalFilename();
    return name == null || name.isBlank()
        ? "file thứ " + position
        : "file thứ " + position + " (" + name + ")";
  }

  private static BusinessException tooLarge(String label) {
    return new BusinessException(
        ErrorCode.STORAGE_FILE_TOO_LARGE,
        label + " vượt quá " + MAX_SIZE_BYTES / (1024 * 1024) + " MB.");
  }

  private static BusinessException invalidPreview(String message, int position) {
    return new BusinessException(
        ErrorCode.ARCHIVE_PREVIEW_INVALID, message, Map.of("field", "files", "position", position));
  }

  /** Kết quả kiểm một ảnh xem trước. */
  record PreviewImage(int sizeBytes, int width, int height, String checksumSha256) {}
}
