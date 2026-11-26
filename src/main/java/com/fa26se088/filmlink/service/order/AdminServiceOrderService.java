package com.fa26se088.filmlink.service.order;

import com.fa26se088.filmlink.dto.order.request.RevertProcessingStageRequest;
import com.fa26se088.filmlink.dto.order.response.AdminOrderStageRevertResponse;

/**
 * UC24, BR-07: dịch vụ quản trị Service Order dành cho Admin (lùi công đoạn xử lý).
 */
public interface AdminServiceOrderService {

  /**
   * UC24, BR-07: Admin lùi công đoạn xử lý của đơn {@code IN_PROCESSING} về một công đoạn trước đó
   * (append-only, {@code is_admin_reversion = true}), ghi audit {@code ORDER_STAGE_REVERTED} và báo
   * cho cả Photographer lẫn Film Lab Owner.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404),
   *     {@code ORDER_STATUS_CONFLICT} (409: đơn không IN_PROCESSING), {@code
   *     ORDER_STAGE_OUT_OF_ORDER} (422: đang ở RECEIVED hoặc công đoạn đích không đứng trước công
   *     đoạn hiện tại), {@code VALIDATION_ERROR} (400: lý do rỗng / quá 300 ký tự)
   */
  AdminOrderStageRevertResponse revertStage(
      Long adminAccountId, Long orderId, RevertProcessingStageRequest request);
}
