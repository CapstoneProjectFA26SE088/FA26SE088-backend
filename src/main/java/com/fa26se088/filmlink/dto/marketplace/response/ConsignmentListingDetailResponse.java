package com.fa26se088.filmlink.dto.marketplace.response;

import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemType;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentListingStatus;
import java.time.Instant;

/** Chi tiết tin đăng ký gửi kèm biên bản kiểm định công khai (UC48, UC61). */
public record ConsignmentListingDetailResponse(
    Long id,
    Long consignmentItemId,
    ConsignmentItemStatus itemStatus,
    Long filmLabId,
    String labName,
    String labAddress,
    ConsignmentItemType itemType,
    String itemName,
    String title,
    String description,
    Long priceVnd,
    boolean isPickupAtLab,
    boolean isDeliveryAvailable,
    ConsignmentListingStatus status,
    Instant consignmentStartedAt,
    Instant publishedAt,
    Instant expiresAt,
    ConditionReportResponse conditionReport) {}
