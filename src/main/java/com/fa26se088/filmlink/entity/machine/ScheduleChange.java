package com.fa26se088.filmlink.entity.machine;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.machine.enums.ScheduleChangeChoice;
import com.fa26se088.filmlink.entity.machine.enums.ScheduleChangeStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Lịch sử thay đổi ngày hẹn trả đơn hàng khi xảy ra sự cố máy (UC71, UC72, bảng {@code
 * schedule_changes}).
 */
@Getter
@Setter
@Entity
@Table(name = "schedule_changes")
public class ScheduleChange extends BaseEntity {

  @Column(name = "service_order_id", nullable = false)
  private Long serviceOrderId;

  @Column(name = "machine_incident_id", nullable = false)
  private Long machineIncidentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_applied", nullable = false, length = 20)
  private ScheduleChangeStep stepApplied;

  @Column(name = "old_promised_at", nullable = false)
  private Instant oldPromisedAt;

  @Column(name = "new_promised_at", nullable = false)
  private Instant newPromisedAt;

  @Column(name = "requires_response", nullable = false)
  private boolean requiresResponse;

  @Column(name = "response_due_at")
  private Instant responseDueAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "customer_choice", length = 20)
  private ScheduleChangeChoice customerChoice;

  @Column(name = "customer_note", length = 300)
  private String customerNote;

  @Column(name = "responded_at")
  private Instant respondedAt;
}
