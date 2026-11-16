package com.fa26se088.filmlink.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.client.notification.EmailClient;
import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.account.request.ApproveLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.request.RejectLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.response.AdminLabApplicationSummaryResponse;
import com.fa26se088.filmlink.dto.account.response.LabApplicationResponse;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.LabRegistrationDocument;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.account.enums.UserAccountStatus;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.account.LabApplicationMapper;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.service.account.event.LabRegistrationApprovedEvent;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LabApplicationAdminServiceImplTest {

  @Mock private LabRegistrationApplicationRepository applicationRepository;
  @Mock private UserAccountRepository userAccountRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private AuditLogService auditLogService;
  @Mock private EmailClient emailClient;
  @Mock private StorageClient storageClient;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
  private LabApplicationAdminServiceImpl adminService;

  @BeforeEach
  void setUp() {
    adminService =
        new LabApplicationAdminServiceImpl(
            applicationRepository,
            userAccountRepository,
            passwordEncoder,
            eventPublisher,
            auditLogService,
            emailClient,
            storageClient,
            new LabApplicationMapper());
  }

  @Test
  @DisplayName("Admin xem danh sách đơn có lọc theo status PENDING_REVIEW -> trả về PageResponse đúng cờ duplicate")
  void getApplications_withStatus_returnsFilteredPage() {
    Pageable pageable = PageRequest.of(0, 20);

    LabRegistrationApplication app1 =
        createApp(1L, "LA261114000001", "Lab A", true, LabApplicationStatus.PENDING_REVIEW);
    LabRegistrationApplication app2 =
        createApp(2L, "LA261114000002", "Lab B", false, LabApplicationStatus.PENDING_REVIEW);

    when(applicationRepository.findByStatus(
            eq(LabApplicationStatus.PENDING_REVIEW), any(Pageable.class)))
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

    verify(applicationRepository)
        .findByStatus(eq(LabApplicationStatus.PENDING_REVIEW), any(Pageable.class));
  }

  @Test
  @DisplayName("Admin xem danh sách không truyền status (null) -> findAll tất cả các đơn")
  void getApplications_withoutStatus_returnsAll() {
    Pageable pageable = PageRequest.of(0, 10);
    LabRegistrationApplication app =
        createApp(10L, "LA261114000010", "Lab C", false, LabApplicationStatus.APPROVED);

    when(applicationRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(app), pageable, 1));

    PageResponse<AdminLabApplicationSummaryResponse> result =
        adminService.getApplications(null, pageable);

    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.content().getFirst().status()).isEqualTo(LabApplicationStatus.APPROVED);
    verify(applicationRepository).findAll(any(Pageable.class));
  }

  @Test
  @DisplayName("Admin xem chi tiết đơn đăng ký theo ID thành công -> trả về đầy đủ kèm signed URL tài liệu")
  void getApplicationDetail_success() {
    LabRegistrationApplication app =
        createApp(
            5L, "LA261114000005", "Saigon Darkroom", false, LabApplicationStatus.PENDING_REVIEW);

    LabRegistrationDocument doc = new LabRegistrationDocument();
    ReflectionTestUtils.setField(doc, "id", 101L);
    doc.setFileName("business_license.pdf");
    doc.setBlobKey("lab-doc/2026/license.pdf");
    doc.setContentType("application/pdf");
    doc.setSizeBytes(1024L);
    app.addDocument(doc);

    when(applicationRepository.findById(5L)).thenReturn(Optional.of(app));
    when(storageClient.signedUrl(anyString()))
        .thenReturn(URI.create("https://storage.local/signed/license.pdf"));

    LabApplicationResponse response = adminService.getApplicationDetail(5L);

    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(5L);
    assertThat(response.applicationCode()).isEqualTo("LA261114000005");
    assertThat(response.labName()).isEqualTo("Saigon Darkroom");
    assertThat(response.documents()).hasSize(1);
    assertThat(response.documents().getFirst().url())
        .isEqualTo("https://storage.local/signed/license.pdf");

    verify(applicationRepository).findById(5L);
    verify(storageClient).signedUrl("lab-doc/2026/license.pdf");
  }

  @Test
  @DisplayName("Admin xem chi tiết ID không tồn tại -> ném ngoại lệ LAB_APPLICATION_NOT_FOUND")
  void getApplicationDetail_notFound_throwsException() {
    when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminService.getApplicationDetail(999L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LAB_APPLICATION_NOT_FOUND));

    verify(applicationRepository).findById(999L);
  }

  @Test
  @DisplayName(
      "TC-F03-01 & TC-SM02-02: Admin duyệt đơn PENDING_REVIEW thành công -> APPROVED, tạo FILM_LAB_OWNER, phát event, ghi audit, gửi email")
  void approveApplication_success() {
    LabRegistrationApplication app =
        createApp(
            15L, "LA261116APPROV", "Saigon Analog Lab", false, LabApplicationStatus.PENDING_REVIEW);
    app.setVersion(0);

    when(applicationRepository.findById(15L)).thenReturn(Optional.of(app));
    when(userAccountRepository.existsByEmail("owner@saigonanalog.vn")).thenReturn(false);
    when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
        .thenAnswer(
            inv -> {
              UserAccount acc = inv.getArgument(0);
              ReflectionTestUtils.setField(acc, "id", 500L);
              return acc;
            });
    when(applicationRepository.saveAndFlush(any(LabRegistrationApplication.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ApproveLabApplicationRequest request =
        new ApproveLabApplicationRequest(0, "Owner@SaigonAnalog.vn");

    LabApplicationResponse response = adminService.approveApplication(15L, request, 99L);

    assertThat(response.status()).isEqualTo(LabApplicationStatus.APPROVED);
    assertThat(response.reviewedByAccountId()).isEqualTo(99L);
    assertThat(response.reviewedAt()).isNotNull();

    // Kiểm tra UserAccount được tạo đúng role FILM_LAB_OWNER và ACTIVE
    ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
    verify(userAccountRepository).saveAndFlush(userCaptor.capture());
    UserAccount createdOwner = userCaptor.getValue();
    assertThat(createdOwner.getEmail()).isEqualTo("owner@saigonanalog.vn");
    assertThat(createdOwner.getRole()).isEqualTo(UserAccountRole.FILM_LAB_OWNER);
    assertThat(createdOwner.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
    assertThat(createdOwner.getPasswordHash()).isNotBlank();

    // Kiểm tra phát sự kiện LabRegistrationApprovedEvent
    ArgumentCaptor<LabRegistrationApprovedEvent> eventCaptor =
        ArgumentCaptor.forClass(LabRegistrationApprovedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    LabRegistrationApprovedEvent publishedEvent = eventCaptor.getValue();
    assertThat(publishedEvent.labRegistrationApplicationId()).isEqualTo(15L);
    assertThat(publishedEvent.ownerAccountId()).isEqualTo(500L);
    assertThat(publishedEvent.labName()).isEqualTo("Saigon Analog Lab");

    // Kiểm tra ghi Audit Log
    ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(auditCaptor.capture());
    AuditEntry auditEntry = auditCaptor.getValue();
    assertThat(auditEntry.action()).isEqualTo(AuditAction.LAB_APPLICATION_APPROVED);
    assertThat(auditEntry.targetType()).isEqualTo(AuditTargetType.LAB_REGISTRATION_APPLICATION);
    assertThat(auditEntry.targetId()).isEqualTo(15L);

    // Kiểm tra gửi email mật khẩu tạm
    verify(emailClient).send(eq("owner@saigonanalog.vn"), anyString(), anyString());
  }

  @Test
  @DisplayName("Duyệt đơn không ở trạng thái PENDING_REVIEW -> ném 409 LAB_APPLICATION_ALREADY_REVIEWED")
  void approveApplication_notPendingReview_throwsConflict() {
    LabRegistrationApplication app =
        createApp(16L, "LA261116000016", "Lab X", false, LabApplicationStatus.APPROVED);
    app.setVersion(1);
    when(applicationRepository.findById(16L)).thenReturn(Optional.of(app));

    ApproveLabApplicationRequest request = new ApproveLabApplicationRequest(1, null);

    assertThatThrownBy(() -> adminService.approveApplication(16L, request, 99L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex ->
                assertThat(ex.getErrorCode())
                    .isEqualTo(ErrorCode.LAB_APPLICATION_ALREADY_REVIEWED));

    verify(userAccountRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("TC-F03-03: Duyệt đơn sai version (Optimistic Locking) -> ném 409 LAB_APPLICATION_CONFLICT")
  void approveApplication_versionMismatch_throwsConflict() {
    LabRegistrationApplication app =
        createApp(17L, "LA261116000017", "Lab Y", false, LabApplicationStatus.PENDING_REVIEW);
    app.setVersion(2);
    when(applicationRepository.findById(17L)).thenReturn(Optional.of(app));

    ApproveLabApplicationRequest request = new ApproveLabApplicationRequest(0, null);

    assertThatThrownBy(() -> adminService.approveApplication(17L, request, 99L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LAB_APPLICATION_CONFLICT));

    verify(userAccountRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName(
      "TC-SM02-03: Admin từ chối đơn PENDING_REVIEW thành công -> REJECTED, lưu lý do, ghi audit, gửi email")
  void rejectApplication_success() {
    LabRegistrationApplication app =
        createApp(
            20L, "LA261116REJECT", "Hue Film Lab", false, LabApplicationStatus.PENDING_REVIEW);
    app.setVersion(0);

    when(applicationRepository.findById(20L)).thenReturn(Optional.of(app));
    when(applicationRepository.saveAndFlush(any(LabRegistrationApplication.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RejectLabApplicationRequest request =
        new RejectLabApplicationRequest(0, "Giấy phép kinh doanh không rõ nét và thiếu con dấu");

    LabApplicationResponse response = adminService.rejectApplication(20L, request, 99L);

    assertThat(response.status()).isEqualTo(LabApplicationStatus.REJECTED);
    assertThat(response.rejectionReason())
        .isEqualTo("Giấy phép kinh doanh không rõ nét và thiếu con dấu");
    assertThat(response.reviewedByAccountId()).isEqualTo(99L);
    assertThat(response.reviewedAt()).isNotNull();

    // Không tạo tài khoản và không phát sự kiện
    verify(userAccountRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publishEvent(any());

    // Kiểm tra ghi Audit Log
    ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(auditCaptor.capture());
    AuditEntry auditEntry = auditCaptor.getValue();
    assertThat(auditEntry.action()).isEqualTo(AuditAction.LAB_APPLICATION_REJECTED);
    assertThat(auditEntry.targetType()).isEqualTo(AuditTargetType.LAB_REGISTRATION_APPLICATION);
    assertThat(auditEntry.targetId()).isEqualTo(20L);

    // Kiểm tra gửi email thông báo từ chối
    verify(emailClient).send(eq("test@lab.vn"), anyString(), anyString());
  }

  @Test
  @DisplayName("Từ chối đơn sai version -> ném 409 LAB_APPLICATION_CONFLICT")
  void rejectApplication_versionMismatch_throwsConflict() {
    LabRegistrationApplication app =
        createApp(21L, "LA261116000021", "Lab Z", false, LabApplicationStatus.PENDING_REVIEW);
    app.setVersion(3);
    when(applicationRepository.findById(21L)).thenReturn(Optional.of(app));

    RejectLabApplicationRequest request =
        new RejectLabApplicationRequest(0, "Hồ sơ pháp lý không hợp lệ theo quy định");

    assertThatThrownBy(() -> adminService.rejectApplication(21L, request, 99L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LAB_APPLICATION_CONFLICT));

    verify(applicationRepository, never()).saveAndFlush(any());
  }

  private LabRegistrationApplication createApp(
      Long id, String code, String name, boolean duplicate, LabApplicationStatus status) {
    LabRegistrationApplication app = new LabRegistrationApplication();
    ReflectionTestUtils.setField(app, "id", id);
    app.setApplicationCode(code);
    app.setLabName(name);
    app.setTaxCode("0123456789");
    app.setAddressLine("123 Nguyen Hue");
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
