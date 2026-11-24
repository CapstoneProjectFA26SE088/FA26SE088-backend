package com.fa26se088.filmlink.dto.payment.response;

import com.fa26se088.filmlink.entity.payment.enums.CommissionDebtStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Response DTO cho kỳ nợ hoa hồng của Film Lab.
 */
public record CommissionDebtResponse(
    Long id,
    Long filmLabId,
    LocalDate billingPeriod,
    LocalDate dueDate,
    Long totalAmountVnd,
    Long paidAmountVnd,
    CommissionDebtStatus status,
    Instant settledAt,
    Long settledByAccountId,
    Instant createdAt,
    Instant updatedAt
) {}

