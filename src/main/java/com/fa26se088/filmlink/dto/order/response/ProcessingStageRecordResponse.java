package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.entity.order.enums.ProcessingStage;
import java.time.Instant;

/**
 * BR-07, UC24: một mốc trong timeline công đoạn của đơn; {@code adminReversion = true} khi mốc do
 * Admin thực hiện lùi công đoạn.
 */
public record ProcessingStageRecordResponse(
    ProcessingStage stage, Instant recordedAt, String note, boolean adminReversion) {}
