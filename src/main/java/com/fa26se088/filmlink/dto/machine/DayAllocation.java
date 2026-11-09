package com.fa26se088.filmlink.dto.machine;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Phân bổ số cuộn phim xử lý vào một ngày làm việc cụ thể của phòng lab (BR-59, BR-60).
 *
 * @param workDate ngày làm việc theo giờ Việt Nam (GMT+7)
 * @param filmProcessId ID quy trình tráng
 * @param filmFormatId ID khổ phim
 * @param rollCount số cuộn được phân bổ trong ngày này
 */
public record DayAllocation(
    LocalDate workDate,
    Long filmProcessId,
    Long filmFormatId,
    int rollCount) {

  /**
   * Khởi tạo và kiểm tra ràng buộc phân bổ ngày.
   */
  public DayAllocation {
    Objects.requireNonNull(workDate, "workDate must not be null");
    Objects.requireNonNull(filmProcessId, "filmProcessId must not be null");
    Objects.requireNonNull(filmFormatId, "filmFormatId must not be null");
    if (rollCount <= 0) {
      throw new IllegalArgumentException("rollCount must be greater than 0");
    }
  }
}

