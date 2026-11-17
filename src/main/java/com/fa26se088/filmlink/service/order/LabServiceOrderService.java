package com.fa26se088.filmlink.service.order;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.order.request.SearchLabServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderSummaryResponse;

/**
 * UC22: Film Lab Owner xem và nhận Service Order của lab mình. Mọi method nhận {@code
 * ownerAccountId} của tài khoản đang đăng nhập (role FILM_LAB_OWNER, kiểm ở controller); lab được
 * tra theo chủ ({@code LabAccessService}), không nhận {@code labId} từ client.
 *
 * <p>Đơn không tồn tại hoặc thuộc lab khác đều trả {@code ORDER_NOT_FOUND} (404), không lộ đơn của
 * lab khác.
 */
public interface LabServiceOrderService {

  /** UC22: đơn của lab mình, mới nhất trước, lọc tuỳ chọn theo trạng thái (CR-01, CR-02). */
  PageResponse<LabServiceOrderSummaryResponse> getMyOrders(
      Long ownerAccountId, SearchLabServiceOrderRequest request);

  /** UC22: chi tiết một đơn của lab mình, kèm cuộn và Photographer. */
  LabServiceOrderResponse getMyOrder(Long ownerAccountId, Long orderId);

  /**
   * UC22: nhận đơn đang {@code AWAITING_LAB_CONFIRMATION} → {@code AWAITING_FILM}.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_STATUS_CONFLICT} (409)
   *     khi đơn không còn chờ lab xác nhận
   */
  LabServiceOrderResponse accept(Long ownerAccountId, Long orderId);
}
