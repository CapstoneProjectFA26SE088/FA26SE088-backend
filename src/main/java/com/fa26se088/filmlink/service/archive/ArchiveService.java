package com.fa26se088.filmlink.service.archive;

import com.fa26se088.filmlink.dto.archive.response.ArchiveRollResponse;
import com.fa26se088.filmlink.dto.archive.response.ArchiveRollSummaryResponse;
import com.fa26se088.filmlink.dto.archive.response.RollScanStatusResponse;
import com.fa26se088.filmlink.dto.archive.response.ScanFileResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

/**
 * UC26, UC28, BR-08: Preview Scan, link file độ phân giải gốc và Digital Film Archive của từng
 * cuộn.
 *
 * <p>Domain archive (tầng 2) chỉ biết {@code film_roll_id}: không đọc Service Order, không biết
 * Photographer hay lab. Domain gọi (order) tự kiểm quyền sở hữu, trạng thái đơn, công đoạn và khoá
 * dòng đơn trước khi gọi, trong transaction của mình.
 */
public interface ArchiveService {

  /** UC26: số file tối đa trong một lần upload. */
  int MAX_FILES_PER_UPLOAD = 40;

  /** UC26: số ảnh xem trước hiện hành tối đa của một cuộn ({@code ck_scan_files_frame} 0–100). */
  int MAX_PREVIEWS_PER_ROLL = 100;

  /**
   * UC26, BR-08: thêm ảnh xem trước cho một cuộn. Mọi file được kiểm trước khi upload file nào:
   * JPEG thật, ≤ 5 MB, cạnh dài ≤ 2048 px.
   *
   * @return mọi ảnh hiện hành của cuộn sau khi thêm, theo số khung
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code VALIDATION_ERROR} (số file
   *     ngoài 1–40), {@code STORAGE_FILE_TYPE_NOT_ALLOWED} (không phải JPEG), {@code
   *     STORAGE_FILE_TOO_LARGE} (> 5 MB), {@code ARCHIVE_PREVIEW_INVALID} (JPEG hỏng / quá 2048
   *     px), {@code ARCHIVE_PREVIEW_LIMIT_REACHED} (vượt 100 ảnh mỗi cuộn), {@code STORAGE_UNAVAILABLE}
   */
  List<ScanFileResponse> addPreviewScans(Long filmRollId, List<MultipartFile> files);

  /**
   * UC26: kiểm link file gốc truy cập được rồi lưu (upsert theo {@code film_roll_id}, {@code
   * link_verified_at = now}).
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ARCHIVE_LINK_UNREACHABLE}
   *     (422)
   */
  RollScanStatusResponse setFullResolutionLink(Long filmRollId, String url);

  /**
   * UC26: tình trạng scan của nhiều cuộn (số ảnh hiện hành, link đã kiểm). Mọi id gửi vào đều có
   * trong kết quả.
   */
  Map<Long, RollScanStatusResponse> getScanStatuses(Collection<Long> filmRollIds);

  /**
   * UC26: đánh dấu đã giao scan ({@code delivered_at = now}) cho các cuộn. Người gọi đã kiểm mọi
   * cuộn có dòng archive.
   */
  void markDelivered(Collection<Long> filmRollIds);

  /**
   * UC28: một trang archive trong tập cuộn domain order đã lọc theo quyền, giao scan mới nhất
   * trước. Mỗi dòng có số ảnh và link có chữ ký của ảnh bìa (không N+1).
   */
  Page<ArchiveRollSummaryResponse> getArchiveRolls(
      Collection<Long> filmRollIds, int page, int size);

  /** UC28: {@code film_roll_id} của một dòng archive, để domain order kiểm quyền. */
  Optional<Long> findFilmRollId(Long archiveRollId);

  /**
   * UC28: chi tiết một dòng archive kèm ảnh xem trước (link có chữ ký 15 phút).
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ARCHIVE_ROLL_NOT_FOUND} (404)
   */
  ArchiveRollResponse getArchiveRoll(Long archiveRollId);
}
