package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UC24, BR-26: Film Lab Owner ghi nhận một cuộn phim bị hỏng (cháy sáng, loá toàn bộ, không lên
 * hình) kèm ghi chú lý do.
 *
 * @param damageNote mô tả tình trạng cuộn hỏng, bắt buộc, tối đa 500 ký tự ({@code
 *     ck_film_rolls_damage})
 */
public record RecordFilmRollDamageRequest(@NotBlank @Size(max = 500) String damageNote) {}
