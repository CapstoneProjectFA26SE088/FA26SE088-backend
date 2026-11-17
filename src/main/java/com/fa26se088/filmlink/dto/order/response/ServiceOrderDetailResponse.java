package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.dto.lab.response.FilmLabBriefResponse;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import java.time.Instant;
import java.util.List;

/**
 * Chi tiết Service Order trả cho Photographer (UC25): thông tin đơn, lab (tên, địa chỉ), các cuộn
 * (giá đã chụp, trạng thái), dòng thời gian công đoạn (BR-07, cũ nhất trước), các lần bàn giao
 * phim (BR-25).
 *
 * <p>DTO riêng cho Photographer, <b>không</b> dùng lại {@link LabServiceOrderResponse}: không có
 * dữ liệu nội bộ của lab (người ghi công đoạn, người thực hiện bàn giao, {@code labResponseDueAt},
 * {@code machineAssignmentConfirmedAt}, thông tin chủ lab).
 *
 * @param lab {@code null} nếu không đọc được lab (không xảy ra với dữ liệu hợp lệ)
 * @param payment thông tin thanh toán; hiện luôn {@code null} (chờ contract thanh toán, FA26-157)
 */
public record ServiceOrderDetailResponse(
    Long id,
    String orderCode,
    ServiceOrderStatus status,
    FilmLabBriefResponse lab,
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
    Instant promisedReturnAt,
    Instant acceptedAt,
    Instant deliveredAt,
    Instant closedAt,
    List<FilmRollResponse> rolls,
    List<ProcessingStageRecordResponse> stages,
    List<OrderFilmHandoverResponse> handovers,
    Object payment,
    Instant createdAt) {}
