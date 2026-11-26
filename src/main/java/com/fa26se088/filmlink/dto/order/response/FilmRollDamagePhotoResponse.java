package com.fa26se088.filmlink.dto.order.response;

import java.net.URI;
import java.time.Instant;

/**
 * BR-26: một ảnh bằng chứng cuộn phim hỏng; {@code url} là link có chữ ký 15 phút (NFR-PRI-02).
 */
public record FilmRollDamagePhotoResponse(Long id, URI url, Instant createdAt) {}
