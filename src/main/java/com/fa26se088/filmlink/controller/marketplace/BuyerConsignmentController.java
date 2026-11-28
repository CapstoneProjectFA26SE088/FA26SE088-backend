package com.fa26se088.filmlink.controller.marketplace;

import com.fa26se088.filmlink.dto.marketplace.request.CreateConsignmentPurchaseRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSaleResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.marketplace.ConsignmentSaleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Người mua đặt mua thiết bị ký gửi đang mở bán công khai (UC62). */
@RestController
@RequestMapping("/api/v1/consignment-listings")
@RequiredArgsConstructor
public class BuyerConsignmentController {

  private final ConsignmentSaleService saleService;

  /** Đặt mua thiết bị ký gửi trực tuyến, khoá tin đăng sang LOCKED và nhận link VNPay. */
  @PostMapping("/{id}/purchases")
  @PreAuthorize("hasRole('PHOTOGRAPHER')")
  public ResponseEntity<ConsignmentSaleResponse> createPurchase(
      @PathVariable("id") Long listingId,
      @Valid @RequestBody CreateConsignmentPurchaseRequest request,
      HttpServletRequest httpRequest,
      @AuthenticationPrincipal AuthenticatedAccount buyer) {
    String clientIp =
        httpRequest.getRemoteAddr() != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(saleService.createPurchase(listingId, request, clientIp, buyer));
  }
}
