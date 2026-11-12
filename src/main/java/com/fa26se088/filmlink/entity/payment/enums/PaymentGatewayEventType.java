package com.fa26se088.filmlink.entity.payment.enums;

/**
 * Loại sự kiện callback từ cổng thanh toán (cột {@code payment_gateway_events.event_type}).
 * Khớp CHECK constraint {@code ck_payment_gateway_events_type} trong DB migration V20261021_2047.
 */
public enum PaymentGatewayEventType {
  /** Server-to-server callback (IPN) từ cổng thanh toán. */
  IPN,

  /** Browser redirect từ cổng thanh toán về trình duyệt của khách hàng. */
  RETURN,

  /** Đối soát chủ động từ job hệ thống gọi API query của cổng. */
  QUERY
}

