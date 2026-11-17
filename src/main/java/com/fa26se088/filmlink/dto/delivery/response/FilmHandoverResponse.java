package com.fa26se088.filmlink.dto.delivery.response;

import com.fa26se088.filmlink.entity.delivery.enums.HandoverPoint;
import java.time.Instant;
import java.util.List;

/** Một lần bàn giao phim (BR-25) kèm ảnh bằng chứng theo {@code displayOrder}. */
public record FilmHandoverResponse(
    Long id,
    HandoverPoint handoverPoint,
    Short rollCount,
    String note,
    Long performedByAccountId,
    String performedByName,
    Instant occurredAt,
    List<FilmHandoverPhotoResponse> photos) {}
