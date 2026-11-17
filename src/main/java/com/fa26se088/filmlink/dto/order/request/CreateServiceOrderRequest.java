package com.fa26se088.filmlink.dto.order.request;

import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UC20: body chung cho báo giá ({@code POST /api/v1/orders/quote}) và tạo đơn ({@code POST
 * /api/v1/orders}). Giới hạn độ dài khớp cột của {@code service_orders}.
 *
 * <p>Service kiểm thêm: {@code idempotencyKey} bắt buộc khi tạo đơn (báo giá thì bỏ qua); PICKUP
 * bắt buộc địa chỉ, quận, tỉnh, số điện thoại và khung giờ lấy {@code start < end}; các phương án
 * chưa hỗ trợ (EXPRESS, PARCEL_SHIPPING, COD) bị từ chối 422.
 *
 * @param idempotencyKey NFR-REL-02: gửi lại cùng khoá thì trả đơn đã tạo, không tạo đơn mới
 * @param rolls 1–20 cuộn (UC20)
 */
public record CreateServiceOrderRequest(
    UUID idempotencyKey,
    @NotNull Long filmLabId,
    @NotNull ServiceTier serviceTier,
    @NotNull FilmHandoverMethod filmHandoverMethod,
    @NotNull PaymentMethod paymentMethod,
    @Size(max = 500) String specialRequest,
    @Size(max = 255) String customerAddressLine,
    @Size(max = 100) String customerDistrict,
    @Size(max = 100) String customerProvince,
    @Size(min = 9, max = 15) String customerPhone,
    Instant pickupWindowStartAt,
    Instant pickupWindowEndAt,
    @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid FilmRollRequest> rolls) {}
