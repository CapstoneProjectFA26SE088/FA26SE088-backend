package com.fa26se088.filmlink.entity.order.enums;

/**
 * Trạng thái của một cuộn phim, lưu ở {@code film_rolls.status} ({@code ck_film_rolls_status}).
 * Photographer khai báo ({@code DECLARED}) → lab đếm khi nhận phim (UC23) → {@code RECEIVED} /
 * {@code NOT_RECEIVED}; hỏng khi xử lý thì {@code DAMAGED} (BR-26, miễn phí).
 */
public enum FilmRollStatus {
  DECLARED,
  RECEIVED,
  NOT_RECEIVED,
  DAMAGED
}
