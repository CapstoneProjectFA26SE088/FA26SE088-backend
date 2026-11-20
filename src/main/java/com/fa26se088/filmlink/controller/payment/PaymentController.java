package com.fa26se088.filmlink.controller.payment;

import com.fa26se088.filmlink.dto.payment.request.CreateOrderPaymentRequest;
import com.fa26se088.filmlink.dto.payment.request.CreateVnPayPaymentUrlRequest;
import com.fa26se088.filmlink.dto.payment.response.PaymentResponse;
import com.fa26se088.filmlink.dto.payment.response.PaymentReturnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayIpnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayPaymentUrlResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý thanh toán trực tuyến (UC30, BR-09).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Quản lý thanh toán trực tuyến và webhook đối soát")
public class PaymentController {

  private final PaymentService paymentService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Tạo URL thanh toán trực tuyến cho đơn hàng (UC30)",
      description = "Chủ đơn hàng tạo phiên thanh toán VNPay. Trả về paymentUrl hiệu lực trong 15 phút.")
  public PaymentResponse createOrderPaymentUrl(
      @Valid @RequestBody CreateOrderPaymentRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account,
      HttpServletRequest httpRequest) {
    return paymentService.createOrderPaymentUrl(request, account, httpRequest.getRemoteAddr());
  }

  @PostMapping("/vnpay/ipn")
  @Operation(
      summary = "Webhook IPN xử lý kết quả thanh toán từ VNPay (POST)",
      description = "Cổng thanh toán VNPay gọi trực tiếp server-to-server. Yêu cầu tính Idempotent.")
  public VnPayIpnResponse processVnPayIpnPost(@RequestParam Map<String, String> allParams) {
    log.info("[PaymentController] Nhận POST IPN từ VNPay: txnRef={}", allParams.get("vnp_TxnRef"));
    return paymentService.processVnPayIpn(allParams);
  }

  @GetMapping("/vnpay/ipn")
  @Operation(
      summary = "Webhook IPN xử lý kết quả thanh toán từ VNPay (GET)",
      description = "Dành cho trường hợp VNPay gọi IPN qua method GET với query parameters.")
  public VnPayIpnResponse processVnPayIpnGet(@RequestParam Map<String, String> allParams) {
    log.info("[PaymentController] Nhận GET IPN từ VNPay: txnRef={}", allParams.get("vnp_TxnRef"));
    return paymentService.processVnPayIpn(allParams);
  }

  @GetMapping("/vnpay/return")
  @Operation(
      summary = "Xử lý redirect người dùng từ VNPay trở về website FilmLink (SRS 4.1)",
      description = "Kiểm tra chữ ký và hiển thị kết quả cho khách hàng. Không thay đổi trạng thái đơn hàng.")
  public PaymentReturnResponse processVnPayReturn(@RequestParam Map<String, String> allParams) {
    log.info("[PaymentController] Nhận return từ VNPay: txnRef={}", allParams.get("vnp_TxnRef"));
    return paymentService.processVnPayReturn(allParams);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Xem chi tiết giao dịch thanh toán theo ID")
  public PaymentResponse getPaymentById(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedAccount account) {
    return paymentService.getPaymentById(id, account);
  }

  @GetMapping("/by-code/{transactionCode}")
  @Operation(summary = "Xem chi tiết giao dịch thanh toán theo mã giao dịch nội bộ (txnRef)")
  public PaymentResponse getPaymentByTransactionCode(
      @PathVariable String transactionCode, @AuthenticationPrincipal AuthenticatedAccount account) {
    return paymentService.getPaymentByTransactionCode(transactionCode, account);
  }

  @GetMapping("/by-order/{serviceOrderId}")
  @Operation(summary = "Lấy toàn bộ lịch sử thanh toán / hoàn tiền của một đơn hàng")
  public List<PaymentResponse> getPaymentsByOrderId(
      @PathVariable Long serviceOrderId, @AuthenticationPrincipal AuthenticatedAccount account) {
    return paymentService.getPaymentsByOrderId(serviceOrderId, account);
  }

  @PostMapping("/vnpay/sandbox-url")
  @Operation(
      summary = "SPIKE: tạo URL thanh toán VNPay sandbox độc lập",
      description = "Không lưu giao dịch vào DB.")
  public VnPayPaymentUrlResponse createVnPaySandboxUrl(
      @Valid @RequestBody CreateVnPayPaymentUrlRequest request, HttpServletRequest httpRequest) {
    return paymentService.createVnPaySandboxUrl(request, httpRequest.getRemoteAddr());
  }
}
