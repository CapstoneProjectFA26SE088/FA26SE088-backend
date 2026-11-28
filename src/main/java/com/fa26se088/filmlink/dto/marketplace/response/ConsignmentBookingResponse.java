package com.fa26se088.filmlink.dto.marketplace.response;

import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentBookingStatus;
import java.time.Instant;

/** Thông tin lịch hẹn ký gửi kèm thiết bị đi cùng. */
public record ConsignmentBookingResponse(
    Long id,
    String bookingCode,
    Long sellerAccountId,
    Long filmLabId,
    Instant slotStartAt,
    Instant slotEndAt,
    ConsignmentBookingStatus status,
    Instant checkedInAt,
    ConsignmentItemResponse item,
    Instant createdAt) {}
