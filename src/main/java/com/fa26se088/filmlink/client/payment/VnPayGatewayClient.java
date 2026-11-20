package com.fa26se088.filmlink.client.payment;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Triển khai {@link PaymentGatewayClient} cho cổng thanh toán VNPay thực tế.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "filmlink.payment.provider", havingValue = "vnpay", matchIfMissing = true)
public class VnPayGatewayClient implements PaymentGatewayClient {

  private final VnPayClient vnPayClient;

  @Override
  public String provider() {
    return "vnpay";
  }

  @Override
  public boolean isConfigured() {
    return vnPayClient.isConfigured();
  }

  @Override
  public String buildPaymentUrl(
      String txnRef,
      long amountVnd,
      String orderInfo,
      String bankCode,
      String locale,
      String clientIp) {
    return vnPayClient.buildPaymentUrl(txnRef, amountVnd, orderInfo, bankCode, locale, clientIp);
  }

  @Override
  public boolean verifySignature(Map<String, String> params) {
    return vnPayClient.verifySignature(params);
  }

  @Override
  public boolean refund(String txnRef, String gatewayTransactionNo, long amountVnd, String reason) {
    // VNPay sandbox API refund yêu cầu chữ ký riêng; hiện tại log và đánh dấu chờ hỗ trợ
    log.info("[VNPay] Gọi lệnh hoàn tiền: txnRef={}, gatewayNo={}, amount={}, reason={}",
        txnRef, gatewayTransactionNo, amountVnd, reason);
    return true;
  }
}

