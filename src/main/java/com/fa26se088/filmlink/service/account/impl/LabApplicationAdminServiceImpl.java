package com.fa26se088.filmlink.service.account.impl;

import com.fa26se088.filmlink.client.notification.EmailClient;
import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.account.request.ApproveLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.request.RejectLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.response.AdminLabApplicationSummaryResponse;
import com.fa26se088.filmlink.dto.account.response.LabApplicationResponse;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
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
import com.fa26se088.filmlink.service.account.LabApplicationAdminService;
import com.fa26se088.filmlink.service.account.event.LabRegistrationApprovedEvent;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Triển khai nghiệp vụ quản trị và xét duyệt đơn đăng ký Film Lab cho Admin (UC08).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabApplicationAdminServiceImpl implements LabApplicationAdminService {

  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
  private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
  private static final String DIGITS = "23456789";
  private static final String SPECIAL = "@#$%!&*";
  private static final String ALL_PASSWORD_CHARS = UPPER + LOWER + DIGITS + SPECIAL;
  private static final int TEMP_PASSWORD_LENGTH = 12;
  private static final int MAX_FULL_NAME_LENGTH = 100;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final LabRegistrationApplicationRepository applicationRepository;
  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditLogService auditLogService;
  private final EmailClient emailClient;
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
    LabRegistrationApplication application = findApplicationOrThrow(id);
    return mapper.toResponse(application, storageClient);
  }

  @Override
  @Transactional
  public LabApplicationResponse approveApplication(
      Long id, ApproveLabApplicationRequest request, Long adminAccountId) {
    LabRegistrationApplication application = findApplicationOrThrow(id);

    // 1. Chỉ cho phép duyệt đơn ở trạng thái PENDING_REVIEW
    if (application.getStatus() != LabApplicationStatus.PENDING_REVIEW) {
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_ALREADY_REVIEWED,
          "Đơn đăng ký đã được xử lý hoặc không ở trạng thái chờ duyệt (PENDING_REVIEW)");
    }

    // Kiểm tra version (Optimistic Locking) -> 409 nếu xung đột
    if (!Objects.equals(application.getVersion(), request.version())) {
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_CONFLICT,
          "Đơn đăng ký đã bị thay đổi bởi một Admin khác. Vui lòng tải lại trang và thử lại.",
          Map.of(
              "currentVersion", application.getVersion(),
              "submittedVersion", request.version()));
    }

    // 2. Xác định email chủ lab và kiểm tra trùng lặp
    String rawEmail =
        (request.accountEmail() != null && !request.accountEmail().isBlank())
            ? request.accountEmail()
            : application.getContactEmail();
    String ownerEmail = rawEmail.trim().toLowerCase(Locale.ROOT);

    if (userAccountRepository.existsByEmail(ownerEmail)) {
      throw new BusinessException(
          ErrorCode.ACCOUNT_EMAIL_ALREADY_EXISTS,
          "Email " + ownerEmail + " đã được sử dụng trong hệ thống.");
    }

    // Tạo bản ghi user_accounts với role FILM_LAB_OWNER, status ACTIVE
    String tempPassword = generateTemporaryPassword();
    Instant now = Instant.now();

    UserAccount ownerAccount = new UserAccount();
    ownerAccount.setEmail(ownerEmail);
    ownerAccount.setPasswordHash(passwordEncoder.encode(tempPassword));
    ownerAccount.setFullName(truncateFullName(application.getLabName()));
    ownerAccount.setPhoneNumber(application.getContactPhone());
    ownerAccount.setRole(UserAccountRole.FILM_LAB_OWNER);
    ownerAccount.setStatus(UserAccountStatus.ACTIVE);
    ownerAccount.setEmailVerifiedAt(now);
    ownerAccount.setTermsAcceptedAt(now);

    try {
      ownerAccount = userAccountRepository.saveAndFlush(ownerAccount);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(
          ErrorCode.ACCOUNT_EMAIL_ALREADY_EXISTS,
          "Email " + ownerEmail + " đã được sử dụng trong hệ thống.");
    }

    // 3. Cập nhật đơn sang APPROVED
    application.setStatus(LabApplicationStatus.APPROVED);
    application.setReviewedByAccountId(adminAccountId);
    application.setReviewedAt(now);

    try {
      application = applicationRepository.saveAndFlush(application);
    } catch (OptimisticLockingFailureException ex) {
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_CONFLICT,
          "Đơn đăng ký đã bị thay đổi bởi một Admin khác. Vui lòng tải lại trang và thử lại.");
    }

    // 4. Phát sự kiện LabRegistrationApprovedEvent ngay bên trong transaction
    eventPublisher.publishEvent(
        new LabRegistrationApprovedEvent(
            application.getId(),
            ownerAccount.getId(),
            application.getLabName(),
            application.getAddressLine(),
            application.getDistrict(),
            application.getProvince(),
            application.getLatitude(),
            application.getLongitude(),
            application.getContactEmail(),
            application.getContactPhone()));

    // 5. Ghi vết Audit Log (Actor: Admin, Target: Application ID)
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.LAB_APPLICATION_APPROVED,
            AuditTargetType.LAB_REGISTRATION_APPLICATION,
            application.getId(),
            "Admin duyệt đơn đăng ký mở Film Lab: " + application.getApplicationCode(),
            Map.of("status", LabApplicationStatus.PENDING_REVIEW.name()),
            Map.of(
                "status", LabApplicationStatus.APPROVED.name(),
                "ownerAccountId", ownerAccount.getId(),
                "ownerEmail", ownerEmail)));

    // 6. Sau khi commit transaction thành công: Gửi email mật khẩu tạm cho Chủ Lab
    String labName = application.getLabName();
    String applicationCode = application.getApplicationCode();
    runAfterCommit(() -> sendApprovalEmail(ownerEmail, labName, applicationCode, tempPassword));

    log.info(
        "Duyệt đơn đăng ký lab thành công: id={}, code={}, ownerAccountId={}",
        application.getId(),
        applicationCode,
        ownerAccount.getId());

    return mapper.toResponse(application, storageClient);
  }

  @Override
  @Transactional
  public LabApplicationResponse rejectApplication(
      Long id, RejectLabApplicationRequest request, Long adminAccountId) {
    LabRegistrationApplication application = findApplicationOrThrow(id);

    // 1. Chỉ cho phép từ chối đơn ở trạng thái PENDING_REVIEW
    if (application.getStatus() != LabApplicationStatus.PENDING_REVIEW) {
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_ALREADY_REVIEWED,
          "Đơn đăng ký đã được xử lý hoặc không ở trạng thái chờ duyệt (PENDING_REVIEW)");
    }

    // 2. Kiểm tra version (Optimistic Locking) -> 409 nếu xung đột
    if (!Objects.equals(application.getVersion(), request.version())) {
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_CONFLICT,
          "Đơn đăng ký đã bị thay đổi bởi một Admin khác. Vui lòng tải lại trang và thử lại.",
          Map.of(
              "currentVersion", application.getVersion(),
              "submittedVersion", request.version()));
    }

    String trimmedReason = request.reason().trim();
    if (trimmedReason.length() < 10 || trimmedReason.length() > 500) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Lý do từ chối phải từ 10 đến 500 ký tự.");
    }

    // 3. Cập nhật trạng thái đơn sang REJECTED
    application.setStatus(LabApplicationStatus.REJECTED);
    application.setRejectionReason(trimmedReason);
    application.setReviewedByAccountId(adminAccountId);
    application.setReviewedAt(Instant.now());

    try {
      application = applicationRepository.saveAndFlush(application);
    } catch (OptimisticLockingFailureException ex) {
      throw new BusinessException(
          ErrorCode.LAB_APPLICATION_CONFLICT,
          "Đơn đăng ký đã bị thay đổi bởi một Admin khác. Vui lòng tải lại trang và thử lại.");
    }

    // 4. Ghi vết Audit Log
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.LAB_APPLICATION_REJECTED,
            AuditTargetType.LAB_REGISTRATION_APPLICATION,
            application.getId(),
            "Admin từ chối đơn đăng ký mở Film Lab: " + application.getApplicationCode(),
            Map.of("status", LabApplicationStatus.PENDING_REVIEW.name()),
            Map.of(
                "status", LabApplicationStatus.REJECTED.name(),
                "rejectionReason", trimmedReason)));

    // 5. Sau khi commit transaction thành công: Gửi email thông báo lý do từ chối
    String contactEmail = application.getContactEmail();
    String labName = application.getLabName();
    String applicationCode = application.getApplicationCode();
    runAfterCommit(
        () -> sendRejectionEmail(contactEmail, labName, applicationCode, trimmedReason));

    log.info(
        "Từ chối đơn đăng ký lab: id={}, code={}, reason={}",
        application.getId(),
        applicationCode,
        trimmedReason);

    return mapper.toResponse(application, storageClient);
  }

  private LabRegistrationApplication findApplicationOrThrow(Long id) {
    return applicationRepository
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.LAB_APPLICATION_NOT_FOUND,
                    "Không tìm thấy đơn đăng ký mở lab với ID " + id));
  }

  private static String truncateFullName(String labName) {
    String trimmed = labName.trim();
    return trimmed.length() > MAX_FULL_NAME_LENGTH
        ? trimmed.substring(0, MAX_FULL_NAME_LENGTH)
        : trimmed;
  }

  private static String generateTemporaryPassword() {
    char[] chars = new char[TEMP_PASSWORD_LENGTH];
    chars[0] = UPPER.charAt(RANDOM.nextInt(UPPER.length()));
    chars[1] = LOWER.charAt(RANDOM.nextInt(LOWER.length()));
    chars[2] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
    chars[3] = SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length()));
    for (int i = 4; i < TEMP_PASSWORD_LENGTH; i++) {
      chars[i] = ALL_PASSWORD_CHARS.charAt(RANDOM.nextInt(ALL_PASSWORD_CHARS.length()));
    }
    for (int i = chars.length - 1; i > 0; i--) {
      int j = RANDOM.nextInt(i + 1);
      char temp = chars[i];
      chars[i] = chars[j];
      chars[j] = temp;
    }
    return new String(chars);
  }

  private void runAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }

  private void sendApprovalEmail(
      String recipientEmail, String labName, String applicationCode, String tempPassword) {
    try {
      String subject =
          "FilmLink - Đơn đăng ký mở Film Lab đã được phê duyệt [" + applicationCode + "]";
      String body =
          String.format(
              "Xin chào %s,\n\n"
                  + "Chúc mừng! Đơn đăng ký mở phòng lab (mã hồ sơ: %s) của bạn đã được phê duyệt.\n"
                  + "Tài khoản Chủ Phòng Lab (FILM_LAB_OWNER) của bạn đã được kích hoạt:\n"
                  + "- Email đăng nhập: %s\n"
                  + "- Mật khẩu tạm thời: %s\n\n"
                  + "Vui lòng đăng nhập vào hệ thống FilmLink, đổi mật khẩu và thiết lập hồ sơ lab"
                  + " cũng như máy tráng/scan để bắt đầu nhận đơn hàng.\n\n"
                  + "Trân trọng,\nĐội ngũ FilmLink.",
              labName, applicationCode, recipientEmail, tempPassword);
      emailClient.send(recipientEmail, subject, body);
    } catch (Exception e) {
      log.warn("Không thể gửi email thông báo duyệt đơn tới {}: {}", recipientEmail, e.getMessage());
    }
  }

  private void sendRejectionEmail(
      String recipientEmail, String labName, String applicationCode, String reason) {
    try {
      String subject =
          "FilmLink - Thông báo kết quả xét duyệt đơn đăng ký mở Film Lab ["
              + applicationCode
              + "]";
      String body =
          String.format(
              "Xin chào %s,\n\n"
                  + "Đơn đăng ký mở phòng lab (mã hồ sơ: %s) của bạn chưa được phê duyệt.\n"
                  + "Lý do từ chối: %s\n\n"
                  + "Bạn có thể điều chỉnh thông tin và nộp lại hồ sơ mới trên hệ thống FilmLink.\n\n"
                  + "Trân trọng,\nĐội ngũ FilmLink.",
              labName, applicationCode, reason);
      emailClient.send(recipientEmail, subject, body);
    } catch (Exception e) {
      log.warn(
          "Không thể gửi email thông báo từ chối đơn tới {}: {}",
          recipientEmail,
          e.getMessage());
    }
  }
}
