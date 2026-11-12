package com.fa26se088.filmlink.client.payment;

import java.util.Map;

/**
 * Interface trừu tượng hóa cổng thanh toán trực tuyến (VNPay, Mock...) theo quy tắc R9.
 * Service chỉ làm việc với interface này, không phụ thuộc trực tiếp vào SDK bên thứ ba.
 */
public interface PaymentGatewayClient {

  /** Tên định danh của provider: "vnpay", "mock"... */
  String provider();

  /** Kiểm tra cấu hình kết nối của cổng thanh toán đã sẵn sàng hay chưa. */
  boolean isConfigured();

  /**
   * Tạo đường dẫn thanh toán trực tuyến.
   *
   * @param txnRef mã giao dịch nội bộ FilmLink
   * @param amountVnd số tiền thanh toán (VND nguyên)
   * @param orderInfo nội dung hiển thị thanh toán
   * @param bankCode mã ngân hàng (nếu có)
   * @param locale ngôn ngữ hiển thị ("vn" hoặc "en")
   * @param clientIp IP của người thanh toán
   * @return URL redirect khách hàng đến cổng thanh toán
   */
  String buildPaymentUrl(
      String txnRef,
      long amountVnd,
      String orderInfo,
      String bankCode,
      String locale,
      String clientIp);

  /**
   * Xác thực tính toàn vẹn chữ ký dữ liệu callback (IPN / Return) từ cổng thanh toán.
   *
   * @param params tập tham số nhận được từ cổng
   * @return true nếu chữ ký hợp lệ
   */
  boolean verifySignature(Map<String, String> params);

  /**
   * Hoàn tiền giao dịch trực tuyến qua API cổng thanh toán.
   *
   * @param txnRef mã giao dịch gốc của FilmLink
   * @param gatewayTransactionNo mã giao dịch phía cổng thanh toán
   * @param amountVnd số tiền hoàn (VND)
   * @param reason lý do hoàn tiền
   * @return true nếu lệnh hoàn tiền được cổng chấp thuận thành công
   */
  boolean refund(String txnRef, String gatewayTransactionNo, long amountVnd, String reason);
}

