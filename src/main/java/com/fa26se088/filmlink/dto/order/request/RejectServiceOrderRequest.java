package com.fa26se088.filmlink.dto.order.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UC22, BR-39: Body của {@code POST /api/v1/lab/orders/{id}/reject}.
 *
 * @param reason lý do Film Lab từ chối đơn, bắt buộc từ 10 đến 500 ký tự (lưu vào {@code
 *     service_orders.rejection_reason})
 */
@Schema(description = "Yêu cầu từ chối đơn dịch vụ của Film Lab")
public record RejectServiceOrderRequest(
    @NotBlank(message = "Cần nhập lý do từ chối đơn.")
        @Size(min = 10, max = 500, message = "Lý do từ chối đơn từ 10 đến 500 ký tự.")
        @Schema(
            description = "Lý do lab từ chối đơn",
            example = "Lab hiện đã quá tải mẻ tráng C-41 trong hôm nay.")
        String reason) {}
