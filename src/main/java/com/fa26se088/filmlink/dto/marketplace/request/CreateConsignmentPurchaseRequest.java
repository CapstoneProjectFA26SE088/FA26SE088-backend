package com.fa26se088.filmlink.dto.marketplace.request;

import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentHandoverMethod;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentPaymentMethod;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Yêu cầu tạo giao dịch mua thiết bị ký gửi (UC62). */
public record CreateConsignmentPurchaseRequest(
    @NotNull UUID idempotencyKey,
    @NotNull ConsignmentPaymentMethod paymentMethod,
    @NotNull ConsignmentHandoverMethod handoverMethod) {}
