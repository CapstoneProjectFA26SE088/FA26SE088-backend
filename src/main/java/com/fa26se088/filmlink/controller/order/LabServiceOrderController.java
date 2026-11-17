package com.fa26se088.filmlink.controller.order;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.order.request.ConfirmFilmReceiptRequest;
import com.fa26se088.filmlink.dto.order.request.SearchLabServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderSummaryResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.order.LabServiceOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * UC22, UC23: Film Lab Owner xem, nhận Service Order và xác nhận nhận phim của lab mình (hàng
 * đợi công việc của lab, tiền tố {@code /api/v1/lab/...} theo SPEC-00 §9). Lab luôn là lab của
 * tài khoản đang đăng nhập, không nhận {@code labId} từ client.
 */
@RestController
@RequestMapping("/api/v1/lab/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FILM_LAB_OWNER')")
@Tag(
    name = "Film Lab Orders",
    description = "Film Lab Owner xem, nhận Service Order và nhận phim (UC22, UC23)")
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

  @PostMapping(path = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary =
          "Xác nhận đã nhận phim, đếm cuộn, chốt giá: AWAITING_FILM → AWAITING_MACHINE_ASSIGNMENT",
      description =
          "UC23, BR-25. Part `data` (JSON, Content-Type application/json): mỗi cuộn đã khai báo"
              + " đúng một lần với received true/false, note ≤ 500. Part `photos`: 1–10 ảnh JPG/PNG"
              + " ≤ 10 MB. Danh sách cuộn sai hoặc số ảnh sai → 400 VALIDATION_ERROR; không nhận"
              + " cuộn nào → 422 ORDER_OPTION_NOT_SUPPORTED; sai trạng thái → 409"
              + " ORDER_STATUS_CONFLICT.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
              encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)))
  public LabServiceOrderResponse confirmFilmReceipt(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable Long id,
      @Valid @RequestPart("data") ConfirmFilmReceiptRequest data,
      @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
    return labServiceOrderService.confirmFilmReceipt(account.id(), id, data, photos);
  }
}
