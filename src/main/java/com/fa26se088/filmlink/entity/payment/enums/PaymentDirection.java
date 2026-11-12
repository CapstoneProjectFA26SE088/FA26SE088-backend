package com.fa26se088.filmlink.entity.payment.enums;

/**
 * Chiều dòng tiền (cột {@code payments.direction}): IN hoặc OUT.
 * Khớp CHECK constraint {@code ck_payments_direction} trong DB migration V20261021_2047.
 */
public enum PaymentDirection {
  /** Tiền nạp vào / thanh toán (ORDER_PAYMENT, ORDER_TOP_UP, SALE_PAYMENT, DEBT_PAYMENT). */
  IN,

  /** Tiền hoàn ra (ORDER_REFUND, SALE_REFUND). */
  OUT
}

