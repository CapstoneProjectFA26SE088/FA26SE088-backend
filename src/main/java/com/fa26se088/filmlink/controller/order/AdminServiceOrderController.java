package com.fa26se088.filmlink.controller.order;

import com.fa26se088.filmlink.dto.order.request.RevertProcessingStageRequest;
import com.fa26se088.filmlink.dto.order.response.AdminOrderStageRevertResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.order.AdminServiceOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC24, BR-07: Controller dành cho Admin can thiệp lùi công đoạn xử lý của Service Order.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Service Orders (Admin)", description = "Admin quản trị Service Order (UC24, BR-07)")
public class AdminServiceOrderController {

  private final AdminServiceOrderService adminServiceOrderService;

  @PostMapping("/{id}/stages/revert")
  @Operation(
      summary = "[Admin] Lùi công đoạn xử lý của đơn IN_PROCESSING (append-only)",
      description =
          "UC24, BR-07. Chỉ Admin được lùi về công đoạn đứng trước công đoạn hiện tại; ghi dòng"
              + " mới với is_admin_reversion = true, ghi audit ORDER_STAGE_REVERTED và báo cho"
              + " Photographer + Film Lab Owner. Đơn không IN_PROCESSING → 409"
              + " ORDER_STATUS_CONFLICT; công đoạn đích không lùi → 422 ORDER_STAGE_OUT_OF_ORDER.")
  public AdminOrderStageRevertResponse revertStage(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable Long id,
      @Valid @RequestBody RevertProcessingStageRequest request) {
    Long adminAccountId = account != null ? account.id() : null;
    return adminServiceOrderService.revertStage(adminAccountId, id, request);
  }
}
