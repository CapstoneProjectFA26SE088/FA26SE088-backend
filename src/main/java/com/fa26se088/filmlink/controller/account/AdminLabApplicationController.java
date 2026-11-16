package com.fa26se088.filmlink.controller.account;

import com.fa26se088.filmlink.dto.account.request.ApproveLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.request.RejectLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.response.AdminLabApplicationSummaryResponse;
import com.fa26se088.filmlink.dto.account.response.LabApplicationResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.account.LabApplicationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller dành cho Admin quản lý và xét duyệt đơn đăng ký Film Lab (UC08, FA26-66).
 */
@RestController
@RequestMapping("/api/v1/admin/lab-applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Lab Application (Admin)", description = "Quản lý và xét duyệt đơn đăng ký Film Lab")
public class AdminLabApplicationController {

  private final LabApplicationAdminService labApplicationAdminService;

  /**
   * Xem danh sách các đơn đăng ký mở lab có phân trang và lọc theo trạng thái (UC08).
   */
  @GetMapping
  @Operation(
      summary = "Xem danh sách đơn đăng ký lab (hỗ trợ lọc theo status, hiển thị cờ trùng lặp)")
  public PageResponse<AdminLabApplicationSummaryResponse> getApplications(
      @RequestParam(required = false) LabApplicationStatus status,
      @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
    return labApplicationAdminService.getApplications(status, pageable);
  }

  /** Xem chi tiết một đơn đăng ký mở lab theo ID kèm signed URL tài liệu (UC08, NFR-PRI-02). */
  @GetMapping("/{id}")
  @Operation(summary = "Xem chi tiết đơn đăng ký mở lab (kèm signed URL tài liệu)")
  public LabApplicationResponse getApplicationDetail(@PathVariable Long id) {
    return labApplicationAdminService.getApplicationDetail(id);
  }

  /** Duyệt đơn đăng ký mở lab, tạo tài khoản FILM_LAB_OWNER và hồ sơ FilmLab (UC08, BR-03). */
  @PostMapping("/{id}/approve")
  @Operation(
      summary = "Duyệt đơn đăng ký mở lab (tạo tài khoản FILM_LAB_OWNER, tạo FilmLab, ghi audit)")
  public LabApplicationResponse approveApplication(
      @PathVariable Long id,
      @Valid @RequestBody ApproveLabApplicationRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    Long adminAccountId = account != null ? account.id() : null;
    return labApplicationAdminService.approveApplication(id, request, adminAccountId);
  }

  /** Từ chối đơn đăng ký mở lab kèm lý do 10–500 ký tự, ghi audit và gửi email thông báo (UC08). */
  @PostMapping("/{id}/reject")
  @Operation(summary = "Từ chối đơn đăng ký mở lab (kèm lý do 10-500 ký tự, ghi audit)")
  public LabApplicationResponse rejectApplication(
      @PathVariable Long id,
      @Valid @RequestBody RejectLabApplicationRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    Long adminAccountId = account != null ? account.id() : null;
    return labApplicationAdminService.rejectApplication(id, request, adminAccountId);
  }
}
