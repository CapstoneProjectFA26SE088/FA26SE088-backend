package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.entity.order.enums.FilmRollStatus;

/**
 * Một cuộn của Service Order, kèm giá đã chụp lúc tạo đơn. Master data trả dạng id.
 *
 * <p>Ba trường scan cuối (UC26, UC28) chỉ có giá trị theo người xem, còn lại {@code null}.
 *
 * @param archiveRollId Photographer: id cuộn trong Digital Film Archive, khi đơn đã giao scan
 * @param previewScanCount Film Lab Owner: số ảnh xem trước hiện hành của cuộn
 * @param fullResolutionLink Film Lab Owner: link file gốc đã kiểm (nếu đã gửi)
 */
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
    FilmRollStatus status,
    Long archiveRollId,
    Long previewScanCount,
    String fullResolutionLink) {}
