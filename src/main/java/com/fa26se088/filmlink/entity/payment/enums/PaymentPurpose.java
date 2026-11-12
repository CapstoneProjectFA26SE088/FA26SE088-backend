package com.fa26se088.filmlink.entity.payment.enums;

/**
 * Mục đích thanh toán (cột {@code payments.purpose}).
 * Khớp hoàn toàn với CHECK constraint {@code ck_payments_purpose} trong DB migration V20261021_2047.
 */
public enum PaymentPurpose {
  /** Thanh toán đơn dịch vụ tráng / scan (ban đầu). */
  ORDER_PAYMENT,

  /** Thanh toán bù chênh lệch sau khi lab chốt giá thực (top-up / balance). */
  ORDER_TOP_UP,

  /** Hoàn tiền đơn dịch vụ (từ chối, hủy, cuộn hỏng, thừa tiền). */
  ORDER_REFUND,

  /** Thanh toán mua hàng ký gửi. */
  SALE_PAYMENT,

  /** Hoàn tiền hàng ký gửi. */
  SALE_REFUND,

  /** Film Lab thanh toán kỳ nợ hoa hồng cho nền tảng. */
  DEBT_PAYMENT
}

