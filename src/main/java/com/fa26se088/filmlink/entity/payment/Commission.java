package com.fa26se088.filmlink.entity.payment;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.payment.enums.CommissionCollectionMode;
import com.fa26se088.filmlink.entity.payment.enums.CommissionKind;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hoa hồng nền tảng (ERD #27), bảng {@code commissions}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commissions")
public class Commission extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 20)
  private CommissionKind kind;

  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  @Column(name = "service_order_id")
  private Long serviceOrderId;

  @Column(name = "consignment_sale_id")
  private Long consignmentSaleId;

  @Column(name = "complaint_id")
  private Long complaintId;

  @Column(name = "base_amount_vnd", nullable = false)
  private Long baseAmountVnd;

  @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal ratePercent;

  @Column(name = "amount_vnd", nullable = false)
  private Long amountVnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "collection_mode", nullable = false, length = 20)
  private CommissionCollectionMode collectionMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CommissionStatus status = CommissionStatus.OPEN;

  @Column(name = "commission_debt_id")
  private Long commissionDebtId;

  @Column(name = "eligible_at", nullable = false)
  private Instant eligibleAt;

  @Column(name = "waiver_reason", length = 500)
  private String waiverReason;
}

