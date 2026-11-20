package com.fa26se088.filmlink.dto.order.response;

import java.net.URI;
import java.time.Instant;

/**
 * Một dòng Digital Film Archive của Photographer (UC28): cuộn đã được giao scan, kèm đơn, lab,
 * dòng phim, khổ phim.
 *
 * @param filmStockName tên dòng phim trong danh mục (hãng + tên) hoặc tên Photographer tự nhập;
 *     {@code null} nếu dòng phim trong danh mục đã ngừng dùng
 * @param filmFormatName tên khổ phim; {@code null} nếu khổ phim đã ngừng dùng
 * @param coverPreviewUrl link có chữ ký (15 phút) của ảnh xem trước đầu tiên
 */
public record FilmArchiveRollSummaryResponse(
    Long archiveRollId,
    Long filmRollId,
    Short rollSequence,
    Long serviceOrderId,
    String orderCode,
    Long filmLabId,
    String labName,
    Long filmStockId,
    String filmStockName,
    Long filmFormatId,
    String filmFormatName,
    Instant deliveredAt,
    long previewCount,
    URI coverPreviewUrl) {}
