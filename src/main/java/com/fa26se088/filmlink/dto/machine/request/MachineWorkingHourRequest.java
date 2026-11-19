package com.fa26se088.filmlink.dto.machine.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * Cấu hình giờ hoạt động của máy theo một ngày trong tuần (UC68).
 *
 * @param dayOfWeek thứ trong tuần theo ISO-8601 (1 = Thứ Hai … 7 = Chủ nhật)
 * @param closed ngày nghỉ của máy ({@code true} thì không truyền {@code opensAt}/{@code closesAt})
 * @param opensAt giờ bắt đầu vận hành (bắt buộc khi {@code closed = false})
 * @param closesAt giờ kết thúc vận hành (bắt buộc khi {@code closed = false}, phải sau {@code
 *     opensAt})
 */
public record MachineWorkingHourRequest(
    @NotNull(message = "Thứ trong tuần không được để trống")
        @Min(value = 1, message = "Thứ trong tuần phải từ 1 đến 7")
        @Max(value = 7, message = "Thứ trong tuần phải từ 1 đến 7")
        Short dayOfWeek,
    @NotNull(message = "Trạng thái ngày nghỉ không được để trống") Boolean closed,
    @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "08:00") LocalTime opensAt,
    @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "16:00")
        LocalTime closesAt) {}
