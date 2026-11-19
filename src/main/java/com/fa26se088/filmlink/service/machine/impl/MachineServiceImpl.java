package com.fa26se088.filmlink.service.machine.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.machine.CapacityProposal;
import com.fa26se088.filmlink.dto.machine.CapacityRequest;
import com.fa26se088.filmlink.dto.machine.DayAllocation;
import com.fa26se088.filmlink.dto.machine.RollGroup;
import com.fa26se088.filmlink.dto.machine.request.CreateMachineRequest;
import com.fa26se088.filmlink.dto.machine.request.MachineWorkingHourRequest;
import com.fa26se088.filmlink.dto.machine.request.ReportMachineIncidentRequest;
import com.fa26se088.filmlink.dto.machine.request.UpdateMachineRequest;
import com.fa26se088.filmlink.dto.machine.request.UpdateSchedulingSettingsRequest;
import com.fa26se088.filmlink.dto.machine.response.MachineIncidentResponse;
import com.fa26se088.filmlink.dto.machine.response.MachineResponse;
import com.fa26se088.filmlink.dto.machine.response.ScheduleChangeSummaryResponse;
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
import com.fa26se088.filmlink.service.machine.MachineService;
import com.fa26se088.filmlink.service.machine.event.PromisedDateChangedEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai nghiệp vụ quản lý thiết bị máy tráng, máy scan, công suất ngày và
 * sự cố thiết bị cho
 * Film Lab Owner (UC68, UC70, UC71, FA26-72, FA26-76, FA26-77).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MachineServiceImpl implements MachineService {

  private static final int DAYS_PER_WEEK = 7;
  private static final int DEFAULT_FRAMES_PER_ROLL = 36;
  private static final Duration CUSTOMER_RESPONSE_WINDOW = Duration.ofHours(48);

  private final MachineRepository machineRepository;
  private final FilmProcessRepository filmProcessRepository;
  private final FilmFormatRepository filmFormatRepository;
  private final LabAccessService labAccessService;
  private final AuditLogService auditLogService;
  private final MachineMapper machineMapper;
  private final MachineIncidentRepository machineIncidentRepository;
  private final ScheduleChangeRepository scheduleChangeRepository;
  private final OrderCapacityAllocationRepository orderCapacityAllocationRepository;
  private final CapacitySchedulingService capacitySchedulingService;
  private final ServiceOrderRepository serviceOrderRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional(readOnly = true)
  public List<MachineResponse> getMyMachines(Long ownerAccountId) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    return machineRepository.findByFilmLabIdOrderByIdAsc(lab.getId()).stream()
        .map(machineMapper::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public MachineResponse createMachine(Long ownerAccountId, CreateMachineRequest request) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);

    String trimmedCode = request.machineCode().trim();
    if (trimmedCode.length() < 2 || trimmedCode.length() > 50) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Mã máy phải từ 2 đến 50 ký tự sau khi cắt khoảng trắng.");
    }

    if (machineRepository.existsByFilmLabIdAndMachineCodeIgnoreCase(lab.getId(), trimmedCode)) {
      throw new BusinessException(
          ErrorCode.MACHINE_CODE_DUPLICATE,
          "Mã máy " + trimmedCode + " đã tồn tại trong phòng lab của bạn.");
    }

    String modelName = normalizeModelName(request.modelName());
    Set<FilmProcess> filmProcesses = resolveProcessesForType(request.machineType(), request.filmProcessIds());

    if (request.filmFormatIds() == null || request.filmFormatIds().isEmpty()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Phải chọn ít nhất một khổ phim hỗ trợ.");
    }
    Set<FilmFormat> filmFormats = validateActiveFormats(request.filmFormatIds());

    validateWorkingHours(request.workingHours());

    Machine machine = new Machine();
    machine.setFilmLabId(lab.getId());
    machine.setMachineCode(trimmedCode);
    machine.setModelName(modelName);
    machine.setMachineType(request.machineType());
    machine.setCapacityPerHour(request.capacityPerHour());
    machine.setStatus(MachineStatus.ACTIVE);
    machine.setFilmProcesses(filmProcesses);
    machine.setFilmFormats(filmFormats);

    for (MachineWorkingHourRequest item : request.workingHours()) {
      MachineWorkingHour wh = new MachineWorkingHour();
      wh.setDayOfWeek(item.dayOfWeek());
      boolean closed = Boolean.TRUE.equals(item.closed());
      wh.setClosed(closed);
      wh.setOpensAt(closed ? null : item.opensAt());
      wh.setClosesAt(closed ? null : item.closesAt());
      machine.addWorkingHour(wh);
    }

    try {
      machine = machineRepository.saveAndFlush(machine);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(
          ErrorCode.MACHINE_CODE_DUPLICATE,
          "Mã máy " + trimmedCode + " đã tồn tại trong phòng lab của bạn.");
    }

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.MACHINE_CREATED,
            AuditTargetType.MACHINE,
            machine.getId(),
            "Khai báo máy mới: " + machine.getMachineCode() + " (" + machine.getMachineType() + ")",
            null,
            toSnapshot(machine)));

    log.info(
        "Khai báo máy thành công: id={}, labId={}, code={}, type={}",
        machine.getId(),
        lab.getId(),
        machine.getMachineCode(),
        machine.getMachineType());

    return machineMapper.toResponse(machine);
  }

  @Override
  @Transactional
  public MachineResponse updateMachine(
      Long ownerAccountId, Long machineId, UpdateMachineRequest request) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    Machine machine = machineRepository
        .findByIdAndFilmLabId(machineId, lab.getId())
        .orElseThrow(
            () -> new BusinessException(
                ErrorCode.MACHINE_NOT_FOUND,
                "Không tìm thấy máy hoặc máy không thuộc phòng lab của bạn."));

    String modelName = normalizeModelName(request.modelName());
    Set<FilmProcess> filmProcesses = resolveProcessesForType(machine.getMachineType(), request.filmProcessIds());

    if (request.filmFormatIds() == null || request.filmFormatIds().isEmpty()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Phải chọn ít nhất một khổ phim hỗ trợ.");
    }
    Set<FilmFormat> filmFormats = validateActiveFormats(request.filmFormatIds());

    validateWorkingHours(request.workingHours());

    Map<String, Object> beforeSnapshot = toSnapshot(machine);

    machine.setModelName(modelName);
    machine.setCapacityPerHour(request.capacityPerHour());
    machine.setFilmProcesses(filmProcesses);
    machine.setFilmFormats(filmFormats);

    // Upsert giờ hoạt động theo dayOfWeek để tránh xung đột unique constraint
    // (machine_id, day_of_week)
    Map<Short, MachineWorkingHour> existingByDay = machine.getWorkingHours().stream()
        .collect(Collectors.toMap(MachineWorkingHour::getDayOfWeek, Function.identity()));
    Set<Short> requestedDays = new HashSet<>();
    for (MachineWorkingHourRequest item : request.workingHours()) {
      requestedDays.add(item.dayOfWeek());
      boolean closed = Boolean.TRUE.equals(item.closed());
      MachineWorkingHour wh = existingByDay.get(item.dayOfWeek());
      if (wh == null) {
        wh = new MachineWorkingHour();
        wh.setDayOfWeek(item.dayOfWeek());
        machine.addWorkingHour(wh);
      }
      wh.setClosed(closed);
      wh.setOpensAt(closed ? null : item.opensAt());
      wh.setClosesAt(closed ? null : item.closesAt());
    }
    machine.getWorkingHours().removeIf(wh -> !requestedDays.contains(wh.getDayOfWeek()));

    machine = machineRepository.saveAndFlush(machine);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.MACHINE_UPDATED,
            AuditTargetType.MACHINE,
            machine.getId(),
            "Cập nhật cấu hình máy: "
                + machine.getMachineCode()
                + " ("
                + machine.getMachineType()
                + ")",
            beforeSnapshot,
            toSnapshot(machine)));

    log.info(
        "Cập nhật máy thành công: id={}, labId={}, code={}, capacityPerHour={}",
        machine.getId(),
        lab.getId(),
        machine.getMachineCode(),
        machine.getCapacityPerHour());

    return machineMapper.toResponse(machine);
  }

  @Override
  @Transactional
  public SchedulingSettingsResponse updateSchedulingSettings(
      Long ownerAccountId, UpdateSchedulingSettingsRequest request) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);

    Map<String, Object> beforeSnapshot = toSchedulingSettingsSnapshot(lab);

    lab.setQcBufferHours(request.qcBufferHours());
    lab.setExpressDailyCapPercent(request.expressDailyCapPercent());
    lab.setExpressCommitmentWorkingDays(request.expressCommitmentWorkingDays());

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.LAB_SCHEDULING_SETTINGS_UPDATED,
            AuditTargetType.FILM_LAB,
            lab.getId(),
            "Cập nhật tham số lập lịch tự động của Film Lab #" + lab.getId(),
            beforeSnapshot,
            toSchedulingSettingsSnapshot(lab)));

    log.info(
        "Cập nhật tham số lập lịch labId={}: qcBufferHours={}, expressCapPercent={}, expressDays={}",
        lab.getId(),
        lab.getQcBufferHours(),
        lab.getExpressDailyCapPercent(),
        lab.getExpressCommitmentWorkingDays());

    return new SchedulingSettingsResponse(
        lab.getId(),
        lab.getQcBufferHours(),
        lab.getExpressDailyCapPercent(),
        lab.getExpressCommitmentWorkingDays());
  }

  @Override
  @Transactional
  public MachineIncidentResponse reportIncident(
      Long ownerAccountId, Long machineId, ReportMachineIncidentRequest request) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    Machine machine = requireOwnedMachine(machineId, lab.getId());

    String description = normalizeModelName(request.description());
    validateIncidentRequest(request, description);

    MachineStatus statusBefore = machine.getStatus();
    MachineStatus statusAfter = request.expectedAvailableAt() != null ? MachineStatus.MAINTENANCE : MachineStatus.DOWN;

    MachineIncident incident = new MachineIncident();
    incident.setMachineId(machine.getId());
    incident.setCause(request.cause());
    incident.setDescription(description);
    incident.setUnavailableFrom(request.unavailableFrom());
    incident.setExpectedAvailableAt(request.expectedAvailableAt());
    incident.setMachineStatusAfter(statusAfter);
    incident.setReportedByAccountId(ownerAccountId);
    incident = machineIncidentRepository.saveAndFlush(incident);

    machine.setStatus(statusAfter);
    machineRepository.saveAndFlush(machine);

    RescheduleResult rescheduleResult = rescheduleAffectedOrders(lab.getId(), incident);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.MACHINE_INCIDENT_REPORTED,
            AuditTargetType.MACHINE_INCIDENT,
            incident.getId(),
            "Báo cáo sự cố máy " + machine.getMachineCode() + " (" + incident.getCause() + ")",
            toIncidentBeforeSnapshot(machine.getMachineCode(), statusBefore),
            toIncidentAfterSnapshot(incident, rescheduleResult)));

    log.info(
        "Khai báo sự cố máy thành công: incidentId={}, machineId={}, statusAfter={}, affected={}, "
            + "scheduleChanges={}",
        incident.getId(),
        machine.getId(),
        statusAfter,
        rescheduleResult.affectedOrderCount(),
        rescheduleResult.scheduleChanges().size());

    return toIncidentResponse(
        incident,
        machine,
        incident.getMachineStatusAfter(),
        rescheduleResult.affectedOrderCount(),
        rescheduleResult.scheduleChanges());
  }

  @Override
  @Transactional
  public MachineIncidentResponse resolveIncident(
      Long ownerAccountId, Long machineId, Long incidentId) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    Machine machine = requireOwnedMachine(machineId, lab.getId());

    MachineIncident incident =
        machineIncidentRepository
            .findByIdAndMachineId(incidentId, machine.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MACHINE_INCIDENT_NOT_FOUND,
                        "Không tìm thấy bản ghi sự cố của máy này."));

    if (incident.getResolvedAt() != null) {
      throw new BusinessException(
          ErrorCode.MACHINE_INCIDENT_ALREADY_RESOLVED,
          "Sự cố máy #" + incidentId + " đã được đánh dấu khắc phục trước đó.");
    }

    final MachineStatus statusBefore = machine.getStatus();
    incident.setResolvedAt(Instant.now());
    incident = machineIncidentRepository.saveAndFlush(incident);

    MachineStatus resolvedMachineStatus =
        resolveMachineStatusFromOpenIncidents(
            machineIncidentRepository.findByMachineIdAndResolvedAtIsNull(machine.getId()));
    machine.setStatus(resolvedMachineStatus);
    machine = machineRepository.saveAndFlush(machine);

    List<ScheduleChangeSummaryResponse> scheduleChanges =
        scheduleChangeRepository.findByMachineIncidentIdOrderByIdAsc(incident.getId()).stream()
            .map(MachineServiceImpl::toScheduleChangeSummary)
            .toList();

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.MACHINE_INCIDENT_RESOLVED,
            AuditTargetType.MACHINE_INCIDENT,
            incident.getId(),
            "Khôi phục máy "
                + machine.getMachineCode()
                + " sau sự cố #"
                + incident.getId()
                + " -> "
                + machine.getStatus(),
            toResolvedIncidentSnapshot(machine.getMachineCode(), statusBefore, null),
            toResolvedIncidentSnapshot(
                machine.getMachineCode(), machine.getStatus(), incident.getResolvedAt())));

    log.info(
        "Giải quyết sự cố máy thành công: incidentId={}, machineId={}, machineStatus={}",
        incident.getId(),
        machine.getId(),
        machine.getStatus());

    return toIncidentResponse(
        incident, machine, machine.getStatus(), scheduleChanges.size(), scheduleChanges);
  }

  private Machine requireOwnedMachine(Long machineId, Long labId) {
    return machineRepository
        .findByIdAndFilmLabId(machineId, labId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.MACHINE_NOT_FOUND,
                    "Không tìm thấy máy hoặc máy không thuộc phòng lab của bạn."));
  }

  private static MachineStatus resolveMachineStatusFromOpenIncidents(
      List<MachineIncident> openIncidents) {
    if (openIncidents == null || openIncidents.isEmpty()) {
      return MachineStatus.ACTIVE;
    }
    boolean hasUnboundedDowntime =
        openIncidents.stream().anyMatch(open -> open.getExpectedAvailableAt() == null);
    return hasUnboundedDowntime ? MachineStatus.DOWN : MachineStatus.MAINTENANCE;
  }

  private static MachineIncidentResponse toIncidentResponse(
      MachineIncident incident,
      Machine machine,
      MachineStatus machineStatusAfter,
      int affectedOrderCount,
      List<ScheduleChangeSummaryResponse> scheduleChanges) {
    return new MachineIncidentResponse(
        incident.getId(),
        machine.getId(),
        machine.getMachineCode(),
        incident.getCause(),
        incident.getDescription(),
        incident.getUnavailableFrom(),
        incident.getExpectedAvailableAt(),
        machineStatusAfter,
        incident.getReportedByAccountId(),
        incident.getResolvedAt(),
        affectedOrderCount,
        scheduleChanges,
        incident.getCreatedAt());
  }

  private static ScheduleChangeSummaryResponse toScheduleChangeSummary(ScheduleChange change) {
    return new ScheduleChangeSummaryResponse(
        change.getId(),
        change.getServiceOrderId(),
        change.getStepApplied(),
        change.getOldPromisedAt(),
        change.getNewPromisedAt(),
        change.isRequiresResponse(),
        change.getResponseDueAt());
  }

  private static Map<String, Object> toResolvedIncidentSnapshot(
      String machineCode, MachineStatus machineStatus, Instant resolvedAt) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("machineCode", machineCode);
    snapshot.put("machineStatus", machineStatus.name());
    snapshot.put("resolvedAt", resolvedAt != null ? resolvedAt.toString() : null);
    return snapshot;
  }

  private void validateIncidentRequest(
      ReportMachineIncidentRequest request, String normalizedDescription) {
    if (request.cause() == MachineIncidentCause.OTHER && normalizedDescription == null) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Phải nhập mô tả chi tiết khi chọn nguyên nhân sự cố là OTHER.");
    }
    if (request.expectedAvailableAt() != null
        && !request.expectedAvailableAt().isAfter(request.unavailableFrom())) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Thời điểm dự kiến khắc phục xong phải sau thời điểm bắt đầu sự cố.");
    }
  }

  private RescheduleResult rescheduleAffectedOrders(Long labId, MachineIncident incident) {
    LocalDate fromDate = incident.getUnavailableFrom().atZone(CapacitySchedulingEngine.VIETNAM_ZONE).toLocalDate();
    List<OrderCapacityAllocation> affectedSlice = orderCapacityAllocationRepository
        .findByFilmLabIdAndWorkDateGreaterThanEqual(
            labId, fromDate);
    if (affectedSlice.isEmpty()) {
      return new RescheduleResult(0, List.of());
    }

    Map<Long, List<OrderCapacityAllocation>> sliceByOrderId = new LinkedHashMap<>();
    for (OrderCapacityAllocation allocation : affectedSlice) {
      sliceByOrderId
          .computeIfAbsent(allocation.getServiceOrderId(), ignored -> new ArrayList<>())
          .add(allocation);
    }

    List<ServiceOrder> candidateOrders = serviceOrderRepository.findAllById(sliceByOrderId.keySet());
    List<AffectedOrderPlan> plans = new ArrayList<>();
    for (ServiceOrder order : candidateOrders) {
      if (order.getStatus() != ServiceOrderStatus.IN_PROCESSING
          || order.getPromisedReturnAt() == null) {
        continue;
      }
      List<OrderCapacityAllocation> currentAllocations = orderCapacityAllocationRepository
          .findByServiceOrderIdOrderByWorkDateAsc(order.getId());
      if (currentAllocations == null || currentAllocations.isEmpty()) {
        currentAllocations = sliceByOrderId.getOrDefault(order.getId(), List.of());
      }
      if (currentAllocations.isEmpty()) {
        continue;
      }
      plans.add(toAffectedOrderPlan(order, currentAllocations));
    }

    if (plans.isEmpty()) {
      return new RescheduleResult(0, List.of());
    }

    // BR-61: ưu tiên đơn EXPRESS trước, sau đó STANDARD; cùng hạng sắp FIFO theo
    // orderReceivedAt
    plans.sort(
        Comparator.comparingInt(
            (AffectedOrderPlan p) -> p.order().getServiceTier() == ServiceTier.EXPRESS ? 0 : 1)
            .thenComparing(AffectedOrderPlan::receivedAt)
            .thenComparing(p -> p.order().getId()));

    for (AffectedOrderPlan plan : plans) {
      orderCapacityAllocationRepository.deleteByServiceOrderId(plan.order().getId());
    }
    orderCapacityAllocationRepository.flush();

    List<ScheduleChangeSummaryResponse> scheduleChanges = new ArrayList<>();
    for (AffectedOrderPlan plan : plans) {
      ScheduleChangeSummaryResponse change = rescheduleSingleOrder(labId, incident, plan);
      if (change != null) {
        scheduleChanges.add(change);
      }
    }

    return new RescheduleResult(plans.size(), scheduleChanges);
  }

  private ScheduleChangeSummaryResponse rescheduleSingleOrder(
      Long labId, MachineIncident incident, AffectedOrderPlan plan) {
    ServiceOrder order = plan.order();
    CapacityRequest capacityRequest = new CapacityRequest(
        order.getId(), labId, order.getServiceTier(), plan.receivedAt(), plan.rollGroups());
    CapacityProposal proposal = capacitySchedulingService.propose(capacityRequest);

    if (!proposal.feasible()) {
      orderCapacityAllocationRepository.saveAllAndFlush(plan.originalCopies());
      return null;
    }

    List<OrderCapacityAllocation> newAllocations = buildAllocations(order, labId, plan.receivedAt(),
        proposal.allocations());
    if (!newAllocations.isEmpty()) {
      orderCapacityAllocationRepository.saveAllAndFlush(newAllocations);
    }

    Instant oldPromisedAt = order.getPromisedReturnAt();
    Instant newPromisedAt = proposal.promisedReturnAt();
    if (newPromisedAt == null || newPromisedAt.equals(oldPromisedAt)) {
      return null;
    }

    boolean requiresResponse = proposal.tierCommitmentAt() != null
        && newPromisedAt.isAfter(proposal.tierCommitmentAt());
    Instant responseDueAt = requiresResponse ? Instant.now().plus(CUSTOMER_RESPONSE_WINDOW) : null;

    ScheduleChange change = new ScheduleChange();
    change.setServiceOrderId(order.getId());
    change.setMachineIncidentId(incident.getId());
    change.setStepApplied(ScheduleChangeStep.RESCHEDULED);
    change.setOldPromisedAt(oldPromisedAt);
    change.setNewPromisedAt(newPromisedAt);
    change.setRequiresResponse(requiresResponse);
    change.setResponseDueAt(responseDueAt);
    change = scheduleChangeRepository.saveAndFlush(change);

    eventPublisher.publishEvent(
        new PromisedDateChangedEvent(
            order.getId(), incident.getId(), oldPromisedAt, newPromisedAt, requiresResponse));

    return toScheduleChangeSummary(change);
  }

  private static AffectedOrderPlan toAffectedOrderPlan(
      ServiceOrder order, List<OrderCapacityAllocation> currentAllocations) {
    Instant receivedAt = currentAllocations.getFirst().getOrderReceivedAt();
    if (receivedAt == null) {
      receivedAt = order.getPriceLockedAt() != null ? order.getPriceLockedAt() : order.getCreatedAt();
    }

    Map<List<Long>, Integer> rollCountsByGroup = new LinkedHashMap<>();
    List<OrderCapacityAllocation> copies = new ArrayList<>(currentAllocations.size());
    for (OrderCapacityAllocation existing : currentAllocations) {
      rollCountsByGroup.merge(
          List.of(existing.getFilmProcessId(), existing.getFilmFormatId()),
          (int) existing.getRollCount(),
          Integer::sum);
      copies.add(copyAllocation(existing));
    }

    List<RollGroup> rollGroups = rollCountsByGroup.entrySet().stream()
        .map(
            entry -> new RollGroup(
                entry.getKey().get(0),
                entry.getKey().get(1),
                entry.getValue(),
                DEFAULT_FRAMES_PER_ROLL))
        .toList();

    return new AffectedOrderPlan(order, receivedAt, rollGroups, copies);
  }

  private static OrderCapacityAllocation copyAllocation(OrderCapacityAllocation source) {
    OrderCapacityAllocation copy = new OrderCapacityAllocation();
    copy.setServiceOrderId(source.getServiceOrderId());
    copy.setFilmLabId(source.getFilmLabId());
    copy.setWorkDate(source.getWorkDate());
    copy.setFilmProcessId(source.getFilmProcessId());
    copy.setFilmFormatId(source.getFilmFormatId());
    copy.setServiceTier(source.getServiceTier());
    copy.setRollCount(source.getRollCount());
    copy.setOrderReceivedAt(source.getOrderReceivedAt());
    return copy;
  }

  private static List<OrderCapacityAllocation> buildAllocations(
      ServiceOrder order, Long labId, Instant receivedAt, List<DayAllocation> dayAllocations) {
    List<OrderCapacityAllocation> entities = new ArrayList<>(dayAllocations.size());
    for (DayAllocation item : dayAllocations) {
      OrderCapacityAllocation allocation = new OrderCapacityAllocation();
      allocation.setServiceOrderId(order.getId());
      allocation.setFilmLabId(labId);
      allocation.setWorkDate(item.workDate());
      allocation.setFilmProcessId(item.filmProcessId());
      allocation.setFilmFormatId(item.filmFormatId());
      allocation.setServiceTier(order.getServiceTier());
      allocation.setRollCount((short) item.rollCount());
      allocation.setOrderReceivedAt(receivedAt);
      entities.add(allocation);
    }
    return entities;
  }

  private static Map<String, Object> toIncidentBeforeSnapshot(
      String machineCode, MachineStatus statusBefore) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("machineCode", machineCode);
    snapshot.put("machineStatus", statusBefore.name());
    return snapshot;
  }

  private static Map<String, Object> toIncidentAfterSnapshot(
      MachineIncident incident, RescheduleResult rescheduleResult) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("cause", incident.getCause().name());
    snapshot.put("unavailableFrom", incident.getUnavailableFrom().toString());
    snapshot.put(
        "expectedAvailableAt",
        incident.getExpectedAvailableAt() != null
            ? incident.getExpectedAvailableAt().toString()
            : null);
    snapshot.put("machineStatus", incident.getMachineStatusAfter().name());
    snapshot.put("affectedOrderCount", rescheduleResult.affectedOrderCount());
    snapshot.put("scheduleChangeCount", rescheduleResult.scheduleChanges().size());
    return snapshot;
  }

  private record AffectedOrderPlan(
      ServiceOrder order,
      Instant receivedAt,
      List<RollGroup> rollGroups,
      List<OrderCapacityAllocation> originalCopies) {
  }

  private record RescheduleResult(
      int affectedOrderCount, List<ScheduleChangeSummaryResponse> scheduleChanges) {
  }

  private Set<FilmProcess> resolveProcessesForType(
      MachineType machineType, Set<Long> filmProcessIds) {
    if (machineType == MachineType.PROCESSOR) {
      if (filmProcessIds == null || filmProcessIds.isEmpty()) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR,
            "Máy tráng (PROCESSOR) bắt buộc phải chọn ít nhất một quy trình tráng.");
      }
      return validateActiveProcesses(filmProcessIds);
    }
    if (filmProcessIds != null && !filmProcessIds.isEmpty()) {
      return validateActiveProcesses(filmProcessIds);
    }
    return Collections.emptySet();
  }

  private Set<FilmProcess> validateActiveProcesses(Set<Long> ids) {
    List<FilmProcess> list = filmProcessRepository.findAllById(ids);
    if (list.size() != ids.size()
        || list.stream().anyMatch(p -> p.getStatus() != CatalogStatus.ACTIVE)) {
      throw new BusinessException(
          ErrorCode.MACHINE_MASTERDATA_INACTIVE,
          "Một hoặc nhiều quy trình tráng không tồn tại hoặc không ở trạng thái ACTIVE.");
    }
    return new HashSet<>(list);
  }

  private Set<FilmFormat> validateActiveFormats(Set<Long> ids) {
    List<FilmFormat> list = filmFormatRepository.findAllById(ids);
    if (list.size() != ids.size()
        || list.stream().anyMatch(f -> f.getStatus() != CatalogStatus.ACTIVE)) {
      throw new BusinessException(
          ErrorCode.MACHINE_MASTERDATA_INACTIVE,
          "Một hoặc nhiều khổ phim không tồn tại hoặc không ở trạng thái ACTIVE.");
    }
    return new HashSet<>(list);
  }

  private static String normalizeModelName(String modelName) {
    return (modelName != null && !modelName.isBlank()) ? modelName.trim() : null;
  }

  private static Map<String, Object> toSnapshot(Machine machine) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("machineCode", machine.getMachineCode());
    snapshot.put("machineType", machine.getMachineType().name());
    snapshot.put("modelName", machine.getModelName());
    snapshot.put("capacityPerHour", machine.getCapacityPerHour());
    snapshot.put("status", machine.getStatus().name());
    return snapshot;
  }

  private static Map<String, Object> toSchedulingSettingsSnapshot(FilmLab lab) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("qcBufferHours", lab.getQcBufferHours());
    snapshot.put("expressDailyCapPercent", lab.getExpressDailyCapPercent());
    snapshot.put("expressCommitmentWorkingDays", lab.getExpressCommitmentWorkingDays());
    return snapshot;
  }

  private static void validateWorkingHours(List<MachineWorkingHourRequest> workingHours) {
    if (workingHours == null || workingHours.isEmpty() || workingHours.size() > DAYS_PER_WEEK) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Giờ hoạt động phải có từ 1 đến 7 ngày trong tuần.");
    }
    Set<Short> days = new HashSet<>();
    for (MachineWorkingHourRequest item : workingHours) {
      if (item.dayOfWeek() == null || item.dayOfWeek() < 1 || item.dayOfWeek() > DAYS_PER_WEEK) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR, "Thứ trong tuần phải từ 1 (Thứ Hai) đến 7 (Chủ nhật).");
      }
      if (!days.add(item.dayOfWeek())) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR, "Ngày thứ " + item.dayOfWeek() + " bị khai báo trùng.");
      }
      if (Boolean.TRUE.equals(item.closed())) {
        if (item.opensAt() != null || item.closesAt() != null) {
          throw new BusinessException(
              ErrorCode.VALIDATION_ERROR,
              "Ngày thứ " + item.dayOfWeek() + " là ngày nghỉ, không nhập giờ hoạt động.");
        }
      } else if (item.opensAt() == null || item.closesAt() == null) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR,
            "Ngày thứ " + item.dayOfWeek() + " phải có cả giờ bắt đầu và giờ kết thúc.");
      } else if (!item.opensAt().isBefore(item.closesAt())) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR,
            "Ngày thứ " + item.dayOfWeek() + ": giờ bắt đầu phải trước giờ kết thúc.");
      }
    }
  }
}
