package com.fa26se088.filmlink.service.machine.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.machine.request.CreateMachineRequest;
import com.fa26se088.filmlink.dto.machine.request.MachineWorkingHourRequest;
import com.fa26se088.filmlink.dto.machine.request.UpdateMachineRequest;
import com.fa26se088.filmlink.dto.machine.request.UpdateSchedulingSettingsRequest;
import com.fa26se088.filmlink.dto.machine.response.MachineResponse;
import com.fa26se088.filmlink.dto.machine.response.SchedulingSettingsResponse;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.machine.Machine;
import com.fa26se088.filmlink.entity.machine.MachineWorkingHour;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.entity.masterdata.FilmFormat;
import com.fa26se088.filmlink.entity.masterdata.FilmProcess;
import com.fa26se088.filmlink.entity.masterdata.enums.CatalogStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.machine.MachineMapper;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmFormatRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmProcessRepository;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MachineServiceImplTest {

  @Mock
  private MachineRepository machineRepository;
  @Mock
  private FilmProcessRepository filmProcessRepository;
  @Mock
  private FilmFormatRepository filmFormatRepository;
  @Mock
  private LabAccessService labAccessService;
  @Mock
  private AuditLogService auditLogService;

  private MachineServiceImpl machineService;

  @BeforeEach
  void setUp() {
    machineService = new MachineServiceImpl(
        machineRepository,
        filmProcessRepository,
        filmFormatRepository,
        labAccessService,
        auditLogService,
        new MachineMapper());
  }

  @Test
  @DisplayName("TC-F06-01: Lấy danh sách máy của lab -> tính đúng công suất ngày P1 (PROCESSOR: 26*8 = 208 cuộn) và S1 (SCANNER: 327*8/36 = 72 cuộn)")
  void getMyMachines_calculatesProcessorAndScannerDailyCapacityCorrectly() {
    FilmLab lab = createLab(10L, 100L);

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

  @Test
  @DisplayName("TC-F06-01 & TC-SM04-01: Khai báo máy tráng P1 (C-41, 35mm/120, 26 cuộn/h, T2-T7 8h/ngày) -> ACTIVE, dailyCapacityRolls = 208, ghi audit")
  void createMachine_processor_success() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);
    when(machineRepository.existsByFilmLabIdAndMachineCodeIgnoreCase(10L, "P1")).thenReturn(false);

    FilmProcess c41 = new FilmProcess();
    ReflectionTestUtils.setField(c41, "id", 1L);
    c41.setStatus(CatalogStatus.ACTIVE);
    when(filmProcessRepository.findAllById(Set.of(1L))).thenReturn(List.of(c41));

    FilmFormat f35 = new FilmFormat();
    ReflectionTestUtils.setField(f35, "id", 1L);
    f35.setStatus(CatalogStatus.ACTIVE);
    FilmFormat f120 = new FilmFormat();
    ReflectionTestUtils.setField(f120, "id", 2L);
    f120.setStatus(CatalogStatus.ACTIVE);
    when(filmFormatRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(f35, f120));

    when(machineRepository.saveAndFlush(any(Machine.class)))
        .thenAnswer(
            inv -> {
              Machine m = inv.getArgument(0);
              ReflectionTestUtils.setField(m, "id", 50L);
              return m;
            });

    CreateMachineRequest request = new CreateMachineRequest(
        " P1 ",
        "Noritsu V30",
        MachineType.PROCESSOR,
        26,
        Set.of(1L),
        Set.of(1L, 2L),
        createStandardWeekHourRequests());

    MachineResponse response = machineService.createMachine(100L, request);

    assertThat(response.id()).isEqualTo(50L);
    assertThat(response.machineCode()).isEqualTo("P1");
    assertThat(response.status()).isEqualTo(MachineStatus.ACTIVE);
    assertThat(response.dailyCapacityRolls()).isEqualTo(208);
    assertThat(response.filmProcessIds()).containsExactly(1L);
    assertThat(response.filmFormatIds()).containsExactlyInAnyOrder(1L, 2L);

    ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(auditCaptor.capture());
    assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.MACHINE_CREATED);
    assertThat(auditCaptor.getValue().targetType()).isEqualTo(AuditTargetType.MACHINE);
    assertThat(auditCaptor.getValue().targetId()).isEqualTo(50L);
  }

  @Test
  @DisplayName("Khai báo máy trùng machineCode trong cùng lab -> ném 409 MACHINE_CODE_DUPLICATE")
  void createMachine_duplicateCode_throwsConflict() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);
    when(machineRepository.existsByFilmLabIdAndMachineCodeIgnoreCase(10L, "p1")).thenReturn(true);

    CreateMachineRequest request = new CreateMachineRequest(
        "p1",
        "Noritsu V30",
        MachineType.PROCESSOR,
        26,
        Set.of(1L),
        Set.of(1L),
        createStandardWeekHourRequests());

    assertThatThrownBy(() -> machineService.createMachine(100L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MACHINE_CODE_DUPLICATE));

    verify(machineRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Khai báo máy PROCESSOR không chọn quy trình tráng -> ném 400 VALIDATION_ERROR")
  void createMachine_processorWithoutFilmProcess_throwsValidationError() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);
    when(machineRepository.existsByFilmLabIdAndMachineCodeIgnoreCase(10L, "P1")).thenReturn(false);

    CreateMachineRequest request = new CreateMachineRequest(
        "P1",
        "Noritsu V30",
        MachineType.PROCESSOR,
        26,
        Set.of(),
        Set.of(1L),
        createStandardWeekHourRequests());

    assertThatThrownBy(() -> machineService.createMachine(100L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

    verify(machineRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("BR-16: Khai báo máy với khổ phim DISCONTINUED -> ném 422 MACHINE_MASTERDATA_INACTIVE")
  void createMachine_inactiveFilmFormat_throwsMasterdataInactive() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);
    when(machineRepository.existsByFilmLabIdAndMachineCodeIgnoreCase(10L, "S1")).thenReturn(false);

    FilmFormat discontinuedFormat = new FilmFormat();
    ReflectionTestUtils.setField(discontinuedFormat, "id", 1L);
    discontinuedFormat.setStatus(CatalogStatus.DISCONTINUED);
    when(filmFormatRepository.findAllById(Set.of(1L))).thenReturn(List.of(discontinuedFormat));

    CreateMachineRequest request =
        new CreateMachineRequest(
            "S1",
            "Frontier SP-3000",
            MachineType.SCANNER,
            327,
            null,
            Set.of(1L),
            createStandardWeekHourRequests());

    assertThatThrownBy(() -> machineService.createMachine(100L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MACHINE_MASTERDATA_INACTIVE));

    verify(machineRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "Cập nhật thông tin máy thành công -> giữ nguyên machineCode, tính lại dailyCapacityRolls, ghi audit MACHINE_UPDATED")
  void updateMachine_success() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);

    Machine existing = new Machine();
    ReflectionTestUtils.setField(existing, "id", 55L);
    existing.setFilmLabId(10L);
    existing.setMachineCode("P1");
    existing.setModelName("Noritsu V30");
    existing.setMachineType(MachineType.PROCESSOR);
    existing.setCapacityPerHour(26);
    existing.setStatus(MachineStatus.ACTIVE);
    addStandardWeekHours(existing);

    when(machineRepository.findByIdAndFilmLabId(55L, 10L)).thenReturn(Optional.of(existing));

    FilmProcess c41 = new FilmProcess();
    ReflectionTestUtils.setField(c41, "id", 1L);
    c41.setStatus(CatalogStatus.ACTIVE);
    when(filmProcessRepository.findAllById(Set.of(1L))).thenReturn(List.of(c41));

    FilmFormat f35 = new FilmFormat();
    ReflectionTestUtils.setField(f35, "id", 1L);
    f35.setStatus(CatalogStatus.ACTIVE);
    when(filmFormatRepository.findAllById(Set.of(1L))).thenReturn(List.of(f35));

    when(machineRepository.saveAndFlush(any(Machine.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    UpdateMachineRequest request =
        new UpdateMachineRequest(
            "Noritsu V30 Pro",
            30,
            Set.of(1L),
            Set.of(1L),
            List.of(
                new MachineWorkingHourRequest(
                    (short) 1, false, LocalTime.of(8, 0), LocalTime.of(18, 0)),
                new MachineWorkingHourRequest((short) 2, true, null, null)));

    MachineResponse response = machineService.updateMachine(100L, 55L, request);

    assertThat(response.id()).isEqualTo(55L);
    assertThat(response.machineCode()).isEqualTo("P1");
    assertThat(response.modelName()).isEqualTo("Noritsu V30 Pro");
    assertThat(response.capacityPerHour()).isEqualTo(30);
    assertThat(response.dailyCapacityRolls()).isEqualTo(300);
    assertThat(response.workingHours()).hasSize(2);

    ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(auditCaptor.capture());
    assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.MACHINE_UPDATED);
    assertThat(auditCaptor.getValue().targetType()).isEqualTo(AuditTargetType.MACHINE);
    assertThat(auditCaptor.getValue().targetId()).isEqualTo(55L);
  }

  @Test
  @DisplayName("Cập nhật máy không tồn tại hoặc thuộc lab khác -> ném 404 MACHINE_NOT_FOUND")
  void updateMachine_notFound_throwsNotFound() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);
    when(machineRepository.findByIdAndFilmLabId(999L, 10L)).thenReturn(Optional.empty());

    UpdateMachineRequest request =
        new UpdateMachineRequest(
            "Model X",
            20,
            Set.of(1L),
            Set.of(1L),
            createStandardWeekHourRequests());

    assertThatThrownBy(() -> machineService.updateMachine(100L, 999L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MACHINE_NOT_FOUND));

    verify(machineRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "Cập nhật tham số lập lịch tự động (qcBufferHours, expressDailyCapPercent, expressCommitmentWorkingDays) thành công -> ghi audit")
  void updateSchedulingSettings_success() {
    FilmLab lab = createLab(10L, 100L);
    lab.setQcBufferHours((short) 4);
    lab.setExpressDailyCapPercent(null);
    lab.setExpressCommitmentWorkingDays(null);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);

    UpdateSchedulingSettingsRequest request =
        new UpdateSchedulingSettingsRequest((short) 6, (short) 25, (short) 1);

    SchedulingSettingsResponse response = machineService.updateSchedulingSettings(100L, request);

    assertThat(response.filmLabId()).isEqualTo(10L);
    assertThat(response.qcBufferHours()).isEqualTo((short) 6);
    assertThat(response.expressDailyCapPercent()).isEqualTo((short) 25);
    assertThat(response.expressCommitmentWorkingDays()).isEqualTo((short) 1);

    assertThat(lab.getQcBufferHours()).isEqualTo((short) 6);
    assertThat(lab.getExpressDailyCapPercent()).isEqualTo((short) 25);
    assertThat(lab.getExpressCommitmentWorkingDays()).isEqualTo((short) 1);

    ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(auditCaptor.capture());
    assertThat(auditCaptor.getValue().action())
        .isEqualTo(AuditAction.LAB_SCHEDULING_SETTINGS_UPDATED);
    assertThat(auditCaptor.getValue().targetType()).isEqualTo(AuditTargetType.FILM_LAB);
    assertThat(auditCaptor.getValue().targetId()).isEqualTo(10L);
  }

  private FilmLab createLab(Long labId, Long ownerAccountId) {
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", labId);
    lab.setOwnerAccountId(ownerAccountId);
    return lab;
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

  private List<MachineWorkingHourRequest> createStandardWeekHourRequests() {
    List<MachineWorkingHourRequest> list = new ArrayList<>();
    for (short day = 1; day <= 6; day++) {
      list.add(
          new MachineWorkingHourRequest(day, false, LocalTime.of(8, 0), LocalTime.of(16, 0)));
    }
    list.add(new MachineWorkingHourRequest((short) 7, true, null, null));
    return list;
  }
}
