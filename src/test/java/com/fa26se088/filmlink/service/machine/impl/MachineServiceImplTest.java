package com.fa26se088.filmlink.service.machine.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.machine.CapacityProposal;
import com.fa26se088.filmlink.dto.machine.CapacityRequest;
import com.fa26se088.filmlink.dto.machine.DayAllocation;
import com.fa26se088.filmlink.dto.machine.request.CreateMachineRequest;
import com.fa26se088.filmlink.dto.machine.request.MachineWorkingHourRequest;
import com.fa26se088.filmlink.dto.machine.request.ReportMachineIncidentRequest;
import com.fa26se088.filmlink.dto.machine.request.UpdateMachineRequest;
import com.fa26se088.filmlink.dto.machine.request.UpdateSchedulingSettingsRequest;
import com.fa26se088.filmlink.dto.machine.response.MachineIncidentResponse;
import com.fa26se088.filmlink.dto.machine.response.MachineResponse;
import com.fa26se088.filmlink.dto.machine.response.SchedulingSettingsResponse;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.machine.Machine;
import com.fa26se088.filmlink.entity.machine.MachineIncident;
import com.fa26se088.filmlink.entity.machine.MachineWorkingHour;
import com.fa26se088.filmlink.entity.machine.OrderCapacityAllocation;
import com.fa26se088.filmlink.entity.machine.ScheduleChange;
import com.fa26se088.filmlink.entity.machine.enums.MachineIncidentCause;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.entity.machine.enums.ScheduleChangeStep;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.masterdata.FilmFormat;
import com.fa26se088.filmlink.entity.masterdata.FilmProcess;
import com.fa26se088.filmlink.entity.masterdata.enums.CatalogStatus;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.machine.MachineMapper;
import com.fa26se088.filmlink.repository.machine.MachineIncidentRepository;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.repository.machine.OrderCapacityAllocationRepository;
import com.fa26se088.filmlink.repository.machine.ScheduleChangeRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmFormatRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmProcessRepository;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.machine.CapacitySchedulingService;
import com.fa26se088.filmlink.service.machine.event.PromisedDateChangedEvent;
import java.time.Instant;
import java.time.LocalDate;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
  @Mock
  private MachineIncidentRepository machineIncidentRepository;
  @Mock
  private ScheduleChangeRepository scheduleChangeRepository;
  @Mock
  private OrderCapacityAllocationRepository orderCapacityAllocationRepository;
  @Mock
  private CapacitySchedulingService capacitySchedulingService;
  @Mock
  private ServiceOrderRepository serviceOrderRepository;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private MachineServiceImpl machineService;

  @BeforeEach
  void setUp() {
    machineService = new MachineServiceImpl(
        machineRepository,
        filmProcessRepository,
        filmFormatRepository,
        labAccessService,
        auditLogService,
        new MachineMapper(),
        machineIncidentRepository,
        scheduleChangeRepository,
        orderCapacityAllocationRepository,
        capacitySchedulingService,
        serviceOrderRepository,
        eventPublisher);
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

    CreateMachineRequest request = new CreateMachineRequest(
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
  @DisplayName("Cập nhật thông tin máy thành công -> giữ nguyên machineCode, tính lại dailyCapacityRolls, ghi audit MACHINE_UPDATED")
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

    UpdateMachineRequest request = new UpdateMachineRequest(
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

    UpdateMachineRequest request = new UpdateMachineRequest(
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
  @DisplayName("Cập nhật tham số lập lịch tự động (qcBufferHours, expressDailyCapPercent, expressCommitmentWorkingDays) thành công -> ghi audit")
  void updateSchedulingSettings_success() {
    FilmLab lab = createLab(10L, 100L);
    lab.setQcBufferHours((short) 4);
    lab.setExpressDailyCapPercent(null);
    lab.setExpressCommitmentWorkingDays(null);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);

    UpdateSchedulingSettingsRequest request = new UpdateSchedulingSettingsRequest((short) 6, (short) 25, (short) 1);

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

  @Test
  @DisplayName("TC-SM04-02 & TC-F14-01: Khai báo sự cố có expectedAvailableAt -> MAINTENANCE, tái xếp lịch ưu tiên EXPRESS trước STANDARD (FIFO), lưu schedule_changes và phát event")
  void reportIncident_withExpectedAvailableAt_reschedulesExpressBeforeStandardFifo() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);

    Machine p1 = new Machine();
    ReflectionTestUtils.setField(p1, "id", 1L);
    p1.setFilmLabId(10L);
    p1.setMachineCode("P1");
    p1.setMachineType(MachineType.PROCESSOR);
    p1.setCapacityPerHour(2);
    p1.setStatus(MachineStatus.ACTIVE);
    when(machineRepository.findByIdAndFilmLabId(1L, 10L)).thenReturn(Optional.of(p1));
    when(machineRepository.saveAndFlush(any(Machine.class))).thenAnswer(inv -> inv.getArgument(0));
    when(machineIncidentRepository.saveAndFlush(any(MachineIncident.class)))
        .thenAnswer(
            inv -> {
              MachineIncident inc = inv.getArgument(0);
              ReflectionTestUtils.setField(inc, "id", 88L);
              return inc;
            });

    Instant unavailableFrom = Instant.parse("2026-11-18T01:00:00Z"); // 08:00 VN Thứ 4
    Instant expectedAvailable = Instant.parse("2026-11-19T01:00:00Z"); // 08:00 VN Thứ 5

    Instant stdOlderReceived = Instant.parse("2026-11-16T03:00:00Z");
    Instant stdNewerReceived = Instant.parse("2026-11-16T04:00:00Z");
    Instant expressLaterReceived = Instant.parse("2026-11-16T05:00:00Z");

    Instant stdOldPromised = Instant.parse("2026-11-19T05:00:00Z"); // 12:00 Thứ 5
    Instant expressOldPromised = Instant.parse("2026-11-18T05:00:00Z");

    ServiceOrder stdNewerOrder = createInProcessingOrder(201L, 10L, ServiceTier.STANDARD, stdOldPromised);
    ServiceOrder stdOlderOrder = createInProcessingOrder(202L, 10L, ServiceTier.STANDARD, stdOldPromised);
    ServiceOrder expressOrder = createInProcessingOrder(203L, 10L, ServiceTier.EXPRESS, expressOldPromised);

    OrderCapacityAllocation alloc201 = createAllocation(201L, 10L, LocalDate.of(2026, 11, 18), ServiceTier.STANDARD,
        stdNewerReceived);
    OrderCapacityAllocation alloc202 = createAllocation(202L, 10L, LocalDate.of(2026, 11, 18), ServiceTier.STANDARD,
        stdOlderReceived);
    OrderCapacityAllocation alloc203 = createAllocation(203L, 10L, LocalDate.of(2026, 11, 18), ServiceTier.EXPRESS,
        expressLaterReceived);

    when(orderCapacityAllocationRepository.findByFilmLabIdAndWorkDateGreaterThanEqual(
        10L, LocalDate.of(2026, 11, 18)))
        .thenReturn(List.of(alloc201, alloc202, alloc203));
    when(serviceOrderRepository.findAllById(anyIterable()))
        .thenReturn(List.of(stdNewerOrder, stdOlderOrder, expressOrder));

    when(orderCapacityAllocationRepository.findByServiceOrderIdOrderByWorkDateAsc(201L))
        .thenReturn(List.of(alloc201));
    when(orderCapacityAllocationRepository.findByServiceOrderIdOrderByWorkDateAsc(202L))
        .thenReturn(List.of(alloc202));
    when(orderCapacityAllocationRepository.findByServiceOrderIdOrderByWorkDateAsc(203L))
        .thenReturn(List.of(alloc203));

    Instant expressNewPromised = Instant.parse("2026-11-19T05:00:00Z"); // Trong cam kết
    Instant expressCommitment = Instant.parse("2026-11-20T03:00:00Z");
    Instant stdOlderNewPromised = Instant.parse("2026-11-20T05:00:00Z"); // 12:00 Thứ 6 <= 10:00 T7
    Instant stdCommitment = Instant.parse("2026-11-21T03:00:00Z"); // 10:00 T7
    Instant stdNewerNewPromised = Instant.parse("2026-11-23T05:00:00Z"); // 12:00 T2 > 10:00 T7

    when(capacitySchedulingService.propose(any(CapacityRequest.class)))
        .thenAnswer(
            inv -> {
              CapacityRequest req = inv.getArgument(0);
              if (req.serviceOrderId().equals(203L)) {
                return new CapacityProposal(
                    true,
                    null,
                    List.of(new DayAllocation(LocalDate.of(2026, 11, 19), 1L, 1L, 3)),
                    expressNewPromised,
                    expressCommitment);
              }
              if (req.serviceOrderId().equals(202L)) {
                return new CapacityProposal(
                    true,
                    null,
                    List.of(new DayAllocation(LocalDate.of(2026, 11, 19), 1L, 1L, 3)),
                    stdOlderNewPromised,
                    stdCommitment);
              }
              return new CapacityProposal(
                  true,
                  null,
                  List.of(new DayAllocation(LocalDate.of(2026, 11, 21), 1L, 1L, 3)),
                  stdNewerNewPromised,
                  stdCommitment);
            });

    final long[] changeSeq = { 500L };
    when(scheduleChangeRepository.saveAndFlush(any(ScheduleChange.class)))
        .thenAnswer(
            inv -> {
              ScheduleChange sc = inv.getArgument(0);
              ReflectionTestUtils.setField(sc, "id", changeSeq[0]++);
              return sc;
            });

    ReportMachineIncidentRequest request = new ReportMachineIncidentRequest(
        MachineIncidentCause.MACHINE_FAILURE,
        "Hỏng motor kéo phim P1",
        unavailableFrom,
        expectedAvailable);

    MachineIncidentResponse response = machineService.reportIncident(100L, 1L, request);

    assertThat(response.id()).isEqualTo(88L);
    assertThat(response.machineStatusAfter()).isEqualTo(MachineStatus.MAINTENANCE);
    assertThat(p1.getStatus()).isEqualTo(MachineStatus.MAINTENANCE);
    assertThat(response.affectedOrderCount()).isEqualTo(3);
    assertThat(response.scheduleChanges()).hasSize(3);

    // Xác minh thứ tự tái xếp lịch: EXPRESS (203) -> STANDARD cũ hơn (202) ->
    // STANDARD mới hơn (201)
    ArgumentCaptor<CapacityRequest> reqCaptor = ArgumentCaptor.forClass(CapacityRequest.class);
    verify(capacitySchedulingService, times(3)).propose(reqCaptor.capture());
    assertThat(reqCaptor.getAllValues())
        .extracting(CapacityRequest::serviceOrderId)
        .containsExactly(203L, 202L, 201L);

    // Xác minh delete cũ trước rồi lưu mới
    InOrder order = inOrder(orderCapacityAllocationRepository, capacitySchedulingService);
    order.verify(orderCapacityAllocationRepository).deleteByServiceOrderId(203L);
    order.verify(orderCapacityAllocationRepository).deleteByServiceOrderId(202L);
    order.verify(orderCapacityAllocationRepository).deleteByServiceOrderId(201L);
    order.verify(orderCapacityAllocationRepository).flush();
    order.verify(capacitySchedulingService).propose(any());

    // Xác minh ràng buộc requiresResponse và responseDueAt của schedule_changes
    ArgumentCaptor<ScheduleChange> scCaptor = ArgumentCaptor.forClass(ScheduleChange.class);
    verify(scheduleChangeRepository, times(3)).saveAndFlush(scCaptor.capture());
    List<ScheduleChange> savedChanges = scCaptor.getAllValues();
    assertThat(savedChanges.get(0).getServiceOrderId()).isEqualTo(203L);
    assertThat(savedChanges.get(0).isRequiresResponse()).isFalse();
    assertThat(savedChanges.get(0).getResponseDueAt()).isNull();
    assertThat(savedChanges.get(0).getStepApplied()).isEqualTo(ScheduleChangeStep.RESCHEDULED);

    assertThat(savedChanges.get(1).getServiceOrderId()).isEqualTo(202L);
    assertThat(savedChanges.get(1).isRequiresResponse()).isFalse();
    assertThat(savedChanges.get(1).getResponseDueAt()).isNull();

    assertThat(savedChanges.get(2).getServiceOrderId()).isEqualTo(201L);
    assertThat(savedChanges.get(2).isRequiresResponse()).isTrue();
    assertThat(savedChanges.get(2).getResponseDueAt()).isNotNull();

    ArgumentCaptor<PromisedDateChangedEvent> eventCaptor = ArgumentCaptor.forClass(PromisedDateChangedEvent.class);
    verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues())
        .extracting(PromisedDateChangedEvent::serviceOrderId)
        .containsExactly(203L, 202L, 201L);
    assertThat(eventCaptor.getAllValues().get(2).requiresCustomerResponse()).isTrue();

    ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(auditCaptor.capture());
    assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.MACHINE_INCIDENT_REPORTED);
    assertThat(auditCaptor.getValue().targetType())
        .isEqualTo(AuditTargetType.MACHINE_INCIDENT);
    assertThat(auditCaptor.getValue().targetId()).isEqualTo(88L);
  }

  @Test
  @DisplayName("Khai báo sự cố không có expectedAvailableAt (chưa rõ thời điểm khắc phục) -> máy chuyển DOWN")
  void reportIncident_withoutExpectedAvailableAt_setsMachineStatusDown() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);

    Machine p1 = new Machine();
    ReflectionTestUtils.setField(p1, "id", 1L);
    p1.setFilmLabId(10L);
    p1.setMachineCode("P1");
    p1.setMachineType(MachineType.PROCESSOR);
    p1.setCapacityPerHour(2);
    p1.setStatus(MachineStatus.ACTIVE);
    when(machineRepository.findByIdAndFilmLabId(1L, 10L)).thenReturn(Optional.of(p1));
    when(machineRepository.saveAndFlush(any(Machine.class))).thenAnswer(inv -> inv.getArgument(0));
    when(machineIncidentRepository.saveAndFlush(any(MachineIncident.class)))
        .thenAnswer(
            inv -> {
              MachineIncident inc = inv.getArgument(0);
              ReflectionTestUtils.setField(inc, "id", 89L);
              return inc;
            });
    when(orderCapacityAllocationRepository.findByFilmLabIdAndWorkDateGreaterThanEqual(any(), any()))
        .thenReturn(List.of());

    ReportMachineIncidentRequest request = new ReportMachineIncidentRequest(
        MachineIncidentCause.POWER_OUTAGE,
        "Mất điện lưới khu vực chưa rõ giờ có lại",
        Instant.parse("2026-11-18T01:00:00Z"),
        null);

    MachineIncidentResponse response = machineService.reportIncident(100L, 1L, request);

    assertThat(response.machineStatusAfter()).isEqualTo(MachineStatus.DOWN);
    assertThat(p1.getStatus()).isEqualTo(MachineStatus.DOWN);
    assertThat(response.affectedOrderCount()).isZero();
    assertThat(response.scheduleChanges()).isEmpty();
  }

  @Test
  @DisplayName("Khai báo sự cố cause = OTHER nhưng thiếu mô tả -> ném 400 VALIDATION_ERROR")
  void reportIncident_causeOtherWithoutDescription_throwsValidationError() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);

    Machine p1 = new Machine();
    ReflectionTestUtils.setField(p1, "id", 1L);
    p1.setFilmLabId(10L);
    p1.setMachineCode("P1");
    p1.setStatus(MachineStatus.ACTIVE);
    when(machineRepository.findByIdAndFilmLabId(1L, 10L)).thenReturn(Optional.of(p1));

    ReportMachineIncidentRequest request = new ReportMachineIncidentRequest(
        MachineIncidentCause.OTHER,
        "   ",
        Instant.parse("2026-11-18T01:00:00Z"),
        Instant.parse("2026-11-19T01:00:00Z"));

    assertThatThrownBy(() -> machineService.reportIncident(100L, 1L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

    verify(machineIncidentRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Khai báo sự cố expectedAvailableAt <= unavailableFrom -> ném 400 VALIDATION_ERROR")
  void reportIncident_expectedAvailableNotAfterUnavailableFrom_throwsValidationError() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);

    Machine p1 = new Machine();
    ReflectionTestUtils.setField(p1, "id", 1L);
    p1.setFilmLabId(10L);
    p1.setMachineCode("P1");
    p1.setStatus(MachineStatus.ACTIVE);
    when(machineRepository.findByIdAndFilmLabId(1L, 10L)).thenReturn(Optional.of(p1));

    Instant ts = Instant.parse("2026-11-18T01:00:00Z");
    ReportMachineIncidentRequest request = new ReportMachineIncidentRequest(
        MachineIncidentCause.CHEMICALS_RUN_OUT, "Hết hóa chất C-41", ts, ts);

    assertThatThrownBy(() -> machineService.reportIncident(100L, 1L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

    verify(machineIncidentRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "TC-SM04-06: Giải quyết sự cố thành công -> resolvedAt != null, máy khôi phục ACTIVE, ghi audit MACHINE_INCIDENT_RESOLVED")
  void resolveIncident_success_restoresMachineActiveAndRecordsAudit() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);

    Machine p1 = new Machine();
    ReflectionTestUtils.setField(p1, "id", 1L);
    p1.setFilmLabId(10L);
    p1.setMachineCode("P1");
    p1.setStatus(MachineStatus.MAINTENANCE);
    when(machineRepository.findByIdAndFilmLabId(1L, 10L)).thenReturn(Optional.of(p1));
    when(machineRepository.saveAndFlush(any(Machine.class))).thenAnswer(inv -> inv.getArgument(0));

    MachineIncident incident = new MachineIncident();
    ReflectionTestUtils.setField(incident, "id", 88L);
    incident.setMachineId(1L);
    incident.setCause(MachineIncidentCause.MACHINE_FAILURE);
    incident.setDescription("Hỏng motor kéo phim P1");
    incident.setUnavailableFrom(Instant.parse("2026-11-18T01:00:00Z"));
    incident.setExpectedAvailableAt(Instant.parse("2026-11-19T01:00:00Z"));
    incident.setMachineStatusAfter(MachineStatus.MAINTENANCE);
    incident.setReportedByAccountId(100L);
    when(machineIncidentRepository.findByIdAndMachineId(88L, 1L)).thenReturn(Optional.of(incident));
    when(machineIncidentRepository.saveAndFlush(any(MachineIncident.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(machineIncidentRepository.findByMachineIdAndResolvedAtIsNull(1L)).thenReturn(List.of());
    when(scheduleChangeRepository.findByMachineIncidentIdOrderByIdAsc(88L)).thenReturn(List.of());

    MachineIncidentResponse response = machineService.resolveIncident(100L, 1L, 88L);

    assertThat(response.id()).isEqualTo(88L);
    assertThat(response.machineId()).isEqualTo(1L);
    assertThat(response.resolvedAt()).isNotNull();
    assertThat(response.machineStatusAfter()).isEqualTo(MachineStatus.ACTIVE);
    assertThat(p1.getStatus()).isEqualTo(MachineStatus.ACTIVE);

    ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(auditCaptor.capture());
    assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.MACHINE_INCIDENT_RESOLVED);
    assertThat(auditCaptor.getValue().targetType())
        .isEqualTo(AuditTargetType.MACHINE_INCIDENT);
    assertThat(auditCaptor.getValue().targetId()).isEqualTo(88L);
  }

  @Test
  @DisplayName(
      "Giải quyết sự cố đã được khắc phục trước đó -> ném 409 MACHINE_INCIDENT_ALREADY_RESOLVED")
  void resolveIncident_alreadyResolved_throwsConflict() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);

    Machine p1 = new Machine();
    ReflectionTestUtils.setField(p1, "id", 1L);
    p1.setFilmLabId(10L);
    p1.setMachineCode("P1");
    p1.setStatus(MachineStatus.ACTIVE);
    when(machineRepository.findByIdAndFilmLabId(1L, 10L)).thenReturn(Optional.of(p1));

    MachineIncident incident = new MachineIncident();
    ReflectionTestUtils.setField(incident, "id", 88L);
    incident.setMachineId(1L);
    incident.setResolvedAt(Instant.parse("2026-11-18T10:00:00Z"));
    when(machineIncidentRepository.findByIdAndMachineId(88L, 1L)).thenReturn(Optional.of(incident));

    assertThatThrownBy(() -> machineService.resolveIncident(100L, 1L, 88L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MACHINE_INCIDENT_ALREADY_RESOLVED));

    verify(machineIncidentRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Giải quyết sự cố không tồn tại hoặc không thuộc máy -> ném 404 MACHINE_INCIDENT_NOT_FOUND")
  void resolveIncident_notFound_throwsNotFound() {
    FilmLab lab = createLab(10L, 100L);
    when(labAccessService.requireOwnedLab(100L)).thenReturn(lab);

    Machine p1 = new Machine();
    ReflectionTestUtils.setField(p1, "id", 1L);
    p1.setFilmLabId(10L);
    p1.setMachineCode("P1");
    p1.setStatus(MachineStatus.MAINTENANCE);
    when(machineRepository.findByIdAndFilmLabId(1L, 10L)).thenReturn(Optional.of(p1));
    when(machineIncidentRepository.findByIdAndMachineId(999L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> machineService.resolveIncident(100L, 1L, 999L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MACHINE_INCIDENT_NOT_FOUND));

    verify(machineIncidentRepository, never()).saveAndFlush(any());
  }

  private ServiceOrder createInProcessingOrder(
      Long orderId, Long labId, ServiceTier tier, Instant promisedReturnAt) {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", orderId);
    order.setFilmLabId(labId);
    order.setServiceTier(tier);
    order.setStatus(ServiceOrderStatus.IN_PROCESSING);
    order.setPromisedReturnAt(promisedReturnAt);
    return order;
  }

  private OrderCapacityAllocation createAllocation(
      Long orderId, Long labId, LocalDate workDate, ServiceTier tier, Instant orderReceivedAt) {
    OrderCapacityAllocation allocation = new OrderCapacityAllocation();
    allocation.setServiceOrderId(orderId);
    allocation.setFilmLabId(labId);
    allocation.setWorkDate(workDate);
    allocation.setFilmProcessId(1L);
    allocation.setFilmFormatId(1L);
    allocation.setServiceTier(tier);
    allocation.setRollCount((short) 3);
    allocation.setOrderReceivedAt(orderReceivedAt);
    return allocation;
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
