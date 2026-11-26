package com.fa26se088.filmlink.entity.order.enums;

/**
 * Phương thức thanh toán của Service Order (UC20), lưu ở {@code service_orders.payment_method}
 * ({@code ck_service_orders_payment_method}). ONLINE trả 100% giá tạm tính khi đặt (không đặt
 * cọc). BR-09: COD chỉ đi với PICKUP hoặc PARCEL_SHIPPING ({@code ck_service_orders_cod}).
 */
public enum PaymentMethod {
  ONLINE,
  PAY_AT_LAB,
  COD
}
