package com.fa26se088.filmlink.dto.order.response;

import java.time.Instant;
import java.util.List;

/**
 * UC20: báo giá tạm tính, không ghi DB. {@code estimatedTotalVnd = subtotalVnd +
 * expressSurchargeVnd + deliveryFeeVnd}; ở giai đoạn này phụ phí Express và phí giao phim luôn 0.
 *
 * @param expectedReturnAt BR-60: ước lượng = thời điểm báo giá + số ngày trả lớn nhất trong các gói
 */
public record ServiceOrderQuoteResponse(
    List<FilmRollQuoteResponse> rolls,
    long subtotalVnd,
    long expressSurchargeVnd,
    long deliveryFeeVnd,
    long estimatedTotalVnd,
    Instant expectedReturnAt) {}
