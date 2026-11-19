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

  /** UC08: Admin duyệt đơn đăng ký mở Film Lab. */
  LAB_APPLICATION_APPROVED,

  /** UC08: Admin từ chối đơn đăng ký mở Film Lab. */
  LAB_APPLICATION_REJECTED,

  // --- masterdata (MD-02, UC54) ---
  MASTERDATA_CREATED,
  MASTERDATA_UPDATED,
  MASTERDATA_STATUS_CHANGED,
  MASTERDATA_DELETE_REJECTED,
  SYSTEM_PARAMETER_UPDATED,

  // --- lab ---
  /** UC12: Film Lab Owner bật/tắt nhận đơn; before/after ghi {@code acceptingOrders}. */
  LAB_ACCEPTING_ORDERS_CHANGED,

  /** UC13: Film Lab Owner tạo Service Package; after ghi tên, giá, trạng thái. */
  LAB_SERVICE_PACKAGE_CREATED,

  /** UC13: Film Lab Owner mở bán / ngừng bán Service Package; before/after ghi {@code status}. */
  LAB_SERVICE_PACKAGE_STATUS_CHANGED,

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

  // --- order ---
  /** UC20: Photographer tạo Service Order; after ghi mã đơn, lab, trạng thái, tổng tạm tính. */
  ORDER_CREATED,

  /** UC22: Film Lab Owner nhận Service Order; before/after ghi {@code status}. */
  ORDER_ACCEPTED,

  /**
   * UC23: Film Lab Owner xác nhận đã nhận phim và chốt giá; before/after ghi {@code status}, {@code
   * finalTotalVnd}, số cuộn nhận.
   */
  ORDER_FILM_RECEIVED,

  /**
   * UC69: Film Lab Owner xác nhận ngày hẹn trả, đơn sang {@code IN_PROCESSING}; before/after ghi
   * {@code status}, {@code promisedReturnAt}.
   */
  ORDER_SCHEDULE_CONFIRMED,

  /**
   * UC71: ngày hẹn trả đổi do sự cố máy; before/after ghi {@code promisedReturnAt}, after thêm
   * {@code machineIncidentId}, {@code requiresCustomerResponse}.
   */
  ORDER_PROMISED_DATE_CHANGED,

  /**
   * UC24, BR-07: Film Lab Owner ghi công đoạn xử lý kế tiếp; before/after ghi {@code stage}.
   */
  ORDER_STAGE_RECORDED
}
