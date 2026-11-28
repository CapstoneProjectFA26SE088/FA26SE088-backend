package com.fa26se088.filmlink.dto.marketplace.response;

import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferParty;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferPurpose;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferStatus;
import java.time.Instant;

/** Thông tin một lượt đề xuất giá bán ký gửi (UC47). */
public record PriceOfferResponse(
    Long id,
    Long consignmentItemId,
    ConsignmentItemStatus itemStatus,
    Long agreedPriceVnd,
    PriceOfferPurpose purpose,
    Short roundNumber,
    PriceOfferParty offeredByParty,
    Long offeredByAccountId,
    Long priceVnd,
    String justification,
    PriceOfferStatus status,
    Instant responseDueAt,
    Instant respondedAt,
    Instant createdAt) {}
