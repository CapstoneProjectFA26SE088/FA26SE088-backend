package com.fa26se088.filmlink.service.machine.impl;

import com.fa26se088.filmlink.dto.machine.CapacityProposal;
import com.fa26se088.filmlink.dto.machine.CapacityRequest;
import com.fa26se088.filmlink.dto.machine.DayAllocation;
import com.fa26se088.filmlink.dto.machine.RollGroup;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lớp thuật toán thuần túy (Pure Class) tính toán phân bổ công suất theo ngày và ngày hẹn trả cho
 * đơn hàng (UC69, BR-58–BR-61).
 *
 * <p>Không phụ thuộc Spring Container hay Repository để dễ dàng Unit Test với dữ liệu cố định.
 */
public class CapacitySchedulingEngine {

  /** Múi giờ chuẩn của sàn theo CR-04 (GMT+7). */
  public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private static final int DEFAULT_FRAMES_PER_ROLL = 36;
  private static final int MAX_LOOKAHEAD_DAYS = 60;
  private static final long MINUTES_PER_HOUR = 60L;

  /**
   * Đề xuất phân bổ công suất theo ngày, ngày hẹn trả dự kiến và thời hạn cam kết cho đơn hàng.
   */
  public CapacityProposal propose(
      CapacityRequest request,
      LabScheduleConfig labConfig,
      List<MachineSnapshot> machines,
      List<IncidentWindow> incidents,
      List<ExistingAllocation> existingAllocations) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(labConfig, "labConfig must not be null");

    List<MachineSnapshot> allMachines = machines != null ? machines : List.of();
    List<IncidentWindow> allIncidents = incidents != null ? incidents : List.of();
    List<ExistingAllocation> allExisting =
        existingAllocations != null ? existingAllocations : List.of();

    Map<Long, List<IncidentWindow>> incidentsByMachine = groupIncidentsByMachine(allIncidents);

    // 1. Kiểm tra lab có ngày mở cửa làm việc hay không
    if (!hasAnyOpenDay(labConfig.openingHours())) {
      return infeasible("Phòng lab chưa cấu hình lịch mở cửa làm việc.");
    }

    // 2. Kiểm tra mỗi nhóm cuộn phim đều có máy PROCESSOR và SCANNER tương thích (UH-51)
    for (RollGroup group : request.groups()) {
      List<MachineSnapshot> compatibleProcessors =
          findCompatibleMachines(allMachines, MachineType.PROCESSOR, group, incidentsByMachine);
      if (compatibleProcessors.isEmpty()) {
        return infeasible("Không có máy tráng phù hợp đang hoạt động cho quy trình và khổ phim đã chọn.");
      }

      List<MachineSnapshot> compatibleScanners =
          findCompatibleMachines(allMachines, MachineType.SCANNER, group, incidentsByMachine);
      if (compatibleScanners.isEmpty()) {
        return infeasible("Không có máy scan phù hợp đang hoạt động cho khổ phim đã chọn.");
      }
    }

    // 3. Tổng hợp số cuộn đã phân bổ theo ngày và hạng dịch vụ
    Map<LocalDate, Integer> usedStandardByDay = new HashMap<>();
    Map<LocalDate, Integer> usedExpressByDay = new HashMap<>();
    for (ExistingAllocation existing : allExisting) {
      if (existing.tier() == ServiceTier.EXPRESS) {
        usedExpressByDay.merge(existing.workDate(), existing.rollCount(), Integer::sum);
      } else {
        usedStandardByDay.merge(existing.workDate(), existing.rollCount(), Integer::sum);
      }
    }

    // 4. Phân bổ từng nhóm cuộn phim bắt đầu từ ngày làm việc tiếp theo sau receivedAt
    LocalDate receivedDate = request.receivedAt().atZone(VIETNAM_ZONE).toLocalDate();
    List<DayAllocation> allocations = new ArrayList<>();

