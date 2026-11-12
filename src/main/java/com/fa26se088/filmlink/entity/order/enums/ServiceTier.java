package com.fa26se088.filmlink.entity.order.enums;

/**
 * Cấp độ dịch vụ của đơn hàng (cột {@code service_orders.service_tier}).
 * Khớp CHECK constraint {@code ck_service_orders_tier} trong DB migration V20261014_2342.
 */
public enum ServiceTier {
  STANDARD,
  EXPRESS
}

