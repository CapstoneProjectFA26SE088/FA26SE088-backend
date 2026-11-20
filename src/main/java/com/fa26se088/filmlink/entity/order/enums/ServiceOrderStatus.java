package com.fa26se088.filmlink.entity.order.enums;

/**
 * Trạng thái của đơn dịch vụ tráng / scan (cột {@code service_orders.status}).
 * Khớp hoàn toàn với CHECK constraint {@code ck_service_orders_status} trong DB migration V20261014_2342.
 */
public enum ServiceOrderStatus {
  /** Chờ khách thanh toán ban đầu (đối với phương thức thanh toán ONLINE). */
  AWAITING_PAYMENT,

  /** Chờ Film Lab xác nhận nhận hoặc từ chối đơn. */
  AWAITING_LAB_CONFIRMATION,

  /** Lab đã nhận đơn, chờ khách hàng bàn giao hoặc gửi cuộn phim đến lab. */
  AWAITING_FILM,

  /** Phim đã đến lab, chờ lab xếp lịch máy (BR-58, BR-59). */
  AWAITING_MACHINE_ASSIGNMENT,

  /** Đang trong quy trình xử lý tráng / scan / QC (BR-07). */
  IN_PROCESSING,

  /** Chờ khách thanh toán bổ sung phần chênh lệch giá thực tế (top-up / balance). */
  AWAITING_TOP_UP,

  /** Đã xử lý xong, sẵn sàng trả phim / trả kết quả scan. */
  READY_FOR_HANDOVER,

  /** Đã trả kết quả scan / giao phim thành công. */
  DELIVERED,

  /** Đơn đã đóng hoàn tất (khách xác nhận hoặc hệ thống tự đóng sau 7 ngày). */
  CLOSED,

  /** Đơn đã bị hủy (khách hủy, lab từ chối hoặc hết hạn). */
  CANCELLED,

  /** Đơn bị tạm treo (khi có khiếu nại mở). */
  SUSPENDED
}