    for (RollGroup group : request.groups()) {
      int remainingRolls = group.rollCount();
      List<MachineSnapshot> processors =
          findCompatibleMachines(allMachines, MachineType.PROCESSOR, group, incidentsByMachine);
      List<MachineSnapshot> scanners =
          findCompatibleMachines(allMachines, MachineType.SCANNER, group, incidentsByMachine);

      int framesPerRoll =
          group.framesPerRoll() > 0 ? group.framesPerRoll() : DEFAULT_FRAMES_PER_ROLL;

      for (int offset = 1; offset <= MAX_LOOKAHEAD_DAYS && remainingRolls > 0; offset++) {
        LocalDate workDate = receivedDate.plusDays(offset);
        WorkingWindow labWindow = labWindowForDate(labConfig, workDate);
        if (labWindow == null || !labWindow.isOpen()) {
          continue;
        }

        int processorCap =
            sumDailyCapacity(
                processors, workDate, labWindow, framesPerRoll, incidentsByMachine);
        int scannerCap =
            sumDailyCapacity(
                scanners, workDate, labWindow, framesPerRoll, incidentsByMachine);
        int totalCap = Math.min(processorCap, scannerCap);
        if (totalCap <= 0) {
          continue;
        }

        int availableRolls =
            availableRollsForTier(
                totalCap,
                request.tier(),
                labConfig.expressDailyCapPercent(),
                usedStandardByDay.getOrDefault(workDate, 0),
                usedExpressByDay.getOrDefault(workDate, 0));
        if (availableRolls <= 0) {
          continue;
        }

        int assigned = Math.min(remainingRolls, availableRolls);
        allocations.add(
            new DayAllocation(
                workDate, group.filmProcessId(), group.filmFormatId(), assigned));

        if (request.tier() == ServiceTier.EXPRESS) {
          usedExpressByDay.merge(workDate, assigned, Integer::sum);
        } else {
          usedStandardByDay.merge(workDate, assigned, Integer::sum);
        }
        remainingRolls -= assigned;
      }

      if (remainingRolls > 0) {
        return infeasible(
            "Không đủ công suất xử lý trong vòng " + MAX_LOOKAHEAD_DAYS + " ngày tới.");
      }
    }

    allocations.sort(
        Comparator.comparing(DayAllocation::workDate)
            .thenComparing(DayAllocation::filmProcessId)
            .thenComparing(DayAllocation::filmFormatId));

    LocalDate lastWorkDate =
        allocations.stream()
            .map(DayAllocation::workDate)
            .max(Comparator.naturalOrder())
            .orElse(receivedDate);

    // 5. Tính ngày hẹn trả dự kiến và thời hạn cam kết theo ngày làm việc
    Instant promisedReturnAt = calculatePromisedReturnAt(lastWorkDate, labConfig);
    Instant tierCommitmentAt =
        calculateTierCommitmentAt(request.receivedAt(), labConfig);

    if (promisedReturnAt == null || tierCommitmentAt == null) {
      return infeasible("Không thể tính toán ngày hẹn trả theo lịch mở cửa hiện tại của lab.");
    }

