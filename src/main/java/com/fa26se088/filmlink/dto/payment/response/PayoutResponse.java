package com.fa26se088.filmlink.dto.payment.response;

import com.fa26se088.filmlink.entity.payment.enums.PayoutRecipientType;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import java.time.Instant;

/**
 * Response DTO cho Payout.
 */
public record PayoutResponse(
    Long id,
    String payoutCode,
    PayoutRecipientType recipientType,
    Long filmLabId,
    Long recipientAccountId,
    Long amountVnd,
    PayoutStatus status,
    String bankName,
    String bankAccountNumber,
    String accountHolderName,
    String receiptBlobKey,
    Long executedByAccountId,
    Instant paidAt,
    String failureReason,
    int version,
    Instant createdAt,
    Instant updatedAt
) {}

