package com.fa26se088.filmlink.entity.order.enums;

/**
 * Trạng thái của Service Order, lưu ở {@code service_orders.status} ({@code
 * ck_service_orders_status}). 11 trạng thái theo state machine Report 4 (chốt ở Review 2,
 * FA26-192). Công đoạn xử lý (BR-07) là bảng riêng {@code processing_stage_records}, không nằm ở
 * đây.
 */
public enum ServiceOrderStatus {
  /** UC20: đơn ONLINE vừa tạo, chờ Photographer thanh toán. */
  AWAITING_PAYMENT,
  /** UC22: chờ lab nhận / từ chối trong {@code LAB_RESPONSE_LIMIT_HOURS}. */
  AWAITING_LAB_CONFIRMATION,
  AWAITING_FILM,
  AWAITING_MACHINE_ASSIGNMENT,
  IN_PROCESSING,
  AWAITING_TOP_UP,
  READY_FOR_HANDOVER,
  DELIVERED,
  CLOSED,
  CANCELLED,
  SUSPENDED
}
