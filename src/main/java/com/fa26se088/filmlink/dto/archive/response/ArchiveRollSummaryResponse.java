package com.fa26se088.filmlink.dto.archive.response;

import java.net.URI;
import java.time.Instant;

/**
 * Phần dữ liệu của domain archive cho một dòng Digital Film Archive (UC28). Domain order ghép thêm
 * mã đơn, tên lab, dòng phim, khổ phim.
 *
 * @param coverPreviewUrl link có chữ ký (15 phút) của ảnh có số khung nhỏ nhất; {@code null} nếu
 *     cuộn không còn ảnh hiện hành
 */
public record ArchiveRollSummaryResponse(
    Long id, Long filmRollId, Instant deliveredAt, long previewCount, URI coverPreviewUrl) {}
