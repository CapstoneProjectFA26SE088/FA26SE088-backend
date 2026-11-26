package com.fa26se088.filmlink.dto.machine.response;

import com.fa26se088.filmlink.entity.machine.enums.ScheduleChangeStep;
import java.time.Instant;

/**
 * DTO tóm tắt một thay đổi ngày hẹn trả đơn hàng phát sinh sau sự cố máy (UC71, FA26-77).
 *
 * @param id ID bản ghi thay đổi lịch
 * @param serviceOrderId ID đơn hàng dịch vụ bị ảnh hưởng
 * @param stepApplied bước xử lý đã áp dụng ({@code MOVED_MACHINE} hoặc {@code RESCHEDULED})
 * @param oldPromisedAt ngày hẹn trả cũ trước sự cố
 * @param newPromisedAt ngày hẹn trả mới sau khi tái xếp lịch
 * @param requiresResponse true nếu ngày mới vượt quá hạn cam kết gói dịch vụ và cần khách phản hồi
 * @param responseDueAt hạn chót khách hàng phản hồi nếu {@code requiresResponse = true}
 */
public record ScheduleChangeSummaryResponse(
    Long id,
    Long serviceOrderId,
    ScheduleChangeStep stepApplied,
    Instant oldPromisedAt,
    Instant newPromisedAt,
    boolean requiresResponse,
    Instant responseDueAt) {}
