package com.fa26se088.filmlink.mapper.machine;

import com.fa26se088.filmlink.dto.machine.response.MachineResponse;
import com.fa26se088.filmlink.dto.machine.response.MachineWorkingHourResponse;
import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.machine.Machine;
import com.fa26se088.filmlink.entity.machine.MachineWorkingHour;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi entity {@link Machine} sang DTO {@link MachineResponse} và tính toán công suất ngày
 * theo từng ca hoạt động (UC68, FA26-72).
 */
@Component
public class MachineMapper {

  private static final long MINUTES_PER_HOUR = 60L;
  private static final long STANDARD_FRAMES_PER_ROLL = 36L;

  /**
   * Chuyển đổi entity máy tráng/scan sang DTO phản hồi kèm công suất tính toán theo ngày.
   */
  public MachineResponse toResponse(Machine machine) {
    List<MachineWorkingHourResponse> workingHourResponses =
        machine.getWorkingHours().stream()
            .sorted(Comparator.comparing(MachineWorkingHour::getDayOfWeek))
            .map(
                hour ->
                    toWorkingHourResponse(
                        hour, machine.getMachineType(), machine.getCapacityPerHour()))
            .toList();

    int maxDailyCapacityRolls =
        workingHourResponses.stream()
            .mapToInt(MachineWorkingHourResponse::dailyCapacityRolls)
            .max()
            .orElse(0);

    return new MachineResponse(
        machine.getId(),
        machine.getFilmLabId(),
        machine.getMachineCode(),
        machine.getModelName(),
        machine.getMachineType(),
        machine.getCapacityPerHour(),
        machine.getStatus(),
        maxDailyCapacityRolls,
        machine.getFilmProcesses().stream().map(BaseEntity::getId).collect(Collectors.toSet()),
        machine.getFilmFormats().stream().map(BaseEntity::getId).collect(Collectors.toSet()),
        workingHourResponses,
        machine.getCreatedAt(),
        machine.getUpdatedAt());
  }

  /**
   * Chuyển đổi giờ hoạt động của một ngày kèm công suất tính toán trong ngày đó.
   */
  public MachineWorkingHourResponse toWorkingHourResponse(
      MachineWorkingHour hour, MachineType machineType, Integer capacityPerHour) {
    int dailyCapacity =
        calculateDailyCapacityRolls(
            machineType,
            capacityPerHour != null ? capacityPerHour : 0,
            hour.isClosed(),
            hour.getOpensAt(),
            hour.getClosesAt());

    return new MachineWorkingHourResponse(
        hour.getDayOfWeek(), hour.isClosed(), hour.getOpensAt(), hour.getClosesAt(), dailyCapacity);
  }

  /**
   * Công thức tính công suất ngày (UC68):
   *
   * <ul>
   *   <li>PROCESSOR: {@code capacity_per_hour * tổng giờ mở cửa} (cuộn)
   *   <li>SCANNER: {@code (capacity_per_hour * tổng giờ mở cửa) / 36} (quy đổi ra số cuộn 36 kiểu)
   * </ul>
   */
  public static int calculateDailyCapacityRolls(
      MachineType machineType,
      int capacityPerHour,
      boolean closed,
      LocalTime opensAt,
      LocalTime closesAt) {
    if (closed
        || capacityPerHour <= 0
        || opensAt == null
        || closesAt == null
        || !closesAt.isAfter(opensAt)) {
      return 0;
    }

    long openMinutes = Duration.between(opensAt, closesAt).toMinutes();
    if (machineType == MachineType.SCANNER) {
      return (int) ((capacityPerHour * openMinutes) / (MINUTES_PER_HOUR * STANDARD_FRAMES_PER_ROLL));
    }
    return (int) ((capacityPerHour * openMinutes) / MINUTES_PER_HOUR);
  }
}
