package com.fa26se088.filmlink.dto.lab.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Trang lab công khai (UC05): hồ sơ, giờ mở cửa (theo ngày), ảnh mẫu (link ký có hạn, NFR-PRI-02)
 * và bảng giá (gói {@code ON_SALE} có master data còn ACTIVE, rẻ trước). Không có thông tin liên hệ
 * ({@code contactEmail}, {@code contactPhone}) và các trường nội bộ của lab ({@code
 * qcBufferHours}, {@code expressDailyCapPercent}, trạng thái nhận đơn).
 */
public record PublicFilmLabResponse(
    Long id,
    String labName,
    String description,
    String addressLine,
    String district,
    String province,
    BigDecimal latitude,
    BigDecimal longitude,
    List<FilmLabOpeningHourResponse> openingHours,
    List<FilmLabImageResponse> images,
    List<PublicServicePackageResponse> packages) {}
