package com.fa26se088.filmlink.entity.delivery.enums;

/**
 * Điểm bàn giao phim, lưu ở {@code film_handovers.handover_point} ({@code
 * ck_film_handovers_point}). Mỗi lần phim đổi tay là một dòng bằng chứng (BR-25).
 */
public enum HandoverPoint {
  /** PICKUP: lấy phim tại địa chỉ Photographer. */
  COLLECTED_FROM_PHOTOGRAPHER,
  /** Giao phim cho Delivery Partner. */
  HANDED_TO_PARTNER,
  /** UC23: lab nhận phim (kể cả Self Drop-off). */
  RECEIVED_AT_LAB,
  /** Lab giao phim trả lại cho Delivery Partner. */
  HANDED_BACK_TO_PARTNER,
  /** UC27: phim đã trả tới Photographer. */
  RETURNED_TO_PHOTOGRAPHER,
  /** UC27: Photographer tự tới lab lấy lại phim. */
  COLLECTED_AT_LAB
}
