package com.fa26se088.filmlink.entity.order.enums;

/**
 * Cách giao phim cho lab (UC20), lưu ở {@code service_orders.film_handover_method} ({@code
 * ck_service_orders_handover}).
 */
public enum FilmHandoverMethod {
  /** Photographer tự mang phim tới lab. */
  SELF_DROP_OFF,
  /** Delivery Partner lấy phim nội thành (UC21). */
  PICKUP,
  /** Gửi phim liên tỉnh qua Delivery Partner (chưa hỗ trợ ở giai đoạn này). */
  PARCEL_SHIPPING
}
