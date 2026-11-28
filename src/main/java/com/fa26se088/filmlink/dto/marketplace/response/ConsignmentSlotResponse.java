package com.fa26se088.filmlink.dto.marketplace.response;

import java.time.Instant;

/** Một khung giờ hẹn ký gửi của Lab trong ngày kèm số chỗ còn trống (UC45). */
public record ConsignmentSlotResponse(
    Instant slotStartAt,
    Instant slotEndAt,
    int capacity,
    long bookedCount,
    boolean available) {}
