package com.fa26se088.filmlink.dto.machine.response;

import com.fa26se088.filmlink.entity.machine.enums.MachineIncidentCause;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import java.time.Instant;
import java.util.List;

/**
 * DTO phản hồi kết quả khai báo sự cố máy và tự động tái xếp lịch các đơn bị ảnh hưởng (UC70,
 * UC71).
 *
 * @param id ID bản ghi sự cố
 * @param machineId ID máy gặp sự cố
 * @param machineCode mã máy trong lab
 * @param cause nguyên nhân sự cố
 * @param description mô tả chi tiết sự cố
 * @param unavailableFrom thời điểm bắt đầu gián đoạn
 * @param expectedAvailableAt thời điểm dự kiến hoạt động lại
 * @param machineStatusAfter trạng thái của máy sau khi báo sự cố ({@code MAINTENANCE} hoặc {@code
 *     DOWN})
 * @param reportedByAccountId ID tài khoản đã khai báo sự cố
 * @param resolvedAt thời điểm sự cố được giải quyết (null nếu chưa khắc phục)
 * @param affectedOrderCount số lượng đơn đang xử lý có phân bổ trong khoảng thời gian bị ảnh hưởng
 * @param scheduleChanges danh sách các đơn hàng bị thay đổi ngày hẹn trả sau tái xếp lịch
 * @param createdAt thời điểm tạo bản ghi sự cố
 */
public record MachineIncidentResponse(
    Long id,
    Long machineId,
    String machineCode,
    MachineIncidentCause cause,
    String description,
    Instant unavailableFrom,
    Instant expectedAvailableAt,
    MachineStatus machineStatusAfter,
    Long reportedByAccountId,
    Instant resolvedAt,
    int affectedOrderCount,
    List<ScheduleChangeSummaryResponse> scheduleChanges,
    Instant createdAt) {}
