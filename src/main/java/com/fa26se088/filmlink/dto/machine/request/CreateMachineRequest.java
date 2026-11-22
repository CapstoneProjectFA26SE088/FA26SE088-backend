package com.fa26se088.filmlink.dto.machine.request;

import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

/**
 * DTO yêu cầu khai báo máy tráng (PROCESSOR) hoặc máy scan (SCANNER) mới cho Film Lab (UC68,
 * FA26-72).
 *
 * @param machineCode mã máy duy nhất trong lab (2–50 ký tự, không sửa được sau khi tạo)
 * @param modelName tên dòng máy (tuỳ chọn, tối đa 150 ký tự)
 * @param machineType loại máy (PROCESSOR hoặc SCANNER)
 * @param capacityPerHour công suất mỗi giờ (PROCESSOR: cuộn/giờ, SCANNER: khung ảnh/giờ, > 0)
 * @param filmProcessIds danh sách ID quy trình tráng hỗ trợ (bắt buộc tối thiểu 1 đối với
 *     PROCESSOR)
 * @param filmFormatIds danh sách ID khổ phim hỗ trợ (bắt buộc tối thiểu 1)
 * @param workingHours danh sách cấu hình giờ hoạt động theo ngày trong tuần
 */
public record CreateMachineRequest(
    @NotBlank(message = "Mã máy không được để trống")
        @Size(min = 2, max = 50, message = "Mã máy phải từ 2 đến 50 ký tự")
        String machineCode,
    @Size(max = 150, message = "Tên dòng máy tối đa 150 ký tự") String modelName,
    @NotNull(message = "Loại máy không được để trống") MachineType machineType,
    @NotNull(message = "Công suất mỗi giờ không được để trống")
        @Min(value = 1, message = "Công suất mỗi giờ phải lớn hơn 0")
        Integer capacityPerHour,
    Set<Long> filmProcessIds,
    @NotEmpty(message = "Phải chọn ít nhất một khổ phim hỗ trợ") Set<Long> filmFormatIds,
    @NotEmpty(message = "Phải cấu hình giờ hoạt động cho máy")
        @Size(max = 7, message = "Giờ hoạt động tối đa 7 ngày trong tuần")
        List<@Valid MachineWorkingHourRequest> workingHours) {}
