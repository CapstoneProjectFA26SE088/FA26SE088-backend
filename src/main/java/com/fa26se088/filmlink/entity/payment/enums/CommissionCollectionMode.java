package com.fa26se088.filmlink.entity.payment.enums;

/**
 * Phương thức thu hoa hồng (ERD #27, bảng {@code commissions}).
 */
public enum CommissionCollectionMode {
  DEDUCTED, // Khấu trừ trực tiếp từ tiền đơn online
  DEBT      // Ghi nhận vào kỳ nợ của Lab đối với đơn COD hoặc Pay-at-Lab
}

