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
import com.fa26se088.filmlink.dto.payment.request.CreateVnPayPaymentUrlRequest;
import com.fa26se088.filmlink.dto.payment.response.PaymentLink;
import com.fa26se088.filmlink.dto.payment.response.PaymentReturnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayIpnResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.entity.payment.Payment;
import com.fa26se088.filmlink.entity.payment.PaymentGatewayEvent;
import com.fa26se088.filmlink.entity.payment.enums.PaymentDirection;
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
  @Mock private SystemParameterService systemParameterService;
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
            paymentGatewayClient,
            paymentMapper,
            auditLogService,
            systemParameterService,
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
            paymentGatewayClient,
            paymentMapper,
            auditLogService,
            systemParameterService,
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
  // 2. Contract SPEC-00 §4.5: createOrderPayment / createOrderTopUp / netPaidForOrder / refund
  // -------------------------------------------------------------------------

  private void stubNewPaymentFlow(long amount) {
    when(paymentGatewayClient.isConfigured()).thenReturn(true);
    when(systemParameterService.getInteger(SystemParameterKey.PAYMENT_LINK_TTL_MINUTES))
        .thenReturn(15);
    when(paymentRepository.findReusablePending(
            eq(1L), any(PaymentPurpose.class), eq(PaymentStatus.PENDING), eq(amount), eq(FIXED_NOW)))
        .thenReturn(Optional.empty());
    when(paymentGatewayClient.buildPaymentUrl(
            anyString(), eq(amount), anyString(), any(), anyString(), anyString()))
        .thenReturn(PAY_URL + "?token=mock");
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(
            i -> {
              Payment p = i.getArgument(0);
              ReflectionTestUtils.setField(p, "id", 10L);
              return p;
            });
  }

  @Test
  @DisplayName("UC30: createOrderPayment tạo payment PENDING (IN, VNPAY) và trả URL, hạn = TTL tham số")
  void createOrderPayment_createsPendingAndUrl() {
    stubNewPaymentFlow(250_000L);

    PaymentLink link = service.createOrderPayment(1L, 100L, 250_000L, "127.0.0.1");

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).save(captor.capture());
    Payment saved = captor.getValue();
    assertThat(saved.getPurpose()).isEqualTo(PaymentPurpose.ORDER_PAYMENT);
    assertThat(saved.getDirection()).isEqualTo(PaymentDirection.IN);
    assertThat(saved.getMethod()).isEqualTo(PaymentMethod.VNPAY);
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(saved.getAmountVnd()).isEqualTo(250_000L);
    assertThat(saved.getServiceOrderId()).isEqualTo(1L);
    assertThat(saved.getCounterpartyAccountId()).isEqualTo(100L);
    assertThat(saved.getPaymentUrlExpiresAt()).isEqualTo(FIXED_NOW.plusSeconds(15 * 60));

    assertThat(link.paymentId()).isEqualTo(10L);
    assertThat(link.transactionCode()).isEqualTo(saved.getTransactionCode());
    assertThat(link.paymentUrl()).startsWith(PAY_URL);
    assertThat(link.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(15 * 60));

    verify(auditLogService)
        .recordSuccess(
            argThat(
                entry ->
                    entry.action() == AuditAction.PAYMENT_CREATED
                        && entry.targetType() == AuditTargetType.PAYMENT
                        && entry.targetId().equals(10L)));
  }

  @Test
  @DisplayName("UC30: gọi createOrderPayment lần 2 khi PENDING còn hạn trả lại cùng paymentId, không tạo mới")
  void createOrderPayment_secondCallWhilePending_returnsSamePayment() {
    Payment pending = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.PENDING);
    Instant expiresAt = FIXED_NOW.plusSeconds(600);
    pending.setPaymentUrlExpiresAt(expiresAt);
    when(paymentGatewayClient.isConfigured()).thenReturn(true);
    when(paymentRepository.findReusablePending(
            1L, PaymentPurpose.ORDER_PAYMENT, PaymentStatus.PENDING, 250_000L, FIXED_NOW))
        .thenReturn(Optional.of(pending));
    when(paymentGatewayClient.buildPaymentUrl(
            eq("TXN123456789"), eq(250_000L), anyString(), any(), anyString(), anyString()))
        .thenReturn(PAY_URL + "?token=again");

    PaymentLink link = service.createOrderPayment(1L, 100L, 250_000L, "127.0.0.1");

    assertThat(link.paymentId()).isEqualTo(10L);
    assertThat(link.transactionCode()).isEqualTo("TXN123456789");
    assertThat(link.expiresAt()).isEqualTo(expiresAt);
    verify(paymentRepository, never()).save(any());
  }

  @Test
  @DisplayName("BR-18: createOrderTopUp tạo payment purpose ORDER_TOP_UP")
  void createOrderTopUp_createsTopUpPayment() {
    stubNewPaymentFlow(50_000L);

    service.createOrderTopUp(1L, 100L, 50_000L, "127.0.0.1");

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).save(captor.capture());
    assertThat(captor.getValue().getPurpose()).isEqualTo(PaymentPurpose.ORDER_TOP_UP);
    assertThat(captor.getValue().getDirection()).isEqualTo(PaymentDirection.IN);
  }

  @Test
  @DisplayName("UC30: số tiền <= 0 bị từ chối")
  void createOrderPayment_nonPositiveAmount_throws() {
    assertThatThrownBy(() -> service.createOrderPayment(1L, 100L, 0L, "127.0.0.1"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATE));
  }

  @Test
  @DisplayName("Cổng thanh toán chưa cấu hình -> PAYMENT_GATEWAY_NOT_CONFIGURED")
  void createOrderPayment_gatewayNotConfigured_throws() {
    when(paymentGatewayClient.isConfigured()).thenReturn(false);

    assertThatThrownBy(() -> service.createOrderPayment(1L, 100L, 250_000L, "127.0.0.1"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED));
  }

  @Test
  @DisplayName("netPaidForOrder = tổng IN SUCCEEDED − tổng OUT SUCCEEDED (bỏ PENDING / FAILED)")
  void netPaidForOrder_succeededInMinusSucceededOut() {
    Payment paid = createSamplePayment(1L, "A", 250_000L, PaymentStatus.SUCCEEDED);
    Payment topUp = createSamplePayment(2L, "B", 50_000L, PaymentStatus.SUCCEEDED);
    topUp.setPurpose(PaymentPurpose.ORDER_TOP_UP);
    Payment pendingTopUp = createSamplePayment(3L, "C", 70_000L, PaymentStatus.PENDING);
    Payment failed = createSamplePayment(4L, "D", 90_000L, PaymentStatus.FAILED);
    Payment refund = createSamplePayment(5L, "E", 30_000L, PaymentStatus.SUCCEEDED);
    refund.setPurpose(PaymentPurpose.ORDER_REFUND);
    refund.setDirection(PaymentDirection.OUT);
    Payment manualRefund = createSamplePayment(6L, "F", 20_000L, PaymentStatus.AWAITING_MANUAL);
    manualRefund.setPurpose(PaymentPurpose.ORDER_REFUND);
    manualRefund.setDirection(PaymentDirection.OUT);
    when(paymentRepository.findByServiceOrderId(1L))
        .thenReturn(List.of(paid, topUp, pendingTopUp, failed, refund, manualRefund));

    assertThat(service.netPaidForOrder(1L)).isEqualTo(270_000L);
  }

  @Test
  @DisplayName("BR-38: createOrderRefund tạo ORDER_REFUND (OUT) liên kết khoản IN đã thành công")
  void createOrderRefund_gatewayAccepts_succeeded() {
    Payment origin = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.SUCCEEDED);
    origin.setGatewayTransactionNo("VNPGW1");
    when(paymentRepository.findByServiceOrderId(1L)).thenReturn(List.of(origin));
    when(paymentRepository.findByServiceOrderIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(origin));
    when(paymentGatewayClient.refund("TXN123456789", "VNPGW1", 50_000L, "huy don"))
        .thenReturn(true);
    when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

    service.createOrderRefund(1L, 50_000L, "huy don");

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).save(captor.capture());
    Payment refund = captor.getValue();
    assertThat(refund.getPurpose()).isEqualTo(PaymentPurpose.ORDER_REFUND);
    assertThat(refund.getDirection()).isEqualTo(PaymentDirection.OUT);
    assertThat(refund.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(refund.getRefundedPaymentId()).isEqualTo(10L);
    assertThat(refund.getCounterpartyAccountId()).isEqualTo(100L);
  }

  @Test
  @DisplayName("BR-39: cổng hoàn tiền lỗi -> refund AWAITING_MANUAL cho Admin")
  void createOrderRefund_gatewayError_awaitingManual() {
    Payment origin = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.SUCCEEDED);
    when(paymentRepository.findByServiceOrderId(1L)).thenReturn(List.of(origin));
    when(paymentRepository.findByServiceOrderIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(origin));
    when(paymentGatewayClient.refund(anyString(), any(), anyLong(), anyString()))
        .thenThrow(new IllegalStateException("timeout"));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

    service.createOrderRefund(1L, 50_000L, "huy don");

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.AWAITING_MANUAL);
  }

  @Test
  @DisplayName("createOrderRefund vượt quá số tiền thực nhận (netPaidForOrder) -> PAYMENT_INVALID_STATE")
  void createOrderRefund_exceedsNetPaid_throws() {
    Payment origin = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.SUCCEEDED);
    when(paymentRepository.findByServiceOrderId(1L)).thenReturn(List.of(origin));

    assertThatThrownBy(() -> service.createOrderRefund(1L, 300_000L, "huy don"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATE));
    verify(paymentRepository, never()).save(any());
  }

  @Test
  @DisplayName("createOrderRefund khi đơn chưa có khoản thanh toán thành công -> PAYMENT_INVALID_STATE")
  void createOrderRefund_noSucceededPayment_throws() {
    when(paymentRepository.findByServiceOrderId(1L)).thenReturn(List.of());

    assertThatThrownBy(() -> service.createOrderRefund(1L, 50_000L, "huy don"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATE));
  }

  @Test
  @DisplayName("createSalePayment chưa làm (P-07) -> UnsupportedOperationException")
  void createSalePayment_notYetImplemented() {
    assertThatThrownBy(() -> service.createSalePayment(1L, 100L, 100_000L, "127.0.0.1"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("by-order: người không phải counterparty bị PAYMENT_ACCESS_DENIED; Admin xem được")
  void getPaymentsByOrderId_accessByPaymentData() {
    Payment payment = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.SUCCEEDED);
    when(paymentRepository.findByServiceOrderIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(payment));

    assertThat(service.getPaymentsByOrderId(1L, photographerAuth)).hasSize(1);
    assertThat(service.getPaymentsByOrderId(1L, adminAuth)).hasSize(1);
    assertThatThrownBy(() -> service.getPaymentsByOrderId(1L, otherAuth))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
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
    when(paymentRepository.findByTransactionCodeForUpdate("TXN123456789"))
        .thenReturn(Optional.of(payment));
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
    assertThat(event.purpose()).isEqualTo(PaymentPurpose.ORDER_PAYMENT);
    assertThat(event.serviceOrderId()).isEqualTo(1L);
    assertThat(event.consignmentSaleId()).isNull();
    assertThat(event.amountVnd()).isEqualTo(250_000L);
    assertThat(event.succeededAt()).isEqualTo(FIXED_NOW);
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
    when(paymentRepository.findByTransactionCodeForUpdate("TXN123456789"))
        .thenReturn(Optional.of(payment));

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
  @DisplayName("PAY-05b: IPN cho giao dịch không ở trạng thái PENDING (VD: FAILED) -> Idempotent, trả RspCode 02")
  void processVnPayIpn_nonPendingStatus_returns02WithoutModifying() {
    Payment payment = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.FAILED);
    when(paymentGatewayClient.provider()).thenReturn("vnpay");
    when(paymentGatewayClient.verifySignature(any())).thenReturn(true);
    when(paymentRepository.findByTransactionCodeForUpdate("TXN123456789"))
        .thenReturn(Optional.of(payment));

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
  @DisplayName("PAY-07: IPN sai số tiền -> Đánh dấu FAILED, ghi audit, trả RspCode 04")
  void processVnPayIpn_wrongAmount_returns04() {
    Payment payment = createSamplePayment(10L, "TXN123456789", 250_000L, PaymentStatus.PENDING);
    when(paymentGatewayClient.provider()).thenReturn("vnpay");
    when(paymentGatewayClient.verifySignature(any())).thenReturn(true);
    when(paymentRepository.findByTransactionCodeForUpdate("TXN123456789"))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

    Map<String, String> ipnParams = Map.of(
        "vnp_TxnRef", "TXN123456789",
        "vnp_Amount", "10000000", // Sai số tiền (100k thay vì 250k)
        "vnp_ResponseCode", "00");

    VnPayIpnResponse response = service.processVnPayIpn(ipnParams);

    assertThat(response.rspCode()).isEqualTo("04");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureReason()).contains("250000");
    verify(paymentRepository).save(payment);
    verify(auditLogService).recordSuccess(argThat(entry ->
        entry.action() == AuditAction.PAYMENT_FAILED
            && entry.targetType() == AuditTargetType.PAYMENT
            && entry.targetId().equals(10L)));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("PAY-08: IPN mã giao dịch không tồn tại -> Trả RspCode 01")
  void processVnPayIpn_orderNotFound_returns01() {
    when(paymentGatewayClient.provider()).thenReturn("vnpay");
    when(paymentGatewayClient.verifySignature(any())).thenReturn(true);
    when(paymentRepository.findByTransactionCodeForUpdate("UNKNOWN_TXN"))
        .thenReturn(Optional.empty());

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
    when(paymentRepository.findByTransactionCodeForUpdate("TXN123456789"))
        .thenReturn(Optional.of(payment));
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
