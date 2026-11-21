package com.fa26se088.filmlink.entity.machine.enums;

/**
 * Bước xử lý đã áp dụng khi tái xếp lịch đơn hàng do sự cố máy (UC71, bảng {@code
 * schedule_changes.step_applied}).
 */
public enum ScheduleChangeStep {
  /** Chuyển sang máy thay thế khác trong cùng phòng lab. */
  MOVED_MACHINE,
  /** Tái phân bổ công suất theo ngày và dời ngày hẹn trả của đơn hàng. */
  RESCHEDULED
}
