package com.fa26se088.filmlink.entity.order.enums;

import java.util.Optional;

/**
 * Công đoạn xử lý của Service Order (BR-07), lưu ở {@code processing_stage_records.stage} ({@code
 * ck_processing_stage_records_stage}). Thứ tự bắt buộc: RECEIVED → DEVELOPING → WASHING → DRYING →
 * SCANNING → QC, không bỏ qua, không lùi (chỉ Admin được lùi, UC24).
 */
public enum ProcessingStage {
  /** UC69: dòng đầu tiên, ghi khi lab xác nhận ngày hẹn trả và đơn chuyển {@code IN_PROCESSING}. */
  RECEIVED("Đã nhận phim"),
  DEVELOPING("Tráng phim"),
  WASHING("Rửa phim"),
  DRYING("Sấy phim"),
  SCANNING("Scan"),
  QC("Kiểm tra chất lượng (QC)");

  /** Tên công đoạn tiếng Việt, dùng trong thông báo và thông điệp lỗi. */
  private final String vietnameseName;

  ProcessingStage(String vietnameseName) {
    this.vietnameseName = vietnameseName;
  }

  public String getVietnameseName() {
    return vietnameseName;
  }

  /**
   * BR-07: công đoạn kế tiếp theo thứ tự khai báo của enum (thứ tự hằng số chính là thứ tự bắt
   * buộc); {@link #QC} là cuối cùng nên trả rỗng.
   */
  public Optional<ProcessingStage> next() {
    ProcessingStage[] all = values();
    int index = ordinal() + 1;
    return index < all.length ? Optional.of(all[index]) : Optional.empty();
  }
}
