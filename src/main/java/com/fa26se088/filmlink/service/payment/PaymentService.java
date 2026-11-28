package com.fa26se088.filmlink.service.payment;

import com.fa26se088.filmlink.dto.payment.request.CreateVnPayPaymentUrlRequest;
import com.fa26se088.filmlink.dto.payment.response.OrderPaymentSummaryResponse;
import com.fa26se088.filmlink.dto.payment.response.PaymentLink;
import com.fa26se088.filmlink.dto.payment.response.PaymentResponse;
import com.fa26se088.filmlink.dto.payment.response.PaymentReturnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayIpnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayPaymentUrlResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import java.util.List;
import java.util.Map;

/**
 * Service thanh toán trực tuyến và đối soát (UC30, BR-09, BR-18).
 *
 * <p>Năm method đầu là contract SPEC-00 §4.5: domain {@code order} (tầng 3) và {@code marketplace}
 * gọi xuống; payment (tầng 2) không đọc đơn hay giao dịch bán, số tiền do caller tính và truyền vào.
 */
public interface PaymentService {

  /** UC30: purpose ORDER_PAYMENT, method VNPAY, status PENDING; returns the VNPay URL (valid 15 min). */
  PaymentLink createOrderPayment(
      Long serviceOrderId, Long payerAccountId, long amountVnd, String clientIp);

  /** BR-18: purpose ORDER_TOP_UP. */
  PaymentLink createOrderTopUp(
      Long serviceOrderId, Long payerAccountId, long amountVnd, String clientIp);

  /** BR-18/BR-38/BR-39: purpose ORDER_REFUND (direction OUT). */
  void createOrderRefund(Long serviceOrderId, long amountVnd, String reason);

  /** UC62: purpose SALE_PAYMENT. */
  PaymentLink createSalePayment(
      Long consignmentSaleId, Long buyerAccountId, long amountVnd, String clientIp);

  /** Sum of SUCCEEDED payments IN minus OUT for the order. */
  long netPaidForOrder(Long serviceOrderId);

  /** Tóm tắt thanh toán mới nhất của đơn dịch vụ (trả {@code null} nếu đơn chưa có giao dịch). */
  OrderPaymentSummaryResponse getOrderPaymentSummary(Long serviceOrderId, String clientIp);

  /** UC60: huỷ các giao dịch {@code PENDING} của đơn khi khách huỷ đơn {@code AWAITING_PAYMENT}. */
  void cancelPendingOrderPayments(Long serviceOrderId, String reason);

  /** UC30: tạo lại hoặc lấy lại link thanh toán VNPay cho đơn đang chờ thanh toán trực tuyến. */
  PaymentLink recreateOrderPaymentLink(
      Long serviceOrderId, AuthenticatedAccount currentAccount, String clientIp);

  /**
   * Xử lý IPN (server-to-server) từ VNPay (UC30). Idempotent: IPN lặp lại không đổi trạng thái
   * (BR-09). Thành công thì publish {@code PaymentSucceededEvent} trong cùng transaction.
   *
   * @param params tập tham số callback từ VNPay
   * @return {@link VnPayIpnResponse} theo chuẩn của VNPay
   */
  VnPayIpnResponse processVnPayIpn(Map<String, String> params);

  /**
   * Người dùng được redirect về return URL (SRS 4.1). Chỉ kiểm chữ ký và trả kết quả, không đổi
   * trạng thái (trạng thái chỉ do IPN đổi).
   *
   * @param params tập tham số redirect từ VNPay
   * @return {@link PaymentReturnResponse} thông tin hiển thị cho FE
   */
  PaymentReturnResponse processVnPayReturn(Map<String, String> params);

  /** Tra cứu chi tiết thanh toán theo ID (Admin hoặc người thanh toán). */
  PaymentResponse getPaymentById(Long id, AuthenticatedAccount currentAccount);

  /** Tra cứu chi tiết thanh toán theo mã giao dịch nội bộ (Admin hoặc người thanh toán). */
  PaymentResponse getPaymentByTransactionCode(
      String transactionCode, AuthenticatedAccount currentAccount);

  /**
   * Lịch sử thanh toán / hoàn tiền của một đơn. Quyền kiểm bằng dữ liệu payment: Admin, hoặc mọi
   * payment của đơn có {@code counterparty_account_id} = người gọi.
   */
  List<PaymentResponse> getPaymentsByOrderId(
      Long serviceOrderId, AuthenticatedAccount currentAccount);

  /** SPIKE — tạo URL sandbox thử nghiệm không liên kết đơn hàng. */
  VnPayPaymentUrlResponse createVnPaySandboxUrl(
      CreateVnPayPaymentUrlRequest request, String clientIp);
}
