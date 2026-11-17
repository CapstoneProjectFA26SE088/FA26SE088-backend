package com.fa26se088.filmlink.dto.lab.response;

/**
 * Thông tin lab mà domain order cần khi báo giá / tạo Service Order (UC20). Chỉ dùng giữa các
 * service, không trả ra controller.
 *
 * @param ownerAccountId chủ lab, người nhận thông báo đơn mới
 * @param acceptingOrders cờ {@code is_accepting_orders} (UC12)
 */
public record OrderingFilmLabResponse(
    Long id, Long ownerAccountId, String labName, boolean acceptingOrders) {}
