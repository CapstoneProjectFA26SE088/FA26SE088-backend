package com.fa26se088.filmlink.service.payment.event;

import com.fa26se088.filmlink.entity.payment.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import java.time.Instant;

/**
 * Sự kiện thanh toán thành công (UC30, BR-09, Q9).
 *
 * <p>Theo thiết kế mục 5 của backend doc và Q9:
 * <ul>
 *   <li>Phát ra từ {@code payment} khi khoản thanh toán thành công.
 *   <li>Domain {@code order} lắng nghe và xử lý đồng bộ trong cùng transaction để cập nhật trạng thái đơn
 *       (từ {@code AWAITING_PAYMENT} sang {@code AWAITING_LAB_CONFIRMATION}).
 *   <li>Domain {@code notification} lắng nghe {@code AFTER_COMMIT} để gửi email / thông báo cho khách.
 * </ul>
 */
public record PaymentSucceededEvent(
    Long paymentId,
    String transactionCode,
    Long serviceOrderId,
    Long amountVnd,
    PaymentPurpose purpose,
    PaymentMethod method,
    Instant succeededAt) {}

