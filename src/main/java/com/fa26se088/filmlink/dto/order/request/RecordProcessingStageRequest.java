package com.fa26se088.filmlink.dto.order.request;

import com.fa26se088.filmlink.entity.order.enums.ProcessingStage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC24, BR-07: Film Lab Owner ghi công đoạn xử lý kế tiếp của đơn.
 *
 * @param stage công đoạn mới; phải đúng công đoạn kế tiếp của công đoạn mới nhất (kiểm ở service)
 * @param note ghi chú tuỳ chọn, tối đa 300 ký tự ({@code processing_stage_records.note}); hiển thị
 *     cho Photographer trên dòng thời gian
 */
public record RecordProcessingStageRequest(
    @NotNull ProcessingStage stage, @Size(max = 300) String note) {}
