package com.fa26se088.filmlink.controller.lab;

import com.fa26se088.filmlink.dto.lab.request.SaveServicePackageRequest;
import com.fa26se088.filmlink.dto.lab.request.UpdateServicePackageStatusRequest;
import com.fa26se088.filmlink.dto.lab.response.ServicePackageResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.lab.ServicePackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC13: Film Lab Owner quản lý bảng giá (Service Package) của lab mình. Lab luôn là lab của tài
 * khoản đang đăng nhập ({@code /me}), không nhận {@code labId} từ client.
 */
@RestController
@RequestMapping("/api/v1/film-labs/me/packages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FILM_LAB_OWNER')")
@Tag(name = "Film Lab Price List", description = "Film Lab Owner quản lý Service Package (UC13)")
public class ServicePackageController {

  private final ServicePackageService servicePackageService;

  @GetMapping
  @Operation(summary = "Toàn bộ gói của lab mình (mọi trạng thái), kèm mức scan")
  public List<ServicePackageResponse> getMyPackages(
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return servicePackageService.getMyPackages(account.id());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Tạo gói (bắt đầu ON_SALE); tên duy nhất trong lab, master data ACTIVE")
  public ServicePackageResponse createMyPackage(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody SaveServicePackageRequest request) {
    return servicePackageService.createMyPackage(account.id(), request);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Sửa gói và thay toàn bộ danh sách mức scan (không đổi trạng thái)")
  public ServicePackageResponse updateMyPackage(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable Long id,
      @Valid @RequestBody SaveServicePackageRequest request) {
    return servicePackageService.updateMyPackage(account.id(), id, request);
  }

  @PatchMapping("/{id}/status")
  @Operation(summary = "Mở bán (ON_SALE) / ngừng bán (WITHDRAWN) gói")
  public ServicePackageResponse updateMyPackageStatus(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable Long id,
      @Valid @RequestBody UpdateServicePackageStatusRequest request) {
    return servicePackageService.updateMyPackageStatus(account.id(), id, request);
  }
}
