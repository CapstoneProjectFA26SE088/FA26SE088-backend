package com.fa26se088.filmlink.repository.order;

import com.fa26se088.filmlink.entity.order.ProcessingStageRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** BR-07: timeline công đoạn của đơn ({@code ix_processing_stage_records_order}). */
public interface ProcessingStageRecordRepository
    extends JpaRepository<ProcessingStageRecord, Long> {

  /** SCR-19: các công đoạn của đơn theo thời gian; {@code id} để ổn định khi trùng thời điểm. */
  List<ProcessingStageRecord> findByServiceOrderIdOrderByRecordedAtAscIdAsc(Long serviceOrderId);
}
