package com.fa26se088.filmlink.controller.order;

import com.fa26se088.filmlink.dto.order.request.CancelServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.ConfirmReceiptAndFinalizePriceRequest;
import com.fa26se088.filmlink.dto.order.request.CreateServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.RejectServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.order.ServiceOrderService;
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
 * Controller quản lý đơn dịch vụ tráng / scan (ServiceOrder).
 * Base path: /api/v1/service-orders
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/service-orders")
@RequiredArgsConstructor
public class ServiceOrderController {

  private final ServiceOrderService orderService;

  /**
   * Tạo đơn dịch vụ mới (Photographer).
   */
  @PostMapping
  @PreAuthorize("hasRole('PHOTOGRAPHER')")
  public ResponseEntity<ServiceOrderResponse> createOrder(
      @Valid @RequestBody CreateServiceOrderRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    ServiceOrderResponse response = orderService.createOrder(request, account);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Xem chi tiết đơn hàng theo ID.
   */
  @GetMapping("/{id}")
  public ResponseEntity<ServiceOrderResponse> getOrderById(
      @PathVariable("id") Long id,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(orderService.getOrderById(id, account));
  }

  /**
   * Xem chi tiết đơn hàng theo orderCode.
   */
  @GetMapping("/code/{orderCode}")
  public ResponseEntity<ServiceOrderResponse> getOrderByCode(
      @PathVariable("orderCode") String orderCode,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(orderService.getOrderByCode(orderCode, account));
  }

  /**
   * Danh sách đơn hàng của Photographer đang đăng nhập.
   */
  @GetMapping("/my-orders")
  @PreAuthorize("hasRole('PHOTOGRAPHER')")
  public ResponseEntity<Page<ServiceOrderResponse>> getMyOrders(
      @RequestParam(value = "status", required = false) ServiceOrderStatus status,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(orderService.getPhotographerOrders(status, pageable, account));
  }

  /**
   * Danh sách đơn hàng thuộc Film Lab.
   */
  @GetMapping("/lab/{labId}")
  @PreAuthorize("hasAnyRole('FILM_LAB_OWNER', 'ADMIN')")
  public ResponseEntity<Page<ServiceOrderResponse>> getLabOrders(
      @PathVariable("labId") Long labId,
      @RequestParam(value = "status", required = false) ServiceOrderStatus status,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(orderService.getLabOrders(labId, status, pageable, account));
  }

  /**
   * Film Lab chấp nhận đơn hàng (UC22).
   */
  @PostMapping("/{id}/acceptance")
  @PreAuthorize("hasAnyRole('FILM_LAB_OWNER', 'ADMIN')")
  public ResponseEntity<ServiceOrderResponse> acceptOrder(
      @PathVariable("id") Long id,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(orderService.acceptOrder(id, account));
  }

  /**
   * Film Lab từ chối đơn hàng kèm lý do (UC22, BR-39).
   */
  @PostMapping("/{id}/rejection")
  @PreAuthorize("hasAnyRole('FILM_LAB_OWNER', 'ADMIN')")
  public ResponseEntity<ServiceOrderResponse> rejectOrder(
      @PathVariable("id") Long id,
      @Valid @RequestBody RejectServiceOrderRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(orderService.rejectOrder(id, request, account));
  }

  /**
   * Film Lab xác nhận nhận phim và chốt giá thực tế (UC23, BR-18, BR-25, BR-26).
   */
  @PostMapping("/{id}/receipt")
  @PreAuthorize("hasAnyRole('FILM_LAB_OWNER', 'ADMIN')")
  public ResponseEntity<ServiceOrderResponse> confirmReceiptAndFinalizePrice(
      @PathVariable("id") Long id,
      @Valid @RequestBody ConfirmReceiptAndFinalizePriceRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(orderService.confirmReceiptAndFinalizePrice(id, request, account));
  }

  /**
   * Khách hàng hủy đơn (UC60, BR-38).
   */
  @PostMapping("/{id}/cancellation")
  @PreAuthorize("hasRole('PHOTOGRAPHER')")
  public ResponseEntity<ServiceOrderResponse> cancelOrder(
      @PathVariable("id") Long id,
      @Valid @RequestBody CancelServiceOrderRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(orderService.cancelOrder(id, request, account));
  }

  /**
   * Khách hàng xác nhận hoàn tất / đóng đơn (UC29, BR-24).
   */
  @PostMapping("/{id}/closure")
  @PreAuthorize("hasRole('PHOTOGRAPHER')")
  public ResponseEntity<ServiceOrderResponse> closeOrder(
      @PathVariable("id") Long id,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return ResponseEntity.ok(orderService.closeOrder(id, account));
  }
}