    return new CapacityProposal(true, null, allocations, promisedReturnAt, tierCommitmentAt);
  }

  private static CapacityProposal infeasible(String reason) {
    return new CapacityProposal(false, reason, List.of(), null, null);
  }

  private static Map<Long, List<IncidentWindow>> groupIncidentsByMachine(
      List<IncidentWindow> incidents) {
    Map<Long, List<IncidentWindow>> map = new HashMap<>();
    for (IncidentWindow incident : incidents) {
      map.computeIfAbsent(incident.machineId(), ignored -> new ArrayList<>()).add(incident);
    }
    return map;
  }

  private static List<MachineSnapshot> findCompatibleMachines(
      List<MachineSnapshot> machines,
      MachineType type,
      RollGroup group,
      Map<Long, List<IncidentWindow>> incidentsByMachine) {
    List<MachineSnapshot> matched = new ArrayList<>();
    for (MachineSnapshot machine : machines) {
      if (machine.machineType() != type) {
        continue;
      }
      if (machine.formatIds() == null || !machine.formatIds().contains(group.filmFormatId())) {
        continue;
      }
      if (type == MachineType.PROCESSOR
          && (machine.processIds() == null
              || !machine.processIds().contains(group.filmProcessId()))) {
        continue;
      }
      if (!isMachinePotentiallyRecoverable(machine, incidentsByMachine.get(machine.id()))) {
        continue;
      }
      matched.add(machine);
    }
    return matched;
  }

  private static boolean isMachinePotentiallyRecoverable(
      MachineSnapshot machine, List<IncidentWindow> machineIncidents) {
    if (machineIncidents != null) {
      for (IncidentWindow incident : machineIncidents) {
        if (incident.expectedAvailableAt() == null) {
          return false;
        }
      }
    }
    if (machine.status() != MachineStatus.ACTIVE
        && (machineIncidents == null || machineIncidents.isEmpty())) {
      return false;
    }
    return hasAnyOpenDay(machine.workingHours());
  }

  private static boolean hasAnyOpenDay(Map<Short, WorkingWindow> windows) {
    if (windows == null || windows.isEmpty()) {
      return false;
    }
    for (WorkingWindow window : windows.values()) {
      if (window != null && window.isOpen()) {
        return true;
      }
    }
    return false;
  }

  private static WorkingWindow labWindowForDate(LabScheduleConfig labConfig, LocalDate workDate) {
    if (labConfig.openingHours() == null) {
      return null;
    }
    short dayOfWeek = (short) workDate.getDayOfWeek().getValue();
    return labConfig.openingHours().get(dayOfWeek);
  }

  private static int sumDailyCapacity(
      List<MachineSnapshot> machines,
      LocalDate workDate,
      WorkingWindow labWindow,
      int framesPerRoll,
      Map<Long, List<IncidentWindow>> incidentsByMachine) {
    short dayOfWeek = (short) workDate.getDayOfWeek().getValue();
    int total = 0;

    for (MachineSnapshot machine : machines) {
      WorkingWindow machineWindow =
          machine.workingHours() != null ? machine.workingHours().get(dayOfWeek) : null;
      if (machineWindow == null || !machineWindow.isOpen()) {
        continue;
      }

      LocalTime effectiveOpen =
          labWindow.opensAt().isAfter(machineWindow.opensAt())
              ? labWindow.opensAt()
              : machineWindow.opensAt();
      LocalTime effectiveClose =
          labWindow.closesAt().isBefore(machineWindow.closesAt())
              ? labWindow.closesAt()
              : machineWindow.closesAt();
      if (!effectiveClose.isAfter(effectiveOpen)) {
        continue;
      }

      Instant shiftStart = ZonedDateTime.of(workDate, effectiveOpen, VIETNAM_ZONE).toInstant();
      Instant shiftEnd = ZonedDateTime.of(workDate, effectiveClose, VIETNAM_ZONE).toInstant();
      long totalMinutes = Duration.between(shiftStart, shiftEnd).toMinutes();

      List<IncidentWindow> machineIncidents = incidentsByMachine.get(machine.id());
      long lostMinutes =
          calculateLostMinutes(shiftStart, shiftEnd, machine.status(), machineIncidents);
      long effectiveMinutes = Math.max(0L, totalMinutes - lostMinutes);
      if (effectiveMinutes <= 0) {
        continue;
      }

      if (machine.machineType() == MachineType.SCANNER) {
        total +=
            (int)
                ((machine.capacityPerHour() * effectiveMinutes)
                    / (MINUTES_PER_HOUR * framesPerRoll));
      } else {
        total += (int) ((machine.capacityPerHour() * effectiveMinutes) / MINUTES_PER_HOUR);
      }
    }

    return total;
  }

  private static long calculateLostMinutes(
      Instant shiftStart,
      Instant shiftEnd,
      MachineStatus status,
      List<IncidentWindow> incidents) {
    if (incidents == null || incidents.isEmpty()) {
      return status == MachineStatus.ACTIVE
          ? 0L
          : Duration.between(shiftStart, shiftEnd).toMinutes();
    }

    List<TimeInterval> intervals = new ArrayList<>();
    for (IncidentWindow incident : incidents) {
      Instant from = incident.unavailableFrom();
      Instant to =
          incident.expectedAvailableAt() != null ? incident.expectedAvailableAt() : shiftEnd;
      if (from == null) {
        from = shiftStart;
      }
      Instant clippedStart = from.isAfter(shiftStart) ? from : shiftStart;
      Instant clippedEnd = to.isBefore(shiftEnd) ? to : shiftEnd;
      if (clippedEnd.isAfter(clippedStart)) {
        intervals.add(new TimeInterval(clippedStart, clippedEnd));
      }
    }

    if (intervals.isEmpty()) {
      return 0L;
    }

    intervals.sort(Comparator.comparing(TimeInterval::start));
    long lostMinutes = 0L;
    Instant currentStart = intervals.getFirst().start();
    Instant currentEnd = intervals.getFirst().end();

    for (int i = 1; i < intervals.size(); i++) {
      TimeInterval next = intervals.get(i);
      if (!next.start().isAfter(currentEnd)) {
        if (next.end().isAfter(currentEnd)) {
          currentEnd = next.end();
        }
      } else {
        lostMinutes += Duration.between(currentStart, currentEnd).toMinutes();
        currentStart = next.start();
        currentEnd = next.end();
      }
    }
    lostMinutes += Duration.between(currentStart, currentEnd).toMinutes();
    return lostMinutes;
  }

  private static int availableRollsForTier(
      int totalCap,
      ServiceTier tier,
      Integer expressDailyCapPercent,
      int usedStandard,
      int usedExpress) {
    if (expressDailyCapPercent == null) {
      return Math.max(0, totalCap - (usedStandard + usedExpress));
    }

    int expressCap = (totalCap * expressDailyCapPercent) / 100;
    int standardCap = totalCap - expressCap;

    if (tier == ServiceTier.EXPRESS) {
      int tierRemaining = Math.max(0, expressCap - usedExpress);
      int totalRemaining = Math.max(0, totalCap - (usedStandard + usedExpress));
      return Math.min(tierRemaining, totalRemaining);
    }

    int tierRemaining = Math.max(0, standardCap - usedStandard);
    int totalRemaining = Math.max(0, totalCap - (usedStandard + usedExpress));
    return Math.min(tierRemaining, totalRemaining);
  }

  private static Instant calculatePromisedReturnAt(
      LocalDate lastWorkDate, LabScheduleConfig labConfig) {
    WorkingWindow lastDayWindow = labWindowForDate(labConfig, lastWorkDate);
    if (lastDayWindow == null || !lastDayWindow.isOpen()) {
      return null;
    }

    Instant shiftEnd =
        ZonedDateTime.of(lastWorkDate, lastDayWindow.closesAt(), VIETNAM_ZONE).toInstant();
    if (labConfig.qcBufferHours() <= 0) {
      return shiftEnd;
    }

    long remainingQcMinutes = labConfig.qcBufferHours() * MINUTES_PER_HOUR;
    for (int offset = 1; offset <= MAX_LOOKAHEAD_DAYS; offset++) {
      LocalDate date = lastWorkDate.plusDays(offset);
      WorkingWindow window = labWindowForDate(labConfig, date);
      if (window == null || !window.isOpen()) {
        continue;
      }
      long dayMinutes = Duration.between(window.opensAt(), window.closesAt()).toMinutes();
      if (remainingQcMinutes <= dayMinutes) {
        LocalTime targetTime = window.opensAt().plusMinutes(remainingQcMinutes);
        return ZonedDateTime.of(date, targetTime, VIETNAM_ZONE).toInstant();
      }
      remainingQcMinutes -= dayMinutes;
    }
    return null;
  }

  private static Instant calculateTierCommitmentAt(
      Instant receivedAt, LabScheduleConfig labConfig) {
    ZonedDateTime receivedVn = receivedAt.atZone(VIETNAM_ZONE);
    LocalDate currentDate = receivedVn.toLocalDate();
    LocalTime receivedTime = receivedVn.toLocalTime();

    int remainingWorkingDays = Math.max(1, labConfig.commitmentWorkingDays());
    for (int offset = 1; offset <= MAX_LOOKAHEAD_DAYS && remainingWorkingDays > 0; offset++) {
      LocalDate candidate = receivedVn.toLocalDate().plusDays(offset);
      WorkingWindow window = labWindowForDate(labConfig, candidate);
      if (window != null && window.isOpen()) {
        currentDate = candidate;
        remainingWorkingDays--;
      }
    }

    if (remainingWorkingDays > 0) {
      return null;
    }
    return ZonedDateTime.of(currentDate, receivedTime, VIETNAM_ZONE).toInstant();
  }

  private record TimeInterval(Instant start, Instant end) {}

  /** Khung giờ làm việc trong một ngày trong tuần của lab hoặc máy. */
  public record WorkingWindow(boolean closed, LocalTime opensAt, LocalTime closesAt) {

    /** Trả về true nếu ngày đó mở cửa với khung giờ hợp lệ. */
    public boolean isOpen() {
      return !closed && opensAt != null && closesAt != null && closesAt.isAfter(opensAt);
    }
  }

  /** Cấu hình lịch làm việc và tham số lập lịch của một Film Lab. */
  public record LabScheduleConfig(
      Map<Short, WorkingWindow> openingHours,
      int qcBufferHours,
      Integer expressDailyCapPercent,
      int commitmentWorkingDays) {}

  /** Ảnh chụp thông tin cấu hình của một máy tráng hoặc máy scan. */
  public record MachineSnapshot(
      Long id,
      MachineType machineType,
      int capacityPerHour,
      MachineStatus status,
      Set<Long> processIds,
      Set<Long> formatIds,
      Map<Short, WorkingWindow> workingHours) {}

  /** Khoảng thời gian gián đoạn do sự cố của một máy. */
  public record IncidentWindow(
      Long machineId, Instant unavailableFrom, Instant expectedAvailableAt) {}

  /** Phân bổ công suất đã được giữ chỗ từ các đơn hàng khác. */
  public record ExistingAllocation(
      LocalDate workDate,
      Long filmProcessId,
      Long filmFormatId,
      ServiceTier tier,
      int rollCount) {}
}
