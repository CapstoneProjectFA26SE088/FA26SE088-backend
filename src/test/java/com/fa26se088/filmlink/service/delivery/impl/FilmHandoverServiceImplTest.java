package com.fa26se088.filmlink.service.delivery.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.delivery.request.RecordFilmHandoverRequest;
import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverResponse;
import com.fa26se088.filmlink.entity.delivery.FilmHandover;
import com.fa26se088.filmlink.entity.delivery.FilmHandoverPhoto;
import com.fa26se088.filmlink.entity.delivery.enums.HandoverPoint;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.delivery.FilmHandoverMapper;
import com.fa26se088.filmlink.repository.delivery.FilmHandoverPhotoRepository;
import com.fa26se088.filmlink.repository.delivery.FilmHandoverRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** BR-25: ghi và đọc bằng chứng bàn giao phim, storage và repository được mock. */
@ExtendWith(MockitoExtension.class)
class FilmHandoverServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-17T03:00:00Z");

  @Mock private FilmHandoverRepository filmHandoverRepository;
  @Mock private FilmHandoverPhotoRepository filmHandoverPhotoRepository;
  @Mock private StorageClient storageClient;

  private FilmHandoverServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new FilmHandoverServiceImpl(
            filmHandoverRepository,
            filmHandoverPhotoRepository,
            storageClient,
            new FilmHandoverMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** BR-25: 3 ảnh → upload qua storage với HANDOVER_PHOTO, display_order 1, 2, 3 theo thứ tự gửi. */
  @Test
  void record_uploadsPhotosAndSavesDisplayOrderInSendingOrder() {
    when(storageClient.upload(eq(StoragePurpose.HANDOVER_PHOTO), anyString(), any(), anyLong()))
        .thenReturn("handover-photo/k1", "handover-photo/k2", "handover-photo/k3");
    when(filmHandoverRepository.save(any()))
        .thenAnswer(
            invocation -> {
              FilmHandover handover = invocation.getArgument(0);
              handover.setId(42L);
              return handover;
            });

    Long id = service.record(request(2, "  ghi chú  ", photos(3)));

    assertThat(id).isEqualTo(42L);
    ArgumentCaptor<FilmHandover> handover = ArgumentCaptor.forClass(FilmHandover.class);
    verify(filmHandoverRepository).save(handover.capture());
    assertThat(handover.getValue().getServiceOrderId()).isEqualTo(7L);
    assertThat(handover.getValue().getHandoverPoint()).isEqualTo(HandoverPoint.RECEIVED_AT_LAB);
    assertThat(handover.getValue().getRollCount()).isEqualTo((short) 2);
    assertThat(handover.getValue().getNote()).isEqualTo("ghi chú");
    assertThat(handover.getValue().getPerformedByAccountId()).isEqualTo(5L);
    assertThat(handover.getValue().getOccurredAt()).isEqualTo(NOW);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<FilmHandoverPhoto>> saved = ArgumentCaptor.forClass(List.class);
    verify(filmHandoverPhotoRepository).saveAll(saved.capture());
    assertThat(saved.getValue())
        .extracting(
            FilmHandoverPhoto::getFilmHandoverId,
            FilmHandoverPhoto::getBlobKey,
            FilmHandoverPhoto::getDisplayOrder,
            FilmHandoverPhoto::getCreatedAt)
        .containsExactly(
            tuple(42L, "handover-photo/k1", (short) 1, NOW),
            tuple(42L, "handover-photo/k2", (short) 2, NOW),
            tuple(42L, "handover-photo/k3", (short) 3, NOW));
  }

  @Test
  void record_zeroPhotos_rejectedWithoutUpload() {
    assertValidationError(() -> service.record(request(1, null, List.of())));
    verifyNoInteractions(storageClient, filmHandoverRepository);
  }

  @Test
  void record_elevenPhotos_rejectedWithoutUpload() {
    assertValidationError(() -> service.record(request(1, null, photos(11))));
    verifyNoInteractions(storageClient, filmHandoverRepository);
  }

  @Test
  void record_rollCountOutOfRange_rejected() {
    assertValidationError(() -> service.record(request(26, null, photos(1))));
    assertValidationError(() -> service.record(request(-1, null, photos(1))));
    verifyNoInteractions(storageClient);
  }

  @Test
  void record_withoutPerformer_rejected() {
    RecordFilmHandoverRequest request =
        new RecordFilmHandoverRequest(
            7L, null, HandoverPoint.RECEIVED_AT_LAB, 1, null, null, "  ", photos(1));
    assertValidationError(() -> service.record(request));
    verifyNoInteractions(storageClient);
  }

  /** Ảnh thứ hai bị storage từ chối → xoá ảnh thứ nhất đã upload, không ghi DB. */
  @Test
  void record_storageRejectsSecondPhoto_deletesFirstAndSavesNothing() {
    when(storageClient.upload(eq(StoragePurpose.HANDOVER_PHOTO), anyString(), any(), anyLong()))
        .thenReturn("handover-photo/k1")
        .thenThrow(
            new BusinessException(ErrorCode.STORAGE_FILE_TYPE_NOT_ALLOWED, "Loại file không hợp lệ."));

    assertThatThrownBy(() -> service.record(request(1, null, photos(2))))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.STORAGE_FILE_TYPE_NOT_ALLOWED);

    verify(storageClient).delete("handover-photo/k1");
    verify(filmHandoverRepository, never()).save(any());
    verifyNoInteractions(filmHandoverPhotoRepository);
  }

  /** Đọc theo đơn: ảnh gom theo bàn giao, mỗi ảnh có link ký qua storage. */
  @Test
  void getByServiceOrderId_returnsPhotosWithSignedUrls() {
    FilmHandover handover = new FilmHandover();
    handover.setId(42L);
    handover.setServiceOrderId(7L);
    handover.setHandoverPoint(HandoverPoint.RECEIVED_AT_LAB);
    handover.setRollCount((short) 2);
    handover.setPerformedByAccountId(5L);
    handover.setOccurredAt(NOW);
    when(filmHandoverRepository.findByServiceOrderIdOrderByOccurredAtAscIdAsc(7L))
        .thenReturn(List.of(handover));
    when(filmHandoverPhotoRepository.findByFilmHandoverIdInOrderByFilmHandoverIdAscDisplayOrderAsc(
            List.of(42L)))
        .thenReturn(List.of(photo(1L, 42L, "k1", 1), photo(2L, 42L, "k2", 2)));
    when(storageClient.signedUrl("k1")).thenReturn(URI.create("http://files/k1"));
    when(storageClient.signedUrl("k2")).thenReturn(URI.create("http://files/k2"));

    List<FilmHandoverResponse> result = service.getByServiceOrderId(7L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).handoverPoint()).isEqualTo(HandoverPoint.RECEIVED_AT_LAB);
    assertThat(result.get(0).photos())
        .extracting(p -> p.displayOrder(), p -> p.url().toString())
        .containsExactly(
            tuple((short) 1, "http://files/k1"),
            tuple((short) 2, "http://files/k2"));
  }

  @Test
  void getByServiceOrderId_noHandover_returnsEmptyWithoutPhotoQuery() {
    when(filmHandoverRepository.findByServiceOrderIdOrderByOccurredAtAscIdAsc(7L))
        .thenReturn(List.of());

    assertThat(service.getByServiceOrderId(7L)).isEmpty();
    verifyNoInteractions(filmHandoverPhotoRepository);
  }

  private static RecordFilmHandoverRequest request(
      int rollCount, String note, List<MultipartFile> photos) {
    return new RecordFilmHandoverRequest(
        7L, null, HandoverPoint.RECEIVED_AT_LAB, rollCount, note, 5L, null, photos);
  }

  private static List<MultipartFile> photos(int count) {
    List<MultipartFile> photos = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      photos.add(new MockMultipartFile("photos", "p" + i + ".png", "image/png", new byte[] {1}));
    }
    return photos;
  }

  private static FilmHandoverPhoto photo(Long id, Long handoverId, String blobKey, int order) {
    FilmHandoverPhoto photo = new FilmHandoverPhoto();
    photo.setId(id);
    photo.setFilmHandoverId(handoverId);
    photo.setBlobKey(blobKey);
    photo.setDisplayOrder((short) order);
    photo.setCreatedAt(NOW);
    return photo;
  }

  private static void assertValidationError(
      ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }
}
