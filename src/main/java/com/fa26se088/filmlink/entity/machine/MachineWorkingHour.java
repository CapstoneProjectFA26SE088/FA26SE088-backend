package com.fa26se088.filmlink.entity.machine;

import com.fa26se088.filmlink.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Giờ hoạt động của một máy theo ngày trong tuần (UC68, bảng {@code machine_working_hours}).
 *
 * <p>ISO-8601: {@code dayOfWeek} từ 1 (Thứ Hai) đến 7 (Chủ nhật).
 */
@Getter
@Setter
@Entity
@Table(name = "machine_working_hours")
public class MachineWorkingHour extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "machine_id", nullable = false)
  private Machine machine;

  @Column(name = "day_of_week", nullable = false)
  private Short dayOfWeek;

  @Column(name = "is_closed", nullable = false)
  private boolean closed = false;

  @Column(name = "opens_at")
  private LocalTime opensAt;

  @Column(name = "closes_at")
  private LocalTime closesAt;
}
