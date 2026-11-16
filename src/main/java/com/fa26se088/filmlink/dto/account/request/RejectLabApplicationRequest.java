package com.fa26se088.filmlink.dto.account.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO yêu cầu Admin từ chối đơn đăng ký mở Film Lab (UC08, FA26-66).
 *
 * @param version phiên bản hiện tại của đơn đăng ký để kiểm tra Optimistic Locking
 * @param reason lý do từ chối đơn (bắt buộc từ 10 đến 500 ký tự)
 */
public record RejectLabApplicationRequest(
    @NotNull(message = "Version không được để trống") Integer version,
    @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(min = 10, max = 500, message = "Lý do từ chối phải từ 10 đến 500 ký tự")
        String reason) {}
