package com.fa26se088.filmlink.dto.lab.request;

import com.fa26se088.filmlink.entity.lab.enums.ServicePackageStatus;
import jakarta.validation.constraints.NotNull;

/** UC13: mở bán ({@code ON_SALE}) hoặc ngừng bán ({@code WITHDRAWN}) một gói. */
public record UpdateServicePackageStatusRequest(@NotNull ServicePackageStatus status) {}
