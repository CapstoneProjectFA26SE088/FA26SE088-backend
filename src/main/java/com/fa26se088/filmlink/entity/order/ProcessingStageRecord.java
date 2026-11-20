package com.fa26se088.filmlink.entity.order;

import com.fa26se088.filmlink.entity.order.enums.ProcessingStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Processing Stage Record (BR-07, UC24), bảng {@code processing_stage_records}: mỗi lần đơn sang
 * một công đoạn là một dòng mới (timeline SCR-19).
 *
 * <p>Append-only: bảng không có {@code created_at}/{@code updated_at} (thời điểm là {@code
 * recorded_at}) nên <b>không</b> kế thừa {@code BaseEntity}. {@link Immutable}: Hibernate không
 * phát UPDATE; DB có trigger {@code processing_stage_records_append_only} chặn UPDATE / DELETE.
 * Admin lùi công đoạn cũng là một dòng mới với {@code is_admin_reversion = true}.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "processing_stage_records")
public class ProcessingStageRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "service_order_id", nullable = false, updatable = false)
  private Long serviceOrderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "stage", nullable = false, length = 20, updatable = false)
  private ProcessingStage stage;

  @Column(name = "note", length = 300, updatable = false)
  private String note;

  /** UC24: chỉ Admin được lùi công đoạn; lùi là một dòng mới, không sửa dòng cũ. */
  @Column(name = "is_admin_reversion", nullable = false, updatable = false)
  private boolean adminReversion = false;

  @Column(name = "recorded_by_account_id", nullable = false, updatable = false)
  private Long recordedByAccountId;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;
}
