package com.fa26se088.filmlink.service.payment.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.payment.request.ExecutePayoutRequest;
import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.payment.Payout;
import com.fa26se088.filmlink.entity.payment.enums.PayoutRecipientType;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.payment.PayoutMapper;
import com.fa26se088.filmlink.repository.payment.PayoutRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.payment.PayoutService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String CODE_ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

  private final PayoutRepository payoutRepository;
  private final PayoutMapper payoutMapper;
  private final LabAccessService labAccessService;
  private final AuditLogService auditLogService;
  private final Clock clock;

  @Override
  @Transactional
  public PayoutResponse createPayoutForLab(Long filmLabId, Long amountVnd, AuthenticatedAccount adminAccount) {
    if (adminAccount.role() != UserAccountRole.ADMIN) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chỉ Quản trị viên mới được tạo đợt Payout.");
    }
    if (amountVnd == null || amountVnd <= 0) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số tiền Payout phải lớn hơn 0.");
    }

    Payout payout = new Payout();
    payout.setPayoutCode("PO" + generateCode(10));
    payout.setRecipientType(PayoutRecipientType.FILM_LAB);
    payout.setFilmLabId(filmLabId);
    payout.setAmountVnd(amountVnd);
    payout.setStatus(PayoutStatus.PENDING);

    Payout saved = payoutRepository.save(payout);

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.PAYOUT_CREATED,
        AuditTargetType.PAYOUT,
        saved.getId(),
        "Admin tạo phiên Payout " + saved.getPayoutCode() + " cho Lab #" + filmLabId + " số tiền " + amountVnd + " VND",
        null,
        null
    ));

    log.info("[PayoutService] Tạo phiên Payout thành công: code={}, labId={}, amount={}",
        saved.getPayoutCode(), filmLabId, amountVnd);

    return payoutMapper.toResponse(saved);
  }

  @Override
  @Transactional
  public PayoutResponse executePayout(Long payoutId, ExecutePayoutRequest request, AuthenticatedAccount adminAccount) {
    if (adminAccount.role() != UserAccountRole.ADMIN) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chỉ Quản trị viên mới được thực thi Payout.");
    }

    Payout payout = payoutRepository.findById(payoutId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiên Payout."));

    if (payout.getStatus() == PayoutStatus.PAID) {
      log.info("[PayoutService] Payout #{} đã được thanh toán trước đó", payoutId);
      return payoutMapper.toResponse(payout);
    }

    if (payout.getStatus() == PayoutStatus.CANCELLED) {
      throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể thực thi Payout đã bị hủy.");
    }

    Instant now = clock.instant();

    payout.setStatus(PayoutStatus.PAID);
    payout.setBankName(request.bankName());
    payout.setBankAccountNumber(request.bankAccountNumber());
    payout.setAccountHolderName(request.accountHolderName());
    payout.setReceiptBlobKey(request.receiptBlobKey());
    payout.setExecutedByAccountId(adminAccount.id());
    payout.setPaidAt(now);

    Payout saved = payoutRepository.save(payout);

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.PAYOUT_EXECUTED,
        AuditTargetType.PAYOUT,
        saved.getId(),
        "Admin thực thi Payout " + saved.getPayoutCode() + " số tiền " + saved.getAmountVnd() + " VND thành công",
        null,
        null
    ));

    log.info("[PayoutService] Thực thi Payout #{} thành công: receiptKey={}", payoutId, request.receiptBlobKey());

    return payoutMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PayoutResponse> getLabPayouts(
      Long filmLabId, Pageable pageable, AuthenticatedAccount account) {
    if (account.role() != UserAccountRole.ADMIN) {
      FilmLab lab = labAccessService.requireOwnedLab(account.id());
      if (!Objects.equals(filmLabId, lab.getId())) {
        throw new BusinessException(ErrorCode.LAB_ACCESS_DENIED, "Bạn không có quyền xem Payout của lab khác.");
      }
    }
    return payoutRepository.findByFilmLabId(filmLabId, pageable).map(payoutMapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PayoutResponse> getAllPayouts(
      PayoutStatus status, Pageable pageable, AuthenticatedAccount adminAccount) {
    if (adminAccount.role() != UserAccountRole.ADMIN) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chỉ Admin mới có quyền xem toàn bộ danh sách Payout.");
    }
    Page<Payout> page = (status != null)
        ? payoutRepository.findByStatus(status, pageable)
        : payoutRepository.findAll(pageable);
    return page.map(payoutMapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public PayoutResponse getPayoutById(Long payoutId, AuthenticatedAccount account) {
    Payout payout = payoutRepository.findById(payoutId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiên Payout."));

    if (account.role() != UserAccountRole.ADMIN) {
      FilmLab lab = labAccessService.requireOwnedLab(account.id());
      if (!Objects.equals(payout.getFilmLabId(), lab.getId())) {
        throw new BusinessException(ErrorCode.LAB_ACCESS_DENIED, "Bạn không có quyền xem Payout này.");
      }
    }

    return payoutMapper.toResponse(payout);
  }

  private String generateCode(int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
    }
    return sb.toString();
  }
}

