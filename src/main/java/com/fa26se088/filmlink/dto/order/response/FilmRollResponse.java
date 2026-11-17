package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.entity.order.enums.FilmRollStatus;

/** Một cuộn của Service Order, kèm giá đã chụp lúc tạo đơn. Master data trả dạng id. */
public record FilmRollResponse(
    Long id,
    Short rollSequence,
    Long filmStockId,
    String filmStockNameText,
    Long filmFormatId,
    Long filmProcessId,
    Long servicePackageId,
    Long servicePackageScanOptionId,
    Short exposureCount,
    Short shootingIso,
    Short pushPullStops,
    Long unitPriceVnd,
    Long scanOptionPriceVnd,
    Long pushPullSurchargeVnd,
    Long expressSurchargeVnd,
    FilmRollStatus status) {}
