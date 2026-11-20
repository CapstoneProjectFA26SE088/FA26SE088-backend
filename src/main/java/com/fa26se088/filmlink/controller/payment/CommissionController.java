package com.fa26se088.filmlink.controller.payment;

import com.fa26se088.filmlink.dto.payment.request.ReportDebtPaymentRequest;
import com.fa26se088.filmlink.dto.payment.response.CommissionDebtResponse;
import com.fa26se088.filmlink.dto.payment.response.CommissionResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.payment.CommissionDebtService;
import com.fa26se088.filmlink.service.payment.CommissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý hoa hồng và kỳ nợ của Film Lab (UC15, UC32, UC33, UC36).
 * Base path: /api/v1/commissions & /api/v1/commission-debts
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CommissionController {

  private final CommissionService commissionService;
  private final CommissionDebtService debtService;

  /**
   * Danh sách hoa hồng theo Film Lab.
   */
  @GetMapping("/api/v1/commissions/lab/{labId}")
  @PreAuthorize("hasAnyRole('FILM_LAB_OWNER', 'ADMIN')")
  public ResponseEntity<Page<CommissionResponse>> getLabCommissions(
      @PathVariable("labId") Long labId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(commissionService.getLabCommissions(labId, pageable, account));
  }

  /**
   * Danh sách kỳ nợ hoa hồng của Film Lab.
   */
  @GetMapping("/api/v1/commission-debts/lab/{labId}")
  @PreAuthorize("hasAnyRole('FILM_LAB_OWNER', 'ADMIN')")
  public ResponseEntity<Page<CommissionDebtResponse>> getLabDebts(
      @PathVariable("labId") Long labId,
      @PageableDefault(size = 20, sort = "billingPeriod", direction = Sort.Direction.DESC) Pageable pageable,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(debtService.getLabDebts(labId, pageable, account));
  }

  /**
   * Chi tiết một kỳ nợ hoa hồng.
   */
  @GetMapping("/api/v1/commission-debts/{id}")
  @PreAuthorize("hasAnyRole('FILM_LAB_OWNER', 'ADMIN')")
  public ResponseEntity<CommissionDebtResponse> getDebtById(
      @PathVariable("id") Long id,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(debtService.getDebtById(id, account));
  }

  /**
   * Film Lab báo cáo đã chuyển khoản nộp nợ hoa hồng (UC33).
   */
  @PostMapping("/api/v1/commission-debts/{id}/payment-reports")
  @PreAuthorize("hasAnyRole('FILM_LAB_OWNER', 'ADMIN')")
  public ResponseEntity<CommissionDebtResponse> reportDebtPayment(
      @PathVariable("id") Long id,
      @Valid @RequestBody ReportDebtPaymentRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(debtService.reportDebtPayment(id, request, account));
  }

  /**
   * Admin xác nhận đã nhận đủ tiền nợ và tất toán kỳ nợ (UC33).
   */
  @PostMapping("/api/v1/commission-debts/{id}/confirmation")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CommissionDebtResponse> confirmDebtPayment(
      @PathVariable("id") Long id,
      @AuthenticationPrincipal AuthenticatedAccount adminAccount) {
    return ResponseEntity.ok(debtService.confirmDebtPayment(id, adminAccount));
  }
}

