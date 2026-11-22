package com.fa26se088.filmlink.entity.audit.enums;

/**
 * Thao tác nhạy cảm được ghi audit log (BR-32), lưu ở cột {@code audit_logs.action}
 * (varchar(100), không có CHECK).
 *
 * <p>Đặt tên theo mẫu {@code <DOMAIN>_<ĐỘNG_TỪ_QUÁ_KHỨ>}, chia nhóm theo domain như {@code
 * ErrorCode}. Chỉ thêm giá trị khi có code thật ghi thao tác đó; domain nào cần thì tự thêm vào
 * nhóm của mình.
 */
public enum AuditAction {
  // --- account ---
  ACCOUNT_PASSWORD_CHANGED,
  ACCOUNT_ADMIN_CREATED,

  /** SCR-03: đặt lại mật khẩu thành công qua luồng quên mật khẩu. */
  ACCOUNT_PASSWORD_RESET,

  /**
   * UC06: job định kỳ xoá hàng loạt tài khoản PENDING_VERIFICATION quá 24 giờ chưa xác thực email.
   * Actor là System (Scheduler). Target type USER_ACCOUNT, targetId = null (batch).
   */
  ACCOUNT_UNVERIFIED_PURGED,

  /** ACC-09: Cập nhật thông tin cá nhân tài khoản. */
  ACCOUNT_PROFILE_UPDATED,

  /** ACC-09: Vô hiệu hoá / xoá mềm tài khoản (chuyển trạng thái sang INACTIVE). */
  ACCOUNT_DEACTIVATED,

  // --- machine (UC68) ---
  /** UC68: Film Lab Owner khai báo máy tráng / máy scan mới; after ghi mã máy, loại máy, trạng thái. */
  MACHINE_CREATED,

  /** UC68: Film Lab Owner cập nhật cấu hình máy tráng / máy scan. */
  MACHINE_UPDATED,

  /** UC68: Film Lab Owner cập nhật cấu hình tham số lập lịch (qcBufferHours, expressDailyCapPercent, expressCommitmentWorkingDays). */
  LAB_SCHEDULING_SETTINGS_UPDATED,

  /** UC70: Film Lab Owner khai báo sự cố máy tráng / máy scan. */
  MACHINE_INCIDENT_REPORTED,

  /** UC70: Film Lab Owner xác nhận giải quyết xong sự cố và khôi phục máy ACTIVE. */
  MACHINE_INCIDENT_RESOLVED,

  /** UC72: Photographer phản hồi thông báo dời ngày hẹn trả do sự cố máy. */
  SCHEDULE_CHANGE_ANSWERED,

  // --- order ---
  ORDER_CREATED,
  ORDER_STATUS_CHANGED,

  // --- payment ---
  PAYMENT_CREATED,
  PAYMENT_SUCCEEDED,
  PAYMENT_FAILED,
  PAYMENT_REFUNDED,
  COMMISSION_RECORDED,
  COMMISSION_DEBT_CREATED,
  COMMISSION_DEBT_SETTLED,
  PAYOUT_CREATED,
  PAYOUT_EXECUTED
}
