package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.Min;

/**
 * UC28: tham số xem Digital Film Archive của Photographer, nhận qua query string.
 *
 * @param page số trang, bắt đầu từ 0 (mặc định 0)
 * @param size số dòng mỗi trang, mặc định 20; lớn hơn 100 thì hạ về 100 (CR-01)
 */
public record SearchFilmArchiveRequest(@Min(0) Integer page, @Min(1) Integer size) {}
