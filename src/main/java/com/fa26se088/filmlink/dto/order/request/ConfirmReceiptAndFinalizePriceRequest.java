package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request payload Film Lab xác nhận nhận phim và chốt giá thực tế (UC23, BR-18, BR-25, BR-26).
 */
public record ConfirmReceiptAndFinalizePriceRequest(
    @NotNull(message = "Số cuộn thực nhận không được để trống")
    @Min(value = 1, message = "Số cuộn thực nhận tối thiểu là 1")
    Integer receivedRollCount,

    @NotNull(message = "Tổng tiền chốt thực tế không được để trống")
    @Min(value = 0, message = "Tổng tiền chốt thực tế không thể âm")
    Long finalTotalVnd,

    List<String> evidencePhotoUrls,

    String note
) {}

