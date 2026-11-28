package com.fa26se088.filmlink.dto.marketplace.response;

import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentClosingReason;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemType;
import java.time.Instant;

/** Thông tin thiết bị ký gửi gắn với lịch hẹn. */
public record ConsignmentItemResponse(
    Long id,
    Long bookingId,
    ConsignmentItemType itemType,
    String itemName,
    String sellerDescription,
    Long desiredPriceVnd,
    ConsignmentItemStatus status,
    ConsignmentClosingReason closingReason,
    Long agreedPriceVnd,
    Instant priceAgreedAt,
    Instant collectionDueAt,
    Instant createdAt) {}
