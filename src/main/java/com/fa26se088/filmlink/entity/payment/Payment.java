package com.fa26se088.filmlink.entity.payment;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.payment.enums.PaymentConfirmationStatus;
import com.fa26se088.filmlink.entity.payment.enums.PaymentDirection;
import com.fa26se088.filmlink.entity.payment.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import com.fa26se088.filmlink.entity.payment.enums.PaymentStatus;
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
 * Giao dịch thanh toán / hoàn tiền (ERD #26), bảng {@code payments}.
 *
 * <p>Quy tắc bất biến:
 * <ul>
 *   <li>Mọi số tiền lưu dạng VND nguyên (Long, amount_vnd > 0).
 *   <li>{@code transaction_code} là mã định danh duy nhất (truyền sang VNPay qua vnp_TxnRef).
 *   <li>Xử lý cập nhật đồng thời với IPN và đối soát dùng {@code @Version}.
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

  @Column(name = "transaction_code", nullable = false, unique = true, length = 30)
  private String transactionCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, length = 20)
  private PaymentPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 3)
  private PaymentDirection direction;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 20)
  private PaymentMethod method;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PaymentStatus status = PaymentStatus.PENDING;

  @Column(name = "amount_vnd", nullable = false)
  private Long amountVnd;

  @Column(name = "service_order_id")
  private Long serviceOrderId;

  @Column(name = "consignment_sale_id")
  private Long consignmentSaleId;

  @Column(name = "commission_debt_id")
  private Long commissionDebtId;

  @Column(name = "counterparty_account_id", nullable = false)
  private Long counterpartyAccountId;

  @Column(name = "refunded_payment_id")
  private Long refundedPaymentId;

  @Column(name = "payment_url_expires_at")
  private Instant paymentUrlExpiresAt;

  @Column(name = "gateway_transaction_no", length = 50)
  private String gatewayTransactionNo;

  @Column(name = "gateway_response_code", length = 10)
  private String gatewayResponseCode;

  @Column(name = "gateway_bank_code", length = 20)
  private String gatewayBankCode;

  @Column(name = "shipment_id")
  private Long shipmentId;

  @Column(name = "recorded_by_account_id")
  private Long recordedByAccountId;

  @Column(name = "receipt_blob_key", length = 500)
  private String receiptBlobKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "confirmation_status", nullable = false, length = 25)
  private PaymentConfirmationStatus confirmationStatus = PaymentConfirmationStatus.NOT_REQUIRED;

  @Column(name = "confirmation_due_at")
  private Instant confirmationDueAt;

  @Column(name = "confirmation_reminder_count", nullable = false)
  private Short confirmationReminderCount = 0;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "succeeded_at")
  private Instant succeededAt;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Version
  @Column(name = "version", nullable = false)
  private int version = 0;
}

