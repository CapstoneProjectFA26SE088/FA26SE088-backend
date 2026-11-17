package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import java.time.Instant;
import java.util.List;

/**
 * Service Order trả cho Photographer (UC20), kèm các cuộn.
 *
 * @param payment link thanh toán VNPay cho đơn ONLINE. Hiện luôn {@code null}: contract {@code
 *     PaymentService.createOrderPayment} (SPEC-00 §4.5) chưa có trên {@code dev}
 */
public record ServiceOrderResponse(
    Long id,
    String orderCode,
    ServiceOrderStatus status,
    Long filmLabId,
    ServiceTier serviceTier,
    FilmHandoverMethod filmHandoverMethod,
    PaymentMethod paymentMethod,
    String specialRequest,
    String customerAddressLine,
    String customerDistrict,
    String customerProvince,
    String customerPhone,
    Instant pickupWindowStartAt,
    Instant pickupWindowEndAt,
    Long estimatedTotalVnd,
    Instant expectedReturnAt,
    Instant labResponseDueAt,
    List<FilmRollResponse> rolls,
    Object payment,
    Instant createdAt) {}
