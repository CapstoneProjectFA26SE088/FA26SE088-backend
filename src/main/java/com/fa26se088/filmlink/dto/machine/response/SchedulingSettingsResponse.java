package com.fa26se088.filmlink.dto.machine.response;

/**
 * Thông tin phản hồi cấu hình tham số lập lịch tự động của Film Lab (UC68, BR-60, BR-61).
 *
 * @param filmLabId ID phòng lab
 * @param qcBufferHours thời gian đệm QC tính theo giờ làm việc (0–48)
 * @param expressDailyCapPercent tỉ lệ công suất tối đa mỗi ngày dành cho đơn Express (1–100%,
 *     {@code null} = không giới hạn)
 * @param expressCommitmentWorkingDays số ngày làm việc cam kết cho đơn Express (1 hoặc 2, {@code
 *     null} = dùng mặc định của sàn)
 */
public record SchedulingSettingsResponse(
    Long filmLabId,
    Short qcBufferHours,
    Short expressDailyCapPercent,
    Short expressCommitmentWorkingDays) {}
