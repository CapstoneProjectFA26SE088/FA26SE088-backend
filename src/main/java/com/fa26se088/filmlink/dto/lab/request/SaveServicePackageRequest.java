package com.fa26se088.filmlink.dto.lab.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * UC13: tạo hoặc sửa một Service Package (dùng chung cho POST và PUT). Giới hạn khớp CHECK của
 * {@code service_packages}.
 *
 * <p>Service kiểm thêm: tên duy nhất trong lab (không phân biệt hoa thường), master data ACTIVE
 * (BR-16), push/pull có phụ phí khi và chỉ khi bật, nhãn scan không trùng.
 *
 * @param pushPullSurchargePerStopVnd bắt buộc ({@code ≥ 1}) khi {@code pushPullAvailable}, phải
 *     {@code null} khi không
 * @param scanOptions 0..n mức scan; {@code null} coi như rỗng. Khi sửa: thay toàn bộ danh sách
 */
public record SaveServicePackageRequest(
    @NotBlank @Size(min = 2, max = 150) String packageName,
    @NotNull Long serviceTypeId,
    @NotNull Long filmProcessId,
    @NotNull Long filmFormatId,
    @NotNull @Min(1) @Max(50_000_000) Long priceVnd,
    @NotNull @Min(1) @Max(60) Short turnaroundDays,
    @NotNull Boolean pushPullAvailable,
    @Min(1) Long pushPullSurchargePerStopVnd,
    List<@NotNull @Valid ServicePackageScanOptionRequest> scanOptions) {}
