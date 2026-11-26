package com.fa26se088.filmlink.dto.payment;

import java.time.Instant;
import java.util.List;

/**
 * Dữ liệu đơn vừa CLOSED mà {@code order} truyền xuống để ghi hoa hồng (contract SPEC-00 §4.6).
 *
 * @param paymentMethod {@code ONLINE} | {@code PAY_AT_LAB} | {@code COD}
 * @param serviceValueVnd subtotal + phụ phí Express, không gồm phí giao nhận
 */
public record ClosedOrderCommission(
    Long serviceOrderId,
    Long filmLabId,
    List<Long> servicePackageIds,
    String paymentMethod, // ONLINE | PAY_AT_LAB | COD
    long serviceValueVnd, // subtotal + Express surcharge, delivery excluded
    Instant closedAt) {}
