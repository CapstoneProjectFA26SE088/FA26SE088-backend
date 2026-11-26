package com.fa26se088.filmlink.service.order.event;

import java.time.Instant;
import java.util.Objects;

/**
 * UC22, BR-39: Film Lab Owner từ chối Service Order (trạng thái sang CANCELLED). Domain payment
 * lắng nghe để hoàn 100% tiền đã thu tự động mà không cần Admin can thiệp. UH-09: nếu phim đã gửi
 * đi, domain delivery lắng nghe để tạo chiều trả phim (phí lab chịu).
 *
 * @param serviceOrderId id của Service Order
 * @param orderCode mã hiển thị của đơn
 * @param photographerAccountId id tài khoản Photographer tạo đơn
 * @param filmLabId id của Film Lab từ chối đơn
 * @param refundAmountVnd số tiền hoàn 100% (nếu đã thanh toán online)
 * @param rejectionReason lý do từ chối của lab
 * @param filmAlreadyShipped true nếu phim đã được gửi đi (UH-09)
 * @param rejectedAt thời điểm từ chối
 */
public record OrderRejectedEvent(
    Long serviceOrderId,
    String orderCode,
    Long photographerAccountId,
    Long filmLabId,
    long refundAmountVnd,
    String rejectionReason,
    boolean filmAlreadyShipped,
    Instant rejectedAt) {

  public OrderRejectedEvent {
    Objects.requireNonNull(serviceOrderId, "serviceOrderId");
    Objects.requireNonNull(orderCode, "orderCode");
    Objects.requireNonNull(photographerAccountId, "photographerAccountId");
    Objects.requireNonNull(filmLabId, "filmLabId");
    Objects.requireNonNull(rejectionReason, "rejectionReason");
    Objects.requireNonNull(rejectedAt, "rejectedAt");
  }
}
