package com.fa26se088.filmlink.dto.payment.response;

import com.fa26se088.filmlink.entity.payment.enums.PaymentStatus;
import lombok.Builder;

/**
 * Kết quả hiển thị cho người dùng khi redirect từ cổng thanh toán về trình duyệt.
 */
@Builder
public record PaymentReturnResponse(
    String transactionCode,
    boolean success,
    String responseCode,
    String message,
    Long amountVnd,
    Long serviceOrderId,
    Long consignmentSaleId,
    PaymentStatus paymentStatus) {}

