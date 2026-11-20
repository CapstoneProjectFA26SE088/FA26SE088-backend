package com.fa26se088.filmlink.service.payment.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.payment.response.CommissionResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.OrderPaymentMethod;
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
import com.fa26se088.filmlink.service.payment.CommissionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
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
public class CommissionServiceImpl implements CommissionService {

  private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("10.00"); // 10% mặc định theo BR-10

  private final CommissionRepository commissionRepository;
  private final CommissionMapper commissionMapper;
  private final LabAccessService labAccessService;
  private final AuditLogService auditLogService;
  private final Clock clock;

  @Override
  @Transactional
  public CommissionResponse createCommissionForOrder(ServiceOrder order) {
    Long orderId = order.getId();

    // Idempotent: Kiểm tra đã tồn tại Commission cho order này chưa
    Optional<Commission> existing = commissionRepository.findByServiceOrderIdAndKind(orderId, CommissionKind.ORDER);
    if (existing.isPresent()) {
      log.info("[CommissionService] Idempotent hit: Đơn #{} đã có hoa hồng", orderId);
      return commissionMapper.toResponse(existing.get());
    }

    // Giá tính hoa hồng là finalTotalVnd (nếu có), fallback về estimatedTotalVnd
    long baseAmount = order.getFinalTotalVnd() != null ? order.getFinalTotalVnd() : order.getEstimatedTotalVnd();
    if (baseAmount < 0) {
      baseAmount = 0;
    }

    // Tính hoa hồng theo tỷ lệ (mặc định 10%)
    BigDecimal rate = DEFAULT_COMMISSION_RATE;
    long commissionAmount = BigDecimal.valueOf(baseAmount)
        .multiply(rate)
        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
        .longValue();

    CommissionCollectionMode mode;
    CommissionStatus status;

    if (order.getPaymentMethod() == OrderPaymentMethod.ONLINE) {
      // Đơn ONLINE: khấu trừ trực tiếp tại nguồn -> SETTLED
      mode = CommissionCollectionMode.DEDUCTED;
      status = CommissionStatus.SETTLED;
    } else {
      // Đơn COD hoặc Pay-at-Lab: ghi nhận nợ cho lab -> OPEN (chờ job cuối tháng gom vào kỳ nợ)
      mode = CommissionCollectionMode.DEBT;
      status = CommissionStatus.OPEN;
    }

    Instant now = clock.instant();

    Commission commission = new Commission();
    commission.setKind(CommissionKind.ORDER);
    commission.setFilmLabId(order.getFilmLabId());
    commission.setServiceOrderId(orderId);
    commission.setBaseAmountVnd(baseAmount);
    commission.setRatePercent(rate);
    commission.setAmountVnd(commissionAmount);
    commission.setCollectionMode(mode);
    commission.setStatus(status);
    commission.setEligibleAt(order.getClosedAt() != null ? order.getClosedAt() : now);

    Commission saved = commissionRepository.save(commission);

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.COMMISSION_RECORDED,
        AuditTargetType.COMMISSION,
        saved.getId(),
        "Ghi nhận hoa hồng " + commissionAmount + " VND cho đơn " + order.getOrderCode() + " (" + mode + ")",
        null,
        null
    ));

    log.info("[CommissionService] Ghi nhận Commission thành công: id={}, orderId={}, amount={} VND, mode={}",
        saved.getId(), orderId, commissionAmount, mode);

    return commissionMapper.toResponse(saved);
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

