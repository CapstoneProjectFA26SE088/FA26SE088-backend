package com.fa26se088.filmlink.controller.payment;

import com.fa26se088.filmlink.dto.payment.request.ExecutePayoutRequest;
import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.payment.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Controller quản lý giải ngân Payout cho Film Lab (UC35, UC36).
 * Base path: /api/v1/payouts
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
public class PayoutController {

  private final PayoutService payoutService;

  /**
   * Admin tạo đợt Payout giải ngân cho một Lab (UC35).
   */
  @PostMapping("/lab/{labId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<PayoutResponse> createPayoutForLab(
      @PathVariable("labId") Long labId,
      @RequestParam("amountVnd") Long amountVnd,
      @AuthenticationPrincipal AuthenticatedAccount adminAccount) {
    PayoutResponse response = payoutService.createPayoutForLab(labId, amountVnd, adminAccount);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Admin thực thi chuyển khoản giải ngân đính kèm biên lai (UC35, BR-31).
   */
  @PostMapping("/{id}/execution")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<PayoutResponse> executePayout(
      @PathVariable("id") Long id,
      @Valid @RequestBody ExecutePayoutRequest request,
      @AuthenticationPrincipal AuthenticatedAccount adminAccount) {
    return ResponseEntity.ok(payoutService.executePayout(id, request, adminAccount));
  }

  /**
   * Danh sách Payout của Film Lab.
   */
  @GetMapping("/lab/{labId}")
  @PreAuthorize("hasAnyRole('FILM_LAB_OWNER', 'ADMIN')")
  public ResponseEntity<Page<PayoutResponse>> getLabPayouts(
      @PathVariable("labId") Long labId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(payoutService.getLabPayouts(labId, pageable, account));
  }

  /**
   * Màn hình giám sát tất cả các đợt Payout của Admin (UC36).
   */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Page<PayoutResponse>> getAllPayouts(
      @RequestParam(value = "status", required = false) PayoutStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
      @AuthenticationPrincipal AuthenticatedAccount adminAccount) {
    return ResponseEntity.ok(payoutService.getAllPayouts(status, pageable, adminAccount));
  }

  /**
   * Chi tiết đợt Payout theo ID.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('FILM_LAB_OWNER', 'ADMIN')")
  public ResponseEntity<PayoutResponse> getPayoutById(
      @PathVariable("id") Long id,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(payoutService.getPayoutById(id, account));
  }
}

