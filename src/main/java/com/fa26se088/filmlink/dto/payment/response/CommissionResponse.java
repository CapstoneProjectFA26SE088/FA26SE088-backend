package com.fa26se088.filmlink.dto.payment.response;

import com.fa26se088.filmlink.entity.payment.enums.CommissionCollectionMode;
import com.fa26se088.filmlink.entity.payment.enums.CommissionKind;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO cho hoa hồng nền tảng.
 */
public record CommissionResponse(
    Long id,
    CommissionKind kind,
    Long filmLabId,
    Long serviceOrderId,
    Long consignmentSaleId,
    Long complaintId,
    Long baseAmountVnd,
    BigDecimal ratePercent,
    Long amountVnd,
    CommissionCollectionMode collectionMode,
    CommissionStatus status,
    Long commissionDebtId,
    Instant eligibleAt,
    String waiverReason,
    Instant createdAt,
    Instant updatedAt
) {}

