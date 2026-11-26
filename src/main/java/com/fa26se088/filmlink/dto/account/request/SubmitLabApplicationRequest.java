package com.fa26se088.filmlink.dto.account.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * DTO yêu cầu nộp đơn đăng ký mở Film Lab mới (UC07, BR-16).
 *
 * @param labName tên phòng lab (2–150 ký tự)
 * @param taxCode mã số thuế (10 chữ số hoặc 10 chữ số + '-' + 3 chữ số chi nhánh)
 * @param addressLine địa chỉ chi tiết (số nhà, tên đường)
 * @param district quận / huyện
 * @param province tỉnh / thành phố
 * @param contactEmail email liên hệ
 * @param contactPhone số điện thoại liên hệ
 * @param serviceTypeIds danh sách ID loại dịch vụ dự kiến cung cấp (tối thiểu 1)
 * @param filmProcessIds danh sách ID quy trình tráng dự kiến hỗ trợ (tối thiểu 1)
 * @param filmFormatIds danh sách ID khổ phim dự kiến hỗ trợ (tối thiểu 1)
 */
public record SubmitLabApplicationRequest(
    @NotBlank(message = "Tên phòng lab không được để trống")
    @Size(min = 2, max = 150, message = "Tên lab phải từ 2 đến 150 ký tự")
    String labName,

    @NotBlank(message = "Mã số thuế không được để trống")
    @Pattern(regexp = "^[0-9]{10}(-[0-9]{3})?$", message = "Mã số thuế không đúng định dạng")
    String taxCode,

    @NotBlank(message = "Địa chỉ không được để trống")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    String addressLine,

    @NotBlank(message = "Quận/Huyện không được để trống")
    @Size(max = 100, message = "Quận/Huyện tối đa 100 ký tự")
    String district,

    @NotBlank(message = "Tỉnh/Thành phố không được để trống")
    @Size(max = 100, message = "Tỉnh/Thành phố tối đa 100 ký tự")
    String province,

    @NotBlank(message = "Email liên hệ không được để trống")
    @Email(message = "Email liên hệ không đúng định dạng")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    String contactEmail,

    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(min = 9, max = 15, message = "Số điện thoại phải từ 9 đến 15 ký tự")
    String contactPhone,

    @NotEmpty(message = "Phải chọn ít nhất một loại dịch vụ")
    Set<Long> serviceTypeIds,

    @NotEmpty(message = "Phải chọn ít nhất một quy trình tráng")
    Set<Long> filmProcessIds,

    @NotEmpty(message = "Phải chọn ít nhất một khổ phim")
    Set<Long> filmFormatIds) {}

