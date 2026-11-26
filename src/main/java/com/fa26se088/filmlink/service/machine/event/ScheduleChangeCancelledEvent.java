package com.fa26se088.filmlink.service.machine.event;

import java.util.Objects;

/**
 * Sự kiện thông báo thay đổi lịch hẹn trả bị hủy bỏ (UC71, UC72).
 *
 * @param serviceOrderId ID đơn hàng dịch vụ
 * @param scheduleChangeId ID bản ghi thay đổi lịch bị hủy
 */
public record ScheduleChangeCancelledEvent(
    Long serviceOrderId,
    Long scheduleChangeId) {

  /**
   * Khởi tạo và kiểm tra ràng buộc sự kiện hủy thay đổi lịch.
   */
  public ScheduleChangeCancelledEvent {
    Objects.requireNonNull(serviceOrderId, "serviceOrderId must not be null");
    Objects.requireNonNull(scheduleChangeId, "scheduleChangeId must not be null");
  }
}

