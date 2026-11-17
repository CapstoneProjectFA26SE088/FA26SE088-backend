package com.fa26se088.filmlink.dto.archive.response;

import java.net.URI;
import java.time.Instant;

/**
 * Một ảnh xem trước (Preview Scan) của cuộn (UC26, BR-08). {@code url} là link có chữ ký, hết hạn
 * sau 15 phút (NFR-PRI-02); {@code checksumSha256} để kiểm file tải về (NFR-CAP-03).
 */
public record ScanFileResponse(
    Long id,
    Short frameNumber,
    Short widthPx,
    Short heightPx,
    Integer sizeBytes,
    String checksumSha256,
    URI url,
    Instant createdAt) {}
