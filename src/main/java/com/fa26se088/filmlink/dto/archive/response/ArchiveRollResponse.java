package com.fa26se088.filmlink.dto.archive.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chi tiết một cuộn trong Digital Film Archive (UC26, UC28): link file gốc (BR-08, file gốc không
 * lưu ở FilmLink), metadata và các ảnh xem trước hiện hành theo số khung.
 */
public record ArchiveRollResponse(
    Long id,
    Long filmRollId,
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
