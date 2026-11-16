package com.fa26se088.filmlink.dto.lab.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * UC17: tham số tìm lab công khai, nhận qua query string. Mọi bộ lọc đều tuỳ chọn.
 *
 * <p>Ràng buộc giữa các tham số (service kiểm, lỗi 400 {@code VALIDATION_ERROR}): {@code lat} và
 * {@code lng} đi cùng nhau; {@code radiusKm} và {@code sort=distance} cần {@code lat}, {@code lng}.
 *
 * @param radiusKm bán kính tìm, 1–50 km
 * @param sort {@code price} (mặc định, rẻ trước) hoặc {@code distance} (gần trước), không phân
 *     biệt hoa thường
 * @param page số trang, bắt đầu từ 0 (mặc định 0)
 * @param size số dòng mỗi trang, mặc định 20; lớn hơn 100 thì hạ về 100 (CR-01)
 */
public record SearchFilmLabRequest(
    @Positive Long filmProcessId,
    @Positive Long filmFormatId,
    @Positive Long serviceTypeId,
    @DecimalMin("-90") @DecimalMax("90") Double lat,
    @DecimalMin("-180") @DecimalMax("180") Double lng,
    @DecimalMin("1") @DecimalMax("50") Double radiusKm,
    @Pattern(regexp = "(?i)price|distance", message = "sort chỉ nhận price hoặc distance")
        String sort,
    @Min(0) Integer page,
    @Min(1) Integer size) {}
