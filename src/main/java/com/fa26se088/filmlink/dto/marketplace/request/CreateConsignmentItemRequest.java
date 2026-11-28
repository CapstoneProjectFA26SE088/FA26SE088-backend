package com.fa26se088.filmlink.dto.marketplace.request;

import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Thông tin thiết bị người bán khai báo khi đặt lịch ký gửi (UC45). */
public record CreateConsignmentItemRequest(
    @NotNull ConsignmentItemType itemType,
    @NotBlank @Size(min = 2, max = 150) String itemName,
    @NotBlank @Size(min = 20, max = 2000) String sellerDescription,
    @Min(1) @Max(500_000_000L) Long desiredPriceVnd) {}
