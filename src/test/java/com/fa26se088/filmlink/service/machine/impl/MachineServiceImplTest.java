package com.fa26se088.filmlink.service.machine.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.machine.response.MachineResponse;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.machine.Machine;
import com.fa26se088.filmlink.entity.machine.MachineWorkingHour;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.mapper.machine.MachineMapper;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MachineServiceImplTest {

  @Mock private MachineRepository machineRepository;
  @Mock private LabAccessService labAccessService;

  private MachineServiceImpl machineService;

  @BeforeEach
  void setUp() {
    machineService =
        new MachineServiceImpl(machineRepository, labAccessService, new MachineMapper());
  }

  @Test
  @DisplayName(
      "TC-F06-01: Lấy danh sách máy của lab -> tính đúng công suất ngày P1 (PROCESSOR: 26*8 = 208 cuộn) và S1 (SCANNER: 327*8/36 = 72 cuộn)")
  void getMyMachines_calculatesProcessorAndScannerDailyCapacityCorrectly() {
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", 10L);
    lab.setOwnerAccountId(100L);

    Machine p1 = new Machine();
    ReflectionTestUtils.setField(p1, "id", 1L);
    p1.setFilmLabId(10L);
    p1.setMachineCode("P1");
    p1.setModelName("Noritsu V30");
    p1.setMachineType(MachineType.PROCESSOR);
    p1.setCapacityPerHour(26);
    p1.setStatus(MachineStatus.ACTIVE);
    addStandardWeekHours(p1);

    Machine s1 = new Machine();
    ReflectionTestUtils.setField(s1, "id", 2L);
    s1.setFilmLabId(10L);
    s1.setMachineCode("S1");
    s1.setModelName("Frontier SP-3000");
    s1.setMachineType(MachineType.SCANNER);
    s1.setCapacityPerHour(327);
    s1.setStatus(MachineStatus.ACTIVE);
    addStandardWeekHours(s1);

    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);
    when(machineRepository.findByFilmLabIdOrderByIdAsc(10L)).thenReturn(List.of(p1, s1));

    List<MachineResponse> result = machineService.getMyMachines(100L);

    assertThat(result).hasSize(2);

    MachineResponse processorRes = result.get(0);
    assertThat(processorRes.machineCode()).isEqualTo("P1");
    assertThat(processorRes.machineType()).isEqualTo(MachineType.PROCESSOR);
    assertThat(processorRes.dailyCapacityRolls()).isEqualTo(208);
    assertThat(processorRes.workingHours()).hasSize(7);
    assertThat(processorRes.workingHours().get(0).dailyCapacityRolls()).isEqualTo(208);
    assertThat(processorRes.workingHours().get(6).closed()).isTrue();
    assertThat(processorRes.workingHours().get(6).dailyCapacityRolls()).isEqualTo(0);

    MachineResponse scannerRes = result.get(1);
    assertThat(scannerRes.machineCode()).isEqualTo("S1");
    assertThat(scannerRes.machineType()).isEqualTo(MachineType.SCANNER);
    assertThat(scannerRes.dailyCapacityRolls()).isEqualTo(72);
    assertThat(scannerRes.workingHours()).hasSize(7);
    assertThat(scannerRes.workingHours().get(0).dailyCapacityRolls()).isEqualTo(72);
    assertThat(scannerRes.workingHours().get(6).dailyCapacityRolls()).isEqualTo(0);

    verify(labAccessService).requireOwnedLab(100L);
    verify(machineRepository).findByFilmLabIdOrderByIdAsc(10L);
  }

  private void addStandardWeekHours(Machine machine) {
    for (short day = 1; day <= 6; day++) {
      MachineWorkingHour wh = new MachineWorkingHour();
      wh.setDayOfWeek(day);
      wh.setClosed(false);
      wh.setOpensAt(LocalTime.of(8, 0));
      wh.setClosesAt(LocalTime.of(16, 0));
      machine.addWorkingHour(wh);
    }
    MachineWorkingHour sunday = new MachineWorkingHour();
    sunday.setDayOfWeek((short) 7);
    sunday.setClosed(true);
    machine.addWorkingHour(sunday);
  }
}
