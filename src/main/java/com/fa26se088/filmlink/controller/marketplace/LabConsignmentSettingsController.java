package com.fa26se088.filmlink.controller.marketplace;

import com.fa26se088.filmlink.dto.marketplace.request.UpdateConsignmentSettingsRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSettingsResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.marketplace.ConsignmentInspectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chủ Film Lab quản lý cấu hình nhận ký gửi thiết bị của Lab mình (UC45, BR-54). */
@RestController
@RequestMapping("/api/v1/film-labs/me/consignment-settings")
@RequiredArgsConstructor
public class LabConsignmentSettingsController {

  private final ConsignmentInspectionService inspectionService;

  /** Xem cấu hình nhận ký gửi hiện tại của Film Lab. */
  @GetMapping
  @PreAuthorize("hasRole('FILM_LAB_OWNER')")
  public ResponseEntity<ConsignmentSettingsResponse> getMySettings(
      @AuthenticationPrincipal AuthenticatedAccount owner) {
    return ResponseEntity.ok(inspectionService.getMySettings(owner));
  }

  /** Cập nhật trạng thái nhận ký gửi, tỷ lệ phí và cấu hình khung giờ hẹn của Film Lab. */
  @PutMapping
  @PreAuthorize("hasRole('FILM_LAB_OWNER')")
  public ResponseEntity<ConsignmentSettingsResponse> updateMySettings(
      @Valid @RequestBody UpdateConsignmentSettingsRequest request,
      @AuthenticationPrincipal AuthenticatedAccount owner) {
    return ResponseEntity.ok(inspectionService.updateMySettings(request, owner));
  }
}
