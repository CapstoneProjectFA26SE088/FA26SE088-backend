package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Snapshot chi tiết cuộn phim do client gửi khi tạo đơn (Phương án B: không dùng entity JPA FilmRoll).
 */
public record FilmRollItemRequest(
    @NotBlank(message = "Tên loại phim không được để trống")
    @Size(max = 100, message = "Tên loại phim tối đa 100 ký tự")
    String filmFormat,

    @NotBlank(message = "Tên quy trình tráng không được để trống")
    @Size(max = 100, message = "Tên quy trình tối đa 100 ký tự")
    String processName,

    @NotNull(message = "Đơn giá tạm tính không được để trống")
    @Min(value = 0, message = "Đơn giá tạm tính không thể âm")
    Long estimatedPriceVnd,

    @Size(max = 255, message = "Ghi chú cuộn tối đa 255 ký tự")
    String note
) {}

