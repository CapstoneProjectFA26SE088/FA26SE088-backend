package com.fa26se088.filmlink.dto.lab.response;

import java.util.List;

/**
 * Một gói đang bán trên trang lab công khai (UC05, UC13), kèm tên master data và các mức scan (giá
 * tăng dần). Không có trạng thái, ngày tạo / sửa: trang công khai chỉ hiện gói {@code ON_SALE}.
 */
public record PublicServicePackageResponse(
    Long id,
    String packageName,
    CatalogRefResponse serviceType,
    CatalogRefResponse filmProcess,
    CatalogRefResponse filmFormat,
    Long priceVnd,
    Short turnaroundDays,
    boolean pushPullAvailable,
    Long pushPullSurchargePerStopVnd,
    List<ServicePackageScanOptionResponse> scanOptions) {}
