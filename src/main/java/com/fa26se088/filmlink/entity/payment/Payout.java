package com.fa26se088.filmlink.entity.payment;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.payment.enums.PayoutRecipientType;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Đợt chi trả Payout cho Film Lab (ERD #29), bảng {@code payouts}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payouts")
public class Payout extends BaseEntity {

  @Column(name = "payout_code", nullable = false, unique = true, length = 30)
  private String payoutCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "recipient_type", nullable = false, length = 20)
  private PayoutRecipientType recipientType = PayoutRecipientType.FILM_LAB;

  @Column(name = "film_lab_id")
  private Long filmLabId;

  @Column(name = "recipient_account_id")
  private Long recipientAccountId;

  @Column(name = "amount_vnd", nullable = false)
  private Long amountVnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PayoutStatus status = PayoutStatus.PENDING;

  @Column(name = "bank_name", length = 100)
  private String bankName;

  @Column(name = "bank_account_number", length = 30)
  private String bankAccountNumber;

  @Column(name = "account_holder_name", length = 100)
  private String accountHolderName;

  @Column(name = "receipt_blob_key", length = 500)
  private String receiptBlobKey;

  @Column(name = "executed_by_account_id")
  private Long executedByAccountId;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Version
  @Column(name = "version", nullable = false)
  private int version = 0;
}

