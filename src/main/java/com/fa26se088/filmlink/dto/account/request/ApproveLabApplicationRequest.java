package com.fa26se088.filmlink.dto.account.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO yêu cầu Admin duyệt đơn đăng ký mở Film Lab (UC08, FA26-66).
 *
 * @param version phiên bản hiện tại của đơn đăng ký để kiểm tra Optimistic Locking
 * @param accountEmail email tạo tài khoản FILM_LAB_OWNER (tuỳ chọn; nếu để trống sẽ dùng
 *     contactEmail trong đơn)
 */
public record ApproveLabApplicationRequest(
    @NotNull(message = "Version không được để trống") Integer version,
    @Email(message = "Email tài khoản không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String accountEmail) {}
