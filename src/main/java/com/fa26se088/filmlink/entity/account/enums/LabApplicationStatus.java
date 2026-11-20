package com.fa26se088.filmlink.entity.account.enums;

/**
 * Trạng thái xử lý của đơn đăng ký mở Film Lab (UC07, UC08).
 */
public enum LabApplicationStatus {

  /** Đơn mới nộp bởi Guest, đang chờ Admin xét duyệt. */
  PENDING_REVIEW,

  /** Admin yêu cầu ứng viên bổ sung thông tin hoặc tài liệu (M-03). */
  AWAITING_INFORMATION,

  /** Đơn đã được Admin phê duyệt thành công. */
  APPROVED,

  /** Đơn bị Admin từ chối. */
  REJECTED
}

