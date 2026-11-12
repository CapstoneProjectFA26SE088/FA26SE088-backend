package com.fa26se088.filmlink.entity.order.enums;

/**
 * Phương thức bàn giao phim giữa khách hàng và Film Lab (cột {@code service_orders.film_handover_method}).
 * Khớp CHECK constraint {@code ck_service_orders_handover} trong DB migration V20261014_2342.
 */
public enum FilmHandoverMethod {
  /** Khách tự mang phim đến lab. */
  SELF_DROP_OFF,

  /** Đơn vị vận chuyển lấy phim tận nơi (nội thành). */
  PICKUP,

  /** Gửi bưu kiện qua đơn vị chuyển phát (liên tỉnh). */
  PARCEL_SHIPPING
}

