package com.fa26se088.filmlink.service.order;

import com.fa26se088.filmlink.dto.order.request.AnswerScheduleChangeRequest;
import com.fa26se088.filmlink.dto.order.request.CreateServiceOrderRequest;
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
}
