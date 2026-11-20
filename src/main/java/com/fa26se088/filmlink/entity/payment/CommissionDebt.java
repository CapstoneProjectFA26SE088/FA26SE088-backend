package com.fa26se088.filmlink.entity.payment;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.payment.enums.CommissionDebtStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kỳ nợ hoa hồng của Film Lab (ERD #28), bảng {@code commission_debts}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commission_debts")
public class CommissionDebt extends BaseEntity {

  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  @Column(name = "billing_period", nullable = false)
  private LocalDate billingPeriod;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "total_amount_vnd", nullable = false)
  private Long totalAmountVnd;

  @Column(name = "paid_amount_vnd", nullable = false)
  private Long paidAmountVnd = 0L;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CommissionDebtStatus status = CommissionDebtStatus.UNPAID;

  @Column(name = "settled_at")
  private Instant settledAt;

  @Column(name = "settled_by_account_id")
  private Long settledByAccountId;
}

