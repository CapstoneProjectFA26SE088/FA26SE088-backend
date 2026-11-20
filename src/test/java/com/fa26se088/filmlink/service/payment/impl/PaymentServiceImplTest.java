package com.fa26se088.filmlink.service.payment.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.client.payment.PaymentGatewayClient;
import com.fa26se088.filmlink.client.payment.VnPayClient;
import com.fa26se088.filmlink.client.payment.VnPayProperties;
import com.fa26se088.filmlink.dto.payment.request.CreateOrderPaymentRequest;
import com.fa26se088.filmlink.dto.payment.request.CreateVnPayPaymentUrlRequest;
import com.fa26se088.filmlink.dto.payment.response.PaymentResponse;
import com.fa26se088.filmlink.dto.payment.response.PaymentReturnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayIpnResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.entity.payment.Payment;
import com.fa26se088.filmlink.entity.payment.PaymentGatewayEvent;
import com.fa26se088.filmlink.entity.payment.enums.PaymentDirection;
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
import com.fa26se088.filmlink.service.payment.event.PaymentSucceededEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

  private static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
  private static final Instant FIXED_NOW = Instant.parse("2026-11-12T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentGatewayEventRepository paymentGatewayEventRepository;
  @Mock private ServiceOrderRepository serviceOrderRepository;
  @Mock private PaymentGatewayClient paymentGatewayClient;
  @Mock private AuditLogService auditLogService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final PaymentMapper paymentMapper = new PaymentMapper();
  private VnPayClient vnPayClient;
  private PaymentServiceImpl service;

  private final AuthenticatedAccount photographerAuth =
      new AuthenticatedAccount(100L, "photographer@example.com", UserAccountRole.PHOTOGRAPHER);
  private final AuthenticatedAccount otherAuth =
      new AuthenticatedAccount(200L, "other@example.com", UserAccountRole.PHOTOGRAPHER);
  private final AuthenticatedAccount adminAuth =
      new AuthenticatedAccount(999L, "admin@example.com", UserAccountRole.ADMIN);

  @BeforeEach
  void setUp() {
    vnPayClient =
        new VnPayClient(
            new VnPayProperties("TESTCODE", "test-only-secret", PAY_URL, "http://localhost/r"));
    service =
        new PaymentServiceImpl(
            paymentRepository,
            paymentGatewayEventRepository,
            serviceOrderRepository,
            paymentGatewayClient,
            paymentMapper,
            auditLogService,
            eventPublisher,
            CLOCK,
            vnPayClient);
  }

  // -------------------------------------------------------------------------
  // 1. SPIKE Sandbox Tests (backward compatibility)
  // -------------------------------------------------------------------------

  @Test
  void notConfigured_returns503() {
    PaymentServiceImpl unconfiguredService =
        new PaymentServiceImpl(
            paymentRepository,
            paymentGatewayEventRepository,
            serviceOrderRepository,
            paymentGatewayClient,
            paymentMapper,
            auditLogService,
            eventPublisher,
            CLOCK,
            new VnPayClient(new VnPayProperties("", "", PAY_URL, "")));

    assertThatThrownBy(
            () ->
                unconfiguredService.createVnPaySandboxUrl(
                    new CreateVnPayPaymentUrlRequest(100_000L, null, null), "127.0.0.1"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED);
  }

  @Test
  void configured_returnsSandboxUrlWithDefaultLocale() {
    String url =
        service
            .createVnPaySandboxUrl(
                new CreateVnPayPaymentUrlRequest(100_000L, null, null), "127.0.0.1")
            .paymentUrl();

    assertThat(url).startsWith(PAY_URL + "?").contains("vnp_Locale=vn").contains("vnp_SecureHash=");
  }

  // -------------------------------------------------------------------------
  // 2. createOrderPaymentUrl (UC30, BR-09, BR-17)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("PAY-01: Tạo lượt thanh toán thành công cho đơn AWAITING_PAYMENT")
  void createOrderPaymentUrl_awaitingPayment_success() {
    ServiceOrder order = createSampleOrder(1L, 100L, ServiceOrderStatus.AWAITING_PAYMENT, 250_000L);
    when(serviceOrderRepository.findById(1L)).thenReturn(Optional.of(order));
    when(paymentGatewayClient.isConfigured()).thenReturn(true);
    when(paymentGatewayClient.buildPaymentUrl(anyString(), eq(250_000L), anyString(), any(), anyString(), anyString()))
        .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?token=mock");
    when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
      Payment p = i.getArgument(0);
      ReflectionTestUtils.setField(p, "id", 10L);
      return p;
    });

    CreateOrderPaymentRequest request = new CreateOrderPaymentRequest(1L, null, "vn");
    PaymentResponse response = service.createOrderPaymentUrl(request, photographerAuth, "127.0.0.1");

    assertThat(response).isNotNull();
    assertThat(response.amountVnd()).isEqualTo(250_000L);
    assertThat(response.purpose()).isEqualTo(PaymentPurpose.ORDER_PAYMENT);
    assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    assertThat(response.paymentUrl()).contains("https://sandbox.vnpayment.vn");

    verify(auditLogService).recordSuccess(argThat(entry ->
        entry.action() == AuditAction.PAYMENT_CREATED
            && entry.targetType() == AuditTargetType.PAYMENT
            && entry.targetId().equals(10L)));
  }

  @Test
  @DisplayName("PAY-02: Đơn không tồn tại ném ORDER_NOT_FOUND")
  void createOrderPaymentUrl_orderNotFound_throws() {
    when(serviceOrderRepository.findById(999L)).thenReturn(Optional.empty());

    CreateOrderPaymentRequest request = new CreateOrderPaymentRequest(999L, null, "vn");

    assertThatThrownBy(() -> service.createOrderPaymentUrl(request, photographerAuth, "127.0.0.1"))
        .isInstanceOfSatisfying(BusinessException.class, ex ->
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
  }

  @Test
  @DisplayName("PAY-02: Người khác tạo thanh toán bị ném ORDER_ACCESS_DENIED")
  void createOrderPaymentUrl_otherUser_throwsAccessDenied() {
    ServiceOrder order = createSampleOrder(1L, 100L, ServiceOrderStatus.AWAITING_PAYMENT, 250_000L);
    when(serviceOrderRepository.findById(1L)).thenReturn(Optional.of(order));

    CreateOrderPaymentRequest request = new CreateOrderPaymentRequest(1L, null, "vn");

    assertThatThrownBy(() -> service.createOrderPaymentUrl(request, otherAuth, "127.0.0.1"))
        .isInstanceOfSatisfying(BusinessException.class, ex ->
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
  }

  @Test
  @DisplayName("PAY-02: Đơn sai trạng thái (DELIVERED) ném ORDER_INVALID_STATE")
  void createOrderPaymentUrl_invalidState_throws() {
    ServiceOrder order = createSampleOrder(1L, 100L, ServiceOrderStatus.DELIVERED, 250_000L);
    when(serviceOrderRepository.findById(1L)).thenReturn(Optional.of(order));

    CreateOrderPaymentRequest request = new CreateOrderPaymentRequest(1L, null, "vn");

    assertThatThrownBy(() -> service.createOrderPaymentUrl(request, photographerAuth, "127.0.0.1"))
        .isInstanceOfSatisfying(BusinessException.class, ex ->
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_INVALID_STATE));
  }

  // -------------------------------------------------------------------------
  // 3. processVnPayIpn (UC30, BR-09, Q9, Q10)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("PAY-03: IPN hợp lệ (RspCode 00) -> Payment SUCCEEDED, phát PaymentSucceededEvent")
  void processVnPayIpn_success_updatesPaymentAndPublishesEvent() {
    Payment payment = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.PENDING);
    when(paymentGatewayClient.provider()).thenReturn("vnpay");
    when(paymentGatewayClient.verifySignature(any())).thenReturn(true);
    when(paymentRepository.findByTransactionCode("TXN123456789")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

    Map<String, String> ipnParams = new HashMap<>();
    ipnParams.put("vnp_TxnRef", "TXN123456789");
    ipnParams.put("vnp_Amount", "25000000"); // 250,000 x 100
    ipnParams.put("vnp_ResponseCode", "00");
    ipnParams.put("vnp_TransactionNo", "VNPGW999888");
    ipnParams.put("vnp_BankCode", "NCB");

    VnPayIpnResponse response = service.processVnPayIpn(ipnParams);

    assertThat(response.rspCode()).isEqualTo("00");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(payment.getGatewayTransactionNo()).isEqualTo("VNPGW999888");
    assertThat(payment.getSucceededAt()).isEqualTo(FIXED_NOW);

    // Ghi audit log
    verify(auditLogService).recordSuccess(argThat(entry ->
        entry.action() == AuditAction.PAYMENT_SUCCEEDED
            && entry.targetType() == AuditTargetType.PAYMENT
            && entry.targetId().equals(10L)));

    // Phát sự kiện PaymentSucceededEvent đồng bộ trong cùng transaction
    ArgumentCaptor<PaymentSucceededEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    PaymentSucceededEvent event = eventCaptor.getValue();
    assertThat(event.paymentId()).isEqualTo(10L);
    assertThat(event.transactionCode()).isEqualTo("TXN123456789");
    assertThat(event.amountVnd()).isEqualTo(250_000L);
  }

  @Test
  @DisplayName("PAY-04: IPN chữ ký sai -> Từ chối, trả RspCode 97")
  void processVnPayIpn_invalidSignature_returns97() {
    when(paymentGatewayClient.provider()).thenReturn("vnpay");
    when(paymentGatewayClient.verifySignature(any())).thenReturn(false);

    Map<String, String> ipnParams = Map.of(
        "vnp_TxnRef", "TXN123456789",
        "vnp_Amount", "25000000",
        "vnp_ResponseCode", "00");

    VnPayIpnResponse response = service.processVnPayIpn(ipnParams);

    assertThat(response.rspCode()).isEqualTo("97");
    verify(paymentRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("PAY-05: IPN gửi lại cùng mã giao dịch đã thành công -> Idempotent, trả RspCode 02")
  void processVnPayIpn_alreadyConfirmed_returns02() {
    Payment payment = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.SUCCEEDED);
    when(paymentGatewayClient.provider()).thenReturn("vnpay");
    when(paymentGatewayClient.verifySignature(any())).thenReturn(true);
    when(paymentRepository.findByTransactionCode("TXN123456789")).thenReturn(Optional.of(payment));

    Map<String, String> ipnParams = Map.of(
        "vnp_TxnRef", "TXN123456789",
        "vnp_Amount", "25000000",
        "vnp_ResponseCode", "00");

    VnPayIpnResponse response = service.processVnPayIpn(ipnParams);

    assertThat(response.rspCode()).isEqualTo("02");
    verify(paymentRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("PAY-07: IPN sai số tiền -> Từ chối, trả RspCode 04")
  void processVnPayIpn_wrongAmount_returns04() {
    Payment payment = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.PENDING);
    when(paymentGatewayClient.provider()).thenReturn("vnpay");
    when(paymentGatewayClient.verifySignature(any())).thenReturn(true);
    when(paymentRepository.findByTransactionCode("TXN123456789")).thenReturn(Optional.of(payment));

    Map<String, String> ipnParams = Map.of(
        "vnp_TxnRef", "TXN123456789",
        "vnp_Amount", "10000000", // Sai số tiền (100k thay vì 250k)
        "vnp_ResponseCode", "00");

    VnPayIpnResponse response = service.processVnPayIpn(ipnParams);

    assertThat(response.rspCode()).isEqualTo("04");
    verify(paymentRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("PAY-08: IPN mã giao dịch không tồn tại -> Trả RspCode 01")
  void processVnPayIpn_orderNotFound_returns01() {
    when(paymentGatewayClient.provider()).thenReturn("vnpay");
    when(paymentGatewayClient.verifySignature(any())).thenReturn(true);
    when(paymentRepository.findByTransactionCode("UNKNOWN_TXN")).thenReturn(Optional.empty());

    Map<String, String> ipnParams = Map.of(
        "vnp_TxnRef", "UNKNOWN_TXN",
        "vnp_Amount", "25000000",
        "vnp_ResponseCode", "00");

    VnPayIpnResponse response = service.processVnPayIpn(ipnParams);

    assertThat(response.rspCode()).isEqualTo("01");
    verify(paymentRepository, never()).save(any());
  }

  @Test
  @DisplayName("PAY-09: IPN báo thất bại (ResponseCode != 00) -> Payment FAILED")
  void processVnPayIpn_failedResponseCode_marksPaymentFailed() {
    Payment payment = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.PENDING);
    when(paymentGatewayClient.provider()).thenReturn("vnpay");
    when(paymentGatewayClient.verifySignature(any())).thenReturn(true);
    when(paymentRepository.findByTransactionCode("TXN123456789")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

    Map<String, String> ipnParams = new HashMap<>();
    ipnParams.put("vnp_TxnRef", "TXN123456789");
    ipnParams.put("vnp_Amount", "25000000");
    ipnParams.put("vnp_ResponseCode", "24"); // Khách hủy giao dịch

    VnPayIpnResponse response = service.processVnPayIpn(ipnParams);

    assertThat(response.rspCode()).isEqualTo("00"); // Phản hồi cho VNPay là đã nhận tin
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureReason()).contains("24");
    verify(eventPublisher, never()).publishEvent(any());
  }

  // -------------------------------------------------------------------------
  // 4. processVnPayReturn (SRS 4.1)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("PAY-12: Gọi return URL thành công không làm đổi trạng thái DB")
  void processVnPayReturn_validSignature_returnsResultWithoutChangingState() {
    Payment payment = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.PENDING);
    when(paymentGatewayClient.verifySignature(any())).thenReturn(true);
    when(paymentRepository.findByTransactionCode("TXN123456789")).thenReturn(Optional.of(payment));

    Map<String, String> returnParams = Map.of(
        "vnp_TxnRef", "TXN123456789",
        "vnp_ResponseCode", "00",
        "vnp_Amount", "25000000");

    PaymentReturnResponse response = service.processVnPayReturn(returnParams);

    assertThat(response.success()).isTrue();
    assertThat(response.transactionCode()).isEqualTo("TXN123456789");
    assertThat(response.amountVnd()).isEqualTo(250_000L);
    verify(paymentRepository, never()).save(any());
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  private ServiceOrder createSampleOrder(Long id, Long photographerId, ServiceOrderStatus status, Long estimatedTotal) {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", id);
    order.setOrderCode("ORD-20261112-001");
    order.setPhotographerAccountId(photographerId);
    order.setFilmLabId(1L);
    order.setStatus(status);
    order.setEstimatedTotalVnd(estimatedTotal);
    return order;
  }

  private Payment createSamplePayment(Long id, String txnRef, Long amount, PaymentStatus status) {
    Payment payment = new Payment();
    ReflectionTestUtils.setField(payment, "id", id);
    payment.setTransactionCode(txnRef);
    payment.setPurpose(PaymentPurpose.ORDER_PAYMENT);
    payment.setDirection(PaymentDirection.IN);
    payment.setMethod(PaymentMethod.VNPAY);
    payment.setStatus(status);
    payment.setAmountVnd(amount);
    payment.setServiceOrderId(1L);
    payment.setCounterpartyAccountId(100L);
    return payment;
  }
}
