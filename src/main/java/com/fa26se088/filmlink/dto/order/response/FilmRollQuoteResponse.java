package com.fa26se088.filmlink.dto.order.response;

/**
 * Giá của một cuộn trong báo giá (Report 4 §3.5): {@code rollPriceVnd = unitPriceVnd +
 * scanOptionPriceVnd + pushPullSurchargeVnd}.
 *
 * @param rollSequence thứ tự cuộn trong đơn, bắt đầu từ 1
 */
public record FilmRollQuoteResponse(
    int rollSequence,
    Long servicePackageId,
    String packageName,
    Long servicePackageScanOptionId,
    String scanResolutionLabel,
    short pushPullStops,
    long unitPriceVnd,
    long scanOptionPriceVnd,
    long pushPullSurchargeVnd,
    long rollPriceVnd) {}
