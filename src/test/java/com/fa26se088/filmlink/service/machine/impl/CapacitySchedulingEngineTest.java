package com.fa26se088.filmlink.service.machine.impl;

import static com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.VIETNAM_ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fa26se088.filmlink.dto.machine.CapacityProposal;
import com.fa26se088.filmlink.dto.machine.CapacityRequest;
import com.fa26se088.filmlink.dto.machine.DayAllocation;
import com.fa26se088.filmlink.dto.machine.RollGroup;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.ExistingAllocation;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.IncidentWindow;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.LabScheduleConfig;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.MachineSnapshot;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.WorkingWindow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapacitySchedulingEngineTest {

  private static final long PROCESS_C41 = 1L;
  private static final long PROCESS_BW = 2L;
  private static final long FORMAT_135 = 1L;
  private static final long P1_ID = 101L;
  private static final long S1_ID = 102L;

  /** Thứ Hai 16/11/2026: ngày nhận phim của bộ dữ liệu DS-05. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

  private static final LocalDate TUESDAY = MONDAY.plusDays(1);
  private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
  private static final LocalDate THURSDAY = MONDAY.plusDays(3);
  private static final LocalDate FRIDAY = MONDAY.plusDays(4);
  private static final LocalDate SATURDAY = MONDAY.plusDays(5);
  private static final LocalDate NEXT_MONDAY = MONDAY.plusDays(7);

  private CapacitySchedulingEngine engine;

  @BeforeEach
  void setUp() {
    engine = new CapacitySchedulingEngine();
  }

  @Test
  @DisplayName(
      "TC-F12-01 (DS-05): Thứ 3 đầy 12 cuộn Standard -> đơn 3 cuộn nhận 10:00 Thứ 2 xếp vào Thứ 4, hẹn trả 12:00 Thứ 5")
  void propose_ds05_fullTuesday_assignsWednesdayAndReturnsThursdayNoon() {
    LabScheduleConfig labConfig = ds05LabConfig();
    List<MachineSnapshot> machines = ds05Machines();
    List<ExistingAllocation> existing =
        List.of(
            new ExistingAllocation(
                TUESDAY, PROCESS_C41, FORMAT_135, ServiceTier.STANDARD, 12));

    CapacityRequest request = standardOrderReceivedMondayMorning(3);

    CapacityProposal proposal =
        engine.propose(request, labConfig, machines, List.of(), existing);

    assertThat(proposal.feasible()).isTrue();
    assertThat(proposal.infeasibleReason()).isNull();
    assertThat(proposal.allocations())
        .containsExactly(new DayAllocation(WEDNESDAY, PROCESS_C41, FORMAT_135, 3));
    assertThat(proposal.promisedReturnAt()).isEqualTo(vnTime(THURSDAY, 12, 0));
    assertThat(proposal.tierCommitmentAt()).isEqualTo(vnTime(SATURDAY, 10, 0));
  }

  @Test
  @DisplayName(
      "TC-F14-01: Máy P1 hỏng trong ngày Thứ 4 -> đẩy sang Thứ 5, hẹn trả 12:00 Thứ 6 (trong cam kết 10:00 Thứ 7)")
  void propose_p1DownOnWednesday_pushesToThursdayAndReturnsFridayNoon() {
    LabScheduleConfig labConfig = ds05LabConfig();
    List<MachineSnapshot> machines = ds05Machines();
    List<ExistingAllocation> existing =
        List.of(
            new ExistingAllocation(
                TUESDAY, PROCESS_C41, FORMAT_135, ServiceTier.STANDARD, 12));
    List<IncidentWindow> incidents =
        List.of(
            new IncidentWindow(
                P1_ID, vnTime(WEDNESDAY, 8, 0), vnTime(WEDNESDAY, 16, 0)));

    CapacityRequest request = standardOrderReceivedMondayMorning(3);

    CapacityProposal proposal =
        engine.propose(request, labConfig, machines, incidents, existing);

    assertThat(proposal.feasible()).isTrue();
    assertThat(proposal.allocations())
        .containsExactly(new DayAllocation(THURSDAY, PROCESS_C41, FORMAT_135, 3));
    assertThat(proposal.promisedReturnAt()).isEqualTo(vnTime(FRIDAY, 12, 0));
    assertThat(proposal.tierCommitmentAt()).isEqualTo(vnTime(SATURDAY, 10, 0));
    assertThat(proposal.promisedReturnAt()).isBeforeOrEqualTo(proposal.tierCommitmentAt());
  }

  @Test
  @DisplayName(
      "DS-06: Máy P1 hỏng từ Thứ 4 đến hết Thứ 6 -> đẩy sang Thứ 7, qua Chủ Nhật nghỉ -> hẹn trả 12:00 Thứ 2 tuần sau (vượt cam kết 10:00 Thứ 7)")
  void propose_p1DownWednesdayThroughFriday_pushesToSaturdayAndReturnsNextMondayNoon() {
    LabScheduleConfig labConfig = ds05LabConfig();
    List<MachineSnapshot> machines = ds05Machines();
    List<ExistingAllocation> existing =
        List.of(
            new ExistingAllocation(
                TUESDAY, PROCESS_C41, FORMAT_135, ServiceTier.STANDARD, 12));
    List<IncidentWindow> incidents =
        List.of(
            new IncidentWindow(
                P1_ID, vnTime(WEDNESDAY, 8, 0), vnTime(FRIDAY, 16, 0)));

    CapacityRequest request = standardOrderReceivedMondayMorning(3);

    CapacityProposal proposal =
        engine.propose(request, labConfig, machines, incidents, existing);

    assertThat(proposal.feasible()).isTrue();
    assertThat(proposal.allocations())
        .containsExactly(new DayAllocation(SATURDAY, PROCESS_C41, FORMAT_135, 3));
    assertThat(proposal.promisedReturnAt()).isEqualTo(vnTime(NEXT_MONDAY, 12, 0));
    assertThat(proposal.tierCommitmentAt()).isEqualTo(vnTime(SATURDAY, 10, 0));
    assertThat(proposal.promisedReturnAt()).isAfter(proposal.tierCommitmentAt());
  }

  @Test
  @DisplayName("UH-51: Không có máy hỗ trợ quy trình hoặc khổ phim -> feasible = false")
  void propose_noCompatibleMachine_returnsInfeasible() {
    LabScheduleConfig labConfig = ds05LabConfig();
    List<MachineSnapshot> machines = ds05Machines();

    CapacityRequest unsupportedRequest =
        new CapacityRequest(
            2L,
            10L,
            ServiceTier.STANDARD,
            vnTime(MONDAY, 10, 0),
            List.of(new RollGroup(PROCESS_BW, FORMAT_135, 2, 36)));

    CapacityProposal proposal =
        engine.propose(unsupportedRequest, labConfig, machines, List.of(), List.of());

    assertThat(proposal.feasible()).isFalse();
    assertThat(proposal.infeasibleReason()).isNotBlank();
    assertThat(proposal.allocations()).isEmpty();
    assertThat(proposal.promisedReturnAt()).isNull();
  }

  private static CapacityRequest standardOrderReceivedMondayMorning(int rolls) {
    return new CapacityRequest(
        1L,
        10L,
        ServiceTier.STANDARD,
        vnTime(MONDAY, 10, 0),
        List.of(new RollGroup(PROCESS_C41, FORMAT_135, rolls, 36)));
  }

  private static LabScheduleConfig ds05LabConfig() {
    return new LabScheduleConfig(standardWeekWindows(), 4, 25, 5);
  }

  private static List<MachineSnapshot> ds05Machines() {
    Map<Short, WorkingWindow> windows = standardWeekWindows();
    MachineSnapshot p1 =
        new MachineSnapshot(
            P1_ID,
            MachineType.PROCESSOR,
            2,
            MachineStatus.ACTIVE,
            Set.of(PROCESS_C41),
            Set.of(FORMAT_135),
            windows);
    MachineSnapshot s1 =
        new MachineSnapshot(
            S1_ID,
            MachineType.SCANNER,
            72,
            MachineStatus.ACTIVE,
            Set.of(),
            Set.of(FORMAT_135),
            windows);
    return List.of(p1, s1);
  }

  private static Map<Short, WorkingWindow> standardWeekWindows() {
    Map<Short, WorkingWindow> map = new HashMap<>();
    for (short day = 1; day <= 6; day++) {
      map.put(day, new WorkingWindow(false, LocalTime.of(8, 0), LocalTime.of(16, 0)));
    }
    map.put((short) 7, new WorkingWindow(true, null, null));
    return map;
  }

  private static Instant vnTime(LocalDate date, int hour, int minute) {
    return ZonedDateTime.of(date, LocalTime.of(hour, minute), VIETNAM_ZONE).toInstant();
  }
}
