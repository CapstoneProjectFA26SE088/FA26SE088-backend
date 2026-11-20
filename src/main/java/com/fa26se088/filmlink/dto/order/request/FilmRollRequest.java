package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC20: một cuộn phim khai báo khi tạo đơn. Giới hạn khớp CHECK của {@code film_rolls}.
 *
 * <p>Service kiểm thêm: đúng một trong {@code filmStockId} / {@code filmStockNameText} ({@code
 * ck_film_rolls_stock}); gói thuộc lab, đang bán, cùng khổ phim; mức scan thuộc gói; push/pull chỉ
 * khi gói cho phép.
 *
 * @param filmStockId dòng phim chọn từ danh mục {@code film_stocks}
 * @param filmStockNameText tên dòng phim nhập tự do khi không tìm thấy trong danh mục
 * @param servicePackageScanOptionId mức scan của gói, tuỳ chọn
 * @param pushPullStops số stop push (dương) / pull (âm), -3..3; {@code null} = 0
 */
public record FilmRollRequest(
    Long filmStockId,
    @Size(max = 100) String filmStockNameText,
    @NotNull Long filmFormatId,
    @NotNull @Min(1) @Max(40) Short exposureCount,
    @Min(1) @Max(12_800) Short shootingIso,
    @NotNull Long servicePackageId,
    Long servicePackageScanOptionId,
    @Min(-3) @Max(3) Short pushPullStops) {}
