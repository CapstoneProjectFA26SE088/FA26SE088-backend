package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.NotNull;

/**
 * UC23: kết quả đếm một cuộn đã khai báo khi lab nhận phim.
 *
 * @param filmRollId id cuộn của đơn
 * @param received {@code true} → {@code RECEIVED}, {@code false} → {@code NOT_RECEIVED}
 */
public record FilmRollReceiptRequest(
    @NotNull(message = "Thiếu filmRollId.") Long filmRollId,
    @NotNull(message = "Thiếu received.") Boolean received) {}
