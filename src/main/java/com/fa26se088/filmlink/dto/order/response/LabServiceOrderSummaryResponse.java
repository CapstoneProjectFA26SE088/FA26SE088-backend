package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import java.time.Instant;

/** Một dòng trong danh sách đơn của lab (UC22). */
public record LabServiceOrderSummaryResponse(
    Long id,
    String orderCode,
    ServiceOrderStatus status,
    ServiceTier serviceTier,
    FilmHandoverMethod filmHandoverMethod,
    PaymentMethod paymentMethod,
    long rollCount,
    Long estimatedTotalVnd,
    Instant labResponseDueAt,
    Instant expectedReturnAt,
    Instant createdAt) {}
