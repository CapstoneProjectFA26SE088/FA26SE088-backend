package com.fa26se088.filmlink.controller.payment;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.payment.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin giám sát và ghi nhận chuyển khoản chi trả cho lab (UC35, UC36). */
@RestController
@RequestMapping("/api/v1/admin/payouts")
@RequiredArgsConstructor
public class AdminPayoutController {

  private final PayoutService payoutService;

  /** Tra cứu danh sách đợt chi trả toàn hệ thống có phân trang và lọc theo trạng thái. */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<PageResponse<PayoutResponse>> list(
      @RequestParam(value = "status", required = false) PayoutStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @AuthenticationPrincipal AuthenticatedAccount admin) {
    return ResponseEntity.ok(
        PageResponse.from(payoutService.getAllPayouts(status, pageable, admin)));
  }

  /** Xem chi tiết một đợt chi trả và danh sách các dòng đơn hàng bên trong. */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<PayoutResponse> detail(
      @PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedAccount admin) {
    return ResponseEntity.ok(payoutService.getPayoutById(id, admin));
  }

  /** Ghi nhận đã chuyển khoản giải ngân kèm file biên lai chứng từ (BR-31). */
  @PostMapping(path = "/{id}/paid", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<PayoutResponse> markPaid(
      @PathVariable("id") Long id,
      @RequestPart(value = "receipt", required = false) MultipartFile receipt,
      @AuthenticationPrincipal AuthenticatedAccount admin) {
    return ResponseEntity.ok(payoutService.markPaid(id, receipt, admin));
  }
}
