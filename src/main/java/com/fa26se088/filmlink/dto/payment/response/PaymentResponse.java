package com.fa26se088.filmlink.dto.payment.response;

import com.fa26se088.filmlink.entity.payment.enums.PaymentConfirmationStatus;
import com.fa26se088.filmlink.entity.payment.enums.PaymentDirection;
import com.fa26se088.filmlink.entity.payment.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import com.fa26se088.filmlink.entity.payment.enums.PaymentStatus;
import java.time.Instant;
import lombok.Builder;

/**
 * Thông tin chi tiết một giao dịch thanh toán / hoàn tiền.
 */
@Builder
public record PaymentResponse(
    Long id,
    String transactionCode,
    PaymentPurpose purpose,
    PaymentDirection direction,
    PaymentMethod method,
    PaymentStatus status,
    Long amountVnd,
    Long serviceOrderId,
    Long counterpartyAccountId,
    String paymentUrl,
    Instant paymentUrlExpiresAt,
    String gatewayTransactionNo,
    String gatewayResponseCode,
    String gatewayBankCode,
    PaymentConfirmationStatus confirmationStatus,
    Instant confirmedAt,
    Instant succeededAt,
    String failureReason,
    Instant createdAt) {}

