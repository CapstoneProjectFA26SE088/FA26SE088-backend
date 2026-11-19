package com.fa26se088.filmlink.controller.machine;

import com.fa26se088.filmlink.dto.machine.request.CreateMachineRequest;
import com.fa26se088.filmlink.dto.machine.request.ReportMachineIncidentRequest;
import com.fa26se088.filmlink.dto.machine.request.UpdateMachineRequest;
import com.fa26se088.filmlink.dto.machine.request.UpdateSchedulingSettingsRequest;
import com.fa26se088.filmlink.dto.machine.response.MachineIncidentResponse;
import com.fa26se088.filmlink.dto.machine.response.MachineResponse;
import com.fa26se088.filmlink.dto.machine.response.SchedulingSettingsResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.machine.MachineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cho phép Film Lab Owner quản lý thiết bị máy tráng, máy scan, công suất tính toán
 * theo ngày và tham số lập lịch tự động (UC68, FA26-72).
 */
@RestController
@RequestMapping("/api/v1/film-labs/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FILM_LAB_OWNER')")
@Tag(
    name = "Film Lab Machines",
    description = "Film Lab Owner quản lý máy tráng, máy scan và công suất ngày (UC68)")
public class MachineController {

  private final MachineService machineService;

  /** Lấy danh sách máy của lab kèm công suất tính toán theo ngày (UC68). */
  @GetMapping("/machines")
  @Operation(summary = "Lấy danh sách máy của lab kèm công suất tính toán theo ngày (UC68)")
  public List<MachineResponse> getMyMachines(
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return machineService.getMyMachines(account.id());
  }

  /** Khai báo máy tráng (PROCESSOR) hoặc máy scan (SCANNER) mới cho lab (UC68, FA26-72). */
  @PostMapping("/machines")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Khai báo máy tráng (PROCESSOR) hoặc máy scan (SCANNER) mới cho lab (UC68)")
  public MachineResponse createMachine(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateMachineRequest request) {
    return machineService.createMachine(account.id(), request);
  }

  /** Cập nhật thông tin cấu hình máy (modelName, capacityPerHour, quy trình, khổ phim, giờ hoạt động) (UC68). */
  @PutMapping("/machines/{id}")
  @Operation(
      summary =
          "Cập nhật thông tin máy của lab (không sửa machineCode và machineType, tính lại công suất)")
  public MachineResponse updateMachine(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable Long id,
      @Valid @RequestBody UpdateMachineRequest request) {
    return machineService.updateMachine(account.id(), id, request);
  }

  /** Cập nhật cấu hình tham số lập lịch tự động của lab (qcBufferHours, expressDailyCapPercent, expressCommitmentWorkingDays) (UC68). */
  @PutMapping("/scheduling-settings")
  @Operation(
      summary =
          "Cập nhật tham số lập lịch tự động của lab (qcBufferHours, expressDailyCapPercent, expressCommitmentWorkingDays)")
  public SchedulingSettingsResponse updateSchedulingSettings(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody UpdateSchedulingSettingsRequest request) {
    return machineService.updateSchedulingSettings(account.id(), request);
  }

  /**
   * Khai báo sự cố máy tráng/scan và tự động tái xếp lịch các đơn đang xử lý bị ảnh hưởng
   * (UC70, UC71, FA26-76, FA26-77).
   */
  @PostMapping("/machines/{id}/incidents")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary =
          "Khai báo sự cố máy và tự động tái xếp lịch các đơn bị ảnh hưởng (UC70, UC71)")
  public MachineIncidentResponse reportIncident(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable Long id,
      @Valid @RequestBody ReportMachineIncidentRequest request) {
    return machineService.reportIncident(account.id(), id, request);
  }
}
