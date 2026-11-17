package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.entity.order.enums.ProcessingStage;
import java.time.Instant;

/** BR-07: một mốc trong timeline công đoạn của đơn. */
public record ProcessingStageRecordResponse(
    ProcessingStage stage, Instant recordedAt, String note) {}
