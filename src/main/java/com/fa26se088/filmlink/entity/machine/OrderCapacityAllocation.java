package com.fa26se088.filmlink.entity.machine;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Phân bổ công suất theo ngày của đơn hàng trong phòng lab (BR-59, BR-60, BR-61), bảng {@code
 * order_capacity_allocations}.
 */
@Getter
@Setter
@Entity
@Table(name = "order_capacity_allocations")
public class OrderCapacityAllocation extends BaseEntity {

  @Column(name = "service_order_id", nullable = false)
  private Long serviceOrderId;

  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  /** Ngày làm việc theo giờ Việt Nam (GMT+7). */
  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  @Column(name = "film_process_id", nullable = false)
  private Long filmProcessId;

  @Column(name = "film_format_id", nullable = false)
  private Long filmFormatId;

  @Enumerated(EnumType.STRING)
  @Column(name = "service_tier", nullable = false, length = 20)
  private ServiceTier serviceTier;

  @Column(name = "roll_count", nullable = false)
  private Short rollCount;

  /** Thời điểm lab xác nhận nhận phim, dùng để sắp thứ tự FIFO khi tái xếp lịch (BR-61). */
  @Column(name = "order_received_at", nullable = false)
  private Instant orderReceivedAt;
}
