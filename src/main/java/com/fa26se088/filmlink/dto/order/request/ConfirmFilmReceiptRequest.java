package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * UC23: part {@code data} (JSON) của {@code POST /api/v1/lab/orders/{id}/receipt}. Ảnh bằng chứng
 * gửi ở part {@code photos}.
 *
 * @param rolls mỗi cuộn đã khai báo của đơn đúng một lần
 * @param note ghi chú bàn giao, tối đa 500 ký tự
 */
public record ConfirmFilmReceiptRequest(
    @NotEmpty(message = "Cần kết quả đếm của từng cuộn.") List<@Valid FilmRollReceiptRequest> rolls,
    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.") String note) {}
