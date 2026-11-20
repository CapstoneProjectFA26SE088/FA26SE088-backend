package com.fa26se088.filmlink.dto.order.request;

import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import jakarta.validation.constraints.Min;

/**
 * UC25: tham số xem danh sách đơn của Photographer, nhận qua query string.
 *
 * @param status lọc theo một trạng thái; {@code null} = mọi trạng thái
 * @param page số trang, bắt đầu từ 0 (mặc định 0)
 * @param size số dòng mỗi trang, mặc định 20; lớn hơn 100 thì hạ về 100 (CR-01)
 */
public record SearchServiceOrderRequest(
    ServiceOrderStatus status, @Min(0) Integer page, @Min(1) Integer size) {}
