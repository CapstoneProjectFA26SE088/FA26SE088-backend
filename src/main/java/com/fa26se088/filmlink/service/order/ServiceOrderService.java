package com.fa26se088.filmlink.service.order;

import com.fa26se088.filmlink.dto.order.request.CancelServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.ConfirmReceiptAndFinalizePriceRequest;
import com.fa26se088.filmlink.dto.order.request.CreateServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.RejectServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service quản lý đơn dịch vụ tráng / scan (ServiceOrder).
 */
public interface ServiceOrderService {

  /**
   * Tạo đơn dịch vụ mới (UC20, BR-06, BR-09, BR-17*, BR-19*).
   *
   * @param request dữ liệu tạo đơn
   * @param account tài khoản người dùng thực hiện (Photographer)
   * @return ServiceOrderResponse
   */
  ServiceOrderResponse createOrder(CreateServiceOrderRequest request, AuthenticatedAccount account);

  /**
   * Tra cứu chi tiết đơn dịch vụ theo ID.
   *
   * @param orderId ID đơn
   * @param account tài khoản người dùng thực hiện (Photographer chủ đơn, Film Lab Owner, hoặc Admin)
   * @return ServiceOrderResponse
   */
  ServiceOrderResponse getOrderById(Long orderId, AuthenticatedAccount account);

  /**
   * Tra cứu chi tiết đơn dịch vụ theo mã đơn (orderCode).
   *
   * @param orderCode mã đơn
   * @param account tài khoản người dùng
   * @return ServiceOrderResponse
   */
  ServiceOrderResponse getOrderByCode(String orderCode, AuthenticatedAccount account);

  /**
   * Danh sách đơn hàng của Photographer.
   */
  Page<ServiceOrderResponse> getPhotographerOrders(
      ServiceOrderStatus status, Pageable pageable, AuthenticatedAccount account);

  /**
   * Danh sách đơn hàng của Film Lab.
   */
  Page<ServiceOrderResponse> getLabOrders(
      Long labId, ServiceOrderStatus status, Pageable pageable, AuthenticatedAccount account);

  /**
   * Film Lab chấp nhận đơn hàng (UC22). Chuyển đơn sang AWAITING_FILM.
   */
  ServiceOrderResponse acceptOrder(Long orderId, AuthenticatedAccount account);

  /**
   * Film Lab từ chối đơn hàng (UC22, BR-39). Hoàn tiền nếu đã thanh toán, chuyển đơn sang CANCELLED.
   */
  ServiceOrderResponse rejectOrder(Long orderId, RejectServiceOrderRequest request, AuthenticatedAccount account);

  /**
   * Film Lab xác nhận nhận phim và chốt giá thực tế (UC23, BR-18, BR-25, BR-26).
   * So sánh giá thực và tiền đã thu:
   * - Thiếu: chuyển sang AWAITING_TOP_UP (chờ nạp thêm trước khi xử lý/giao kết quả).
   * - Đủ/Dư: chuyển sang AWAITING_MACHINE_ASSIGNMENT (hoặc IN_PROCESSING) và hoàn tiền phần dư nếu có.
   */
  ServiceOrderResponse confirmReceiptAndFinalizePrice(
      Long orderId, ConfirmReceiptAndFinalizePriceRequest request, AuthenticatedAccount account);

  /**
   * Khách hàng hủy đơn (UC60, BR-38).
   * - Trước khi Lab nhận phim: hoàn 100% tiền đã trả.
   * - Sau khi Lab đã nhận phim: trừ phí hủy (nếu có cấu hình), hoàn phần còn lại.
   */
  ServiceOrderResponse cancelOrder(
      Long orderId, CancelServiceOrderRequest request, AuthenticatedAccount account);

  /**
   * Đóng đơn dịch vụ (UC29, BR-24): Khách xác nhận đã nhận kết quả hoặc hệ thống đóng.
   * Chuyển trạng thái sang CLOSED.
   */
  ServiceOrderResponse closeOrder(Long orderId, AuthenticatedAccount account);
}

