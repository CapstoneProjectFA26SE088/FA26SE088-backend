package com.fa26se088.filmlink.dto.machine.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO yêu cầu cập nhật tham số lập lịch tự động của Film Lab (UC68, BR-60, BR-61).
 *
 * @param qcBufferHours thời gian đệm QC tính theo giờ làm việc (bắt buộc từ 0 đến 48 giờ)
 * @param expressDailyCapPercent tỉ lệ công suất tối đa mỗi ngày dành cho đơn Express (1–100%,
 *     {@code null} = không giới hạn)
 * @param expressCommitmentWorkingDays số ngày làm việc cam kết cho đơn Express (1 hoặc 2 ngày,
 *     {@code null} = dùng mặc định của sàn)
 */
public record UpdateSchedulingSettingsRequest(
    @NotNull(message = "Thời gian đệm QC không được để trống")
        @Min(value = 0, message = "Thời gian đệm QC phải từ 0 đến 48 giờ")
        @Max(value = 48, message = "Thời gian đệm QC phải từ 0 đến 48 giờ")
        Short qcBufferHours,
    @Min(value = 1, message = "Tỉ lệ giới hạn đơn Express mỗi ngày phải từ 1 đến 100%")
        @Max(value = 100, message = "Tỉ lệ giới hạn đơn Express mỗi ngày phải từ 1 đến 100%")
        Short expressDailyCapPercent,
    @Min(value = 1, message = "Số ngày làm việc cam kết Express chỉ được đặt là 1 hoặc 2 ngày")
        @Max(value = 2, message = "Số ngày làm việc cam kết Express chỉ được đặt là 1 hoặc 2 ngày")
        Short expressCommitmentWorkingDays) {}
