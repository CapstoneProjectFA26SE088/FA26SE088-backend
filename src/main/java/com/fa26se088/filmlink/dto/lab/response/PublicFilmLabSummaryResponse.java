package com.fa26se088.filmlink.dto.lab.response;

import java.net.URI;

/**
 * Một dòng kết quả tìm lab công khai (UC17).
 *
 * @param coverImageUrl ảnh đại diện (ảnh có {@code displayOrder} nhỏ nhất), link ký có hạn; {@code
 *     null} nếu lab chưa có ảnh
 * @param minPriceVnd giá thấp nhất trong các gói {@code ON_SALE} khớp bộ lọc
 * @param distanceKm khoảng cách (km, làm tròn 2 chữ số) tới toạ độ đã gửi; {@code null} khi không
 *     gửi toạ độ hoặc lab chưa có toạ độ
 */
public record PublicFilmLabSummaryResponse(
    Long id,
    String labName,
    String addressLine,
    String district,
    String province,
    URI coverImageUrl,
    Long minPriceVnd,
    Double distanceKm) {}
