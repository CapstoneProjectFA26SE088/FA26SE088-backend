package com.fa26se088.filmlink.dto.order.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC72: Photographer trả lời thay đổi lịch do sự cố máy.
 *
 * @param choice {@code WAIT} | {@code CANCEL} | {@code TRANSFER} (giá trị của {@code
 *     schedule_changes.customer_choice}); giai đoạn này chỉ {@code WAIT} được xử lý
 * @param note ghi chú, tối đa 300 ký tự ({@code schedule_changes.customer_note})
 */
public record AnswerScheduleChangeRequest(
    @NotNull
        @Pattern(regexp = "WAIT|CANCEL|TRANSFER", message = "phải là WAIT, CANCEL hoặc TRANSFER")
        String choice,
    @Size(max = 300) String note) {}
