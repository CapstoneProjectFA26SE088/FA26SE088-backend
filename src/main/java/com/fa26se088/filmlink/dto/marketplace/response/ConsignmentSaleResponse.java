package com.fa26se088.filmlink.dto.marketplace.response;

import com.fa26se088.filmlink.dto.payment.response.PaymentLink;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentHandoverMethod;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentListingStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentPaymentMethod;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentSaleStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Giao dịch mua thiết bị ký gửi kèm phân chia doanh thu và link thanh toán (UC62–UC64). */
public record ConsignmentSaleResponse(
    Long id,
    String saleCode,
    Long consignmentListingId,
    ConsignmentListingStatus listingStatus,
    Long consignmentItemId,
    ConsignmentItemStatus itemStatus,
    Long filmLabId,
    Long buyerAccountId,
    ConsignmentPaymentMethod paymentMethod,
    ConsignmentHandoverMethod handoverMethod,
    ConsignmentSaleStatus status,
    Long itemPriceVnd,
    Long shippingFeeVnd,
    Long totalVnd,
    BigDecimal consignmentFeeRate,
    Long consignmentFeeVnd,
    BigDecimal commissionRate,
    Long commissionVnd,
    Long sellerPayoutVnd,
    Instant paymentDueAt,
    Instant handedOverAt,
    Instant returnWindowEndsAt,
    PaymentLink paymentLink,
    ConsignmentHandoverResponse handover,
    Instant createdAt) {}
