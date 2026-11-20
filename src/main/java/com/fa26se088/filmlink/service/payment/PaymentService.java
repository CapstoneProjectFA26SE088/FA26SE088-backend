package com.fa26se088.filmlink.service.payment;

import com.fa26se088.filmlink.dto.payment.request.CreateOrderPaymentRequest;
import com.fa26se088.filmlink.dto.payment.request.CreateVnPayPaymentUrlRequest;
import com.fa26se088.filmlink.dto.payment.response.PaymentResponse;
import com.fa26se088.filmlink.dto.payment.response.PaymentReturnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayIpnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayPaymentUrlResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import java.util.List;
import java.util.Map;

/**
 * Service quản lý thanh toán trực tuyến và đối soát (UC30, BR-09).
 */
public interface PaymentService {

  /**
   * Tạo phiên thanh toán trực tuyến (VNPay) cho đơn hàng (UC30).
   *
   * @param request thông tin tạo thanh toán cho đơn hàng
   * @param currentAccount tài khoản đang đăng nhập (phải là chủ đơn hoặc Admin)
   * @param clientIp IP người thanh toán
   * @return {@link PaymentResponse} chứa URL thanh toán
   */
  PaymentResponse createOrderPaymentUrl(
      CreateOrderPaymentRequest request, AuthenticatedAccount currentAccount, String clientIp);

  /**
   * Xử lý webhook IPN (server-to-server callback) từ cổng thanh toán VNPay (UC30).
   * Đảm bảo tính idempotent tuyệt đối (BR-09, Q9, Q10).
   *
   * @param params tập tham số callback từ VNPay
   * @return {@link VnPayIpnResponse} theo chuẩn của VNPay
   */
  VnPayIpnResponse processVnPayIpn(Map<String, String> params);

  /**
   * Xử lý khi người dùng được redirect về trang return URL (SRS 4.1).
   * Chỉ kiểm tra chữ ký và hiển thị kết quả, KHÔNG cập nhật trạng thái đơn.
   *
   * @param params tập tham số redirect từ VNPay
   * @return {@link PaymentReturnResponse} thông tin hiển thị cho FE
   */
  PaymentReturnResponse processVnPayReturn(Map<String, String> params);

  /**
   * Tra cứu chi tiết thanh toán theo ID (Admin hoặc chủ đơn).
   */
  PaymentResponse getPaymentById(Long id, AuthenticatedAccount currentAccount);

  /**
   * Tra cứu chi tiết thanh toán theo mã giao dịch nội bộ (Admin hoặc chủ đơn).
   */
  PaymentResponse getPaymentByTransactionCode(
      String transactionCode, AuthenticatedAccount currentAccount);

  /**
   * Lấy danh sách mọi khoản thanh toán / hoàn tiền của một đơn hàng.
   */
  List<PaymentResponse> getPaymentsByOrderId(
      Long serviceOrderId, AuthenticatedAccount currentAccount);

  /**
   * Tính tổng số tiền mà khách đã thanh toán thành công (hợp lệ) cho đơn hàng.
   */
  Long getTotalPaidAmount(Long serviceOrderId);

  /**
   * Thực hiện hoàn tiền tự động hoặc ghi nhận yêu cầu hoàn tiền cho đơn hàng (UC22, BR-38, BR-39).
   *
   * @param serviceOrderId ID đơn hàng
   * @param refundAmount Số tiền hoàn (VND)
   * @param reason Lý do hoàn tiền
   * @return PaymentResponse của khoản hoàn tiền (PaymentDirection.OUT, PaymentPurpose.ORDER_REFUND)
   */
  PaymentResponse refundOrder(Long serviceOrderId, Long refundAmount, String reason);

  /**
   * SPIKE — tạo URL sandbox thử nghiệm không liên kết đơn hàng.
   */
  VnPayPaymentUrlResponse createVnPaySandboxUrl(
      CreateVnPayPaymentUrlRequest request, String clientIp);
}
