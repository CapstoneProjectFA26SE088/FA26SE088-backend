package com.fa26se088.filmlink.dto.marketplace.response;

import java.math.BigDecimal;
import java.time.Instant;

/** Cấu hình nhận ký gửi hiện hành của Film Lab. */
public record ConsignmentSettingsResponse(
    Long filmLabId,
    boolean isAcceptingConsignments,
    BigDecimal consignmentFeeRate,
    Short slotMinutes,
    Short appointmentsPerSlot,
    Instant updatedAt) {}
