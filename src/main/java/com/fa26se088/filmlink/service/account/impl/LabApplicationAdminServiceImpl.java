package com.fa26se088.filmlink.service.account.impl;

import com.fa26se088.filmlink.dto.account.response.AdminLabApplicationSummaryResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.mapper.account.LabApplicationMapper;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.service.account.LabApplicationAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai nghiệp vụ quản trị và xét duyệt đơn đăng ký Film Lab cho Admin (UC08).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabApplicationAdminServiceImpl implements LabApplicationAdminService {

  private final LabRegistrationApplicationRepository applicationRepository;
  private final LabApplicationMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public PageResponse<AdminLabApplicationSummaryResponse> getApplications(
      LabApplicationStatus status, Pageable pageable) {
    Page<LabRegistrationApplication> page =
        status != null
            ? applicationRepository.findByStatus(status, pageable)
            : applicationRepository.findAll(pageable);

    return PageResponse.from(page, mapper::toSummaryResponse);
  }
}
