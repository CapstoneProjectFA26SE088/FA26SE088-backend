package com.fa26se088.filmlink.controller.payment;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.payment.request.UpdatePayoutAccountRequest;
import com.fa26se088.filmlink.dto.payment.response.PayoutAccountResponse;
import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.payment.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chủ lab quản lý tài khoản nhận tiền và xem lịch sử chi trả của lab mình (UC35). */
@RestController
@RequestMapping("/api/v1/film-labs/me")
@RequiredArgsConstructor
public class LabPayoutController {

  private final PayoutService payoutService;

  @GetMapping("/payout-account")
  @PreAuthorize("hasRole('FILM_LAB_OWNER')")
  public ResponseEntity<PayoutAccountResponse> getPayoutAccount(
      @AuthenticationPrincipal AuthenticatedAccount owner) {
    return ResponseEntity.ok(payoutService.getMyPayoutAccount(owner));
  }

  @PutMapping("/payout-account")
  @PreAuthorize("hasRole('FILM_LAB_OWNER')")
  public ResponseEntity<PayoutAccountResponse> updatePayoutAccount(
      @Valid @RequestBody UpdatePayoutAccountRequest request,
      @AuthenticationPrincipal AuthenticatedAccount owner) {
    return ResponseEntity.ok(payoutService.updateMyPayoutAccount(request, owner));
  }

  @GetMapping("/payouts")
  @PreAuthorize("hasRole('FILM_LAB_OWNER')")
  public ResponseEntity<PageResponse<PayoutResponse>> myPayouts(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @AuthenticationPrincipal AuthenticatedAccount owner) {
    return ResponseEntity.ok(PageResponse.from(payoutService.getMyLabPayouts(pageable, owner)));
  }
}
