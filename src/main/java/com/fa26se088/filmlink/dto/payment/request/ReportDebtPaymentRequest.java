package com.fa26se088.filmlink.dto.payment.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request Film Lab báo cáo đã nộp tiền nợ hoa hồng đính kèm ảnh biên lai (UC33, Q6).
 */
public record ReportDebtPaymentRequest(
    @NotBlank(message = "URL hoặc key ảnh biên lai không được để trống")
    String receiptBlobKey,

    String note
) {}

