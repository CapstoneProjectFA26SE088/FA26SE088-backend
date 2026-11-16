package com.fa26se088.filmlink.service.account.impl;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.account.response.AdminLabApplicationSummaryResponse;
import com.fa26se088.filmlink.dto.account.response.LabApplicationResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.account.LabApplicationMapper;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.service.account.LabApplicationAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai nghiệp vụ quản trị và xét duyệt đơn đăng ký Film Lab cho Admin (UC08).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabApplicationAdminServiceImpl implements LabApplicationAdminService {

  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  private final LabRegistrationApplicationRepository applicationRepository;
  private final StorageClient storageClient;
  private final LabApplicationMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public PageResponse<AdminLabApplicationSummaryResponse> getApplications(
      LabApplicationStatus status, Pageable pageable) {
    Pageable sorted =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
    Page<LabRegistrationApplication> page =
        status != null
            ? applicationRepository.findByStatus(status, sorted)
            : applicationRepository.findAll(sorted);

    return PageResponse.from(page, mapper::toSummaryResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public LabApplicationResponse getApplicationDetail(Long id) {
    LabRegistrationApplication application =
        applicationRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.LAB_APPLICATION_NOT_FOUND,
                        "Không tìm thấy đơn đăng ký mở lab với ID " + id));

    return mapper.toResponse(application, storageClient);
  }
}
