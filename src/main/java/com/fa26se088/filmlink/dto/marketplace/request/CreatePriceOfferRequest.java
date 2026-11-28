package com.fa26se088.filmlink.dto.marketplace.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Đề xuất giá bán lần đầu của Film Lab cho món đồ ký gửi (UC47). */
public record CreatePriceOfferRequest(
    @NotNull @Min(1) @Max(500_000_000L) Long priceVnd,
    @NotBlank @Size(min = 20, max = 1000) String justification) {}
