package com.fa26se088.filmlink.service.payment.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.payment.request.ReportDebtPaymentRequest;
import com.fa26se088.filmlink.dto.payment.response.CommissionDebtResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.payment.Commission;
import com.fa26se088.filmlink.entity.payment.CommissionDebt;
import com.fa26se088.filmlink.entity.payment.Payment;
import com.fa26se088.filmlink.entity.payment.enums.CommissionDebtStatus;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import com.fa26se088.filmlink.entity.payment.enums.PaymentConfirmationStatus;
import com.fa26se088.filmlink.entity.payment.enums.PaymentDirection;
import com.fa26se088.filmlink.entity.payment.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import com.fa26se088.filmlink.entity.payment.enums.PaymentStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.payment.CommissionDebtMapper;
import com.fa26se088.filmlink.repository.payment.CommissionDebtRepository;
import com.fa26se088.filmlink.repository.payment.CommissionRepository;
import com.fa26se088.filmlink.repository.payment.PaymentRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.payment.CommissionDebtService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionDebtServiceImpl implements CommissionDebtService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String TXN_CHARSET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

  private final CommissionDebtRepository debtRepository;
  private final CommissionRepository commissionRepository;
  private final PaymentRepository paymentRepository;
  private final CommissionDebtMapper debtMapper;
  private final LabAccessService labAccessService;
  private final AuditLogService auditLogService;
  private final Clock clock;

  @Override
  @Transactional
  public CommissionDebtResponse generateMonthlyDebt(Long labId, LocalDate period) {
    // Đảm bảo period là ngày đầu tháng (2026-10-01)
    LocalDate firstDay = period.withDayOfMonth(1);

    // Kiểm tra tính Idempotent: Kỳ nợ labId + period đã tạo chưa
    Optional<CommissionDebt> existing = debtRepository.findByFilmLabIdAndBillingPeriod(labId, firstDay);
    if (existing.isPresent()) {
      log.info("[DebtService] Idempotent hit: Kỳ nợ lab #{} tháng {} đã tồn tại", labId, firstDay);
      return debtMapper.toResponse(existing.get());
    }

    // Thời điểm kết thúc của kỳ tính nợ (cuối tháng period)
    LocalDate nextMonthFirstDay = firstDay.plusMonths(1);
    Instant threshold = nextMonthFirstDay.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

    // Gom các dòng hoa hồng DEBT chưa chốt kỳ (OPEN, DEFERRED) tính đến hết tháng
    List<Commission> unbilledCommissions = commissionRepository.findByFilmLabIdAndStatusInAndEligibleAtBefore(
        labId, List.of(CommissionStatus.OPEN, CommissionStatus.DEFERRED), threshold);

    long totalDebtVnd = unbilledCommissions.stream()
        .mapToLong(Commission::getAmountVnd)
        .sum();

    // Q5 / BR-20: Nếu tổng tiền nợ <= 0 thì không tạo kỳ nợ, các dòng hoa hồng giữ nguyên OPEN cho kỳ sau
    if (totalDebtVnd <= 0) {
      log.info("[DebtService] Lab #{} không phát sinh hoa hồng nợ trong kỳ {}, bỏ qua tạo debt", labId, firstDay);
      return null;
    }

    // BR-20: Hạn thanh toán mặc định là ngày 10 tháng sau
    LocalDate dueDate = nextMonthFirstDay.withDayOfMonth(10);

    CommissionDebt debt = new CommissionDebt();
    debt.setFilmLabId(labId);
    debt.setBillingPeriod(firstDay);
    debt.setDueDate(dueDate);
    debt.setTotalAmountVnd(totalDebtVnd);
    debt.setPaidAmountVnd(0L);
    debt.setStatus(CommissionDebtStatus.UNPAID);

    CommissionDebt savedDebt = debtRepository.save(debt);

    // Gán ID kỳ nợ vào các bản ghi hoa hồng và chuyển trạng thái sang BILLED
    for (Commission comm : unbilledCommissions) {
      comm.setCommissionDebtId(savedDebt.getId());
      comm.setStatus(CommissionStatus.BILLED);
      commissionRepository.save(comm);
    }

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.COMMISSION_DEBT_CREATED,
        AuditTargetType.COMMISSION_DEBT,
        savedDebt.getId(),
        "Tạo kỳ nợ hoa hồng tháng " + firstDay + " cho Lab #" + labId + " với số tiền " + totalDebtVnd + " VND",
        null,
        null
    ));

    log.info("[DebtService] Tạo kỳ nợ thành công cho lab #{}: id={}, total={} VND",
        labId, savedDebt.getId(), totalDebtVnd);

    return debtMapper.toResponse(savedDebt);
  }

  @Override
  @Transactional
  public CommissionDebtResponse reportDebtPayment(
      Long debtId, ReportDebtPaymentRequest request, AuthenticatedAccount account) {
    CommissionDebt debt = debtRepository.findById(debtId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kỳ nợ hoa hồng."));

    // Kiểm tra quyền Lab sở hữu
    if (account.role() != UserAccountRole.ADMIN) {
      FilmLab lab = labAccessService.requireOwnedLab(account.id());
      if (!Objects.equals(debt.getFilmLabId(), lab.getId())) {
        throw new BusinessException(ErrorCode.LAB_ACCESS_DENIED, "Bạn không có quyền báo nộp kỳ nợ của lab khác.");
      }
    }

    if (debt.getStatus() == CommissionDebtStatus.PAID) {
      throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Kỳ nợ này đã được thanh toán hoàn tất.");
    }

    // Tạo bản ghi Payment loại DEBT_PAYMENT, BANK_TRANSFER ở trạng thái PENDING chờ Admin xác nhận
    Payment payment = new Payment();
    payment.setTransactionCode("DEBT" + generateRandomCode(8));
    payment.setPurpose(PaymentPurpose.DEBT_PAYMENT);
    payment.setDirection(PaymentDirection.IN);
    payment.setMethod(PaymentMethod.BANK_TRANSFER);
    payment.setStatus(PaymentStatus.PENDING);
    payment.setAmountVnd(debt.getTotalAmountVnd() - debt.getPaidAmountVnd());
    payment.setCommissionDebtId(debt.getId());
    payment.setCounterpartyAccountId(account.id());
    payment.setRecordedByAccountId(account.id());
    payment.setReceiptBlobKey(request.receiptBlobKey());
    payment.setConfirmationStatus(PaymentConfirmationStatus.AWAITING_CONFIRMATION);

    paymentRepository.save(payment);

    log.info("[DebtService] Lab #{} đã gửi báo cáo nộp nợ cho debt #{}, receiptKey={}",
        debt.getFilmLabId(), debtId, request.receiptBlobKey());

    return debtMapper.toResponse(debt);
  }

  @Override
  @Transactional
  public CommissionDebtResponse confirmDebtPayment(Long debtId, AuthenticatedAccount adminAccount) {
    if (adminAccount.role() != UserAccountRole.ADMIN) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chỉ Quản trị viên (Admin) mới có quyền xác nhận thanh toán nợ.");
    }

    CommissionDebt debt = debtRepository.findById(debtId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kỳ nợ hoa hồng."));

    if (debt.getStatus() == CommissionDebtStatus.PAID) {
      log.info("[DebtService] Kỳ nợ #{} đã ở trạng thái PAID", debtId);
      return debtMapper.toResponse(debt);
    }

    Instant now = clock.instant();

    // Cập nhật tất cả các khoản thanh toán chuyển khoản của kỳ nợ sang SUCCEEDED
    List<Payment> pendingPayments = paymentRepository.findAll().stream()
        .filter(p -> Objects.equals(p.getCommissionDebtId(), debtId) && p.getStatus() == PaymentStatus.PENDING)
        .toList();

    for (Payment p : pendingPayments) {
      p.setStatus(PaymentStatus.SUCCEEDED);
      p.setSucceededAt(now);
      p.setConfirmationStatus(PaymentConfirmationStatus.CONFIRMED);
      p.setConfirmedAt(now);
      paymentRepository.save(p);
    }

    // Chốt trả đủ 100% kỳ nợ (Q6, UC33)
    debt.setPaidAmountVnd(debt.getTotalAmountVnd());
    debt.setStatus(CommissionDebtStatus.PAID);
    debt.setSettledAt(now);
    debt.setSettledByAccountId(adminAccount.id());

    CommissionDebt saved = debtRepository.save(debt);

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.COMMISSION_DEBT_SETTLED,
        AuditTargetType.COMMISSION_DEBT,
        saved.getId(),
        "Admin #" + adminAccount.id() + " xác nhận tất toán kỳ nợ #" + debtId + " số tiền " + debt.getTotalAmountVnd() + " VND",
        null,
        null
    ));

    log.info("[DebtService] Admin đã xác nhận tất toán thành công cho kỳ nợ #{}", debtId);
    return debtMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<CommissionDebtResponse> getLabDebts(
      Long labId, Pageable pageable, AuthenticatedAccount account) {
    if (account.role() != UserAccountRole.ADMIN) {
      FilmLab lab = labAccessService.requireOwnedLab(account.id());
      if (!Objects.equals(labId, lab.getId())) {
        throw new BusinessException(ErrorCode.LAB_ACCESS_DENIED, "Bạn không có quyền xem kỳ nợ của lab khác.");
      }
    }
    return debtRepository.findByFilmLabId(labId, pageable).map(debtMapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public CommissionDebtResponse getDebtById(Long debtId, AuthenticatedAccount account) {
    CommissionDebt debt = debtRepository.findById(debtId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kỳ nợ hoa hồng."));

    if (account.role() != UserAccountRole.ADMIN) {
      FilmLab lab = labAccessService.requireOwnedLab(account.id());
      if (!Objects.equals(debt.getFilmLabId(), lab.getId())) {
        throw new BusinessException(ErrorCode.LAB_ACCESS_DENIED, "Bạn không có quyền xem kỳ nợ của lab khác.");
      }
    }

    return debtMapper.toResponse(debt);
  }

  private String generateRandomCode(int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(TXN_CHARSET.charAt(RANDOM.nextInt(TXN_CHARSET.length())));
    }
    return sb.toString();
  }
}

