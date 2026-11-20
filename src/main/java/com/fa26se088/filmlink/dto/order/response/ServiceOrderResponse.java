package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.OrderPaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.entity.order.enums.ServiceTier;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO cho ServiceOrder.
 */
public record ServiceOrderResponse(
    Long id,
    String orderCode,
    UUID idempotencyKey,
    Long photographerAccountId,
    Long filmLabId,
    ServiceOrderStatus status,
    String statusBeforeSuspension,
    ServiceTier serviceTier,
    FilmHandoverMethod filmHandoverMethod,
    OrderPaymentMethod paymentMethod,
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
    Instant machineAssignmentConfirmedAt,
    Instant labResponseDueAt,
    Instant acceptedAt,
    String rejectionReason,
    Instant deliveredAt,
    Instant closedAt,
    boolean keepNegativesAtLab,
    Instant cancelledAt,
    Long cancelledByAccountId,
    String cancellationReason,
    Long cancellationFeeVnd,
    int version,
    Instant createdAt,
    Instant updatedAt
) {}

