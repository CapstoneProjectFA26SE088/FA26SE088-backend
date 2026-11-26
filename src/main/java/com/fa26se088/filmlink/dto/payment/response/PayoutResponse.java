package com.fa26se088.filmlink.dto.payment.response;

import com.fa26se088.filmlink.entity.payment.enums.PayoutRecipientType;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import java.time.Instant;
import java.util.List;

/** Response DTO cho Payout kèm link tải biên lai có chữ ký và danh sách dòng chi trả. */
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
    String receiptUrl,
    Long executedByAccountId,
    Instant paidAt,
    String failureReason,
    int version,
    Instant createdAt,
    Instant updatedAt,
    List<PayoutItemResponse> items) {}
