package com.fa26se088.filmlink.dto.payment.response;

import com.fa26se088.filmlink.entity.payment.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import com.fa26se088.filmlink.entity.payment.enums.PaymentStatus;
import java.time.Instant;

/** Tóm tắt thông tin thanh toán của đơn dịch vụ trả kèm trong chi tiết đơn (UC25, UC30). */
public record OrderPaymentSummaryResponse(
    Long paymentId,
    String transactionCode,
    PaymentPurpose purpose,
    PaymentMethod method,
    PaymentStatus status,
    Long amountVnd,
    Long netPaidVnd,
    String paymentUrl,
    Instant expiresAt,
    Instant succeededAt) {}
