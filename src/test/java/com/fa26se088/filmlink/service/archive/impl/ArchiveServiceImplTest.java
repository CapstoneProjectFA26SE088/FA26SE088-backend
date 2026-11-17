package com.fa26se088.filmlink.service.archive.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.client.storage.FullResolutionLinkVerifier;
import com.fa26se088.filmlink.client.storage.FullResolutionLinkVerifier.LinkVerification;
import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.archive.response.RollScanStatusResponse;
import com.fa26se088.filmlink.entity.archive.ArchiveRoll;
import com.fa26se088.filmlink.entity.archive.ScanFile;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.archive.ArchiveMapper;
import com.fa26se088.filmlink.repository.archive.ArchiveRollRepository;
import com.fa26se088.filmlink.repository.archive.ScanFileRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** UC26, BR-08: ảnh xem trước và link file gốc; storage, verifier, repository được mock. */
@ExtendWith(MockitoExtension.class)
class ArchiveServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-17T03:00:00Z");
  private static final long ROLL_ID = 7L;

  @Mock private ArchiveRollRepository archiveRollRepository;
  @Mock private ScanFileRepository scanFileRepository;
  @Mock private StorageClient storageClient;
  @Mock private FullResolutionLinkVerifier verifier;

  private ArchiveServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ArchiveServiceImpl(
            archiveRollRepository,
            scanFileRepository,
            storageClient,
            verifier,
            new ArchiveMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  // --- addPreviewScans ---

  /** UC26: kích thước, dung lượng, SHA-256, số khung tiếp theo số lớn nhất đã có. */
  @Test
  void addPreviewScans_savesMetadataChecksumAndContinuesFrameNumbers() throws Exception {
    byte[] first = jpeg(40, 30);
    byte[] second = jpeg(20, 2048);
    when(scanFileRepository.countCurrentByFilmRollId(ROLL_ID)).thenReturn(3L);
    when(scanFileRepository.findMaxFrameNumber(ROLL_ID)).thenReturn((short) 3);
    when(storageClient.upload(eq(StoragePurpose.PREVIEW_SCAN), anyString(), any(), anyLong()))
        .thenReturn("preview-scan/k1", "preview-scan/k2");

    service.addPreviewScans(ROLL_ID, List.of(file("a.jpg", first), file("b.jpg", second)));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ScanFile>> saved = ArgumentCaptor.forClass(List.class);
    verify(scanFileRepository).saveAll(saved.capture());
    List<ScanFile> rows = saved.getValue();
    assertThat(rows).extracting(ScanFile::getFrameNumber).containsExactly((short) 4, (short) 5);
    assertThat(rows).extracting(ScanFile::getBlobKey)
        .containsExactly("preview-scan/k1", "preview-scan/k2");
    assertThat(rows).allSatisfy(row -> assertThat(row.getFilmRollId()).isEqualTo(ROLL_ID));
    assertThat(rows.get(0).getWidthPx()).isEqualTo((short) 40);
    assertThat(rows.get(0).getHeightPx()).isEqualTo((short) 30);
    assertThat(rows.get(1).getHeightPx()).isEqualTo((short) 2048);
    assertThat(rows.get(0).getSizeBytes()).isEqualTo(first.length);
    assertThat(rows.get(0).getChecksumSha256()).isEqualTo(sha256(first)).hasSize(64);
    assertThat(rows.get(1).getChecksumSha256()).isEqualTo(sha256(second));
    assertThat(rows).allSatisfy(row -> assertThat(row.isCurrent()).isTrue());
  }

  @Test
  void addPreviewScans_firstUploadStartsAtFrameOne() throws Exception {
    when(scanFileRepository.findMaxFrameNumber(ROLL_ID)).thenReturn(null);
    when(storageClient.upload(any(), anyString(), any(), anyLong())).thenReturn("preview-scan/k1");

    service.addPreviewScans(ROLL_ID, List.of(file("a.jpg", jpeg(10, 10))));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ScanFile>> saved = ArgumentCaptor.forClass(List.class);
    verify(scanFileRepository).saveAll(saved.capture());
    assertThat(saved.getValue().get(0).getFrameNumber()).isEqualTo((short) 1);
  }

  /** Một file sai thì không file nào được upload. */
  @Test
  void addPreviewScans_pngAmongJpegs_rejectedBeforeAnyUpload() throws Exception {
    byte[] png = image(10, 10, "png");

    assertError(
        () -> service.addPreviewScans(ROLL_ID, List.of(file("a.jpg", jpeg(10, 10)), file("b.png", png))),
        ErrorCode.STORAGE_FILE_TYPE_NOT_ALLOWED);
    verify(storageClient, never()).upload(any(), any(), any(), anyLong());
    verify(scanFileRepository, never()).saveAll(any());
  }

  @Test
  void addPreviewScans_longEdgeOver2048_rejected() throws Exception {
    assertError(
        () -> service.addPreviewScans(ROLL_ID, List.of(file("big.jpg", jpeg(2049, 16)))),
        ErrorCode.ARCHIVE_PREVIEW_INVALID);
    verify(storageClient, never()).upload(any(), any(), any(), anyLong());
  }

  @Test
  void addPreviewScans_corruptJpeg_rejected() {
    byte[] corrupt = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03};

    assertError(
        () -> service.addPreviewScans(ROLL_ID, List.of(file("x.jpg", corrupt))),
        ErrorCode.ARCHIVE_PREVIEW_INVALID);
    verify(storageClient, never()).upload(any(), any(), any(), anyLong());
  }

  @Test
  void addPreviewScans_textFile_rejected() {
    byte[] text = "không phải ảnh".getBytes(StandardCharsets.UTF_8);

    assertError(
        () -> service.addPreviewScans(ROLL_ID, List.of(file("note.jpg", text))),
        ErrorCode.STORAGE_FILE_TYPE_NOT_ALLOWED);
  }

  @Test
  void addPreviewScans_over5Mb_rejected() {
    byte[] big = new byte[5 * 1024 * 1024 + 1];
    big[0] = (byte) 0xFF;
    big[1] = (byte) 0xD8;
    big[2] = (byte) 0xFF;

    assertError(
        () -> service.addPreviewScans(ROLL_ID, List.of(file("big.jpg", big))),
        ErrorCode.STORAGE_FILE_TOO_LARGE);
  }

  @Test
  void addPreviewScans_zeroOrMoreThan40Files_validationError() throws Exception {
    assertError(() -> service.addPreviewScans(ROLL_ID, List.of()), ErrorCode.VALIDATION_ERROR);
    List<MultipartFile> many = new ArrayList<>();
    byte[] jpeg = jpeg(8, 8);
    for (int i = 0; i < 41; i++) {
      many.add(file("f" + i + ".jpg", jpeg));
    }
    assertError(() -> service.addPreviewScans(ROLL_ID, many), ErrorCode.VALIDATION_ERROR);
  }

  /** {@code ck_scan_files_frame}: tối đa 100 ảnh mỗi cuộn. */
  @Test
  void addPreviewScans_over100PerRoll_limitReached() throws Exception {
    when(scanFileRepository.countCurrentByFilmRollId(ROLL_ID)).thenReturn(99L);
    when(scanFileRepository.findMaxFrameNumber(ROLL_ID)).thenReturn((short) 99);
    byte[] jpeg = jpeg(8, 8);

    assertError(
        () -> service.addPreviewScans(ROLL_ID, List.of(file("a.jpg", jpeg), file("b.jpg", jpeg))),
        ErrorCode.ARCHIVE_PREVIEW_LIMIT_REACHED);
    verify(storageClient, never()).upload(any(), any(), any(), anyLong());
  }

  /** Upload lỗi giữa chừng → xoá file đã upload của lần này, không lưu dòng nào. */
  @Test
  void addPreviewScans_uploadFailsMidway_deletesUploadedFiles() throws Exception {
    byte[] jpeg = jpeg(8, 8);
    when(storageClient.upload(any(), anyString(), any(), anyLong()))
        .thenReturn("preview-scan/k1")
        .thenThrow(new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "down"));

    assertError(
        () -> service.addPreviewScans(ROLL_ID, List.of(file("a.jpg", jpeg), file("b.jpg", jpeg))),
        ErrorCode.STORAGE_UNAVAILABLE);
    verify(storageClient).delete("preview-scan/k1");
    verify(scanFileRepository, never()).saveAll(any());
  }

  /** Transaction rollback sau khi upload xong (ví dụ lỗi ở domain gọi) → xoá file. */
  @Test
  void addPreviewScans_transactionRolledBack_deletesFiles() throws Exception {
    TransactionSynchronizationManager.initSynchronization();
    when(storageClient.upload(any(), anyString(), any(), anyLong())).thenReturn("preview-scan/k1");

    service.addPreviewScans(ROLL_ID, List.of(file("a.jpg", jpeg(8, 8))));
    for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    verify(storageClient).delete("preview-scan/k1");
  }

  // --- setFullResolutionLink ---

  @Test
  void setFullResolutionLink_unreachable_422AndNothingSaved() {
    when(verifier.verify("https://drive.example/x"))
        .thenReturn(LinkVerification.failed("Link trả mã HTTP 404, không truy cập được."));

    assertThatThrownBy(() -> service.setFullResolutionLink(ROLL_ID, "https://drive.example/x"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("404")
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.ARCHIVE_LINK_UNREACHABLE);
    verify(archiveRollRepository, never()).save(any());
  }

  /** Lần đầu: tạo dòng mới, {@code link_verified_at} và {@code delivered_at} tạm = now. */
  @Test
  void setFullResolutionLink_firstTime_createsRow() {
    when(verifier.verify("https://drive.example/a")).thenReturn(LinkVerification.ok());
    when(archiveRollRepository.findByFilmRollId(ROLL_ID)).thenReturn(Optional.empty());
    when(archiveRollRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(scanFileRepository.countCurrentByFilmRollId(ROLL_ID)).thenReturn(5L);

    RollScanStatusResponse status = service.setFullResolutionLink(ROLL_ID, " https://drive.example/a ");

    ArgumentCaptor<ArchiveRoll> saved = ArgumentCaptor.forClass(ArchiveRoll.class);
    verify(archiveRollRepository).save(saved.capture());
    assertThat(saved.getValue().getFilmRollId()).isEqualTo(ROLL_ID);
    assertThat(saved.getValue().getFullResolutionLink()).isEqualTo("https://drive.example/a");
    assertThat(saved.getValue().getLinkVerifiedAt()).isEqualTo(NOW);
    assertThat(saved.getValue().getDeliveredAt()).isEqualTo(NOW);
    assertThat(status.previewCount()).isEqualTo(5);
    assertThat(status.hasVerifiedLink()).isTrue();
    assertThat(status.readyForDelivery()).isTrue();
  }

  /** Gửi lại: cập nhật link trên dòng cũ (upsert theo {@code film_roll_id}). */
  @Test
  void setFullResolutionLink_again_updatesExistingRow() {
    ArchiveRoll existing = new ArchiveRoll();
    existing.setFilmRollId(ROLL_ID);
    existing.setFullResolutionLink("https://old.example/a");
    existing.setLinkVerifiedAt(NOW.minusSeconds(3600));
    existing.setDeliveredAt(NOW.minusSeconds(3600));
    when(verifier.verify("https://new.example/b")).thenReturn(LinkVerification.ok());
    when(archiveRollRepository.findByFilmRollId(ROLL_ID)).thenReturn(Optional.of(existing));
    when(archiveRollRepository.save(existing)).thenReturn(existing);

    service.setFullResolutionLink(ROLL_ID, "https://new.example/b");

    assertThat(existing.getFullResolutionLink()).isEqualTo("https://new.example/b");
    assertThat(existing.getLinkVerifiedAt()).isEqualTo(NOW);
  }

  // --- markDelivered ---

  @Test
  void markDelivered_setsDeliveredAtNow() {
    ArchiveRoll roll = new ArchiveRoll();
    roll.setFilmRollId(ROLL_ID);
    roll.setDeliveredAt(NOW.minusSeconds(600));
    when(archiveRollRepository.findByFilmRollIdIn(List.of(ROLL_ID))).thenReturn(List.of(roll));

    service.markDelivered(List.of(ROLL_ID));

    assertThat(roll.getDeliveredAt()).isEqualTo(NOW);
  }

  // --- helpers ---

  private static void assertError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(expected);
  }

  private static MockMultipartFile file(String name, byte[] content) {
    return new MockMultipartFile("files", name, "image/jpeg", content);
  }

  static byte[] jpeg(int width, int height) throws IOException {
    return image(width, height, "jpg");
  }

  private static byte[] image(int width, int height, String format) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, 0xFF8800);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }

  private static String sha256(byte[] content) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
  }
}
