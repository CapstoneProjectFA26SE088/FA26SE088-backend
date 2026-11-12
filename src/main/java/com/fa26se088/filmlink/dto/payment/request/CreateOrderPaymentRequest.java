package com.fa26se088.filmlink.dto.payment.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Yêu cầu tạo phiên thanh toán trực tuyến cho đơn hàng (UC30).
 */
@Builder
public record CreateOrderPaymentRequest(
    @NotNull(message = "Mã đơn hàng không được để trống")
    Long serviceOrderId,

    /** Mã ngân hàng (ví dụ: NCB, VCB, MBAPP...) nếu muốn chuyển thẳng; để trống để người dùng chọn. */
    String bankCode,

    /** Ngôn ngữ ("vn" hoặc "en"). */
    String locale) {}

