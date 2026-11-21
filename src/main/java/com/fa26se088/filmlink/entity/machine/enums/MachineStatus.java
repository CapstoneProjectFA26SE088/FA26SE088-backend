package com.fa26se088.filmlink.entity.machine.enums;

/**
 * Trạng thái vận hành của máy tráng/scan (UC68, UC70, bảng {@code machines.status}).
 */
public enum MachineStatus {
  /** Đang hoạt động bình thường. */
  ACTIVE,
  /** Đang bảo trì định kỳ hoặc tạm ngưng có thời hạn dự kiến. */
  MAINTENANCE,
  /** Ngưng hoạt động do sự cố chưa xác định thời điểm khắc phục. */
  DOWN
}
