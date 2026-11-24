package com.fa26se088.filmlink.dto.account.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ACC-09: DTO cập nhật thông tin cá nhân tài khoản.
 *
 * <p>Quy tắc bảo mật & phân quyền:
 * <ul>
 *   <li>Không cho phép sửa trực tiếp Email, Role, Password ở API update.
 *   <li>fullName bắt buộc từ 2 đến 100 ký tự (khớp ràng buộc DB {@code ck_user_accounts_full_name}).
 *   <li>phoneNumber kiểm tra định dạng số điện thoại Việt Nam hợp lệ nếu được cung cấp.
 *   <li>avatarBlobKey tối đa 500 ký tự.
 * </ul>
 */
public record UpdateAccountRequest(
    @NotBlank(message = "Họ và tên không được để trống")
        @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
        String fullName,
    @Pattern(
            regexp = "^$|^(0|\\+84)[0-9]{9,10}$",
            message = "Số điện thoại không đúng định dạng")
        @Size(max = 15, message = "Số điện thoại không được vượt quá 15 ký tự")
        String phoneNumber,
    @Size(max = 500, message = "Avatar blob key không được vượt quá 500 ký tự")
        String avatarBlobKey,
    Boolean inAppNotificationEnabled,
    Boolean emailNotificationEnabled,
    Boolean pushNotificationEnabled) {}

