package com.fa26se088.filmlink.dto.lab.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC13: một mức độ phân giải scan của gói. Giá {@code ≥ 1} khớp {@code
 * ck_service_package_scan_options_price (price_vnd > 0)}; nhãn không trùng trong gói (service kiểm).
 */
public record ServicePackageScanOptionRequest(
    @NotBlank @Size(max = 50) String resolutionLabel, @NotNull @Min(1) Long priceVnd) {}
