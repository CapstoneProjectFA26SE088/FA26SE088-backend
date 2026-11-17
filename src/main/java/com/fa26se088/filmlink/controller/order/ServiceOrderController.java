package com.fa26se088.filmlink.controller.order;

import com.fa26se088.filmlink.dto.order.request.CreateServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderCreationResult;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderQuoteResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.order.ServiceOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC20: Photographer báo giá và tạo Service Order. {@code hasRole('PHOTOGRAPHER')} gồm cả
 * PHOTOGRAPHY_EXPERT (BR-05, {@code RoleHierarchy}).
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHOTOGRAPHER')")
@Tag(name = "Service Orders", description = "Photographer báo giá và tạo đơn tráng / scan (UC20)")
public class ServiceOrderController {

  private final ServiceOrderService serviceOrderService;

  @PostMapping("/quote")
  @Operation(summary = "Báo giá từng cuộn, tổng tạm tính và ngày dự kiến trả; không ghi DB")
  public ServiceOrderQuoteResponse quote(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateServiceOrderRequest request) {
    return serviceOrderService.quote(account.id(), request);
  }

  @PostMapping
  @Operation(
      summary = "Tạo đơn (201); gửi lại cùng idempotencyKey thì trả đơn đã có (200)",
      description =
          "ONLINE → AWAITING_PAYMENT; PAY_AT_LAB → AWAITING_LAB_CONFIRMATION. Trường payment hiện"
              + " luôn null (chờ contract thanh toán).")
  public ResponseEntity<ServiceOrderResponse> create(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateServiceOrderRequest request) {
    ServiceOrderCreationResult result = serviceOrderService.create(account.id(), request);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result.order());
  }
}
