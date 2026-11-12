package com.fa26se088.filmlink.service.payment;

import com.fa26se088.filmlink.dto.payment.response.CommissionResponse;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service quản lý hoa hồng nền tảng (Commission) - BR-10, BR-55.
 */
public interface CommissionService {

  /**
   * Tính và ghi nhận hoa hồng khi đóng đơn dịch vụ (ServiceOrder).
   * Đảm bảo tính idempotent: một đơn hàng chỉ có tối đa 1 bản ghi hoa hồng ORDER.
   *
   * @param order đơn hàng đã hoàn tất (CLOSED)
   * @return CommissionResponse
   */
  CommissionResponse createCommissionForOrder(ServiceOrder order);

  /**
   * Lấy danh sách hoa hồng của một Film Lab (Lab Owner xem của mình, Admin xem tất cả).
   */
  Page<CommissionResponse> getLabCommissions(Long labId, Pageable pageable, AuthenticatedAccount account);
}

