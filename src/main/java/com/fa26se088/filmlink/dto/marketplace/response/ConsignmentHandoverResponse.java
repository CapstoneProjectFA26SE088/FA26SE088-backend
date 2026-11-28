package com.fa26se088.filmlink.dto.marketplace.response;

import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentHandoverMethod;
import java.time.Instant;
import java.util.List;

/** Bằng chứng bàn giao thiết bị ký gửi cho người mua (UC63). */
public record ConsignmentHandoverResponse(
    Long id,
    ConsignmentHandoverMethod handoverMethod,
    boolean recipientCodeVerified,
    String note,
    Long performedByAccountId,
    Instant occurredAt,
    List<ConsignmentPhotoResponse> photos) {}
