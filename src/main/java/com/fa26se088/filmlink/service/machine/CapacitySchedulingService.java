package com.fa26se088.filmlink.service.machine;

import com.fa26se088.filmlink.dto.machine.CapacityProposal;
import com.fa26se088.filmlink.dto.machine.CapacityRequest;
import java.time.Instant;

/**
 * Dịch vụ tính toán công suất lab và xác nhận lịch hẹn trả đơn hàng (BR-58–BR-61, UC69).
 */
public interface CapacitySchedulingService {

  /**
   * Đề xuất ngày hẹn trả và phân bổ công suất dự kiến cho đơn hàng (Read-only, không ghi DB).
   *
   * @param request thông tin đơn hàng và các nhóm cuộn phim cần tính toán
   * @return kết quả đề xuất gồm tính khả thi, phân bổ ngày và ngày hẹn trả dự kiến
   */
  CapacityProposal propose(CapacityRequest request);

  /**
   * Khóa lịch hẹn trả chính thức và lưu phân bổ vào {@code order_capacity_allocations}.
   *
   * @param request thông tin đơn hàng cần khóa lịch
   * @param promisedReturnAt thời điểm hẹn trả chính thức được xác nhận
   * @return kết quả xác nhận lịch hẹn trả
   */
  CapacityProposal confirm(CapacityRequest request, Instant promisedReturnAt);
}

