package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload từ chối nhận đơn của Film Lab (UC22).
 */
public record RejectServiceOrderRequest(
    @NotBlank(message = "Lý do từ chối không được để trống")
    @Size(min = 10, max = 500, message = "Lý do từ chối phải từ 10 đến 500 ký tự")
    String rejectionReason
) {}

