package com.fa26se088.filmlink.entity.order.enums;

/**
 * Công đoạn xử lý của Service Order (BR-07), lưu ở {@code processing_stage_records.stage} ({@code
 * ck_processing_stage_records_stage}). Thứ tự bắt buộc: RECEIVED → DEVELOPING → WASHING → DRYING →
 * SCANNING → QC, không bỏ qua, không lùi (chỉ Admin được lùi, UC24).
 */
public enum ProcessingStage {
  /** UC69: dòng đầu tiên, ghi khi lab xác nhận ngày hẹn trả và đơn chuyển {@code IN_PROCESSING}. */
  RECEIVED,
  DEVELOPING,
  WASHING,
  DRYING,
  SCANNING,
  QC
}
