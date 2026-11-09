package com.fa26se088.filmlink.dto.machine;

import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Yêu cầu tính toán công suất hoặc khóa lịch hẹn trả cho đơn hàng (BR-58–BR-61).
 *
 * @param serviceOrderId ID đơn hàng dịch vụ
 * @param filmLabId ID phòng lab tiếp nhận
 * @param tier hạng dịch vụ (STANDARD hoặc EXPRESS)
 * @param receivedAt thời điểm lab xác nhận nhận được phim vật lý
 * @param groups danh sách các nhóm cuộn phim cần xử lý
 */
public record CapacityRequest(
    Long serviceOrderId,
    Long filmLabId,
    ServiceTier tier,
    Instant receivedAt,
    List<RollGroup> groups) {

  /**
   * Khởi tạo và kiểm tra ràng buộc yêu cầu tính công suất.
   */
  public CapacityRequest {
    Objects.requireNonNull(serviceOrderId, "serviceOrderId must not be null");
    Objects.requireNonNull(filmLabId, "filmLabId must not be null");
    Objects.requireNonNull(tier, "tier must not be null");
    Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    Objects.requireNonNull(groups, "groups must not be null");
    if (groups.isEmpty()) {
      throw new IllegalArgumentException("groups must not be empty");
    }
    groups = List.copyOf(groups);
  }
}

