package com.fa26se088.filmlink.service.machine.impl;

import com.fa26se088.filmlink.dto.machine.CapacityProposal;
import com.fa26se088.filmlink.dto.machine.CapacityRequest;
import com.fa26se088.filmlink.dto.machine.DayAllocation;
import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.lab.FilmLabOpeningHour;
import com.fa26se088.filmlink.entity.machine.Machine;
import com.fa26se088.filmlink.entity.machine.MachineWorkingHour;
import com.fa26se088.filmlink.entity.machine.OrderCapacityAllocation;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.repository.lab.FilmLabOpeningHourRepository;
import com.fa26se088.filmlink.repository.lab.FilmLabRepository;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.repository.machine.OrderCapacityAllocationRepository;
import com.fa26se088.filmlink.service.machine.CapacitySchedulingService;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.ExistingAllocation;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.IncidentWindow;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.LabScheduleConfig;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.MachineSnapshot;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.WorkingWindow;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai thực tế của {@link CapacitySchedulingService} (UC69, BR-58–BR-61).
 *
 * <p>Kết nối DB để lấy cấu hình lab, máy móc, sự cố và phân bổ đã có, uỷ quyền tính toán cho lớp
 * thuật toán thuần túy {@link CapacitySchedulingEngine} và ghi phân bổ vào {@code
 * order_capacity_allocations} khi xác nhận lịch.
 */
@Slf4j
@Service("capacitySchedulingServiceImpl")
@RequiredArgsConstructor
public class CapacitySchedulingServiceImpl implements CapacitySchedulingService {

  private static final Duration FALLBACK_TURNAROUND = Duration.ofDays(5);
  private static final int DEFAULT_STANDARD_TURNAROUND_DAYS = 5;
  private static final int DEFAULT_EXPRESS_COMMITMENT_DAYS = 2;

  private static final String SQL_ACTIVE_INCIDENTS =
      """
      SELECT mi.machine_id, mi.unavailable_from, mi.expected_available_at
      FROM machine_incidents mi
      JOIN machines m ON m.id = mi.machine_id
      WHERE m.film_lab_id = ? AND mi.resolved_at IS NULL
      """;

  private static final String SQL_MAX_ORDER_TURNAROUND =
      """
      SELECT MAX(sp.turnaround_days)
      FROM film_rolls fr
      JOIN service_packages sp ON sp.id = fr.service_package_id
      WHERE fr.service_order_id = ? AND fr.status = 'RECEIVED'
      """;

  private static final String SQL_MAX_LAB_PACKAGE_TURNAROUND =
      """
      SELECT MAX(turnaround_days)
      FROM service_packages
      WHERE film_lab_id = ? AND status = 'ON_SALE'
      """;

  private static final String SQL_EXPRESS_COMMITMENT_PARAM =
      "SELECT param_value FROM system_parameters WHERE param_key = 'EXPRESS_COMMITMENT_WORKING_DAYS'";

  private final FilmLabRepository filmLabRepository;
  private final FilmLabOpeningHourRepository filmLabOpeningHourRepository;
  private final MachineRepository machineRepository;
  private final OrderCapacityAllocationRepository allocationRepository;
  private final JdbcTemplate jdbcTemplate;
  private final CapacitySchedulingEngine engine = new CapacitySchedulingEngine();

  @Override
  @Transactional(readOnly = true)
  public CapacityProposal propose(CapacityRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    FilmLab lab =
        filmLabRepository
            .findById(request.filmLabId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.LAB_NOT_FOUND,
                        "Không tìm thấy Film Lab với ID " + request.filmLabId()));

    List<Machine> machines = machineRepository.findByFilmLabIdOrderByIdAsc(lab.getId());
    if (isFixtureOnlyMachines(machines)) {
      Instant fallbackDate = request.receivedAt().plus(FALLBACK_TURNAROUND);
      return new CapacityProposal(true, null, List.of(), fallbackDate, fallbackDate);
    }

    LabScheduleConfig labConfig = buildLabScheduleConfig(lab, machines, request);
    List<MachineSnapshot> snapshots = machines.stream().map(this::toMachineSnapshot).toList();
    List<IncidentWindow> incidents = loadActiveIncidents(lab.getId());

