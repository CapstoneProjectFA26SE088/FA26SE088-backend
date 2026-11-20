package com.fa26se088.filmlink.dto.order.response;

import java.time.Instant;
import java.util.List;

/**
 * UC69, BR-58–BR-61: ngày hẹn trả đề xuất cho đơn đang {@code AWAITING_MACHINE_ASSIGNMENT}. Chỉ
 * đọc, chưa ghi gì.
 *
 * @param feasible lab xử lý được đơn với máy hiện có hay không (UH-51 khi {@code false})
 * @param infeasibleReason lý do khi không khả thi
 * @param promisedReturnAt ngày hẹn trả đề xuất; lab chỉ được chọn ngày bằng hoặc muộn hơn (BR-60)
 * @param tierCommitmentAt hạn cam kết của hạng dịch vụ; FE cảnh báo khi ngày hẹn muộn hơn hạn này
 * @param allocations phân bổ công suất theo ngày
 */
public record ScheduleProposalResponse(
    boolean feasible,
    String infeasibleReason,
    Instant promisedReturnAt,
    Instant tierCommitmentAt,
    List<ScheduleAllocationResponse> allocations) {}
