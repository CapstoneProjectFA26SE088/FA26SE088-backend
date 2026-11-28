package com.fa26se088.filmlink.dto.marketplace.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Yêu cầu đặt lịch hẹn mang thiết bị tới Lab kiểm định ký gửi (UC45). */
public record CreateConsignmentBookingRequest(
    @NotNull Long filmLabId,
    @NotNull Instant slotStartAt,
    @Valid @NotNull CreateConsignmentItemRequest item) {}
