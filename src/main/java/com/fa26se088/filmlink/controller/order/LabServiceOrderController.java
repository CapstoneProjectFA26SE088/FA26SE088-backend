package com.fa26se088.filmlink.controller.order;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.order.request.SearchLabServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderSummaryResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.order.LabServiceOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC22: Film Lab Owner xem và nhận Service Order của lab mình (hàng đợi công việc của lab, tiền tố
 * {@code /api/v1/lab/...} theo SPEC-00 §9). Lab luôn là lab của tài khoản đang đăng nhập, không
 * nhận {@code labId} từ client.
 */
@RestController
@RequestMapping("/api/v1/lab/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FILM_LAB_OWNER')")
@Tag(name = "Film Lab Orders", description = "Film Lab Owner xem và nhận Service Order (UC22)")
public class LabServiceOrderController {

  private final LabServiceOrderService labServiceOrderService;

  @GetMapping
  @Operation(
      summary = "Đơn của lab mình, mới nhất trước; lọc tuỳ chọn theo status",
      description = "Phân trang 20 dòng mặc định, tối đa 100.")
  public PageResponse<LabServiceOrderSummaryResponse> getMyOrders(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @ParameterObject @ModelAttribute SearchLabServiceOrderRequest request) {
    return labServiceOrderService.getMyOrders(account.id(), request);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Chi tiết đơn của lab mình: cuộn (kèm giá đã chụp) và Photographer",
      description = "Đơn không tồn tại hoặc thuộc lab khác → 404 ORDER_NOT_FOUND.")
  public LabServiceOrderResponse getMyOrder(
      @AuthenticationPrincipal AuthenticatedAccount account, @PathVariable Long id) {
    return labServiceOrderService.getMyOrder(account.id(), id);
  }

  @PostMapping("/{id}/accept")
  @Operation(
      summary = "Nhận đơn: AWAITING_LAB_CONFIRMATION → AWAITING_FILM",
      description = "Trạng thái khác hoặc đơn vừa bị người khác đổi → 409 ORDER_STATUS_CONFLICT.")
  public LabServiceOrderResponse accept(
      @AuthenticationPrincipal AuthenticatedAccount account, @PathVariable Long id) {
    return labServiceOrderService.accept(account.id(), id);
  }
}
