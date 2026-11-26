package com.fa26se088.filmlink.entity.audit.enums;

/**
 * Loại bản ghi bị tác động, lưu ở cột {@code audit_logs.target_type} (varchar(50), không có CHECK).
 * Cùng với {@code target_id} tạo thành khoá tra lịch sử một bản ghi ({@code ix_audit_logs_target}).
 *
 * <p>Tên là entity theo glossary, viết UPPER_SNAKE_CASE. Chia nhóm theo domain như {@code
 * ErrorCode}; domain nào cần thì tự thêm vào nhóm của mình.
 */
public enum AuditTargetType {
  // --- account ---
  USER_ACCOUNT,
  LAB_REGISTRATION_APPLICATION,

  // --- masterdata (MD-02, UC54) ---
  FILM_PROCESS,
  FILM_FORMAT,
  SERVICE_TYPE,
  FILM_STOCK,
  SYSTEM_PARAMETER,

  // --- lab ---
  FILM_LAB,
  /** UC13: gói dịch vụ trong bảng giá của lab. */
  SERVICE_PACKAGE,

  // --- machine (UC68) ---
  /** UC68: thiết bị máy tráng / máy scan của lab. */
  MACHINE,
  /** UC70: sự cố thiết bị máy tráng / máy scan của lab. */
  MACHINE_INCIDENT,
  /** UC71, UC72: bản ghi thay đổi lịch hẹn trả đơn hàng do sự cố máy. */
  SCHEDULE_CHANGE,

  // --- order ---
  /** UC20: đơn dịch vụ tráng / scan. */
  SERVICE_ORDER,

  // --- payment ---
  PAYMENT,
  COMMISSION,
  COMMISSION_DEBT,
  PAYOUT,

  // --- ai ---
  /** Knowledge Base Entry (ERD #46): chunk của Expert Article đã duyệt (BR-29). */
  KNOWLEDGE_BASE_ENTRY
}