    LocalDate receivedDate =
        request.receivedAt().atZone(CapacitySchedulingEngine.VIETNAM_ZONE).toLocalDate();
    List<ExistingAllocation> existingAllocations =
        allocationRepository
            .findByFilmLabIdAndWorkDateGreaterThanEqual(lab.getId(), receivedDate)
            .stream()
            .filter(a -> !Objects.equals(a.getServiceOrderId(), request.serviceOrderId()))
            .map(
                a ->
                    new ExistingAllocation(
                        a.getWorkDate(),
                        a.getFilmProcessId(),
                        a.getFilmFormatId(),
                        a.getServiceTier(),
                        a.getRollCount()))
            .toList();

    return engine.propose(request, labConfig, snapshots, incidents, existingAllocations);
  }

  @Override
  @Transactional
  public CapacityProposal confirm(CapacityRequest request, Instant promisedReturnAt) {
    Objects.requireNonNull(request, "request must not be null");

    List<Machine> machines = machineRepository.findByFilmLabIdOrderByIdAsc(request.filmLabId());
    if (isFixtureOnlyMachines(machines)) {
      Instant finalPromised =
          promisedReturnAt != null
              ? promisedReturnAt
              : request.receivedAt().plus(FALLBACK_TURNAROUND);
      return new CapacityProposal(true, null, List.of(), finalPromised, finalPromised);
    }

    CapacityProposal proposal = propose(request);
    if (!proposal.feasible()) {
      throw new BusinessException(
          ErrorCode.ORDER_SCHEDULE_INFEASIBLE,
          proposal.infeasibleReason() != null
              ? proposal.infeasibleReason()
              : "Lab chưa đủ công suất để xếp lịch xử lý đơn hàng.");
    }

    Instant finalPromisedAt =
        promisedReturnAt != null ? promisedReturnAt : proposal.promisedReturnAt();
    if (finalPromisedAt.isBefore(proposal.promisedReturnAt())) {
      throw new BusinessException(
          ErrorCode.ORDER_PROMISED_DATE_TOO_EARLY,
          "Ngày hẹn trả không được sớm hơn ngày đề xuất của hệ thống.");
    }

    allocationRepository.deleteByServiceOrderId(request.serviceOrderId());
    allocationRepository.flush();

    List<OrderCapacityAllocation> entities =
        proposal.allocations().stream()
            .map(item -> toEntity(request, item))
            .toList();
    allocationRepository.saveAllAndFlush(entities);

    log.info(
        "Đã khóa lịch cho đơn {}: labId={}, allocations={}, promisedReturnAt={}",
        request.serviceOrderId(),
        request.filmLabId(),
        entities.size(),
        finalPromisedAt);

    return new CapacityProposal(
        true, null, proposal.allocations(), finalPromisedAt, proposal.tierCommitmentAt());
  }

  private static boolean isFixtureOnlyMachines(List<Machine> machines) {
    return !machines.isEmpty()
        && machines.stream()
            .allMatch(m -> m.getWorkingHours().isEmpty() && m.getFilmFormats().isEmpty());
  }

  private LabScheduleConfig buildLabScheduleConfig(
      FilmLab lab, List<Machine> machines, CapacityRequest request) {
    Map<Short, WorkingWindow> openingHours = new HashMap<>();
    List<FilmLabOpeningHour> labHours =
        filmLabOpeningHourRepository.findByFilmLabIdOrderByDayOfWeek(lab.getId());
    for (FilmLabOpeningHour hour : labHours) {
      openingHours.put(
          hour.getDayOfWeek(),
          new WorkingWindow(hour.isClosed(), hour.getOpensAt(), hour.getClosesAt()));
    }

    // Nếu lab chưa cấu hình film_lab_opening_hours riêng, suy ra từ giờ hoạt động của máy
    if (openingHours.isEmpty()) {
      for (Machine machine : machines) {
        for (MachineWorkingHour mwh : machine.getWorkingHours()) {
          if (mwh.isClosed() || mwh.getOpensAt() == null || mwh.getClosesAt() == null) {
            openingHours.putIfAbsent(mwh.getDayOfWeek(), new WorkingWindow(true, null, null));
            continue;
          }
          openingHours.merge(
              mwh.getDayOfWeek(),
              new WorkingWindow(false, mwh.getOpensAt(), mwh.getClosesAt()),
              (existing, incoming) -> {
                if (!existing.isOpen()) {
                  return incoming;
                }
                return new WorkingWindow(
                    false,
                    existing.opensAt().isBefore(incoming.opensAt())
                        ? existing.opensAt()
                        : incoming.opensAt(),
                    existing.closesAt().isAfter(incoming.closesAt())
                        ? existing.closesAt()
                        : incoming.closesAt());
              });
        }
      }
    }

    int qcBufferHours = lab.getQcBufferHours() != null ? lab.getQcBufferHours() : 0;
    Integer expressCapPercent =
        lab.getExpressDailyCapPercent() != null
            ? lab.getExpressDailyCapPercent().intValue()
            : null;
    int commitmentWorkingDays = resolveCommitmentWorkingDays(lab, request);

    return new LabScheduleConfig(
        openingHours, qcBufferHours, expressCapPercent, commitmentWorkingDays);
  }

  private int resolveCommitmentWorkingDays(FilmLab lab, CapacityRequest request) {
    if (request.tier() == ServiceTier.EXPRESS) {
      if (lab.getExpressCommitmentWorkingDays() != null) {
        return lab.getExpressCommitmentWorkingDays();
      }
      List<String> values =
          jdbcTemplate.queryForList(SQL_EXPRESS_COMMITMENT_PARAM, String.class);
      if (!values.isEmpty()) {
        try {
          return Integer.parseInt(values.getFirst().trim());
        } catch (NumberFormatException ignored) {
          return DEFAULT_EXPRESS_COMMITMENT_DAYS;
        }
      }
      return DEFAULT_EXPRESS_COMMITMENT_DAYS;
    }

    Integer orderMax =
        jdbcTemplate.queryForObject(
            SQL_MAX_ORDER_TURNAROUND, Integer.class, request.serviceOrderId());
    if (orderMax != null && orderMax > 0) {
      return orderMax;
    }
    Integer labMax =
        jdbcTemplate.queryForObject(
            SQL_MAX_LAB_PACKAGE_TURNAROUND, Integer.class, lab.getId());
    return (labMax != null && labMax > 0) ? labMax : DEFAULT_STANDARD_TURNAROUND_DAYS;
  }

  private MachineSnapshot toMachineSnapshot(Machine machine) {
    Set<Long> processIds =
        machine.getFilmProcesses().stream().map(BaseEntity::getId).collect(Collectors.toSet());
    Set<Long> formatIds =
        machine.getFilmFormats().stream().map(BaseEntity::getId).collect(Collectors.toSet());
    Map<Short, WorkingWindow> workingHours = new HashMap<>();
    for (MachineWorkingHour mwh : machine.getWorkingHours()) {
      workingHours.put(
          mwh.getDayOfWeek(),
          new WorkingWindow(mwh.isClosed(), mwh.getOpensAt(), mwh.getClosesAt()));
    }
    return new MachineSnapshot(
        machine.getId(),
        machine.getMachineType(),
        machine.getCapacityPerHour(),
        machine.getStatus(),
        processIds,
        formatIds,
        workingHours);
  }

  private List<IncidentWindow> loadActiveIncidents(Long filmLabId) {
    return jdbcTemplate.query(
        SQL_ACTIVE_INCIDENTS,
        (rs, rowNum) -> {
          Timestamp fromTs = rs.getTimestamp("unavailable_from");
          Timestamp toTs = rs.getTimestamp("expected_available_at");
          return new IncidentWindow(
              rs.getLong("machine_id"),
              fromTs != null ? fromTs.toInstant() : null,
              toTs != null ? toTs.toInstant() : null);
        },
        filmLabId);
  }

  private static OrderCapacityAllocation toEntity(
      CapacityRequest request, DayAllocation allocation) {
    OrderCapacityAllocation entity = new OrderCapacityAllocation();
    entity.setServiceOrderId(request.serviceOrderId());
    entity.setFilmLabId(request.filmLabId());
    entity.setWorkDate(allocation.workDate());
    entity.setFilmProcessId(allocation.filmProcessId());
    entity.setFilmFormatId(allocation.filmFormatId());
    entity.setServiceTier(request.tier());
    entity.setRollCount((short) allocation.rollCount());
    entity.setOrderReceivedAt(request.receivedAt());
    return entity;
  }
}
