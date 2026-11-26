package com.fa26se088.filmlink.service.payment;

import com.fa26se088.filmlink.dto.payment.request.ExecutePayoutRequest;
import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service quản lý Payout giải ngân cho Film Lab (UC35, UC36, BR-31, BR-45, BR-46).
 */
public interface PayoutService {

  /**
   * Tạo phiên giải ngân Payout cho Film Lab (Admin).
   */
  PayoutResponse createPayoutForLab(Long filmLabId, Long amountVnd, AuthenticatedAccount adminAccount);

  /**
   * Admin thực thi chuyển khoản giải ngân và đính kèm ảnh biên lai (UC35, BR-31).
   * Chuyển trạng thái Payout sang PAID.
   */
  PayoutResponse executePayout(Long payoutId, ExecutePayoutRequest request, AuthenticatedAccount adminAccount);

  /**
   * Danh sách Payout của một Lab (Lab Owner xem của mình, Admin xem tất cả).
   */
  Page<PayoutResponse> getLabPayouts(Long filmLabId, Pageable pageable, AuthenticatedAccount account);

  /**
   * Danh sách tất cả Payout dành cho Admin giám sát.
   */
  Page<PayoutResponse> getAllPayouts(PayoutStatus status, Pageable pageable, AuthenticatedAccount adminAccount);

  /**
   * Xem chi tiết một Payout theo ID.
   */
  PayoutResponse getPayoutById(Long payoutId, AuthenticatedAccount account);
}

