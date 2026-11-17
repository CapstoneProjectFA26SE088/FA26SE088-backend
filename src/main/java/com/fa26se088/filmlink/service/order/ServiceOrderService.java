package com.fa26se088.filmlink.service.order;

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
}
