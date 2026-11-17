package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC27: part {@code data} (JSON) của {@code POST /api/v1/lab/orders/{id}/film-return}. Ảnh bằng
 * chứng trả phim gửi ở part {@code photos}. Gửi phim về bằng vận chuyển là Later nên không có trong
 * body: {@code keepNegativesAtLab = false} nghĩa là Photographer nhận phim tại lab.
 *
 * @param keepNegativesAtLab {@code true} = lab giữ âm bản theo yêu cầu của Photographer (không có
 *     bàn giao vật lý, ảnh không bắt buộc); {@code false} = trả phim tại lab (bắt buộc 1–10 ảnh)
 * @param note ghi chú bàn giao, tối đa 500 ký tự
 */
public record RecordFilmReturnRequest(
    @NotNull(message = "Cần chọn trả phim tại lab hay lab giữ âm bản.") Boolean keepNegativesAtLab,
    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.") String note) {}
