package com.fa26se088.filmlink.dto.marketplace.response;

import com.fa26se088.filmlink.entity.marketplace.enums.ConditionGrade;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemType;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentListingStatus;
import java.time.Instant;

/** Tóm tắt tin đăng ký gửi trên trang danh sách công khai (UC61). */
public record ConsignmentListingSummaryResponse(
    Long id,
    Long consignmentItemId,
    Long filmLabId,
    String labName,
    ConsignmentItemType itemType,
    ConditionGrade overallGrade,
    String title,
    Long priceVnd,
    boolean isPickupAtLab,
    boolean isDeliveryAvailable,
    ConsignmentListingStatus status,
    String thumbnailUrl,
    Instant publishedAt,
    Instant expiresAt) {}
