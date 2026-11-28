package com.fa26se088.filmlink.dto.marketplace.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Yêu cầu xuất bản tin đăng bán thiết bị ký gửi công khai (UC48). */
public record PublishConsignmentListingRequest(
    @NotBlank @Size(min = 10, max = 150) String title,
    @NotBlank @Size(min = 50, max = 3000) String description,
    @NotNull Boolean isPickupAtLab,
    @NotNull Boolean isDeliveryAvailable) {}
