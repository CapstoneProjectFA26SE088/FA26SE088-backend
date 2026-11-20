package com.fa26se088.filmlink.service.account.impl;

import com.fa26se088.filmlink.client.notification.EmailClient;
import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.account.request.SubmitLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.response.LabApplicationResponse;
import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.LabRegistrationDocument;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.entity.masterdata.FilmFormat;
import com.fa26se088.filmlink.entity.masterdata.FilmProcess;
import com.fa26se088.filmlink.entity.masterdata.ServiceType;
import com.fa26se088.filmlink.entity.masterdata.enums.CatalogStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.account.LabApplicationMapper;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmFormatRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmProcessRepository;
import com.fa26se088.filmlink.repository.masterdata.ServiceTypeRepository;
import com.fa26se088.filmlink.service.account.LabApplicationService;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Triển khai nghiệp vụ nộp và tra cứu đơn đăng ký Film Lab (UC07, BR-16, UH-02).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabApplicationServiceImpl implements LabApplicationService {

  private static final String BASE32_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  private static final int CODE_RANDOM_LENGTH = 6;
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final LabRegistrationApplicationRepository applicationRepository;
  private final ServiceTypeRepository serviceTypeRepository;
  private final FilmProcessRepository filmProcessRepository;
  private final FilmFormatRepository filmFormatRepository;
  private final StorageClient storageClient;
  private final EmailClient emailClient;
  private final LabApplicationMapper mapper;

  @Override
  @Transactional
  public LabApplicationResponse submitApplication(
      SubmitLabApplicationRequest request, List<MultipartFile> documents) {
    if (documents == null || documents.isEmpty() || documents.size() > 5) {
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_DOCUMENT_COUNT_INVALID,
          "Hồ sơ đăng ký phải đính kèm từ 1 đến 5 tài liệu pháp lý");
    }

    // BR-16: Kiểm tra tính hợp lệ và trạng thái ACTIVE của Master Data
    Set<ServiceType> serviceTypes = validateServiceTypes(request.serviceTypeIds());
    Set<FilmProcess> filmProcesses = validateFilmProcesses(request.filmProcessIds());
    Set<FilmFormat> filmFormats = validateFilmFormats(request.filmFormatIds());

    // Atomic Operation: Upload các file lên storage, nếu có lỗi thì dọn dẹp blob đã tải
    List<String> uploadedKeys = new ArrayList<>();
    List<LabRegistrationDocument> documentEntities = new ArrayList<>();
    registerRollbackCleanup(uploadedKeys);

    try {
      for (MultipartFile file : documents) {
        if (file == null || file.isEmpty()) {
          continue;
        }
        String filename =
            file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String contentType =
            file.getContentType() != null ? file.getContentType() : "application/pdf";

        String blobKey =
            storageClient.upload(
                StoragePurpose.LAB_DOCUMENT, filename, file.getInputStream(), file.getSize());
        uploadedKeys.add(blobKey);

        LabRegistrationDocument doc = new LabRegistrationDocument();
        doc.setFileName(filename);
        doc.setBlobKey(blobKey);
        doc.setContentType(contentType);
        doc.setSizeBytes(file.getSize());
        documentEntities.add(doc);
      }
    } catch (IOException e) {
      cleanupBlobs(uploadedKeys);
      throw new BusinessException(
          ErrorCode.STORAGE_UNAVAILABLE, "Không thể đọc nội dung file tải lên");
    } catch (RuntimeException e) {
      cleanupBlobs(uploadedKeys);
      throw e;
    }

    if (documentEntities.isEmpty()) {
      cleanupBlobs(uploadedKeys);
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_DOCUMENT_COUNT_INVALID,
          "Hồ sơ đăng ký cần ít nhất 1 file tài liệu hợp lệ");
    }

    // UH-02: Kiểm tra trùng lặp mã số thuế hoặc địa chỉ
    boolean dupTax = applicationRepository.existsByTaxCode(request.taxCode().trim());
    boolean dupAddress =
        applicationRepository
            .existsByAddressLineIgnoreCaseAndDistrictIgnoreCaseAndProvinceIgnoreCase(
                request.addressLine().trim(),
                request.district().trim(),
                request.province().trim());
    boolean isDuplicateSuspected = dupTax || dupAddress;

    // Sinh mã tra cứu duy nhất: LA + yyMMdd + 6 ký tự Base32 (MSG14)
    String applicationCode = generateUniqueApplicationCode();

    LabRegistrationApplication application = new LabRegistrationApplication();
    application.setApplicationCode(applicationCode);
    application.setLabName(request.labName().trim());
    application.setTaxCode(request.taxCode().trim());
    application.setAddressLine(request.addressLine().trim());
    application.setDistrict(request.district().trim());
    application.setProvince(request.province().trim());
    application.setContactEmail(request.contactEmail().trim().toLowerCase(Locale.ROOT));
    application.setContactPhone(request.contactPhone().trim());
    application.setStatus(LabApplicationStatus.PENDING_REVIEW);
    application.setDuplicateSuspected(isDuplicateSuspected);
    application.setServiceTypes(serviceTypes);
    application.setFilmProcesses(filmProcesses);
    application.setFilmFormats(filmFormats);

    for (LabRegistrationDocument doc : documentEntities) {
      application.addDocument(doc);
    }

    application = applicationRepository.save(application);

    // Gửi email xác nhận nộp đơn kèm mã applicationCode
    sendConfirmationEmail(application);

    log.info(
        "Nộp đơn đăng ký lab thành công: code={}, labName={}, isDuplicate={}",
        applicationCode,
        application.getLabName(),
        isDuplicateSuspected);

    return mapper.toResponse(application, storageClient);
  }

  @Override
  @Transactional(readOnly = true)
  public LabApplicationResponse getApplicationByCode(String applicationCode) {
    LabRegistrationApplication application =
        applicationRepository
            .findByApplicationCode(applicationCode)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.LAB_APPLICATION_NOT_FOUND,
                        "Không tìm thấy đơn đăng ký với mã " + applicationCode));
    return mapper.toResponse(application, storageClient);
  }

  private Set<ServiceType> validateServiceTypes(Set<Long> ids) {
    List<ServiceType> list = serviceTypeRepository.findAllById(ids);
    if (list.size() != ids.size()
        || list.stream().anyMatch(s -> s.getStatus() != CatalogStatus.ACTIVE)) {
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_CATALOG_NOT_ACTIVE,
          "Một hoặc nhiều loại dịch vụ đã chọn không tồn tại hoặc không ở trạng thái ACTIVE");
    }
    return new HashSet<>(list);
  }

  private Set<FilmProcess> validateFilmProcesses(Set<Long> ids) {
    List<FilmProcess> list = filmProcessRepository.findAllById(ids);
    if (list.size() != ids.size()
        || list.stream().anyMatch(p -> p.getStatus() != CatalogStatus.ACTIVE)) {
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_CATALOG_NOT_ACTIVE,
          "Một hoặc nhiều quy trình tráng đã chọn không tồn tại hoặc không ở trạng thái ACTIVE");
    }
    return new HashSet<>(list);
  }

  private Set<FilmFormat> validateFilmFormats(Set<Long> ids) {
    List<FilmFormat> list = filmFormatRepository.findAllById(ids);
    if (list.size() != ids.size()
        || list.stream().anyMatch(f -> f.getStatus() != CatalogStatus.ACTIVE)) {
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_CATALOG_NOT_ACTIVE,
          "Một hoặc nhiều khổ phim đã chọn không tồn tại hoặc không ở trạng thái ACTIVE");
    }
    return new HashSet<>(list);
  }

  private String generateUniqueApplicationCode() {
    String datePart = LocalDate.now().format(DATE_FORMAT);
    for (int attempt = 0; attempt < 5; attempt++) {
      StringBuilder sb = new StringBuilder("LA").append(datePart);
      for (int i = 0; i < CODE_RANDOM_LENGTH; i++) {
        sb.append(BASE32_CHARS.charAt(RANDOM.nextInt(BASE32_CHARS.length())));
      }
      String code = sb.toString();
      if (!applicationRepository.existsByApplicationCode(code)) {
        return code;
      }
    }
    throw new BusinessException(
        ErrorCode.INTERNAL_ERROR, "Không thể sinh mã hồ sơ duy nhất, vui lòng thử lại");
  }

  private void sendConfirmationEmail(LabRegistrationApplication application) {
    try {
      String subject =
          "FilmLink - Đã tiếp nhận đơn đăng ký mở Film Lab ["
              + application.getApplicationCode()
              + "]";
      String body =
          String.format(
              "Xin chào %s,\n\n"
                  + "FilmLink đã tiếp nhận đơn đăng ký mở phòng lab của bạn.\n"
                  + "Mã tra cứu hồ sơ: %s\n"
                  + "Trạng thái: Đang chờ xét duyệt (PENDING_REVIEW)\n\n"
                  + "Ban quản trị sẽ xem xét hồ sơ và phản hồi kết quả trong thời gian sớm nhất.\n"
                  + "Trân trọng,\nĐội ngũ FilmLink.",
              application.getLabName(), application.getApplicationCode());
      emailClient.send(application.getContactEmail(), subject, body);
    } catch (Exception e) {
      log.warn(
          "Không thể gửi email xác nhận nộp đơn tới {}: {}",
          application.getContactEmail(),
          e.getMessage());
    }
  }

  private void registerRollbackCleanup(List<String> uploadedKeys) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_ROLLED_BACK) {
                cleanupBlobs(uploadedKeys);
              }
            }
          });
    }
  }

  private void cleanupBlobs(List<String> keys) {
    for (String key : keys) {
      try {
        storageClient.delete(key);
      } catch (Exception e) {
        log.warn("Không thể xóa blob khi rollback hồ sơ: {}", key, e);
      }
    }
  }
}

