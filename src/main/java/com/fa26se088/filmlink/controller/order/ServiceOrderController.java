package com.fa26se088.filmlink.controller.order;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.order.request.AnswerScheduleChangeRequest;
import com.fa26se088.filmlink.dto.order.request.CreateServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.SearchServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderCreationResult;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderDetailResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderQuoteResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderSummaryResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.order.ServiceOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC20, UC72, UC25: Photographer báo giá, tạo Service Order, trả lời thay đổi lịch và theo dõi đơn
 * của mình. {@code
 * hasRole('PHOTOGRAPHER')} gồm cả PHOTOGRAPHY_EXPERT (BR-05, {@code RoleHierarchy}).
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHOTOGRAPHER')")
@Tag(
    name = "Service Orders",
    description = "Photographer báo giá, tạo đơn tráng / scan, trả lời thay đổi lịch (UC20, UC72)")
public class ServiceOrderController {

  private final ServiceOrderService serviceOrderService;

  @GetMapping
  @Operation(
      summary = "Đơn của tôi, mới nhất trước; lọc tuỳ chọn theo status (UC25)",
      description =
          "Phân trang 20 dòng mặc định, tối đa 100. Mỗi dòng có tên lab, số cuộn, tổng tạm tính,"
              + " giá chốt, ngày dự kiến / ngày hẹn trả.")
  public PageResponse<ServiceOrderSummaryResponse> getMyOrders(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @ParameterObject @ModelAttribute SearchServiceOrderRequest request) {
    return serviceOrderService.getMyOrders(account.id(), request);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Chi tiết đơn của tôi: lab, cuộn, dòng thời gian công đoạn, bàn giao (UC25)",
      description =
          "Đơn không tồn tại hoặc của người khác → 404 ORDER_NOT_FOUND. Trường payment hiện luôn"
              + " null (chờ contract thanh toán).")
  public ServiceOrderDetailResponse getMyOrder(
      @AuthenticationPrincipal AuthenticatedAccount account, @PathVariable Long id) {
    return serviceOrderService.getMyOrder(account.id(), id);
  }

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

  @PostMapping("/{id}/schedule-changes/{changeId}/answer")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Trả lời thay đổi lịch do sự cố máy (UC72); giai đoạn này chỉ WAIT",
      description =
          "Đơn không thuộc Photographer → 404 ORDER_NOT_FOUND; CANCEL / TRANSFER → 422"
              + " ORDER_OPTION_NOT_SUPPORTED. Kiểm changeId thuộc đơn và còn chờ trả lời do domain"
              + " machine.")
  public void answerScheduleChange(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable Long id,
      @PathVariable Long changeId,
      @Valid @RequestBody AnswerScheduleChangeRequest request) {
    serviceOrderService.answerScheduleChange(account.id(), id, changeId, request);
  }
}
