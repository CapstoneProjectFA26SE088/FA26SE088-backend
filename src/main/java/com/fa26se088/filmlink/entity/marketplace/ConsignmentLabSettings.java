package com.fa26se088.filmlink.entity.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cấu hình nhận ký gửi của từng Film Lab (UC45, BR-54), bảng {@code consignment_lab_settings}.
 *
 * <p>Bảng dùng khoá chính {@code film_lab_id} (không có cột {@code id}) nên không kế thừa {@code
 * BaseEntity}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "consignment_lab_settings")
public class ConsignmentLabSettings {

  @Id
  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  @Column(name = "is_accepting_consignments", nullable = false)
  private boolean acceptingConsignments = false;

  @Column(name = "consignment_fee_rate", precision = 5, scale = 2)
  private BigDecimal consignmentFeeRate;

  @Column(name = "slot_minutes", nullable = false)
  private Short slotMinutes = 30;

  @Column(name = "appointments_per_slot", nullable = false)
  private Short appointmentsPerSlot = 1;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
