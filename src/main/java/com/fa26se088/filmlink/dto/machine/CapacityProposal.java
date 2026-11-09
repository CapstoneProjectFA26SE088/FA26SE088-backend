package com.fa26se088.filmlink.dto.machine;

import java.time.Instant;
import java.util.List;

/**
 * Kết quả đề xuất hoặc xác nhận phân bổ lịch hẹn trả cho đơn hàng (BR-58–BR-61).
 *
 * @param feasible lab có khả năng tiếp nhận và xử lý đơn hàng hay không
 * @param infeasibleReason lý do từ chối nếu không khả thi (ví dụ không có máy phù hợp)
 * @param allocations danh sách phân bổ công suất theo từng ngày
 * @param promisedReturnAt thời điểm hẹn trả dự kiến được tính toán
 * @param tierCommitmentAt thời hạn cam kết tối đa của gói dịch vụ
 */
public record CapacityProposal(
    boolean feasible,
    String infeasibleReason,
    List<DayAllocation> allocations,
    Instant promisedReturnAt,
    Instant tierCommitmentAt) {

  /**
   * Khởi tạo đề xuất công suất.
   */
  public CapacityProposal {
    allocations = (allocations != null) ? List.copyOf(allocations) : List.of();
  }
}

