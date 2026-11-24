package com.fa26se088.filmlink.client.payment;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bản mock của {@link PaymentGatewayClient} dùng khi cấu hình {@code filmlink.payment.provider=mock}
 * để kiểm thử cục bộ hoặc chạy integration test không cần kết nối VNPay thật.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "filmlink.payment.provider", havingValue = "mock")
public class MockPaymentGatewayClient implements PaymentGatewayClient {

  @Override
  public String provider() {
    return "mock";
  }

  @Override
  public boolean isConfigured() {
    return true;
  }

  @Override
  public String buildPaymentUrl(
      String txnRef,
      long amountVnd,
      String orderInfo,
      String bankCode,
      String locale,
      String clientIp) {
    log.info("[MockPayment] Tạo URL giả lập: txnRef={}, amount={}", txnRef, amountVnd);
    return "https://mock-payment.filmlink.vn/checkout?txnRef=" + txnRef + "&amount=" + amountVnd;
  }

  @Override
  public boolean verifySignature(Map<String, String> params) {
    String hash = params.get("vnp_SecureHash");
    // Giả lập: chữ ký hợp lệ nếu không cố tình truyền "invalid"
    return hash != null && !hash.equalsIgnoreCase("invalid");
  }

  @Override
  public boolean refund(String txnRef, String gatewayTransactionNo, long amountVnd, String reason) {
    log.info("[MockPayment] Hoàn tiền giả lập thành công: txnRef={}, amount={}", txnRef, amountVnd);
    return true;
  }
}

