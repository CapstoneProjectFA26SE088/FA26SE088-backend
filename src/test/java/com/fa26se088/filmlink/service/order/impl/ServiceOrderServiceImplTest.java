package com.fa26se088.filmlink.service.order.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.order.request.CancelServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.ConfirmReceiptAndFinalizePriceRequest;
import com.fa26se088.filmlink.dto.order.request.CreateServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.FilmRollItemRequest;
import com.fa26se088.filmlink.dto.order.request.RejectServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.OrderPaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.entity.order.enums.ServiceTier;
import com.fa26se088.filmlink.entity.payment.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.order.ServiceOrderMapper;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.payment.PaymentService;
import com.fa26se088.filmlink.service.payment.event.PaymentSucceededEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ServiceOrderServiceImplTest {

  @Mock private ServiceOrderRepository orderRepository;
  @Mock private LabAccessService labAccessService;
  @Mock private PaymentService paymentService;
  @Mock private com.fa26se088.filmlink.service.payment.CommissionService commissionService;
  @Mock private AuditLogService auditLogService;

  private final ServiceOrderMapper orderMapper = new ServiceOrderMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

  private ServiceOrderServiceImpl orderService;

  private AuthenticatedAccount photographerAccount;
  private AuthenticatedAccount labOwnerAccount;

  @BeforeEach
  void setUp() {
    orderService = new ServiceOrderServiceImpl(
        orderRepository, orderMapper, labAccessService, paymentService, commissionService, auditLogService, clock);

    photographerAccount = new AuthenticatedAccount(10L, "photographer@test.com", UserAccountRole.PHOTOGRAPHER);
    labOwnerAccount = new AuthenticatedAccount(20L, "labowner@test.com", UserAccountRole.FILM_LAB_OWNER);
  }

  @Test
  @DisplayName("Tạo đơn ONLINE thành công, trạng thái ban đầu là AWAITING_PAYMENT")
  void createOrder_online_startsInAwaitingPayment() {
    UUID key = UUID.randomUUID();
    CreateServiceOrderRequest request = new CreateServiceOrderRequest(
        key,
        1L,
        ServiceTier.STANDARD,
        FilmHandoverMethod.SELF_DROP_OFF,
        OrderPaymentMethod.ONLINE,
        "Yêu cầu đặc biệt",
        "123 Nguyen Hue",
        "Quan 1",
        "TP HCM",
        "0901234567",
        null,
        null,
        false,
        List.of(new FilmRollItemRequest("35mm Color", "C-41", 120000L, "Ghi chú"))
    );

    when(orderRepository.findByPhotographerAccountIdAndIdempotencyKey(10L, key))
        .thenReturn(Optional.empty());

    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", 1L);
    lab.setAcceptingOrders(true);
    when(labAccessService.getLabForOrdering(1L)).thenReturn(lab);

    when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(invocation -> {
      ServiceOrder so = invocation.getArgument(0);
      ReflectionTestUtils.setField(so, "id", 100L);
      return so;
    });

    ServiceOrderResponse response = orderService.createOrder(request, photographerAccount);

    assertNotNull(response);
    assertEquals(100L, response.id());
    assertEquals(ServiceOrderStatus.AWAITING_PAYMENT, response.status());
    assertEquals(120000L, response.estimatedTotalVnd());
    assertEquals(OrderPaymentMethod.ONLINE, response.paymentMethod());
    verify(orderRepository).save(any(ServiceOrder.class));
  }

  @Test
  @DisplayName("Tạo đơn PAY_AT_LAB thành công, trạng thái ban đầu là AWAITING_LAB_CONFIRMATION")
  void createOrder_payAtLab_startsInAwaitingLabConfirmation() {
    UUID key = UUID.randomUUID();
    CreateServiceOrderRequest request = new CreateServiceOrderRequest(
        key,
        1L,
        ServiceTier.EXPRESS,
        FilmHandoverMethod.SELF_DROP_OFF,
        OrderPaymentMethod.PAY_AT_LAB,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        List.of(new FilmRollItemRequest("120 B&W", "B&W", 150000L, null))
    );

    when(orderRepository.findByPhotographerAccountIdAndIdempotencyKey(10L, key))
        .thenReturn(Optional.empty());

    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", 1L);
    when(labAccessService.getLabForOrdering(1L)).thenReturn(lab);

    when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(invocation -> {
      ServiceOrder so = invocation.getArgument(0);
      ReflectionTestUtils.setField(so, "id", 101L);
      return so;
    });

    ServiceOrderResponse response = orderService.createOrder(request, photographerAccount);

    assertNotNull(response);
    assertEquals(101L, response.id());
    assertEquals(ServiceOrderStatus.AWAITING_LAB_CONFIRMATION, response.status());
    assertEquals(150000L, response.estimatedTotalVnd());
    assertNotNull(response.labResponseDueAt());
  }

  @Test
  @DisplayName("Tạo đơn với Idempotency Key đã tồn tại thì trả về đơn cũ không lưu mới")
  void createOrder_idempotentHit_returnsExisting() {
    UUID key = UUID.randomUUID();
    CreateServiceOrderRequest request = new CreateServiceOrderRequest(
        key,
        1L,
        ServiceTier.STANDARD,
        FilmHandoverMethod.SELF_DROP_OFF,
        OrderPaymentMethod.ONLINE,
        null, null, null, null, null, null, null, false,
        List.of(new FilmRollItemRequest("35mm", "C-41", 100000L, null))
    );

    ServiceOrder existing = new ServiceOrder();
    ReflectionTestUtils.setField(existing, "id", 99L);
    existing.setIdempotencyKey(key);
    existing.setPhotographerAccountId(10L);
    existing.setStatus(ServiceOrderStatus.AWAITING_PAYMENT);
    existing.setEstimatedTotalVnd(100000L);

    when(orderRepository.findByPhotographerAccountIdAndIdempotencyKey(10L, key))
        .thenReturn(Optional.of(existing));

    ServiceOrderResponse response = orderService.createOrder(request, photographerAccount);

    assertEquals(99L, response.id());
    verify(orderRepository, never()).save(any());
  }

  @Test
  @DisplayName("PaymentSucceededEvent chuyển đơn từ AWAITING_PAYMENT sang AWAITING_LAB_CONFIRMATION")
  void handlePaymentSucceeded_transitionsToAwaitingLabConfirmation() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 50L);
    order.setStatus(ServiceOrderStatus.AWAITING_PAYMENT);
    order.setPhotographerAccountId(10L);

    when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

    PaymentSucceededEvent event = new PaymentSucceededEvent(
        1L,
        "TXN123",
        50L,
        250000L,
        PaymentPurpose.ORDER_PAYMENT,
        PaymentMethod.VNPAY,
        clock.instant()
    );

    orderService.handlePaymentSucceeded(event);

    assertEquals(ServiceOrderStatus.AWAITING_LAB_CONFIRMATION, order.getStatus());
    assertNotNull(order.getLabResponseDueAt());
    verify(orderRepository).save(order);
  }

  @Test
  @DisplayName("Xem đơn hàng của người khác khi không phải ADMIN/Lab sở hữu sẽ bị ném lỗi 403")
  void getOrderById_accessDenied() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 50L);
    order.setPhotographerAccountId(999L); // thuộc người khác
    order.setFilmLabId(2L);

    when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> orderService.getOrderById(50L, photographerAccount)
    );
    assertEquals(ErrorCode.ORDER_ACCESS_DENIED, ex.getErrorCode());
  }

  @Test
  @DisplayName("ORD-12: Lab nhận đúng đơn của mình -> ACCEPTED (trạng thái AWAITING_FILM)")
  void acceptOrder_success() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 60L);
    order.setFilmLabId(1L);
    order.setStatus(ServiceOrderStatus.AWAITING_LAB_CONFIRMATION);

    when(orderRepository.findById(60L)).thenReturn(Optional.of(order));
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", 1L);
    when(labAccessService.requireOwnedLab(20L)).thenReturn(lab);
    when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));

    ServiceOrderResponse response = orderService.acceptOrder(60L, labOwnerAccount);

    assertEquals(ServiceOrderStatus.AWAITING_FILM, response.status());
    assertNotNull(response.acceptedAt());
  }

  @Test
  @DisplayName("ORD-14: Từ chối đơn đã trả online -> Hoàn 100% tự động, đơn CANCELLED")
  void rejectOrder_online_refundsAndCancels() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 70L);
    order.setFilmLabId(1L);
    order.setPhotographerAccountId(10L);
    order.setStatus(ServiceOrderStatus.AWAITING_LAB_CONFIRMATION);

    when(orderRepository.findById(70L)).thenReturn(Optional.of(order));
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", 1L);
    when(labAccessService.requireOwnedLab(20L)).thenReturn(lab);
    when(paymentService.getTotalPaidAmount(70L)).thenReturn(250000L);
    when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));

    RejectServiceOrderRequest request = new RejectServiceOrderRequest("Lab quá tải không nhận thêm");
    ServiceOrderResponse response = orderService.rejectOrder(70L, request, labOwnerAccount);

    assertEquals(ServiceOrderStatus.CANCELLED, response.status());
    assertEquals("Lab quá tải không nhận thêm", response.rejectionReason());
    verify(paymentService).refundOrder(eq(70L), eq(250000L), any());
  }

  @Test
  @DisplayName("ORD-18: Xác nhận nhận phim: giá thực > đã thu -> chuyển AWAITING_TOP_UP chờ nạp bù")
  void confirmReceiptAndFinalizePrice_excessPrice_transitionsToAwaitingTopUp() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 80L);
    order.setFilmLabId(1L);
    order.setStatus(ServiceOrderStatus.AWAITING_FILM);

    when(orderRepository.findById(80L)).thenReturn(Optional.of(order));
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", 1L);
    when(labAccessService.requireOwnedLab(20L)).thenReturn(lab);
    when(paymentService.getTotalPaidAmount(80L)).thenReturn(200000L);
    when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));

    ConfirmReceiptAndFinalizePriceRequest request = new ConfirmReceiptAndFinalizePriceRequest(
        2, 250000L, List.of("http://evidence.jpg"), "Phát sinh thêm phí tráng đặc biệt");

    ServiceOrderResponse response = orderService.confirmReceiptAndFinalizePrice(80L, request, labOwnerAccount);

    assertEquals(ServiceOrderStatus.AWAITING_TOP_UP, response.status());
    assertEquals(250000L, response.finalTotalVnd());
    assertNotNull(response.priceLockedAt());
    verify(paymentService, never()).refundOrder(any(), any(), any());
  }

  @Test
  @DisplayName("ORD-19: Xác nhận nhận phim: giá thực < đã thu -> chuyển AWAITING_MACHINE_ASSIGNMENT và hoàn phần dư")
  void confirmReceiptAndFinalizePrice_lowerPrice_refundsDifference() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 81L);
    order.setFilmLabId(1L);
    order.setStatus(ServiceOrderStatus.AWAITING_FILM);

    when(orderRepository.findById(81L)).thenReturn(Optional.of(order));
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", 1L);
    when(labAccessService.requireOwnedLab(20L)).thenReturn(lab);
    when(paymentService.getTotalPaidAmount(81L)).thenReturn(300000L);
    when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));

    ConfirmReceiptAndFinalizePriceRequest request = new ConfirmReceiptAndFinalizePriceRequest(
        1, 200000L, null, "1 cuộn bị hỏng không tráng");

    ServiceOrderResponse response = orderService.confirmReceiptAndFinalizePrice(81L, request, labOwnerAccount);

    assertEquals(ServiceOrderStatus.AWAITING_MACHINE_ASSIGNMENT, response.status());
    assertEquals(200000L, response.finalTotalVnd());
    verify(paymentService).refundOrder(eq(81L), eq(100000L), any());
  }

  @Test
  @DisplayName("ORD-25: Huỷ trước khi lab xác nhận nhận phim -> Hoàn 100% tiền đã trả")
  void cancelOrder_beforeReceipt_refunds100Percent() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 90L);
    order.setPhotographerAccountId(10L);
    order.setStatus(ServiceOrderStatus.AWAITING_LAB_CONFIRMATION);

    when(orderRepository.findById(90L)).thenReturn(Optional.of(order));
    when(paymentService.getTotalPaidAmount(90L)).thenReturn(150000L);
    when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));

    CancelServiceOrderRequest request = new CancelServiceOrderRequest("Tôi đổi ý không tráng nữa");
    ServiceOrderResponse response = orderService.cancelOrder(90L, request, photographerAccount);

    assertEquals(ServiceOrderStatus.CANCELLED, response.status());
    assertEquals(0L, response.cancellationFeeVnd());
    verify(paymentService).refundOrder(eq(90L), eq(150000L), any());
  }

  @Test
  @DisplayName("ORD-30: Khách xác nhận đóng đơn -> chuyển trạng thái CLOSED")
  void closeOrder_success() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 91L);
    order.setPhotographerAccountId(10L);
    order.setStatus(ServiceOrderStatus.DELIVERED);

    when(orderRepository.findById(91L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));

    ServiceOrderResponse response = orderService.closeOrder(91L, photographerAccount);

    assertEquals(ServiceOrderStatus.CLOSED, response.status());
    assertNotNull(response.closedAt());
  }
}
