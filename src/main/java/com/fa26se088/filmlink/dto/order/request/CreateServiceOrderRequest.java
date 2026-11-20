package com.fa26se088.filmlink.dto.order.request;

import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.OrderPaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request payload tạo đơn dịch vụ tráng / scan (ServiceOrder).
 */
public record CreateServiceOrderRequest(
    @NotNull(message = "Idempotency key không được để trống")
    UUID idempotencyKey,

    @NotNull(message = "filmLabId không được để trống")
    Long filmLabId,

    @NotNull(message = "Gói dịch vụ không được để trống")
    ServiceTier serviceTier,

    @NotNull(message = "Phương thức gửi phim không được để trống")
    FilmHandoverMethod filmHandoverMethod,

    @NotNull(message = "Phương thức thanh toán không được để trống")
    OrderPaymentMethod paymentMethod,

    @Size(max = 500, message = "Yêu cầu đặc biệt tối đa 500 ký tự")
    String specialRequest,

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    String customerAddressLine,

    @Size(max = 100, message = "Quận/huyện tối đa 100 ký tự")
    String customerDistrict,

    @Size(max = 100, message = "Tỉnh/thành phố tối đa 100 ký tự")
    String customerProvince,

    @Size(max = 15, message = "Số điện thoại tối đa 15 ký tự")
    String customerPhone,

    Instant pickupWindowStartAt,
    Instant pickupWindowEndAt,

    boolean keepNegativesAtLab,

    @NotEmpty(message = "Đơn hàng phải chứa ít nhất một cuộn phim")
    List<@Valid FilmRollItemRequest> items
) {}

