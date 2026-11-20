package com.fa26se088.filmlink.service.order.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.order.request.CancelServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.ConfirmReceiptAndFinalizePriceRequest;
import com.fa26se088.filmlink.dto.order.request.CreateServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.FilmRollItemRequest;
import com.fa26se088.filmlink.dto.order.request.RejectServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.OrderPaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.order.ServiceOrderMapper;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.order.ServiceOrderService;
import com.fa26se088.filmlink.service.payment.CommissionService;
import com.fa26se088.filmlink.service.payment.PaymentService;
import com.fa26se088.filmlink.service.payment.event.PaymentSucceededEvent;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceOrderServiceImpl implements ServiceOrderService {

  private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 8;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ServiceOrderRepository orderRepository;
  private final ServiceOrderMapper orderMapper;
  private final LabAccessService labAccessService;
  private final PaymentService paymentService;
  private final CommissionService commissionService;
  private final AuditLogService auditLogService;
  private final Clock clock;

  @Override
  @Transactional
  public ServiceOrderResponse createOrder(CreateServiceOrderRequest request, AuthenticatedAccount account) {
    Long photographerId = account.id();

    // Idempotency check: nếu đã tồn tại đơn với idempotencyKey của photographer này thì trả về đơn cũ
    Optional<ServiceOrder> existing = orderRepository
        .findByPhotographerAccountIdAndIdempotencyKey(photographerId, request.idempotencyKey());
    if (existing.isPresent()) {
      log.info("[OrderService] Idempotent hit: photographerId={}, idempotencyKey={}",
          photographerId, request.idempotencyKey());
      return orderMapper.toResponse(existing.get());
    }

    // Kiểm tra FilmLab mở nhận đơn
    FilmLab lab = labAccessService.getLabForOrdering(request.filmLabId());

    // Tính tổng tiền tạm tính estimated_total_vnd từ các rolls
    long estimatedTotal = request.items().stream()
        .mapToLong(FilmRollItemRequest::estimatedPriceVnd)
        .sum();

    Instant now = clock.instant();

    ServiceOrder order = new ServiceOrder();
    order.setOrderCode(generateOrderCode());
    order.setIdempotencyKey(request.idempotencyKey());
    order.setPhotographerAccountId(photographerId);
    order.setFilmLabId(lab.getId());
    order.setServiceTier(request.serviceTier());
    order.setFilmHandoverMethod(request.filmHandoverMethod());
    order.setPaymentMethod(request.paymentMethod());
    order.setSpecialRequest(request.specialRequest());
    order.setCustomerAddressLine(request.customerAddressLine());
    order.setCustomerDistrict(request.customerDistrict());
    order.setCustomerProvince(request.customerProvince());
    order.setCustomerPhone(request.customerPhone());
    order.setPickupWindowStartAt(request.pickupWindowStartAt());
    order.setPickupWindowEndAt(request.pickupWindowEndAt());
    order.setKeepNegativesAtLab(request.keepNegativesAtLab());
    order.setEstimatedTotalVnd(estimatedTotal);

    // Tính thời gian trả dự kiến tạm thời (ví dụ: mặc định 3 ngày sau nếu chưa tính SLA chi tiết)
    order.setExpectedReturnAt(now.plus(Duration.ofDays(3)));

    // Xác định trạng thái khởi tạo theo phương thức thanh toán
    if (request.paymentMethod() == OrderPaymentMethod.ONLINE) {
      // Đơn ONLINE chờ thanh toán
      order.setStatus(ServiceOrderStatus.AWAITING_PAYMENT);
    } else {
      // Đơn Pay-at-Lab hoặc COD vào thẳng AWAITING_LAB_CONFIRMATION
      order.setStatus(ServiceOrderStatus.AWAITING_LAB_CONFIRMATION);
      order.setLabResponseDueAt(now.plus(Duration.ofHours(24))); // Deadline lab phản hồi 24h
    }

    ServiceOrder saved = orderRepository.save(order);

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.ORDER_CREATED,
        AuditTargetType.SERVICE_ORDER,
        saved.getId(),
        "Tạo đơn dịch vụ mới " + saved.getOrderCode() + " với phương thức " + saved.getPaymentMethod(),
        null,
        null
    ));

    log.info("[OrderService] Tạo đơn thành công: orderCode={}, status={}", saved.getOrderCode(), saved.getStatus());
    return orderMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public ServiceOrderResponse getOrderById(Long orderId, AuthenticatedAccount account) {
    ServiceOrder order = orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));
    validateOrderAccess(order, account);
    return orderMapper.toResponse(order);
  }

  @Override
  @Transactional(readOnly = true)
  public ServiceOrderResponse getOrderByCode(String orderCode, AuthenticatedAccount account) {
    ServiceOrder order = orderRepository.findByOrderCode(orderCode)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));
    validateOrderAccess(order, account);
    return orderMapper.toResponse(order);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ServiceOrderResponse> getPhotographerOrders(
      ServiceOrderStatus status, Pageable pageable, AuthenticatedAccount account) {
    Page<ServiceOrder> page;
    if (status != null) {
      page = orderRepository.findByPhotographerAccountIdAndStatus(account.id(), status, pageable);
    } else {
      page = orderRepository.findByPhotographerAccountId(account.id(), pageable);
    }
    return page.map(orderMapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ServiceOrderResponse> getLabOrders(
      Long labId, ServiceOrderStatus status, Pageable pageable, AuthenticatedAccount account) {
    // Kiểm tra quyền sở hữu lab
    if (account.role() != UserAccountRole.ADMIN) {
      labAccessService.requireOwnedLab(account.id(), labId);
    }
    Page<ServiceOrder> page;
    if (status != null) {
      page = orderRepository.findByFilmLabIdAndStatus(labId, status, pageable);
    } else {
      page = orderRepository.findByFilmLabId(labId, pageable);
    }
    return page.map(orderMapper::toResponse);
  }

  @Override
  @Transactional
  public ServiceOrderResponse acceptOrder(Long orderId, AuthenticatedAccount account) {
    ServiceOrder order = orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));

    // Chỉ Lab sở hữu hoặc Admin mới được nhận đơn
    validateLabOwnership(order.getFilmLabId(), account);

    if (order.getStatus() != ServiceOrderStatus.AWAITING_LAB_CONFIRMATION) {
      throw new BusinessException(ErrorCode.ORDER_INVALID_STATE,
          "Đơn hàng không ở trạng thái chờ Lab xác nhận (trạng thái hiện tại: " + order.getStatus() + ").");
    }

    Instant now = clock.instant();
    order.setStatus(ServiceOrderStatus.AWAITING_FILM);
    order.setAcceptedAt(now);
    ServiceOrder saved = orderRepository.save(order);

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.ORDER_STATUS_CHANGED,
        AuditTargetType.SERVICE_ORDER,
        saved.getId(),
        "Film Lab chấp nhận đơn hàng " + saved.getOrderCode() + ", chuyển sang AWAITING_FILM",
        null,
        null
    ));

    log.info("[OrderService] Lab đã chấp nhận đơn hàng #{}", saved.getId());
    return orderMapper.toResponse(saved);
  }

  @Override
  @Transactional
  public ServiceOrderResponse rejectOrder(Long orderId, RejectServiceOrderRequest request, AuthenticatedAccount account) {
    ServiceOrder order = orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));

    validateLabOwnership(order.getFilmLabId(), account);

    if (order.getStatus() != ServiceOrderStatus.AWAITING_LAB_CONFIRMATION) {
      throw new BusinessException(ErrorCode.ORDER_INVALID_STATE,
          "Đơn hàng không ở trạng thái chờ Lab xác nhận (trạng thái hiện tại: " + order.getStatus() + ").");
    }

    Instant now = clock.instant();
    order.setStatus(ServiceOrderStatus.CANCELLED);
    order.setCancelledAt(now);
    order.setCancelledByAccountId(account.id());
    order.setRejectionReason(request.rejectionReason());
    order.setCancellationReason("Film Lab từ chối đơn: " + request.rejectionReason());

    // BR-39: Nếu đơn đã thanh toán (ONLINE), hoàn 100% tự động
    Long paidAmount = paymentService.getTotalPaidAmount(order.getId());
    if (paidAmount != null && paidAmount > 0) {
      log.info("[OrderService] Đơn #{} bị từ chối đã thanh toán {} VND, kích hoạt hoàn tiền tự động",
          order.getId(), paidAmount);
      paymentService.refundOrder(order.getId(), paidAmount, "Hoàn tiền do Film Lab từ chối đơn: " + request.rejectionReason());
    }

    ServiceOrder saved = orderRepository.save(order);

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.ORDER_STATUS_CHANGED,
        AuditTargetType.SERVICE_ORDER,
        saved.getId(),
        "Film Lab từ chối đơn hàng " + saved.getOrderCode() + ". Lý do: " + request.rejectionReason(),
        null,
        null
    ));

    log.info("[OrderService] Lab đã từ chối đơn hàng #{}, chuyển CANCELLED", saved.getId());
    return orderMapper.toResponse(saved);
  }

  @Override
  @Transactional
  public ServiceOrderResponse confirmReceiptAndFinalizePrice(
      Long orderId, ConfirmReceiptAndFinalizePriceRequest request, AuthenticatedAccount account) {
    ServiceOrder order = orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));

    validateLabOwnership(order.getFilmLabId(), account);

    if (order.getStatus() != ServiceOrderStatus.AWAITING_FILM) {
      throw new BusinessException(ErrorCode.ORDER_INVALID_STATE,
          "Đơn hàng không ở trạng thái chờ nhận phim (trạng thái hiện tại: " + order.getStatus() + ").");
    }

    Instant now = clock.instant();
    long finalPrice = request.finalTotalVnd();
    order.setFinalTotalVnd(finalPrice);
    order.setPriceLockedAt(now);

    Long totalPaid = paymentService.getTotalPaidAmount(order.getId());
    long paid = (totalPaid != null) ? totalPaid : 0L;

    log.info("[OrderService] Xác nhận nhận phim đơn #{}: finalPrice={}, paidAmount={}",
        order.getId(), finalPrice, paid);

    if (finalPrice > paid) {
      // Thiếu tiền: chuyển sang AWAITING_TOP_UP để khách nộp bù (BR-18)
      order.setStatus(ServiceOrderStatus.AWAITING_TOP_UP);
    } else {
      // Đủ hoặc thừa tiền: chuyển sang AWAITING_MACHINE_ASSIGNMENT để chuẩn bị vào máy
      order.setStatus(ServiceOrderStatus.AWAITING_MACHINE_ASSIGNMENT);

      if (paid > finalPrice) {
        // Thừa tiền: hoàn tiền phần chênh lệch cho khách (BR-18)
        long refundExcess = paid - finalPrice;
        log.info("[OrderService] Hoàn phần tiền dư cho đơn #{}: excess={} VND", order.getId(), refundExcess);
        paymentService.refundOrder(order.getId(), refundExcess, "Hoàn tiền chênh lệch sau khi chốt giá thực tế");
      }
    }

    ServiceOrder saved = orderRepository.save(order);

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.ORDER_STATUS_CHANGED,
        AuditTargetType.SERVICE_ORDER,
        saved.getId(),
        "Xác nhận nhận phim và chốt giá thực tế " + finalPrice + " VND. Trạng thái mới: " + saved.getStatus(),
        null,
        null
    ));

    return orderMapper.toResponse(saved);
  }

  @Override
  @Transactional
  public ServiceOrderResponse cancelOrder(Long orderId, CancelServiceOrderRequest request, AuthenticatedAccount account) {
    ServiceOrder order = orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));

    // Chỉ chủ đơn hoặc Admin được hủy đơn
    if (account.role() != UserAccountRole.ADMIN && !Objects.equals(order.getPhotographerAccountId(), account.id())) {
      throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "Bạn không có quyền hủy đơn hàng này.");
    }

    if (order.getStatus() == ServiceOrderStatus.CLOSED || order.getStatus() == ServiceOrderStatus.CANCELLED) {
      throw new BusinessException(ErrorCode.ORDER_INVALID_STATE,
          "Không thể hủy đơn hàng đã ở trạng thái " + order.getStatus() + ".");
    }

    Instant now = clock.instant();
    ServiceOrderStatus previousStatus = order.getStatus();
    order.setStatus(ServiceOrderStatus.CANCELLED);
    order.setCancelledAt(now);
    order.setCancelledByAccountId(account.id());
    order.setCancellationReason(request.reason());

    Long totalPaid = paymentService.getTotalPaidAmount(order.getId());
    long paid = (totalPaid != null) ? totalPaid : 0L;

    // BR-38: Huỷ trước khi lab nhận phim (AWAITING_PAYMENT, AWAITING_LAB_CONFIRMATION, AWAITING_FILM): hoàn 100%
    // Huỷ sau khi lab đã nhận phim: có thể tính phí hủy nếu có cấu hình. Mặc định 0 VND cancellation_fee.
    long cancellationFee = 0L;
    if (previousStatus != ServiceOrderStatus.AWAITING_PAYMENT
        && previousStatus != ServiceOrderStatus.AWAITING_LAB_CONFIRMATION
        && previousStatus != ServiceOrderStatus.AWAITING_FILM) {
      // Đã nhận phim / đang xử lý
      // Áp dụng phí hủy nếu cần thiết (ví dụ mặc định 10% hoặc giữ nguyên theo cấu hình)
      cancellationFee = Math.min(paid, 20000L); // phí huỷ tượng trưng 20k hoặc bằng tiền đã trả
    }
    order.setCancellationFeeVnd(cancellationFee);

    long refundAmount = Math.max(0L, paid - cancellationFee);
    if (refundAmount > 0) {
      log.info("[OrderService] Hoàn tiền hủy đơn #{}: paid={}, fee={}, refund={}",
          order.getId(), paid, cancellationFee, refundAmount);
      paymentService.refundOrder(order.getId(), refundAmount, "Hoàn tiền do khách hủy đơn: " + request.reason());
    }

    ServiceOrder saved = orderRepository.save(order);

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.ORDER_STATUS_CHANGED,
        AuditTargetType.SERVICE_ORDER,
        saved.getId(),
        "Khách hủy đơn hàng. Lý do: " + request.reason() + ", phí hủy: " + cancellationFee + " VND",
        null,
        null
    ));

    log.info("[OrderService] Khách đã hủy đơn #{}, trạng thái CANCELLED", saved.getId());
    return orderMapper.toResponse(saved);
  }

  @Override
  @Transactional
  public ServiceOrderResponse closeOrder(Long orderId, AuthenticatedAccount account) {
    ServiceOrder order = orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));

    if (account.role() != UserAccountRole.ADMIN && !Objects.equals(order.getPhotographerAccountId(), account.id())) {
      throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "Bạn không có quyền đóng đơn hàng này.");
    }

    if (order.getStatus() == ServiceOrderStatus.CLOSED) {
      log.info("[OrderService] Đơn #{} đã ở trạng thái CLOSED", orderId);
      return orderMapper.toResponse(order);
    }

    if (order.getStatus() == ServiceOrderStatus.CANCELLED) {
      throw new BusinessException(ErrorCode.ORDER_INVALID_STATE, "Không thể đóng đơn hàng đã bị hủy.");
    }

    Instant now = clock.instant();
    order.setStatus(ServiceOrderStatus.CLOSED);
    order.setClosedAt(now);

    ServiceOrder saved = orderRepository.save(order);

    // BR-10, BR-55: Khi đơn đóng thành công (CLOSED), tạo Commission tương ứng
    try {
      commissionService.createCommissionForOrder(saved);
    } catch (Exception e) {
      log.error("[OrderService] Lỗi khi tạo Commission cho đơn #{}: {}", saved.getId(), e.getMessage());
    }

    auditLogService.recordSuccess(new AuditEntry(
        AuditAction.ORDER_STATUS_CHANGED,
        AuditTargetType.SERVICE_ORDER,
        saved.getId(),
        "Đơn hàng " + saved.getOrderCode() + " đã được đóng thành công (CLOSED)",
        null,
        null
    ));

    log.info("[OrderService] Đơn #{} đã chuyển sang CLOSED", saved.getId());
    return orderMapper.toResponse(saved);
  }

  private void validateLabOwnership(Long labId, AuthenticatedAccount account) {
    if (account.role() == UserAccountRole.ADMIN) {
      return;
    }
    if (account.role() == UserAccountRole.FILM_LAB_OWNER) {
      FilmLab lab = labAccessService.requireOwnedLab(account.id());
      if (!Objects.equals(labId, lab.getId())) {
        throw new BusinessException(ErrorCode.LAB_ACCESS_DENIED, "Bạn không có quyền thao tác trên Film Lab này.");
      }
      return;
    }
    throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thực hiện thao tác này.");
  }

  /**
   * Lắng nghe sự kiện thanh toán thành công (PaymentSucceededEvent) được phát từ PaymentService.
   * Xử lý đồng bộ trong cùng transaction của IPN để chuyển đơn sang AWAITING_LAB_CONFIRMATION.
   */
  @EventListener
  @Transactional
  public void handlePaymentSucceeded(PaymentSucceededEvent event) {
    if (event.serviceOrderId() == null) {
      return;
    }

    log.info("[OrderService] Nhận PaymentSucceededEvent cho orderId={}, amount={}",
        event.serviceOrderId(), event.amountVnd());

    ServiceOrder order = orderRepository.findById(event.serviceOrderId()).orElse(null);
    if (order == null) {
      log.warn("[OrderService] Không tìm thấy đơn hàng #{} khi xử lý PaymentSucceededEvent", event.serviceOrderId());
      return;
    }

    if (order.getStatus() == ServiceOrderStatus.AWAITING_PAYMENT) {
      order.setStatus(ServiceOrderStatus.AWAITING_LAB_CONFIRMATION);
      Instant now = clock.instant();
      order.setLabResponseDueAt(now.plus(Duration.ofHours(24)));
      orderRepository.save(order);

      auditLogService.recordSuccess(new AuditEntry(
          AuditAction.ORDER_STATUS_CHANGED,
          AuditTargetType.SERVICE_ORDER,
          order.getId(),
          "Đơn hàng được chuyển sang AWAITING_LAB_CONFIRMATION sau khi thanh toán thành công qua " + event.method(),
          null,
          null
      ));
      log.info("[OrderService] Cập nhật order #{} sang AWAITING_LAB_CONFIRMATION thành công", order.getId());
    } else {
      log.info("[OrderService] Đơn hàng #{} đang ở trạng thái {}, bỏ qua cập nhật từ event",
          order.getId(), order.getStatus());
    }
  }

  private void validateOrderAccess(ServiceOrder order, AuthenticatedAccount account) {
    if (account.role() == UserAccountRole.ADMIN) {
      return;
    }
    if (account.role() == UserAccountRole.PHOTOGRAPHER) {
      if (!Objects.equals(order.getPhotographerAccountId(), account.id())) {
        throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "Bạn không có quyền truy cập đơn hàng này.");
      }
      return;
    }
    if (account.role() == UserAccountRole.FILM_LAB_OWNER) {
      FilmLab lab = labAccessService.requireOwnedLab(account.id());
      if (!Objects.equals(order.getFilmLabId(), lab.getId())) {
        throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "Bạn không có quyền truy cập đơn hàng của lab khác.");
      }
      return;
    }
    throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "Quyền truy cập không hợp lệ.");
  }

  private String generateOrderCode() {
    StringBuilder sb = new StringBuilder("FL-");
    for (int i = 0; i < CODE_LENGTH; i++) {
      int idx = RANDOM.nextInt(CODE_ALPHABET.length());
      sb.append(CODE_ALPHABET.charAt(idx));
    }
    return sb.toString();
  }
}

