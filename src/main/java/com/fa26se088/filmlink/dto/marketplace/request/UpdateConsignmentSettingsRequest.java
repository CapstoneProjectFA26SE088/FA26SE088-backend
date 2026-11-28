package com.fa26se088.filmlink.dto.marketplace.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Yêu cầu cấu hình nhận ký gửi của Film Lab (UC45, BR-54). */
public record UpdateConsignmentSettingsRequest(
    @NotNull Boolean isAcceptingConsignments,
    @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal consignmentFeeRate,
    @NotNull @Min(15) @Max(240) Short slotMinutes,
    @NotNull @Min(1) @Max(20) Short appointmentsPerSlot) {}
