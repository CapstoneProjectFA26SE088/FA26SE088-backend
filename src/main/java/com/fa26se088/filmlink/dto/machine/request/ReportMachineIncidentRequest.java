package com.fa26se088.filmlink.dto.machine.request;

import com.fa26se088.filmlink.entity.machine.enums.MachineIncidentCause;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * DTO yêu cầu khai báo sự cố máy tráng hoặc máy scan của Film Lab (UC70, FA26-76).
 *
 * @param cause nguyên nhân sự cố ({@code MACHINE_FAILURE}, {@code CHEMICALS_RUN_OUT}, {@code
 *     CHEMICALS_EXPIRED}, {@code POWER_OUTAGE}, {@code OTHER})
 * @param description mô tả chi tiết sự cố (bắt buộc nếu {@code cause = OTHER}, tối đa 500 ký tự)
 * @param unavailableFrom thời điểm máy bắt đầu ngưng hoạt động
 * @param expectedAvailableAt thời điểm dự kiến khắc phục xong (null nếu chưa xác định → máy chuyển
 *     sang {@code DOWN}; khác null → máy chuyển sang {@code MAINTENANCE})
 */
public record ReportMachineIncidentRequest(
    @NotNull(message = "Nguyên nhân sự cố không được để trống") MachineIncidentCause cause,
    @Size(max = 500, message = "Mô tả sự cố tối đa 500 ký tự") String description,
    @NotNull(message = "Thời điểm bắt đầu sự cố không được để trống") Instant unavailableFrom,
    Instant expectedAvailableAt) {}
