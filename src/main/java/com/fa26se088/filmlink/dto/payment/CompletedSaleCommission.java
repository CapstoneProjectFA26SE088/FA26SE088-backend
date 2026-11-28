package com.fa26se088.filmlink.dto.payment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lệnh ghi nhận hoa hồng nền tảng khi giao dịch mua ký gửi hoàn tất bàn giao (UC64, BR-54, BR-55).
 */
public record CompletedSaleCommission(
    Long consignmentSaleId,
    Long filmLabId,
    long itemPriceVnd,
    BigDecimal commissionRate,
    long commissionVnd,
    Instant returnWindowEndsAt) {}
