package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.entity.order.enums.ProcessingStage;
import java.util.List;

/**
 * UC24, BR-07: kết quả sau khi Admin lùi công đoạn xử lý của một Service Order, kèm toàn bộ dòng
 * thời gian công đoạn mới nhất.
 */
public record AdminOrderStageRevertResponse(
    Long orderId,
    String orderCode,
    ProcessingStage previousStage,
    ProcessingStage currentStage,
    List<ProcessingStageRecordResponse> stages) {}
