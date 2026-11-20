package com.fa26se088.filmlink.entity.order.enums;

/**
 * Phương thức thanh toán được chọn khi tạo đơn hàng (cột {@code service_orders.payment_method}).
 * Khớp CHECK constraint {@code ck_service_orders_payment_method} trong DB migration V20261014_2342.
 */
public enum OrderPaymentMethod {
  /** Thanh toán trực tuyến (VNPay). */
  ONLINE,

  /** Thanh toán trực tiếp tại lab khi bàn giao phim hoặc nhận kết quả. */
  PAY_AT_LAB,

  /** Thu hộ tiền qua đơn vị giao nhận (chỉ áp dụng với PICKUP hoặc PARCEL_SHIPPING). */
  COD
}

