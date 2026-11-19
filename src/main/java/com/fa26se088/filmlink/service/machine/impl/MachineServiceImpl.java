package com.fa26se088.filmlink.service.machine.impl;

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
import com.fa26se088.filmlink.service.machine.MachineService;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai nghiệp vụ quản lý thiết bị máy tráng, máy scan và công suất ngày
 * cho Film Lab Owner
 * (UC68, FA26-72).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MachineServiceImpl implements MachineService {

  private static final int DAYS_PER_WEEK = 7;

  private final MachineRepository machineRepository;
  private final FilmProcessRepository filmProcessRepository;
  private final FilmFormatRepository filmFormatRepository;
  private final LabAccessService labAccessService;
  private final AuditLogService auditLogService;
  private final MachineMapper machineMapper;

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
    Set<FilmProcess> filmProcesses =
        resolveProcessesForType(machine.getMachineType(), request.filmProcessIds());

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
    Map<Short, MachineWorkingHour> existingByDay =
        machine.getWorkingHours().stream()
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
