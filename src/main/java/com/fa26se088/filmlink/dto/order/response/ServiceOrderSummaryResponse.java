package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import java.time.Instant;

/**
 * Một dòng trong danh sách đơn của Photographer (UC25). {@code labName} và {@code rollCount} do
 * service đọc gộp cho cả trang. {@code finalTotalVnd} có giá trị sau khi lab chốt giá (UC23), {@code
 * promisedReturnAt} sau khi lab xác nhận ngày hẹn trả (UC69).
 */
public record ServiceOrderSummaryResponse(
    Long id,
    String orderCode,
    ServiceOrderStatus status,
    Long filmLabId,
    String labName,
    long rollCount,
    Long estimatedTotalVnd,
    Long finalTotalVnd,
    Instant expectedReturnAt,
    Instant promisedReturnAt,
    Instant createdAt) {}
