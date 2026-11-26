package com.fa26se088.filmlink.service.payment.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.payment.ClosedOrderCommission;
import com.fa26se088.filmlink.dto.payment.response.CommissionResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.entity.payment.Commission;
import com.fa26se088.filmlink.entity.payment.enums.CommissionCollectionMode;
import com.fa26se088.filmlink.entity.payment.enums.CommissionKind;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.payment.CommissionMapper;
import com.fa26se088.filmlink.repository.payment.CommissionRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.payment.CommissionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
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
public class CommissionServiceImpl implements CommissionService {

  private static final String PAYMENT_METHOD_ONLINE = "ONLINE";
  private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("10.00"); // 10% mặc định theo BR-10

  private final CommissionRepository commissionRepository;
  private final CommissionMapper commissionMapper;
  private final LabAccessService labAccessService;
  private final AuditLogService auditLogService;
  private final SystemParameterService systemParameterService;
  private final Clock clock;

  /**
   * BR-10/BR-11: ghi hoa hồng khi đơn CLOSED, gọi từ {@code order} trong cùng transaction (SPEC-00
   * §4.6). Idempotent theo đơn: đơn đã có commission ORDER thì không tạo thêm.
   *
   * <p>ONLINE: tiền qua nền tảng, khấu trừ tại nguồn ({@code DEDUCTED}, {@code SETTLED} — DB
   * {@code ck_commissions_billing} bắt buộc DEDUCTED đi với SETTLED). PAY_AT_LAB / COD: ghi nợ cho lab
   * ({@code DEBT}, {@code OPEN}) chờ job chốt kỳ gom vào Commission Debt.
   */
  @Override
  @Transactional
  public void recordForClosedOrder(ClosedOrderCommission command) {
    Long orderId = command.serviceOrderId();

    if (commissionRepository.existsByServiceOrderIdAndKind(orderId, CommissionKind.ORDER)) {
      log.info("[CommissionService] Idempotent hit: Đơn #{} đã có hoa hồng", orderId);
      return;
    }

    long baseAmount = Math.max(0, command.serviceValueVnd());

    // Đọc tỷ lệ hoa hồng mặc định từ tham số hệ thống (BR-10 / BR-38); rule theo gói / lab làm ở P-04
    BigDecimal rate = systemParameterService.getDecimal(SystemParameterKey.DEFAULT_COMMISSION_RATE);
    if (rate == null) {
      log.warn("[CommissionService] Tham số DEFAULT_COMMISSION_RATE chưa được seed, dùng giá trị mặc định 10%");
      rate = DEFAULT_COMMISSION_RATE;
    }
    long commissionAmount =
        BigDecimal.valueOf(baseAmount)
            .multiply(rate)
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            .longValue();

    CommissionCollectionMode mode;
    CommissionStatus status;
    if (PAYMENT_METHOD_ONLINE.equals(command.paymentMethod())) {
      mode = CommissionCollectionMode.DEDUCTED;
      status = CommissionStatus.SETTLED;
    } else {
      mode = CommissionCollectionMode.DEBT;
      status = CommissionStatus.OPEN;
    }

    Commission commission = new Commission();
    commission.setKind(CommissionKind.ORDER);
    commission.setFilmLabId(command.filmLabId());
    commission.setServiceOrderId(orderId);
    commission.setBaseAmountVnd(baseAmount);
    commission.setRatePercent(rate);
    commission.setAmountVnd(commissionAmount);
    commission.setCollectionMode(mode);
    commission.setStatus(status);
    commission.setEligibleAt(command.closedAt() != null ? command.closedAt() : clock.instant());

    Commission saved = commissionRepository.save(commission);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.COMMISSION_RECORDED,
            AuditTargetType.COMMISSION,
            saved.getId(),
            "Ghi nhận hoa hồng "
                + commissionAmount
                + " VND cho đơn #"
                + orderId
                + " ("
                + mode
                + ")",
            null,
            null));

    log.info(
        "[CommissionService] Ghi nhận Commission: id={}, orderId={}, amount={} VND, mode={}",
        saved.getId(),
        orderId,
        commissionAmount,
        mode);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<CommissionResponse> getLabCommissions(
      Long labId, Pageable pageable, AuthenticatedAccount account) {
    if (account.role() != UserAccountRole.ADMIN) {
      FilmLab lab = labAccessService.requireOwnedLab(account.id());
      if (!Objects.equals(labId, lab.getId())) {
        throw new BusinessException(ErrorCode.LAB_ACCESS_DENIED, "Bạn không có quyền xem hoa hồng của lab khác.");
      }
    }
    return commissionRepository.findByFilmLabId(labId, pageable).map(commissionMapper::toResponse);
  }
}

