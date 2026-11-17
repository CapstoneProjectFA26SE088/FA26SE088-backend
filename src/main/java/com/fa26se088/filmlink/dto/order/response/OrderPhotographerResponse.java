package com.fa26se088.filmlink.dto.order.response;

/** Photographer của đơn, phần chủ lab được xem (UC22): chỉ tên và số điện thoại. */
public record OrderPhotographerResponse(Long id, String fullName, String phoneNumber) {}
