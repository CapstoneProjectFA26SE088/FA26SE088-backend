package com.fa26se088.filmlink.entity.machine.enums;

/**
 * Nguyên nhân sự cố máy tráng hoặc máy scan (UC70, bảng {@code machine_incidents.cause}).
 */
public enum MachineIncidentCause {
  /** Hỏng hóc phần cứng hoặc lỗi kỹ thuật của máy. */
  MACHINE_FAILURE,
  /** Hết hóa chất xử lý phim. */
  CHEMICALS_RUN_OUT,
  /** Hóa chất xử lý phim hết hạn sử dụng. */
  CHEMICALS_EXPIRED,
  /** Mất điện hoặc sự cố nguồn điện tại phòng lab. */
  POWER_OUTAGE,
  /** Nguyên nhân khác (bắt buộc nhập mô tả chi tiết). */
  OTHER
}
