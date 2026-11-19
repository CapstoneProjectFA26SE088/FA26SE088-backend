package com.fa26se088.filmlink.entity.machine;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.machine.enums.MachineIncidentCause;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Sự cố máy tráng hoặc máy scan do Film Lab Owner khai báo (UC70, bảng {@code machine_incidents}).
 */
@Getter
@Setter
@Entity
@Table(name = "machine_incidents")
public class MachineIncident extends BaseEntity {

  @Column(name = "machine_id", nullable = false)
  private Long machineId;

  @Enumerated(EnumType.STRING)
  @Column(name = "cause", nullable = false, length = 30)
  private MachineIncidentCause cause;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "unavailable_from", nullable = false)
  private Instant unavailableFrom;

  @Column(name = "expected_available_at")
  private Instant expectedAvailableAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "machine_status_after", nullable = false, length = 20)
  private MachineStatus machineStatusAfter;

  @Column(name = "reported_by_account_id", nullable = false)
  private Long reportedByAccountId;

  @Column(name = "resolved_at")
  private Instant resolvedAt;
}
