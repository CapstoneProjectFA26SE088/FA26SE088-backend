package com.fa26se088.filmlink.dto.order.request;

import com.fa26se088.filmlink.entity.order.enums.ProcessingStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC24, BR-07: Admin lùi công đoạn xử lý của đơn về một công đoạn trước đó (append-only, {@code
 * is_admin_reversion = true}).
 *
 * @param stage công đoạn đích muốn lùi về; phải đứng trước công đoạn hiện tại
 * @param note lý do lùi công đoạn, bắt buộc, tối đa 300 ký tự ({@code processing_stage_records})
 */
public record RevertProcessingStageRequest(
    @NotNull ProcessingStage stage, @NotBlank @Size(max = 300) String note) {}
