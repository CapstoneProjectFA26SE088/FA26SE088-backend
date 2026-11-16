package com.fa26se088.filmlink.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.account.response.AdminLabApplicationSummaryResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.mapper.account.LabApplicationMapper;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LabApplicationAdminServiceImplTest {

  @Mock private LabRegistrationApplicationRepository applicationRepository;

  private LabApplicationAdminServiceImpl adminService;

  @BeforeEach
  void setUp() {
    adminService = new LabApplicationAdminServiceImpl(applicationRepository, new LabApplicationMapper());
  }

  @Test
  @DisplayName("Admin xem danh sách đơn có lọc theo status PENDING_REVIEW -> trả về PageResponse đúng cờ duplicate")
  void getApplications_withStatus_returnsFilteredPage() {
    Pageable pageable = PageRequest.of(0, 20);

    LabRegistrationApplication app1 = createApp(1L, "LA261114000001", "Lab A", true, LabApplicationStatus.PENDING_REVIEW);
    LabRegistrationApplication app2 = createApp(2L, "LA261114000002", "Lab B", false, LabApplicationStatus.PENDING_REVIEW);

    when(applicationRepository.findByStatus(LabApplicationStatus.PENDING_REVIEW, pageable))
        .thenReturn(new PageImpl<>(List.of(app1, app2), pageable, 2));

    PageResponse<AdminLabApplicationSummaryResponse> result =
        adminService.getApplications(LabApplicationStatus.PENDING_REVIEW, pageable);

    assertThat(result).isNotNull();
    assertThat(result.totalElements()).isEqualTo(2);
    assertThat(result.content()).hasSize(2);

    AdminLabApplicationSummaryResponse first = result.content().get(0);
    assertThat(first.id()).isEqualTo(1L);
    assertThat(first.applicationCode()).isEqualTo("LA261114000001");
    assertThat(first.labName()).isEqualTo("Lab A");
    assertThat(first.isDuplicateSuspected()).isTrue();
    assertThat(first.status()).isEqualTo(LabApplicationStatus.PENDING_REVIEW);

    AdminLabApplicationSummaryResponse second = result.content().get(1);
    assertThat(second.id()).isEqualTo(2L);
    assertThat(second.isDuplicateSuspected()).isFalse();

    verify(applicationRepository).findByStatus(LabApplicationStatus.PENDING_REVIEW, pageable);
  }

  @Test
  @DisplayName("Admin xem danh sách không truyền status (null) -> findAll tất cả các đơn")
  void getApplications_withoutStatus_returnsAll() {
    Pageable pageable = PageRequest.of(0, 10);
    LabRegistrationApplication app = createApp(10L, "LA261114000010", "Lab C", false, LabApplicationStatus.APPROVED);

    when(applicationRepository.findAll(pageable))
        .thenReturn(new PageImpl<>(List.of(app), pageable, 1));

    PageResponse<AdminLabApplicationSummaryResponse> result = adminService.getApplications(null, pageable);

    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.content().getFirst().status()).isEqualTo(LabApplicationStatus.APPROVED);
    verify(applicationRepository).findAll(pageable);
  }

  private LabRegistrationApplication createApp(
      Long id, String code, String name, boolean duplicate, LabApplicationStatus status) {
    LabRegistrationApplication app = new LabRegistrationApplication();
    ReflectionTestUtils.setField(app, "id", id);
    app.setApplicationCode(code);
    app.setLabName(name);
    app.setTaxCode("0123456789");
    app.setProvince("TP Ho Chi Minh");
    app.setDistrict("Quan 1");
    app.setContactEmail("test@lab.vn");
    app.setContactPhone("0901234567");
    app.setStatus(status);
    app.setDuplicateSuspected(duplicate);
    ReflectionTestUtils.setField(app, "createdAt", Instant.now());
    return app;
  }
}
