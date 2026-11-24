package com.fa26se088.filmlink.service.payment.event;

import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import java.time.Instant;

/**
 * Sự kiện thanh toán thành công (UC30, BR-09), contract SPEC-00 §4.5.
 *
 * <p>Publish trong transaction xử lý IPN. Listener ({@code order}, {@code marketplace}) dùng
 * {@code @Transactional(propagation = MANDATORY)} để payment và trạng thái đơn / giao dịch bán
 * commit cùng nhau.
 */
public record PaymentSucceededEvent(
    Long paymentId,
    PaymentPurpose purpose,
    Long serviceOrderId,
    Long consignmentSaleId,
    long amountVnd,
    Instant succeededAt) {}
