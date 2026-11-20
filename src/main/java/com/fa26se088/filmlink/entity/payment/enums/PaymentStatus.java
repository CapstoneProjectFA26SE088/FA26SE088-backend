package com.fa26se088.filmlink.entity.payment.enums;

/**
 * Trạng thái thanh toán (cột {@code payments.status}).
 * Khớp CHECK constraint {@code ck_payments_status} trong DB migration V20261021_2047.
 */
public enum PaymentStatus {
  /** Đang chờ khách thanh toán (link thanh toán còn hiệu lực). */
  PENDING,

  /** Thanh toán thành công (đã nhận IPN hợp lệ / đã xác nhận thu tiền). */
  SUCCEEDED,

  /** Thanh toán thất bại. */
  FAILED,

  /** Hết hạn thanh toán (sau 15 phút không nhận được IPN). */
  EXPIRED,

  /** Khách hủy lượt thanh toán. */
  CANCELLED,

  /** Hoàn tiền tự động thất bại, chờ Admin xử lý chuyển khoản thủ công. */
  AWAITING_MANUAL
}

