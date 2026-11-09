package com.fa26se088.filmlink.service.machine.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Sự kiện thông báo ngày hẹn trả của đơn hàng bị thay đổi do sự cố máy (UC71, BR-61).
 *
 * <p>Domain {@code order} lắng nghe để cập nhật trạng thái đơn hàng và gửi thông báo cho khách.
 *
 * @param serviceOrderId ID đơn hàng dịch vụ bị ảnh hưởng
 * @param machineIncidentId ID sự cố máy gây ra thay đổi lịch
 * @param oldPromisedAt thời điểm hẹn trả cũ
 * @param newPromisedAt thời điểm hẹn trả mới sau khi tái xếp lịch
 * @param requiresCustomerResponse cờ xác định khách hàng có cần phản hồi (WAIT/CANCEL/TRANSFER) hay
 *     không (true nếu ngày mới vượt quá thời hạn cam kết gói dịch vụ)
 */
public record PromisedDateChangedEvent(
    Long serviceOrderId,
    Long machineIncidentId,
    Instant oldPromisedAt,
    Instant newPromisedAt,
    boolean requiresCustomerResponse) {

  /**
   * Khởi tạo và kiểm tra ràng buộc sự kiện đổi ngày hẹn trả.
   */
  public PromisedDateChangedEvent {
    Objects.requireNonNull(serviceOrderId, "serviceOrderId must not be null");
    Objects.requireNonNull(machineIncidentId, "machineIncidentId must not be null");
    Objects.requireNonNull(oldPromisedAt, "oldPromisedAt must not be null");
    Objects.requireNonNull(newPromisedAt, "newPromisedAt must not be null");
  }
}

