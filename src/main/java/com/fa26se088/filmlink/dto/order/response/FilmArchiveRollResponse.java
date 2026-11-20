package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.dto.archive.response.ScanFileResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chi tiết một cuộn trong Digital Film Archive của Photographer (UC28, BR-08): đơn, lab, dòng
 * phim, khổ phim, link file độ phân giải gốc, metadata và ảnh xem trước (link có chữ ký 15 phút).
 */
public record FilmArchiveRollResponse(
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
    Short exposureCount,
    String fullResolutionLink,
    Instant linkVerifiedAt,
    Instant deliveredAt,
    String camera,
    String lens,
    LocalDate shotOn,
    String location,
    String note,
    String frameCountDiscrepancyNote,
    List<ScanFileResponse> previews) {}
