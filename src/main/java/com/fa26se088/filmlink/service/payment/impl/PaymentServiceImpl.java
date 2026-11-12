package com.fa26se088.filmlink.service.payment.impl;

import com.fa26se088.filmlink.client.payment.PaymentGatewayClient;
import com.fa26se088.filmlink.client.payment.VnPayClient;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.payment.request.CreateOrderPaymentRequest;
import com.fa26se088.filmlink.dto.payment.request.CreateVnPayPaymentUrlRequest;
import com.fa26se088.filmlink.dto.payment.response.PaymentResponse;
import com.fa26se088.filmlink.dto.payment.response.PaymentReturnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayIpnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayPaymentUrlResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
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
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.repository.payment.PaymentGatewayEventRepository;
import com.fa26se088.filmlink.repository.payment.PaymentRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
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
  private static final Duration PAYMENT_TTL = Duration.ofMinutes(15);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final PaymentRepository paymentRepository;
  private final PaymentGatewayEventRepository paymentGatewayEventRepository;
  private final ServiceOrderRepository serviceOrderRepository;
  private final PaymentGatewayClient paymentGatewayClient;
  private final PaymentMapper paymentMapper;
  private final AuditLogService auditLogService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;
  private final VnPayClient vnPayClient; // Giữ để phục vụ method spike sandbox URL cũ

  @Override
  @Transactional
  public PaymentResponse createOrderPaymentUrl(
      CreateOrderPaymentRequest request, AuthenticatedAccount currentAccount, String clientIp) {
    if (currentAccount == null) {
      throw new BusinessException(
          ErrorCode.AUTHENTICATION_REQUIRED, "Cần đăng nhập để thực hiện thanh toán.");
    }

    ServiceOrder order =
        serviceOrderRepository
            .findById(request.serviceOrderId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND,
                        "Không tìm thấy đơn hàng: " + request.serviceOrderId()));

    // Ma trận phân quyền: Chỉ chủ đơn hoặc Admin mới được thanh toán
    if (!order.getPhotographerAccountId().equals(currentAccount.id())
        && currentAccount.role() != UserAccountRole.ADMIN) {
      throw new BusinessException(
          ErrorCode.ORDER_ACCESS_DENIED, "Bạn không có quyền thanh toán cho đơn hàng này.");
    }

    // Kiểm tra trạng thái đơn hợp lệ để thanh toán
    PaymentPurpose purpose;
    long amount;

    if (order.getStatus() == ServiceOrderStatus.AWAITING_PAYMENT) {
      purpose = PaymentPurpose.ORDER_PAYMENT;
      amount = order.getEstimatedTotalVnd();
    } else if (order.getStatus() == ServiceOrderStatus.AWAITING_TOP_UP) {
      purpose = PaymentPurpose.ORDER_TOP_UP;
      long alreadyPaid = calculateAlreadyPaid(order.getId());
      long finalTotal = order.getFinalTotalVnd() != null ? order.getFinalTotalVnd() : 0L;
      amount = finalTotal - alreadyPaid;
      if (amount <= 0) {
        throw new BusinessException(
            ErrorCode.ORDER_ALREADY_PAID, "Đơn hàng đã được thanh toán đủ số tiền.");
      }
    } else {
      throw new BusinessException(
          ErrorCode.ORDER_INVALID_STATE,
          "Đơn hàng không ở trạng thái chờ thanh toán (trạng thái hiện tại: "
              + order.getStatus()
              + ").");
    }

    if (!paymentGatewayClient.isConfigured()) {
      throw new BusinessException(
          ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED,
          "Cổng thanh toán chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
    }

    String txnRef = generateTxnRef();
    Instant now = clock.instant();
    Instant expiresAt = now.plus(PAYMENT_TTL);

    Payment payment = new Payment();
    payment.setTransactionCode(txnRef);
    payment.setPurpose(purpose);
    payment.setDirection(PaymentDirection.IN);
    payment.setMethod(PaymentMethod.VNPAY);
    payment.setStatus(PaymentStatus.PENDING);
    payment.setAmountVnd(amount);
    payment.setServiceOrderId(order.getId());
    payment.setCounterpartyAccountId(currentAccount.id());
    payment.setPaymentUrlExpiresAt(expiresAt);
    payment.setConfirmationStatus(PaymentConfirmationStatus.NOT_REQUIRED);

    Payment saved = paymentRepository.save(payment);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.PAYMENT_CREATED,
            AuditTargetType.PAYMENT,
            saved.getId(),
            "Tạo lượt thanh toán trực tuyến "
                + txnRef
                + " cho đơn hàng "
                + order.getOrderCode()
                + " số tiền "
                + amount
                + " VND",
            null,
            null));

    String locale = request.locale() == null ? DEFAULT_LOCALE : request.locale();
    String orderInfo = "Thanh toan don hang FilmLink " + order.getOrderCode();
    String paymentUrl =
        paymentGatewayClient.buildPaymentUrl(
            txnRef, amount, orderInfo, request.bankCode(), locale, clientIp);

    return paymentMapper.toResponse(saved, paymentUrl);
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
              updated.getTransactionCode(),
              updated.getServiceOrderId(),
              updated.getAmountVnd(),
              updated.getPurpose(),
              updated.getMethod(),
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

    ServiceOrder order =
        serviceOrderRepository
            .findById(serviceOrderId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng: " + serviceOrderId));

    // Admin, chủ đơn hoặc chủ Lab được xem danh sách thanh toán của đơn
    if (currentAccount.role() != UserAccountRole.ADMIN
        && !order.getPhotographerAccountId().equals(currentAccount.id())) {
      throw new BusinessException(
          ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem thông tin thanh toán của đơn hàng này.");
    }

    List<Payment> payments =
        paymentRepository.findByServiceOrderIdOrderByCreatedAtDesc(serviceOrderId);
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

  @Override
  @Transactional(readOnly = true)
  public Long getTotalPaidAmount(Long serviceOrderId) {
    return calculateAlreadyPaid(serviceOrderId);
  }

  @Override
  @Transactional
  public PaymentResponse refundOrder(Long serviceOrderId, Long refundAmount, String reason) {
    if (refundAmount == null || refundAmount <= 0) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số tiền hoàn phải lớn hơn 0.");
    }

    ServiceOrder order = serviceOrderRepository.findById(serviceOrderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng để hoàn tiền."));

    // Tìm khoản thanh toán IN gần nhất đã SUCCEEDED của đơn để liên kết refundedPayment
    List<Payment> succeededInPayments = paymentRepository.findByServiceOrderId(serviceOrderId).stream()
        .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED && p.getDirection() == PaymentDirection.IN)
        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
        .toList();

    if (succeededInPayments.isEmpty()) {
      log.warn("[PaymentService] Đơn #{} chưa có khoản thanh toán IN nào thành công để hoàn tiền", serviceOrderId);
      throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE, "Đơn hàng chưa có giao dịch thanh toán thành công để hoàn.");
    }

    Payment originPayment = succeededInPayments.get(0);
    String refundTxnRef = "RF" + generateTxnRef();
    Instant now = clock.instant();

    Payment refund = new Payment();
    refund.setTransactionCode(refundTxnRef);
    refund.setPurpose(PaymentPurpose.ORDER_REFUND);
    refund.setDirection(PaymentDirection.OUT);
    refund.setMethod(originPayment.getMethod());
    refund.setAmountVnd(refundAmount);
    refund.setServiceOrderId(serviceOrderId);
    refund.setCounterpartyAccountId(originPayment.getCounterpartyAccountId());
    refund.setRefundedPaymentId(originPayment.getId());

    // Nếu phương thức ban đầu là VNPAY thì thử hoàn tiền qua cổng; nếu lỗi thì chuyển AWAITING_MANUAL
    if (originPayment.getMethod() == PaymentMethod.VNPAY) {
      try {
        boolean gatewayRefundSuccess = paymentGatewayClient.refund(
            originPayment.getTransactionCode(),
            originPayment.getGatewayTransactionNo(),
            refundAmount,
            reason
        );
        if (gatewayRefundSuccess) {
          refund.setStatus(PaymentStatus.SUCCEEDED);
          refund.setSucceededAt(now);
        } else {
          refund.setStatus(PaymentStatus.AWAITING_MANUAL);
          refund.setFailureReason("Cổng thanh toán phản hồi từ chối hoàn tiền tự động.");
        }
      } catch (Exception e) {
        log.error("[PaymentService] Lỗi khi gọi cổng hoàn tiền cho đơn #{}: {}", serviceOrderId, e.getMessage());
        refund.setStatus(PaymentStatus.AWAITING_MANUAL);
        refund.setFailureReason("Lỗi kết nối cổng thanh toán: " + e.getMessage());
      }
    } else {
      // Đối với COD hoặc PAY_AT_LAB: chuyển sang AWAITING_MANUAL để Admin hoặc Lab xử lý hoàn trả tiền mặt / chuyển khoản
      refund.setStatus(PaymentStatus.AWAITING_MANUAL);
      refund.setFailureReason("Khoản thu ngoài nền tảng, hoàn tiền thủ công.");
    }

    Payment saved = paymentRepository.save(refund);

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.PAYMENT_REFUNDED,
        AuditTargetType.PAYMENT,
        saved.getId(),
        "Hoàn tiền đơn " + order.getOrderCode() + " số tiền " + refundAmount + " VND. Lý do: " + reason,
        null,
        null
    ));

    log.info("[PaymentService] Tạo giao dịch hoàn tiền thành công: txnRef={}, status={}, amount={}",
        saved.getTransactionCode(), saved.getStatus(), saved.getAmountVnd());

    return paymentMapper.toResponse(saved, null);
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

  private long calculateAlreadyPaid(Long serviceOrderId) {
    return paymentRepository.findByServiceOrderId(serviceOrderId).stream()
        .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED && p.getDirection() == PaymentDirection.IN)
        .mapToLong(Payment::getAmountVnd)
        .sum();
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
      sb.append("\"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
      first = false;
    }
    sb.append("}");
    return sb.toString();
  }
}
