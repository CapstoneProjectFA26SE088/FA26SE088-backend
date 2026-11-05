package com.fa26se088.filmlink.dto.account.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request tạo tài khoản Admin mới (Admin-only). */
public record CreateAdminRequest(
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    String email,

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
    @Pattern(
        regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
        message = "Mật khẩu phải có cả chữ và số")
    String password,

    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
    String fullName,

    @Size(max = 15, message = "Số điện thoại tối đa 15 ký tự")
    String phoneNumber) {}
