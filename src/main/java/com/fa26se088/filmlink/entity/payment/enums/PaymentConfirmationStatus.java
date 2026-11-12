package com.fa26se088.filmlink.entity.payment.enums;

/**
 * Trạng thái đối soát của khách hàng với khoản thu Pay-at-Lab (cột {@code payments.confirmation_status}).
 * Khớp CHECK constraint {@code ck_payments_confirmation} trong DB migration V20261021_2047.
 */
public enum PaymentConfirmationStatus {
  /** Không áp dụng (cho thanh toán online VNPAY, COD hoặc BANK_TRANSFER). */
  NOT_REQUIRED,

  /** Chờ khách hàng đối soát và xác nhận đã trả tiền tại lab. */
  AWAITING_CONFIRMATION,

  /** Khách hàng đã xác nhận đã thanh toán tại lab. */
  CONFIRMED,

  /** Khách hàng phản đối / khiếu nại khoản ghi nhận này. */
  DISPUTED
}

