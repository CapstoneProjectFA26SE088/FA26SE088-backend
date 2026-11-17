package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverResponse;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import java.time.Instant;
import java.util.List;

/**
 * Chi tiết Service Order trả cho Film Lab Owner (UC22, UC23), kèm các cuộn (giá đã chụp, trạng
 * thái), Photographer và các lần bàn giao phim (BR-25, link ảnh có chữ ký 15 phút). Địa chỉ / khung
 * giờ lấy phim chỉ có giá trị khi {@code filmHandoverMethod = PICKUP}. {@code finalTotalVnd} và
 * {@code priceLockedAt} có giá trị sau khi lab xác nhận nhận phim (UC23).
 *
 * <p>Chưa có shipment, timeline công đoạn, thanh toán (task sau).
 */
public record LabServiceOrderResponse(
    Long id,
    String orderCode,
    ServiceOrderStatus status,
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
    Long finalTotalVnd,
    Instant priceLockedAt,
    Instant expectedReturnAt,
    Instant labResponseDueAt,
    Instant acceptedAt,
    OrderPhotographerResponse photographer,
    List<FilmRollResponse> rolls,
    List<FilmHandoverResponse> handovers,
    Instant createdAt) {}
