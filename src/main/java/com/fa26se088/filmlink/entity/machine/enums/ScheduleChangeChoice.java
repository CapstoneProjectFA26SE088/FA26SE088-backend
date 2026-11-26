package com.fa26se088.filmlink.entity.machine.enums;

/**
 * Lựa chọn phản hồi của khách hàng khi ngày hẹn trả vượt quá cam kết (UC72, bảng {@code
 * schedule_changes.customer_choice}).
 */
public enum ScheduleChangeChoice {
  /** Đồng ý tiếp tục chờ theo ngày hẹn trả mới. */
  WAIT,
  /** Yêu cầu hủy đơn và hoàn tiền. */
  CANCEL,
  /** Yêu cầu chuyển đơn sang Film Lab khác. */
  TRANSFER
}
