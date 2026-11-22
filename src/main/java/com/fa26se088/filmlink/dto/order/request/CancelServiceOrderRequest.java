package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UC60: body của {@code POST /api/v1/orders/{id}/cancel}.
 *
 * @param reason lý do huỷ, 10–500 ký tự (lưu vào {@code service_orders.cancellation_reason})
 */
public record CancelServiceOrderRequest(
    @NotBlank(message = "Cần nhập lý do huỷ đơn.")
        @Size(min = 10, max = 500, message = "Lý do huỷ đơn từ 10 đến 500 ký tự.")
        String reason) {}
