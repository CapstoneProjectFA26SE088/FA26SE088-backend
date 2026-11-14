package com.fa26se088.filmlink.mapper.account;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.account.response.LabApplicationDocumentResponse;
import com.fa26se088.filmlink.dto.account.response.LabApplicationResponse;
import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.LabRegistrationDocument;
import com.fa26se088.filmlink.entity.BaseEntity;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi entity {@link LabRegistrationApplication} và {@link LabRegistrationDocument} sang DTO.
 */
@Component
public class LabApplicationMapper {

  /**
   * Chuyển đổi entity đơn đăng ký sang DTO phản hồi đầy đủ.
   */
  public LabApplicationResponse toResponse(
      LabRegistrationApplication application, StorageClient storageClient) {
    List<LabApplicationDocumentResponse> documentResponses =
        application.getDocuments().stream()
            .map(doc -> toDocumentResponse(doc, storageClient))
            .toList();

    return new LabApplicationResponse(
        application.getId(),
        application.getApplicationCode(),
        application.getLabName(),
        application.getTaxCode(),
        application.getAddressLine(),
        application.getDistrict(),
        application.getProvince(),
        application.getLatitude(),
        application.getLongitude(),
        application.getContactEmail(),
        application.getContactPhone(),
        application.getStatus(),
        application.isDuplicateSuspected(),
        application.getServiceTypes().stream()
            .map(BaseEntity::getId)
            .collect(Collectors.toSet()),
        application.getFilmProcesses().stream()
            .map(BaseEntity::getId)
            .collect(Collectors.toSet()),
        application.getFilmFormats().stream()
            .map(BaseEntity::getId)
            .collect(Collectors.toSet()),
        documentResponses,
        application.getCreatedAt());
  }

  /**
   * Chuyển đổi entity tài liệu sang DTO đính kèm signed URL.
   */
  public LabApplicationDocumentResponse toDocumentResponse(
      LabRegistrationDocument document, StorageClient storageClient) {
    String url = null;
    if (storageClient != null && document.getBlobKey() != null) {
      try {
        url = storageClient.signedUrl(document.getBlobKey()).toString();
      } catch (Exception ignored) {
        url = null;
      }
    }
    return new LabApplicationDocumentResponse(
        document.getId(),
        document.getFileName(),
        document.getContentType(),
        document.getSizeBytes(),
        url);
  }
}

