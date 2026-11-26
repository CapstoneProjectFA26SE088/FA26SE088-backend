package com.fa26se088.filmlink.entity.payment.enums;

/**
 * Trạng thái yêu cầu Payout cho Film Lab hoặc người bán (ERD #29, bảng {@code payouts}).
 */
public enum PayoutStatus {
  PENDING,
  PAID,
  FAILED,
  CANCELLED
}

