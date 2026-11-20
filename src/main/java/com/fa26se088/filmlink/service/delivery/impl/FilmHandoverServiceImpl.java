package com.fa26se088.filmlink.service.delivery.impl;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.delivery.request.RecordFilmHandoverRequest;
import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverPhotoResponse;
import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverResponse;
import com.fa26se088.filmlink.entity.delivery.FilmHandover;
import com.fa26se088.filmlink.entity.delivery.FilmHandoverPhoto;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.delivery.FilmHandoverMapper;
import com.fa26se088.filmlink.repository.delivery.FilmHandoverPhotoRepository;
import com.fa26se088.filmlink.repository.delivery.FilmHandoverRepository;
import com.fa26se088.filmlink.service.delivery.FilmHandoverService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilmHandoverServiceImpl implements FilmHandoverService {

  /** BR-25, UC23: 1–10 ảnh mỗi lần bàn giao ({@code ck_film_handover_photos_order}). */
  static final int MIN_PHOTOS = 1;

  static final int MAX_PHOTOS = 10;

  /** {@code ck_film_handovers_roll_count}: 0 .. số cuộn khai báo tối đa (20) + 5. */
  static final int MAX_ROLL_COUNT = 25;

  static final int MAX_NOTE_LENGTH = 500;

  private final FilmHandoverRepository filmHandoverRepository;
  private final FilmHandoverPhotoRepository filmHandoverPhotoRepository;
  private final StorageClient storageClient;
  private final FilmHandoverMapper filmHandoverMapper;
  private final Clock clock;

  /**
   * BR-25: ghi bằng chứng bàn giao. Kiểm đầu vào trước, upload ảnh sau (từng file qua {@code
   * StorageClient}, kiểm magic bytes và ≤ 10 MB), rồi insert {@code film_handovers} và {@code
   * film_handover_photos} ({@code display_order} 1..n). Một ảnh lỗi thì xoá các ảnh đã upload của
   * lần này; transaction rollback sau đó (ở bất kỳ bước nào của domain gọi) cũng xoá file.
   */
  @Override
  @Transactional
  public Long record(RecordFilmHandoverRequest request) {
    Objects.requireNonNull(request.serviceOrderId(), "serviceOrderId");
    Objects.requireNonNull(request.handoverPoint(), "handoverPoint");
    List<MultipartFile> photos = request.photos() == null ? List.of() : request.photos();
    validatePhotoCount(photos.size());
    if (request.rollCount() < 0 || request.rollCount() > MAX_ROLL_COUNT) {
      throw validationError("Số cuộn bàn giao phải trong khoảng 0–" + MAX_ROLL_COUNT + ".");
    }
    String performedByName = blankToNull(request.performedByName());
    if (request.performedByAccountId() == null && performedByName == null) {
      throw validationError("Cần ghi người thực hiện bàn giao.");
    }
    String note = blankToNull(request.note());
    if (note != null && note.codePointCount(0, note.length()) > MAX_NOTE_LENGTH) {
      throw validationError("Ghi chú bàn giao tối đa " + MAX_NOTE_LENGTH + " ký tự.");
    }

    List<String> blobKeys = uploadAll(photos);
    registerRollbackCleanup(blobKeys);

    Instant now = clock.instant();
    FilmHandover handover = new FilmHandover();
    handover.setServiceOrderId(request.serviceOrderId());
    handover.setShipmentId(request.shipmentId());
    handover.setHandoverPoint(request.handoverPoint());
    handover.setRollCount((short) request.rollCount());
    handover.setNote(note);
    handover.setPerformedByAccountId(request.performedByAccountId());
    handover.setPerformedByName(performedByName);
    handover.setOccurredAt(now);
    handover = filmHandoverRepository.save(handover);

    List<FilmHandoverPhoto> rows = new ArrayList<>(blobKeys.size());
    for (int i = 0; i < blobKeys.size(); i++) {
      FilmHandoverPhoto photo = new FilmHandoverPhoto();
      photo.setFilmHandoverId(handover.getId());
      photo.setBlobKey(blobKeys.get(i));
      photo.setDisplayOrder((short) (i + 1));
      photo.setCreatedAt(now);
      rows.add(photo);
    }
    filmHandoverPhotoRepository.saveAll(rows);
    return handover.getId();
  }

  /** BR-25: đọc bàn giao và ảnh bằng hai query; link ảnh có chữ ký (NFR-PRI-02). */
  @Override
  @Transactional(readOnly = true)
  public List<FilmHandoverResponse> getByServiceOrderId(Long serviceOrderId) {
    List<FilmHandover> handovers =
        filmHandoverRepository.findByServiceOrderIdOrderByOccurredAtAscIdAsc(serviceOrderId);
    if (handovers.isEmpty()) {
      return List.of();
    }
    Map<Long, List<FilmHandoverPhoto>> photosByHandover =
        filmHandoverPhotoRepository
            .findByFilmHandoverIdInOrderByFilmHandoverIdAscDisplayOrderAsc(
                handovers.stream().map(FilmHandover::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(FilmHandoverPhoto::getFilmHandoverId));
    return handovers.stream()
        .map(
            handover -> {
              List<FilmHandoverPhotoResponse> photos =
                  photosByHandover.getOrDefault(handover.getId(), List.of()).stream()
                      .map(
                          photo ->
                              filmHandoverMapper.toPhotoResponse(
                                  photo, storageClient.signedUrl(photo.getBlobKey())))
                      .toList();
              return filmHandoverMapper.toResponse(handover, photos);
            })
        .toList();
  }

  /** BR-25, UC23: 1–10 ảnh. */
  private static void validatePhotoCount(int count) {
    if (count < MIN_PHOTOS || count > MAX_PHOTOS) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Cần " + MIN_PHOTOS + "–" + MAX_PHOTOS + " ảnh bằng chứng bàn giao; đã gửi " + count + ".",
          Map.of("field", "photos", "min", MIN_PHOTOS, "max", MAX_PHOTOS));
    }
  }

  private List<String> uploadAll(List<MultipartFile> photos) {
    List<String> blobKeys = new ArrayList<>(photos.size());
    try {
      for (MultipartFile photo : photos) {
        blobKeys.add(upload(photo));
      }
    } catch (RuntimeException ex) {
      deleteQuietly(blobKeys);
      throw ex;
    }
    return blobKeys;
  }

  private String upload(MultipartFile file) {
    try (InputStream content = file.getInputStream()) {
      return storageClient.upload(
          StoragePurpose.HANDOVER_PHOTO, file.getOriginalFilename(), content, file.getSize());
    } catch (IOException ex) {
      throw validationError("Không đọc được file ảnh gửi lên.");
    }
  }

  /** File chỉ bị xoá khi transaction rollback; commit thì giữ (bảng append-only). */
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
        log.warn("Could not delete handover photo blob {}", blobKey, ex);
      }
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static BusinessException validationError(String message) {
    return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
  }
}
