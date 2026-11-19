package com.fa26se088.filmlink.dto.machine.response;

import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Thông tin chi tiết máy tráng/scan kèm công suất tính toán theo ngày (UC68, FA26-72).
 *
 * @param id ID của máy
 * @param filmLabId ID phòng lab sở hữu máy
 * @param machineCode mã máy duy nhất trong lab (2–50 ký tự)
 * @param modelName tên dòng máy (hiển thị trên hồ sơ công khai đối với máy SCANNER)
 * @param machineType loại máy (PROCESSOR hoặc SCANNER)
 * @param capacityPerHour công suất mỗi giờ (PROCESSOR: cuộn/giờ, SCANNER: khung ảnh/giờ)
 * @param status trạng thái vận hành (ACTIVE, MAINTENANCE, DOWN)
 * @param dailyCapacityRolls công suất ngày làm việc tiêu chuẩn quy đổi ra số cuộn (36 kiểu/cuộn đối
 *     với SCANNER)
 * @param filmProcessIds danh sách ID quy trình tráng hỗ trợ (rỗng đối với SCANNER)
 * @param filmFormatIds danh sách ID khổ phim hỗ trợ
 * @param workingHours danh sách giờ hoạt động và công suất từng ngày trong tuần (1..7)
 * @param createdAt thời điểm tạo
 * @param updatedAt thời điểm cập nhật gần nhất
 */
public record MachineResponse(
    Long id,
    Long filmLabId,
    String machineCode,
    String modelName,
    MachineType machineType,
    Integer capacityPerHour,
    MachineStatus status,
    int dailyCapacityRolls,
    Set<Long> filmProcessIds,
    Set<Long> filmFormatIds,
    List<MachineWorkingHourResponse> workingHours,
    Instant createdAt,
    Instant updatedAt) {}
