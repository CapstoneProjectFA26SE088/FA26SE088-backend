package com.fa26se088.filmlink.dto.archive.response;

import java.time.Instant;

/**
 * Tình trạng scan của một cuộn phía lab (UC26): số ảnh xem trước hiện hành và link file gốc đã
 * kiểm. Dùng để kiểm điều kiện giao scan và hiển thị trong chi tiết đơn của lab.
 *
 * @param archiveRollId {@code null} khi lab chưa gửi link file gốc (chưa có dòng {@code
 *     archive_rolls})
 * @param fullResolutionLink {@code null} khi chưa có link
 * @param linkVerifiedAt thời điểm link được kiểm truy cập được lần gần nhất
 */
public record RollScanStatusResponse(
    Long filmRollId,
    Long archiveRollId,
    long previewCount,
    String fullResolutionLink,
    Instant linkVerifiedAt) {

  /** UC26: cuộn đủ điều kiện giao scan khi có ≥ 1 ảnh xem trước và link đã kiểm. */
  public boolean readyForDelivery() {
    return previewCount > 0 && hasVerifiedLink();
  }

  public boolean hasVerifiedLink() {
    return fullResolutionLink != null && linkVerifiedAt != null;
  }
}
