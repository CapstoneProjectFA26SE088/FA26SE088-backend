package com.fa26se088.filmlink.dto.order.response;

import com.fa26se088.filmlink.entity.order.enums.FilmRollStatus;
import java.util.List;

/**
 * Một cuộn của Service Order, kèm giá đã chụp lúc tạo đơn. Master data trả dạng id.
 *
 * <p>{@code damageNote} và {@code damagePhotos} có giá trị khi cuộn ở trạng thái {@code DAMAGED}
 * (BR-26). Ba trường scan cuối (UC26, UC28) chỉ có giá trị theo người xem, còn lại {@code null}.
 *
 * @param damageNote mô tả tình trạng cuộn hỏng (BR-26); {@code null} nếu không hỏng
 * @param damagePhotos danh sách ảnh bằng chứng cuộn hỏng (link ký 15 phút); rỗng nếu không hỏng
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
    String damageNote,
    List<FilmRollDamagePhotoResponse> damagePhotos,
    Long archiveRollId,
    Long previewScanCount,
    String fullResolutionLink) {}
