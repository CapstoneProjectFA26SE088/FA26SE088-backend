package com.fa26se088.filmlink.service.payment.impl;

import com.fa26se088.filmlink.client.payment.PaymentGatewayClient;
import com.fa26se088.filmlink.client.payment.VnPayClient;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.payment.request.CreateVnPayPaymentUrlRequest;
import com.fa26se088.filmlink.dto.payment.response.PaymentLink;
import com.fa26se088.filmlink.dto.payment.response.PaymentResponse;
import com.fa26se088.filmlink.dto.payment.response.PaymentReturnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayIpnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayPaymentUrlResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.entity.payment.Payment;
import com.fa26se088.filmlink.entity.payment.PaymentGatewayEvent;
import com.fa26se088.filmlink.entity.payment.enums.PaymentConfirmationStatus;
import com.fa26se088.filmlink.entity.payment.enums.PaymentDirection;
import com.fa26se088.filmlink.entity.payment.enums.PaymentGatewayEventType;
import com.fa26se088.filmlink.entity.payment.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import com.fa26se088.filmlink.entity.payment.enums.PaymentStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.payment.PaymentMapper;
import com.fa26se088.filmlink.repository.payment.PaymentGatewayEventRepository;
import com.fa26se088.filmlink.repository.payment.PaymentRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.payment.PaymentService;
import com.fa26se088.filmlink.service.payment.event.PaymentSucceededEvent;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai dịch vụ thanh toán lõi (UC30, BR-09, BR-17, BR-18).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

  private static final String DEFAULT_LOCALE = "vn";
  private static final String TXN_REF_ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  private static final int TXN_REF_LENGTH = 12;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final PaymentRepository paymentRepository;
  private final PaymentGatewayEventRepository paymentGatewayEventRepository;
  private final PaymentGatewayClient paymentGatewayClient;
  private final PaymentMapper paymentMapper;
  private final AuditLogService auditLogService;
  private final SystemParameterService systemParameterService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;
  private final VnPayClient vnPayClient; // Giữ để phục vụ method spike sandbox URL cũ

  /**
   * UC30: tạo giao dịch ORDER_PAYMENT (IN, VNPAY, PENDING) và URL VNPay. Số tiền do {@code order}
   * tính; gọi lại khi còn giao dịch PENDING cùng số tiền chưa hết hạn thì trả lại link đó.
   */
  @Override
  @Transactional
  public PaymentLink createOrderPayment(
      Long serviceOrderId, Long payerAccountId, long amountVnd, String clientIp) {
    return createOrderInPayment(
        PaymentPurpose.ORDER_PAYMENT,
        serviceOrderId,
        payerAccountId,
        amountVnd,
        clientIp,
        "Thanh toan don hang FilmLink #" + serviceOrderId);
  }

  /** BR-18: thanh toán bổ sung (ORDER_TOP_UP) sau khi lab chốt giá; số tiền do {@code order} tính. */
  @Override
  @Transactional
  public PaymentLink createOrderTopUp(
      Long serviceOrderId, Long payerAccountId, long amountVnd, String clientIp) {
    return createOrderInPayment(
        PaymentPurpose.ORDER_TOP_UP,
        serviceOrderId,
        payerAccountId,
        amountVnd,
        clientIp,
        "Thanh toan bo sung don hang FilmLink #" + serviceOrderId);
  }

  /** UC62: thanh toán mua hàng ký gửi — làm ở P-07. */
  @Override
  public PaymentLink createSalePayment(
      Long consignmentSaleId, Long buyerAccountId, long amountVnd, String clientIp) {
    throw new UnsupportedOperationException("TODO P-07: createSalePayment (UC62) chưa làm");
  }

  /** Tổng SUCCEEDED chiều IN trừ tổng SUCCEEDED chiều OUT của đơn. */
  @Override
  @Transactional(readOnly = true)
  public long netPaidForOrder(Long serviceOrderId) {
    long net = 0;
    for (Payment p : paymentRepository.findByServiceOrderId(serviceOrderId)) {
      if (p.getStatus() != PaymentStatus.SUCCEEDED) {
        continue;
      }
      net += p.getDirection() == PaymentDirection.IN ? p.getAmountVnd() : -p.getAmountVnd();
    }
    return net;
  }

  private PaymentLink createOrderInPayment(
      PaymentPurpose purpose,
      Long serviceOrderId,
      Long payerAccountId,
      long amountVnd,
      String clientIp,
      String orderInfo) {
    if (serviceOrderId == null || payerAccountId == null) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Thiếu mã đơn hàng hoặc tài khoản thanh toán.");
    }
    if (amountVnd <= 0) {
      throw new BusinessException(
          ErrorCode.PAYMENT_INVALID_STATE, "Số tiền thanh toán phải lớn hơn 0.");
    }
    if (!paymentGatewayClient.isConfigured()) {
      throw new BusinessException(
          ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED,
          "Cổng thanh toán chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
    }

    Instant now = clock.instant();

    // Gọi lần hai khi giao dịch PENDING còn hạn -> trả lại link của giao dịch đó, không tạo trùng.
    Optional<Payment> pending =
        paymentRepository.findReusablePending(
            serviceOrderId, purpose, PaymentStatus.PENDING, amountVnd, now);
    if (pending.isPresent()) {
      Payment existing = pending.get();
      String url =
          paymentGatewayClient.buildPaymentUrl(
              existing.getTransactionCode(),
              existing.getAmountVnd(),
              orderInfo,
              null,
              DEFAULT_LOCALE,
              clientIp);
      return new PaymentLink(
          existing.getId(), existing.getTransactionCode(), url, existing.getPaymentUrlExpiresAt());
    }

    String txnRef = generateTxnRef();
    int ttlMinutes = systemParameterService.getInteger(SystemParameterKey.PAYMENT_LINK_TTL_MINUTES);
    Instant expiresAt = now.plus(Duration.ofMinutes(ttlMinutes));

    Payment payment = new Payment();
    payment.setTransactionCode(txnRef);
    payment.setPurpose(purpose);
    payment.setDirection(PaymentDirection.IN);
    payment.setMethod(PaymentMethod.VNPAY);
    payment.setStatus(PaymentStatus.PENDING);
    payment.setAmountVnd(amountVnd);
    payment.setServiceOrderId(serviceOrderId);
    payment.setCounterpartyAccountId(payerAccountId);
    payment.setPaymentUrlExpiresAt(expiresAt);
    payment.setConfirmationStatus(PaymentConfirmationStatus.NOT_REQUIRED);

    Payment saved = paymentRepository.save(payment);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.PAYMENT_CREATED,
            AuditTargetType.PAYMENT,
            saved.getId(),
            "Tạo lượt thanh toán "
                + purpose
                + " "
                + txnRef
                + " cho đơn hàng #"
                + serviceOrderId
                + " số tiền "
                + amountVnd
                + " VND",
            null,
            null));

    String paymentUrl =
        paymentGatewayClient.buildPaymentUrl(
            txnRef, amountVnd, orderInfo, null, DEFAULT_LOCALE, clientIp);
    return new PaymentLink(saved.getId(), txnRef, paymentUrl, expiresAt);
  }

  @Override
  @Transactional
  public VnPayIpnResponse processVnPayIpn(Map<String, String> params) {
    String txnRef = params.get("vnp_TxnRef");
    String amountStr = params.get("vnp_Amount");
    String vnpResponseCode = params.get("vnp_ResponseCode");
    String vnpTransactionNo = params.get("vnp_TransactionNo");
    String vnpBankCode = params.get("vnp_BankCode");

    // 1. Ghi nhận raw payload sự kiện callback (append-only)
    PaymentGatewayEvent event = new PaymentGatewayEvent();
    event.setProvider(paymentGatewayClient.provider());
    event.setEventType(PaymentGatewayEventType.IPN);
    event.setTransactionCode(txnRef != null ? txnRef : "UNKNOWN");
    event.setGatewayTransactionNo(vnpTransactionNo);
    event.setResponseCode(vnpResponseCode);
    if (amountStr != null) {
      try {
        event.setAmountVnd(Long.parseLong(amountStr) / 100);
      } catch (NumberFormatException ignored) {
      }
    }
    boolean isSignatureValid = paymentGatewayClient.verifySignature(params);
    event.setSignatureValid(isSignatureValid);
    event.setRawPayload(toPayloadString(params));
    event.setReceivedAt(clock.instant());

    // 2. Kiểm tra tính hợp lệ của chữ ký HMAC
    if (!isSignatureValid) {
      paymentGatewayEventRepository.save(event);
      log.warn("[VNPay IPN] Chữ ký không hợp lệ cho giao dịch: {}", txnRef);
      return VnPayIpnResponse.invalidSignature();
    }

    // 3. Tra cứu giao dịch trong DB
    Optional<Payment> optPayment = paymentRepository.findByTransactionCode(txnRef);
    if (optPayment.isEmpty()) {
      paymentGatewayEventRepository.save(event);
      log.warn("[VNPay IPN] Không tìm thấy mã giao dịch: {}", txnRef);
      return VnPayIpnResponse.orderNotFound();
    }

    Payment payment = optPayment.get();
    event.setPaymentId(payment.getId());

    // 4. Kiểm tra số tiền nhận được so với số tiền của giao dịch (VNPay nhân 100)
    long expectedAmountX100 = payment.getAmountVnd() * 100;
    long receivedAmountX100;
    try {
      receivedAmountX100 = Long.parseLong(amountStr != null ? amountStr : "0");
    } catch (NumberFormatException e) {
      paymentGatewayEventRepository.save(event);
      return VnPayIpnResponse.invalidAmount();
    }

    if (expectedAmountX100 != receivedAmountX100) {
      paymentGatewayEventRepository.save(event);
      log.warn(
          "[VNPay IPN] Số tiền không khớp cho txnRef={}: kỳ vọng={}, nhận={}",
          txnRef,
          expectedAmountX100,
          receivedAmountX100);
      return VnPayIpnResponse.invalidAmount();
    }

    // 5. Kiểm tra tính Idempotent: Nếu đã thành công trước đó thì không làm lại
    if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
      paymentGatewayEventRepository.save(event);
      log.info("[VNPay IPN] Giao dịch đã được xác nhận thành công trước đó: txnRef={}", txnRef);
      return VnPayIpnResponse.alreadyConfirmed();
    }

    // 6. Cập nhật trạng thái thanh toán
    if ("00".equals(vnpResponseCode)) {
      payment.setStatus(PaymentStatus.SUCCEEDED);
      payment.setSucceededAt(clock.instant());
      payment.setGatewayTransactionNo(vnpTransactionNo);
      payment.setGatewayResponseCode(vnpResponseCode);
      payment.setGatewayBankCode(vnpBankCode);
      Payment updated = paymentRepository.save(payment);
      paymentGatewayEventRepository.save(event);

      auditLogService.recordSuccess(
          new AuditEntry(
              AuditAction.PAYMENT_SUCCEEDED,
              AuditTargetType.PAYMENT,
              updated.getId(),
              "Thanh toán thành công số tiền "
                  + updated.getAmountVnd()
                  + " VND qua "
                  + updated.getMethod()
                  + " (Mã GD cổng: "
                  + vnpTransactionNo
                  + ")",
              null,
              null));

      // Phát sự kiện đồng bộ trong cùng transaction để cập nhật trạng thái đơn hàng (Q9)
      eventPublisher.publishEvent(
          new PaymentSucceededEvent(
              updated.getId(),
              updated.getPurpose(),
              updated.getServiceOrderId(),
              updated.getConsignmentSaleId(),
              updated.getAmountVnd(),
              updated.getSucceededAt()));

      log.info(
          "[VNPay IPN] Xác nhận thanh toán thành công: txnRef={}, amount={}",
          txnRef,
          updated.getAmountVnd());
    } else {
      payment.setStatus(PaymentStatus.FAILED);
      payment.setFailureReason("VNPay báo mã lỗi: " + vnpResponseCode);
      payment.setGatewayResponseCode(vnpResponseCode);
      payment.setGatewayTransactionNo(vnpTransactionNo);
      Payment updated = paymentRepository.save(payment);
      paymentGatewayEventRepository.save(event);

      auditLogService.recordSuccess(
          new AuditEntry(
              AuditAction.PAYMENT_FAILED,
              AuditTargetType.PAYMENT,
              updated.getId(),
              "Thanh toán thất bại, mã lỗi cổng: " + vnpResponseCode,
              null,
              null));

      log.warn(
          "[VNPay IPN] Thanh toán thất bại từ cổng: txnRef={}, responseCode={}",
          txnRef,
          vnpResponseCode);
    }

    return VnPayIpnResponse.success();
  }

  @Override
  @Transactional(readOnly = true)
  public PaymentReturnResponse processVnPayReturn(Map<String, String> params) {
    String txnRef = params.get("vnp_TxnRef");
    String vnpResponseCode = params.get("vnp_ResponseCode");
    String amountStr = params.get("vnp_Amount");

    boolean signatureValid = paymentGatewayClient.verifySignature(params);
    boolean success = signatureValid && "00".equals(vnpResponseCode);

    Long amountVnd = null;
    if (amountStr != null) {
      try {
        amountVnd = Long.parseLong(amountStr) / 100;
      } catch (NumberFormatException ignored) {
      }
    }

    Long serviceOrderId = null;
    if (txnRef != null) {
      Optional<Payment> optPayment = paymentRepository.findByTransactionCode(txnRef);
      if (optPayment.isPresent()) {
        serviceOrderId = optPayment.get().getServiceOrderId();
      }
    }

    String message;
    if (!signatureValid) {
      message = "Chữ ký bảo mật không hợp lệ";
    } else if ("00".equals(vnpResponseCode)) {
      message = "Thanh toán thành công";
    } else {
      message = "Giao dịch không thành công hoặc đã bị hủy (mã " + vnpResponseCode + ")";
    }

    return PaymentReturnResponse.builder()
        .transactionCode(txnRef)
        .success(success)
        .responseCode(vnpResponseCode)
        .message(message)
        .amountVnd(amountVnd)
        .serviceOrderId(serviceOrderId)
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public PaymentResponse getPaymentById(Long id, AuthenticatedAccount currentAccount) {
    if (currentAccount == null) {
      throw new BusinessException(
          ErrorCode.AUTHENTICATION_REQUIRED, "Cần đăng nhập để tra cứu thanh toán.");
    }

    Payment payment =
        paymentRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.PAYMENT_NOT_FOUND, "Không tìm thấy giao dịch với ID: " + id));

    checkPaymentAccess(payment, currentAccount);
    return paymentMapper.toResponse(payment);
  }

  @Override
  @Transactional(readOnly = true)
  public PaymentResponse getPaymentByTransactionCode(
      String transactionCode, AuthenticatedAccount currentAccount) {
    if (currentAccount == null) {
      throw new BusinessException(
          ErrorCode.AUTHENTICATION_REQUIRED, "Cần đăng nhập để tra cứu thanh toán.");
    }

    Payment payment =
        paymentRepository
            .findByTransactionCode(transactionCode)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "Không tìm thấy giao dịch với mã: " + transactionCode));

    checkPaymentAccess(payment, currentAccount);
    return paymentMapper.toResponse(payment);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PaymentResponse> getPaymentsByOrderId(
      Long serviceOrderId, AuthenticatedAccount currentAccount) {
    if (currentAccount == null) {
      throw new BusinessException(
          ErrorCode.AUTHENTICATION_REQUIRED, "Cần đăng nhập để tra cứu thanh toán.");
    }

    List<Payment> payments =
        paymentRepository.findByServiceOrderIdOrderByCreatedAtDesc(serviceOrderId);

    // Không đọc order (payment tầng 2): Admin, hoặc người gọi là counterparty của mọi payment của
    // đơn. Đơn chưa có payment trả danh sách rỗng, không lộ đơn có tồn tại hay không.
    if (currentAccount.role() != UserAccountRole.ADMIN
        && payments.stream()
            .anyMatch(p -> !currentAccount.id().equals(p.getCounterpartyAccountId()))) {
      throw new BusinessException(
          ErrorCode.PAYMENT_ACCESS_DENIED,
          "Bạn không có quyền xem thông tin thanh toán của đơn hàng này.");
    }
    return payments.stream().map(paymentMapper::toResponse).toList();
  }

  @Override
  public VnPayPaymentUrlResponse createVnPaySandboxUrl(
      CreateVnPayPaymentUrlRequest request, String clientIp) {
    if (!vnPayClient.isConfigured()) {
      throw new BusinessException(
          ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED, "Cổng thanh toán VNPay chưa được cấu hình.");
    }
    String txnRef = generateTxnRef();
    String locale = request.locale() == null ? DEFAULT_LOCALE : request.locale();
    String paymentUrl =
        vnPayClient.buildPaymentUrl(
            txnRef,
            request.amountVnd(),
            "Thanh toan thu nghiem FilmLink " + txnRef,
            request.bankCode(),
            locale,
            clientIp);
    return new VnPayPaymentUrlResponse(paymentUrl);
  }

  /**
   * BR-18/BR-38/BR-39: hoàn tiền đơn (ORDER_REFUND, OUT) liên kết khoản IN SUCCEEDED gần nhất. Gốc
   * VNPAY thì gọi cổng hoàn tiền; cổng từ chối hoặc lỗi thì {@code AWAITING_MANUAL} cho Admin.
   */
  @Override
  @Transactional
  public void createOrderRefund(Long serviceOrderId, long amountVnd, String reason) {
    if (amountVnd <= 0) {
      throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE, "Số tiền hoàn phải lớn hơn 0.");
    }

    // Khoản thanh toán IN gần nhất đã SUCCEEDED của đơn, dùng làm refunded_payment_id
    List<Payment> succeededInPayments =
        paymentRepository.findByServiceOrderIdOrderByCreatedAtDesc(serviceOrderId).stream()
            .filter(
                p ->
                    p.getStatus() == PaymentStatus.SUCCEEDED
                        && p.getDirection() == PaymentDirection.IN)
            .toList();

    if (succeededInPayments.isEmpty()) {
      log.warn(
          "[PaymentService] Đơn #{} chưa có khoản thanh toán IN nào thành công để hoàn tiền",
          serviceOrderId);
      throw new BusinessException(
          ErrorCode.PAYMENT_INVALID_STATE,
          "Đơn hàng chưa có giao dịch thanh toán thành công để hoàn.");
    }

    Payment originPayment = succeededInPayments.get(0);
    String refundTxnRef = "RF" + generateTxnRef();
    Instant now = clock.instant();

    Payment refund = new Payment();
    refund.setTransactionCode(refundTxnRef);
    refund.setPurpose(PaymentPurpose.ORDER_REFUND);
    refund.setDirection(PaymentDirection.OUT);
    refund.setMethod(originPayment.getMethod());
    refund.setAmountVnd(amountVnd);
    refund.setServiceOrderId(serviceOrderId);
    refund.setCounterpartyAccountId(originPayment.getCounterpartyAccountId());
    refund.setRefundedPaymentId(originPayment.getId());

    // Gốc VNPAY thì thử hoàn qua cổng; lỗi thì chuyển AWAITING_MANUAL
    if (originPayment.getMethod() == PaymentMethod.VNPAY) {
      try {
        boolean gatewayRefundSuccess =
            paymentGatewayClient.refund(
                originPayment.getTransactionCode(),
                originPayment.getGatewayTransactionNo(),
                amountVnd,
                reason);
        if (gatewayRefundSuccess) {
          refund.setStatus(PaymentStatus.SUCCEEDED);
          refund.setSucceededAt(now);
        } else {
          refund.setStatus(PaymentStatus.AWAITING_MANUAL);
          refund.setFailureReason("Cổng thanh toán phản hồi từ chối hoàn tiền tự động.");
        }
      } catch (RuntimeException e) {
        log.error(
            "[PaymentService] Lỗi khi gọi cổng hoàn tiền cho đơn #{}: {}",
            serviceOrderId,
            e.getMessage());
        refund.setStatus(PaymentStatus.AWAITING_MANUAL);
        refund.setFailureReason("Lỗi kết nối cổng thanh toán.");
      }
    } else {
      // COD / PAY_AT_LAB: Admin hoặc Lab hoàn trả thủ công
      refund.setStatus(PaymentStatus.AWAITING_MANUAL);
      refund.setFailureReason("Khoản thu ngoài nền tảng, hoàn tiền thủ công.");
    }

    Payment saved = paymentRepository.save(refund);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.PAYMENT_REFUNDED,
            AuditTargetType.PAYMENT,
            saved.getId(),
            "Hoàn tiền đơn #" + serviceOrderId + " số tiền " + amountVnd + " VND. Lý do: " + reason,
            null,
            null));

    log.info(
        "[PaymentService] Tạo giao dịch hoàn tiền: txnRef={}, status={}, amount={}",
        saved.getTransactionCode(),
        saved.getStatus(),
        saved.getAmountVnd());
  }

  private void checkPaymentAccess(Payment payment, AuthenticatedAccount currentAccount) {
    if (currentAccount.role() == UserAccountRole.ADMIN) {
      return;
    }
    if (payment.getCounterpartyAccountId().equals(currentAccount.id())) {
      return;
    }
    throw new BusinessException(
        ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem thông tin giao dịch này.");
  }

  private static String generateTxnRef() {
    StringBuilder builder = new StringBuilder(TXN_REF_LENGTH);
    for (int i = 0; i < TXN_REF_LENGTH; i++) {
      builder.append(TXN_REF_ALPHABET.charAt(SECURE_RANDOM.nextInt(TXN_REF_ALPHABET.length())));
    }
    return builder.toString();
  }

  private static String toPayloadString(Map<String, String> map) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, String> entry : map.entrySet()) {
      if (!first) sb.append(", ");
      sb.append("\"")
          .append(jsonEscape(entry.getKey()))
          .append("\": \"")
          .append(jsonEscape(entry.getValue()))
          .append("\"");
      first = false;
    }
    sb.append("}");
    return sb.toString();
  }

  /** Cột {@code raw_payload} là jsonb: escape để payload lạ không làm hỏng câu INSERT. */
  private static String jsonEscape(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length());
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
