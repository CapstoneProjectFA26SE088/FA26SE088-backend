package com.fa26se088.filmlink.dto.machine.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

/**
 * Giờ hoạt động và công suất tính toán theo ngày của một máy (UC68).
 *
 * @param dayOfWeek thứ trong tuần theo ISO-8601 (1 = Thứ Hai … 7 = Chủ nhật)
 * @param closed ngày nghỉ của máy
 * @param opensAt giờ bắt đầu vận hành
 * @param closesAt giờ kết thúc vận hành
 * @param dailyCapacityRolls công suất tính toán trong ngày (quy đổi ra số cuộn 36 kiểu)
 */
public record MachineWorkingHourResponse(
    Short dayOfWeek,
    boolean closed,
    @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "08:00") LocalTime opensAt,
    @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "16:00") LocalTime closesAt,
    int dailyCapacityRolls) {}
