package com.fa26se088.filmlink.controller.machine;

import com.fa26se088.filmlink.dto.machine.request.CreateMachineRequest;
import com.fa26se088.filmlink.dto.machine.response.MachineResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cho phép Film Lab Owner quản lý thiết bị máy tráng, máy scan và xem công suất tính
 * toán theo ngày (UC68, FA26-72).
 */
@RestController
@RequestMapping("/api/v1/film-labs/me/machines")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FILM_LAB_OWNER')")
@Tag(
    name = "Film Lab Machines",
    description = "Film Lab Owner quản lý máy tráng, máy scan và công suất ngày (UC68)")
public class MachineController {

  private final MachineService machineService;

  /** Lấy danh sách máy của lab kèm công suất tính toán theo ngày (UC68). */
  @GetMapping
  @Operation(summary = "Lấy danh sách máy của lab kèm công suất tính toán theo ngày (UC68)")
  public List<MachineResponse> getMyMachines(
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return machineService.getMyMachines(account.id());
  }

  /** Khai báo máy tráng (PROCESSOR) hoặc máy scan (SCANNER) mới cho lab (UC68, FA26-72). */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Khai báo máy tráng (PROCESSOR) hoặc máy scan (SCANNER) mới cho lab (UC68)")
  public MachineResponse createMachine(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateMachineRequest request) {
    return machineService.createMachine(account.id(), request);
  }
}
