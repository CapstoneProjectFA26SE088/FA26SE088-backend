package com.fa26se088.filmlink.entity.payment.enums;

/**
 * Phương thức thanh toán (cột {@code payments.method}).
 * Khớp CHECK constraint {@code ck_payments_method} trong DB migration V20261021_2047.
 */
public enum PaymentMethod {
  /** Cổng thanh toán trực tuyến VNPay. */
  VNPAY,

  /** Thanh toán trực tiếp tại Film Lab (ngoài nền tảng). */
  PAY_AT_LAB,

  /** Thu hộ qua đơn vị vận chuyển (ngoài nền tảng). */
  COD,

  /** Chuyển khoản ngân hàng thủ công (trả nợ hoa hồng hoặc hoàn tiền thủ công). */
  BANK_TRANSFER
}

