package com.fa26se088.filmlink.entity.payment;

import com.fa26se088.filmlink.entity.payment.enums.PaymentGatewayEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Nhật ký callback từ cổng thanh toán (bảng {@code payment_gateway_events}).
 * Bảng này là append-only (có trigger trg_append_only).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_gateway_events")
public class PaymentGatewayEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "provider", nullable = false, length = 20)
  private String provider = "VNPAY";

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 20)
  private PaymentGatewayEventType eventType;

  @Column(name = "transaction_code", nullable = false, length = 30)
  private String transactionCode;

  @Column(name = "payment_id")
  private Long paymentId;

  @Column(name = "gateway_transaction_no", length = 50)
  private String gatewayTransactionNo;

  @Column(name = "response_code", length = 10)
  private String responseCode;

  @Column(name = "amount_vnd")
  private Long amountVnd;

  @Column(name = "is_signature_valid", nullable = false)
  private boolean signatureValid;

  @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
  private String rawPayload;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt = Instant.now();
}

