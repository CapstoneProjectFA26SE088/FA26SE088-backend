package com.fa26se088.filmlink.service.order;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.order.request.AnswerScheduleChangeRequest;
import com.fa26se088.filmlink.dto.order.request.CancelServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.CreateServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.SearchServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderDetailResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderSummaryResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderCreationResult;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderQuoteResponse;

/**
 * UC20: Photographer báo giá và tạo Service Order. Mọi method nhận {@code photographerAccountId}
 * của tài khoản đang đăng nhập (role PHOTOGRAPHER hoặc PHOTOGRAPHY_EXPERT, kiểm ở controller).
 */
public interface ServiceOrderService {

  /** UC20: báo giá từng cuộn, tổng tạm tính và ngày dự kiến trả. Không ghi DB. */
  ServiceOrderQuoteResponse quote(Long photographerAccountId, CreateServiceOrderRequest request);

  /**
   * UC20: tạo đơn kèm các cuộn, chụp giá vào cuộn. Gửi lại cùng {@code idempotencyKey} thì trả đơn
   * đã có ({@code created = false}), không tạo thêm (NFR-REL-02).
   */
  ServiceOrderCreationResult create(Long photographerAccountId, CreateServiceOrderRequest request);

  /**
   * UC72: Photographer trả lời thay đổi lịch do sự cố máy của đơn mình. Giai đoạn này chỉ {@code
   * WAIT}.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404) khi
   *     đơn không thuộc Photographer, {@code ORDER_OPTION_NOT_SUPPORTED} (422) với {@code CANCEL} /
   *     {@code TRANSFER}
   */
  void answerScheduleChange(
      Long photographerAccountId,
      Long orderId,
      Long scheduleChangeId,
      AnswerScheduleChangeRequest request);

  /** UC25: đơn của Photographer, mới nhất trước, lọc tuỳ chọn theo trạng thái (CR-01, CR-02). */
  PageResponse<ServiceOrderSummaryResponse> getMyOrders(
      Long photographerAccountId, SearchServiceOrderRequest request);

  /**
   * UC25: chi tiết một đơn của Photographer kèm dòng thời gian công đoạn và bàn giao.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404) khi
   *     đơn không tồn tại hoặc không thuộc Photographer
   */
  ServiceOrderDetailResponse getMyOrder(Long photographerAccountId, Long orderId);

  /**
   * UC28: Photographer xác nhận đã nhận phim (hoặc đồng ý lab giữ âm bản) của đơn {@code
   * DELIVERED} → {@code CLOSED}.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404) khi
   *     đơn không tồn tại hoặc không thuộc Photographer, {@code ORDER_STATUS_CONFLICT} (409) khi đơn
   *     không ở {@code DELIVERED}
   */
  ServiceOrderDetailResponse confirmReceipt(Long photographerAccountId, Long orderId);

  /**
   * UC60, BR-38: Photographer huỷ đơn trước khi lab nhận phim ({@code AWAITING_PAYMENT}, {@code
   * AWAITING_LAB_CONFIRMATION}, {@code AWAITING_FILM}) → {@code CANCELLED}, không mất phí.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404) khi
   *     đơn không tồn tại hoặc không thuộc Photographer, {@code ORDER_STATUS_CONFLICT} (409) khi đơn
   *     ở trạng thái khác (kể cả đã huỷ)
   */
  ServiceOrderDetailResponse cancel(
      Long photographerAccountId, Long orderId, CancelServiceOrderRequest request);
}
