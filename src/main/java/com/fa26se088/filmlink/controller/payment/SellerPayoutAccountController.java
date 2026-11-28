package com.fa26se088.filmlink.controller.payment;

import com.fa26se088.filmlink.dto.payment.request.UpdatePayoutAccountRequest;
import com.fa26se088.filmlink.dto.payment.response.PayoutAccountResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.payment.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Người bán ký gửi quản lý tài khoản ngân hàng nhận tiền của chính mình (UC64). */
@RestController
@RequestMapping("/api/v1/me/payout-account")
@RequiredArgsConstructor
public class SellerPayoutAccountController {

  private final PayoutService payoutService;

  /** Lấy tài khoản ngân hàng nhận tiền hiện hành của tài khoản đang đăng nhập. */
  @GetMapping
  public ResponseEntity<PayoutAccountResponse> getMyPayoutAccount(
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(payoutService.getMyPersonalPayoutAccount(account));
  }

  /** Khai báo hoặc cập nhật tài khoản ngân hàng nhận tiền của tài khoản đang đăng nhập. */
  @PutMapping
  public ResponseEntity<PayoutAccountResponse> updateMyPayoutAccount(
      @Valid @RequestBody UpdatePayoutAccountRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(payoutService.updateMyPersonalPayoutAccount(request, account));
  }
}
