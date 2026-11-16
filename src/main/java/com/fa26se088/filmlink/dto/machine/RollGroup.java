package com.fa26se088.filmlink.dto.machine;

import java.util.Objects;

/**
 * Nhóm các cuộn phim có cùng quy trình và khổ phim trong đơn hàng cần xếp lịch (BR-58).
 *
 * @param filmProcessId ID quy trình tráng (C-41, B&amp;W, E-6, ECN-2,...)
 * @param filmFormatId ID khổ phim (135, 120, Sheet,...)
 * @param rollCount số lượng cuộn phim thuộc nhóm này
 * @param framesPerRoll số kiểu ảnh trung bình trên mỗi cuộn (mặc định 36 với 135)
 */
public record RollGroup(
    Long filmProcessId,
    Long filmFormatId,
    int rollCount,
    int framesPerRoll) {

  /**
   * Khởi tạo và kiểm tra ràng buộc dữ liệu nhóm cuộn phim.
   */
  public RollGroup {
    Objects.requireNonNull(filmProcessId, "filmProcessId must not be null");
    Objects.requireNonNull(filmFormatId, "filmFormatId must not be null");
    if (rollCount <= 0) {
      throw new IllegalArgumentException("rollCount must be greater than 0");
    }
    if (framesPerRoll < 0) {
      throw new IllegalArgumentException("framesPerRoll must not be negative");
    }
  }
}

