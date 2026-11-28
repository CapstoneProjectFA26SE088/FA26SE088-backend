package com.fa26se088.filmlink.service.payment;

import com.fa26se088.filmlink.dto.payment.ClosedOrderCommission;
import com.fa26se088.filmlink.dto.payment.CompletedSaleCommission;
import com.fa26se088.filmlink.dto.payment.response.CommissionResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Service hoa hồng nền tảng (Commission) — BR-10, BR-11, BR-55. */
public interface CommissionService {

  /** BR-10/BR-11: called by order when it becomes CLOSED, in the same transaction. Idempotent per order. */
  void recordForClosedOrder(ClosedOrderCommission command);

  /** UC64, BR-54/BR-55: ghi hoa hồng khi giao dịch ký gửi COMPLETED (cùng transaction, idempotent). */
  void recordForCompletedSale(CompletedSaleCommission command);

  /** Danh sách hoa hồng của một Film Lab (Lab Owner xem lab của mình, Admin xem tất cả). */
  Page<CommissionResponse> getLabCommissions(
      Long labId, Pageable pageable, AuthenticatedAccount account);
}
