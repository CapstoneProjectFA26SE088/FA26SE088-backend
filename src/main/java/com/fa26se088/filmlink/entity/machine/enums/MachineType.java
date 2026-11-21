package com.fa26se088.filmlink.entity.machine.enums;

/**
 * Loại thiết bị trong phòng lab (UC68, bảng {@code machines.machine_type}).
 */
public enum MachineType {
  /** Máy tráng phim (công suất tính theo cuộn/giờ). */
  PROCESSOR,
  /** Máy scan phim (công suất tính theo khung ảnh/giờ - frames/hour). */
  SCANNER
}
