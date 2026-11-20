package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload hủy đơn hàng của khách hàng (UC60, BR-38).
 */
public record CancelServiceOrderRequest(
    @NotBlank(message = "Lý do hủy đơn không được để trống")
    @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự")
    String reason
) {}

