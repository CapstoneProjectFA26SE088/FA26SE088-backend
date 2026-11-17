package com.fa26se088.filmlink.dto.order.request;

import java.time.Instant;

/**
 * UC69: Film Lab Owner xác nhận ngày hẹn trả. Body tuỳ chọn.
 *
 * @param promisedReturnAt ngày hẹn trả lab chọn (UTC); {@code null} hoặc không gửi body = dùng
 *     ngày đề xuất. BR-60: không được sớm hơn ngày đề xuất (chỉ được dời muộn hơn).
 */
public record ConfirmScheduleRequest(Instant promisedReturnAt) {}
