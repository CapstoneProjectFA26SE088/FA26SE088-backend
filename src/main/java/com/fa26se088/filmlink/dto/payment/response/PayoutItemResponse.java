package com.fa26se088.filmlink.dto.payment.response;

/** Một dòng đơn hàng hoặc giao dịch ký gửi thuộc đợt chi trả {@code Payout} (UC35, UC64). */
public record PayoutItemResponse(
    Long id, Long serviceOrderId, Long consignmentSaleId, Long amountVnd) {}
