package com.fa26se088.filmlink.dto.lab.response;

import com.fa26se088.filmlink.entity.lab.enums.ServicePackageStatus;
import java.time.Instant;
import java.util.List;

/**
 * Một Service Package trong bảng giá của lab (UC13), kèm các mức scan (giá tăng dần). Master data
 * trả dạng id; FE tra tên qua API danh mục công khai.
 */
public record ServicePackageResponse(
    Long id,
    String packageName,
    Long serviceTypeId,
    Long filmProcessId,
    Long filmFormatId,
    Long priceVnd,
    Short turnaroundDays,
    boolean pushPullAvailable,
    Long pushPullSurchargePerStopVnd,
    ServicePackageStatus status,
    List<ServicePackageScanOptionResponse> scanOptions,
    Instant createdAt,
    Instant updatedAt) {}
