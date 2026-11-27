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
import com.fa26se088.filmlink.entity.payment.CommissionRateRule;
import com.fa26se088.filmlink.entity.payment.enums.CommissionCollectionMode;
import com.fa26se088.filmlink.entity.payment.enums.CommissionKind;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.payment.CommissionMapper;
import com.fa26se088.filmlink.repository.payment.CommissionRateRuleRepository;
import com.fa26se088.filmlink.repository.payment.CommissionRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.payment.CommissionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thực thi tính toán và ghi nhận hoa hồng nền tảng khi đơn hàng hoàn tất (UC32, BR-10, BR-11). */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

  private static final String PAYMENT_METHOD_ONLINE = "ONLINE";
  private static final long DEFAULT_RESCAN_WINDOW_DAYS = 7;
  private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("10.00"); // 10% mặc định theo BR-10

  private final CommissionRepository commissionRepository;
  private final CommissionRateRuleRepository commissionRateRuleRepository;
  private final CommissionMapper commissionMapper;
  private final LabAccessService labAccessService;
  private final AuditLogService auditLogService;
  private final SystemParameterService systemParameterService;
  private final Clock clock;

  /**
   * BR-10/BR-11: ghi hoa hồng khi đơn CLOSED, gọi từ {@code order} trong cùng transaction (SPEC-00
   * §4.6). Idempotent theo đơn: đơn đã có commission ORDER thì không tạo thêm.
   *
   * <p>Ghi chú căn chỉnh kiến trúc DB ({@code V20261021_2048}, constraint {@code
   * ck_commissions_billing}): với đơn {@code ONLINE}, cơ sở dữ liệu quy định {@code
   * collection_mode = 'DEDUCTED'} luôn đi kèm {@code status = 'SETTLED'}. Do đó bản ghi hoa hồng
   * ONLINE được lưu {@code DEDUCTED + SETTLED} với {@code eligible_at = closedAt +
   * RESCAN_WINDOW_DAYS} (mặc định 7 ngày, BR-46), và trạng thái chờ giải ngân cho Lab được đối soát
   * thông qua sự hiện diện của {@code service_order_id} trong bảng {@code payout_items} ({@code
   * uq_payout_items_order}). Với đơn {@code PAY_AT_LAB} / {@code COD}, bản ghi lưu {@code DEBT +
   * OPEN} chờ job chốt kỳ hằng tháng gom vào {@code commission_debts}.
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

    // BR-10: ưu tiên tỷ lệ gói dịch vụ > lab > tham số mặc định hệ thống (10%)
    BigDecimal rate = resolveCommissionRate(command.filmLabId(), command.servicePackageIds());
    final long commissionAmount =
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
    Instant closedAt = command.closedAt() != null ? command.closedAt() : clock.instant();
    commission.setEligibleAt(
        mode == CommissionCollectionMode.DEDUCTED
            ? closedAt.plus(Duration.ofDays(rescanWindowDays()))
            : closedAt);

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

  private long rescanWindowDays() {
    Integer days = systemParameterService.getInteger(SystemParameterKey.RESCAN_WINDOW_DAYS);
    return days != null ? days : DEFAULT_RESCAN_WINDOW_DAYS;
  }

  /**
   * BR-10: xác định tỷ lệ hoa hồng hiệu lực theo thứ tự ưu tiên:
   * 1. Quy tắc theo Service Package (nếu đơn có gói được cấu hình riêng)
   * 2. Quy tắc theo Film Lab
   * 3. Tham số hệ thống {@code DEFAULT_COMMISSION_RATE} (fallback 10.00%).
   */
  private BigDecimal resolveCommissionRate(Long filmLabId, List<Long> servicePackageIds) {
    if (servicePackageIds != null && !servicePackageIds.isEmpty()) {
      Map<Long, BigDecimal> packageRates =
          commissionRateRuleRepository.findByServicePackageIdIn(servicePackageIds).stream()
              .filter(rule -> rule.getServicePackageId() != null)
              .collect(
                  Collectors.toMap(
                      CommissionRateRule::getServicePackageId,
                      CommissionRateRule::getRatePercent,
                      (first, second) -> first));
      // Các gói trong đơn có rule khác nhau thì không chọn gói nào: dùng rule lab / mặc định.
      Set<BigDecimal> distinctRates =
          servicePackageIds.stream()
              .map(packageRates::get)
              .filter(Objects::nonNull)
              .map(rate -> rate.stripTrailingZeros())
              .collect(Collectors.toSet());
      if (distinctRates.size() == 1) {
        return packageRates.get(
            servicePackageIds.stream().filter(packageRates::containsKey).findFirst().orElseThrow());
      }
    }

    if (filmLabId != null) {
      Optional<CommissionRateRule> labRule = commissionRateRuleRepository.findByFilmLabId(filmLabId);
      if (labRule.isPresent()) {
        return labRule.get().getRatePercent();
      }
    }

    BigDecimal defaultRate =
        systemParameterService.getDecimal(SystemParameterKey.DEFAULT_COMMISSION_RATE);
    if (defaultRate == null) {
      log.warn(
          "[CommissionService] Tham số DEFAULT_COMMISSION_RATE chưa được seed, dùng giá trị mặc định 10%");
      return DEFAULT_COMMISSION_RATE;
    }
    return defaultRate;
  }
}

