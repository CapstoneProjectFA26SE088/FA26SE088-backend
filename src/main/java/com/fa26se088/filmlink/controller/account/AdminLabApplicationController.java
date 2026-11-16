package com.fa26se088.filmlink.controller.account;

import com.fa26se088.filmlink.dto.account.response.AdminLabApplicationSummaryResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.service.account.LabApplicationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return labApplicationAdminService.getApplications(status, pageable);
  }
}
