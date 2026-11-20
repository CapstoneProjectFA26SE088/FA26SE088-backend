package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverPhotoResponse;
import com.fa26se088.filmlink.entity.delivery.enums.HandoverPoint;
import java.time.Instant;
import java.util.List;

/**
 * Một lần bàn giao phim (BR-25) trả cho Photographer: điểm, số cuộn, ghi chú tình trạng phim, thời
 * điểm và ảnh bằng chứng (link có chữ ký 15 phút). Không có người thực hiện phía lab (dữ liệu nội
 * bộ của lab).
 */
public record OrderFilmHandoverResponse(
    Long id,
    HandoverPoint handoverPoint,
    Short rollCount,
    String note,
    Instant occurredAt,
    List<FilmHandoverPhotoResponse> photos) {}
