package com.fa26se088.filmlink.service.order.impl;

import static com.fa26se088.filmlink.service.order.impl.ServiceOrderServiceImpl.NOTIFICATION_TIME;
import static com.fa26se088.filmlink.service.order.impl.ServiceOrderServiceImpl.VIETNAM_ZONE;

import com.fa26se088.filmlink.dto.account.response.UserAccountResponse;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.order.request.SearchLabServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderSummaryResponse;
import com.fa26se088.filmlink.dto.order.response.OrderPhotographerResponse;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.order.ServiceOrderMapper;
import com.fa26se088.filmlink.repository.order.FilmRollRepository;
import com.fa26se088.filmlink.repository.order.FilmRollRepository.RollCountRow;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.service.account.UserAccountService;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.notification.event.NotificationEvent;
import com.fa26se088.filmlink.service.order.LabServiceOrderService;
import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LabServiceOrderServiceImpl implements LabServiceOrderService {

  static final int DEFAULT_PAGE_SIZE = 20;
  static final int MAX_PAGE_SIZE = 100;

  /** CR-02: mới nhất trước; {@code id} để thứ tự ổn định khi trùng {@code created_at}. */
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  private final ServiceOrderRepository serviceOrderRepository;
  private final FilmRollRepository filmRollRepository;
  private final LabAccessService labAccessService;
  private final UserAccountService userAccountService;
  private final AuditLogService auditLogService;
  private final ApplicationEventPublisher eventPublisher;
  private final ServiceOrderMapper serviceOrderMapper;
  private final Clock clock;

  /**
   * UC22, CR-01, CR-02: chỉ đơn của lab mình; 20 dòng mặc định, tối đa 100; mới nhất trước. Số
   * cuộn đếm bằng một query gộp cho cả trang (không N+1).
   */
  @Override
  @Transactional(readOnly = true)
  public PageResponse<LabServiceOrderSummaryResponse> getMyOrders(
      Long ownerAccountId, SearchLabServiceOrderRequest request) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    int page = request.page() == null ? 0 : request.page();
    int size = request.size() == null ? DEFAULT_PAGE_SIZE : Math.min(request.size(), MAX_PAGE_SIZE);
    PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
    Page<ServiceOrder> orders =
        request.status() == null
            ? serviceOrderRepository.findByFilmLabId(lab.getId(), pageable)
            : serviceOrderRepository.findByFilmLabIdAndStatus(
                lab.getId(), request.status(), pageable);

    Map<Long, Long> rollCounts =
        orders.isEmpty()
            ? Map.of()
            : filmRollRepository
                .countByServiceOrderIds(orders.map(ServiceOrder::getId).getContent())
                .stream()
                .collect(
                    Collectors.toMap(RollCountRow::getServiceOrderId, RollCountRow::getRollCount));
    return PageResponse.from(
        orders,
        order ->
            serviceOrderMapper.toLabSummaryResponse(
                order, rollCounts.getOrDefault(order.getId(), 0L)));
  }

  /** UC22, CR-14: đơn thuộc lab khác trả 404 như đơn không tồn tại. */
  @Override
  @Transactional(readOnly = true)
  public LabServiceOrderResponse getMyOrder(Long ownerAccountId, Long orderId) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabId(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    return toLabResponse(order);
  }

  /**
   * UC22: Film Lab Owner nhận đơn.
   *
   * <ul>
   *   <li>CR-14: chỉ đơn của lab mình; đơn của lab khác → 404 {@code ORDER_NOT_FOUND}.
   *   <li>State machine Report 4 (TC-SM05-05): chỉ từ {@code AWAITING_LAB_CONFIRMATION}; trạng thái
   *       khác → 409 {@code ORDER_STATUS_CONFLICT}. Đọc đơn bằng {@code SELECT ... FOR UPDATE} rồi
   *       mới kiểm trạng thái: hai request nhận cùng lúc thì request sau chờ khoá, thấy trạng thái
   *       đã đổi và nhận 409. Đụng {@code @Version} với một cập nhật khác → cũng 409.
   *   <li>Nhận đơn: {@code status = AWAITING_FILM}, {@code accepted_at = now}; không đổi {@code
   *       lab_response_due_at}. Nhận sau hạn vẫn cho phép (job tự huỷ đơn quá hạn là ORD-07, Later).
   *   <li>UC04: báo Photographer. SELF_DROP_OFF kèm tên, địa chỉ lab và mã đơn; PICKUP báo lab sẽ
   *       lấy phim theo khung giờ đã chọn (giờ Việt Nam, CR-04).
   *   <li>BR-32: audit {@code ORDER_ACCEPTED}, before/after ghi {@code status}.
   * </ul>
   */
  @Override
  @Transactional
  public LabServiceOrderResponse accept(Long ownerAccountId, Long orderId) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabIdForUpdate(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    ServiceOrderStatus before = order.getStatus();
    if (before != ServiceOrderStatus.AWAITING_LAB_CONFIRMATION) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Chỉ nhận được đơn đang chờ lab xác nhận; đơn "
              + order.getOrderCode()
              + " đang ở trạng thái "
              + before.name()
              + ".");
    }

    order.setStatus(ServiceOrderStatus.AWAITING_FILM);
    order.setAcceptedAt(clock.instant());
    try {
      order = serviceOrderRepository.saveAndFlush(order);
    } catch (OptimisticLockingFailureException ex) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Đơn đã bị thay đổi bởi người khác. Vui lòng tải lại trang và thử lại.");
    }

    // TODO(P-09): gọi ShipmentService.createOrderPickup khi contract (SPEC-00 §4.6b) lên dev

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_ACCEPTED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            Map.of("status", before.name()),
            Map.of("status", order.getStatus().name())));
    notifyPhotographerAccepted(lab, order);
    return toLabResponse(order);
  }

  /** UC22, UC04: báo Photographer lab đã nhận đơn (gửi sau khi transaction commit). */
  private void notifyPhotographerAccepted(FilmLab lab, ServiceOrder order) {
    String body;
    if (order.getFilmHandoverMethod() == FilmHandoverMethod.PICKUP) {
      body =
          "Film Lab "
              + lab.getLabName()
              + " đã nhận đơn "
              + order.getOrderCode()
              + ". Lab sẽ tới lấy phim tại địa chỉ bạn đã cung cấp trong khung giờ "
              + NOTIFICATION_TIME.format(order.getPickupWindowStartAt().atZone(VIETNAM_ZONE))
              + " – "
              + NOTIFICATION_TIME.format(order.getPickupWindowEndAt().atZone(VIETNAM_ZONE))
              + ".";
    } else {
      body =
          "Film Lab "
              + lab.getLabName()
              + " đã nhận đơn "
              + order.getOrderCode()
              + ". Vui lòng mang phim tới lab tại "
              + lab.getAddressLine()
              + ", "
              + lab.getDistrict()
              + ", "
              + lab.getProvince()
              + " và báo mã đơn "
              + order.getOrderCode()
              + " khi gửi phim.";
    }
    eventPublisher.publishEvent(
        new NotificationEvent(
            order.getPhotographerAccountId(),
            NotificationType.ORDER_STATUS,
            "Đơn " + order.getOrderCode() + " đã được nhận",
            body,
            null));
  }

  /**
   * Photographer đọc qua {@link UserAccountService} (không inject repository của account, R4).
   *
   * <p>TODO: shipment, timeline công đoạn, thanh toán khi các contract tương ứng lên dev.
   */
  private LabServiceOrderResponse toLabResponse(ServiceOrder order) {
    UserAccountResponse account =
        userAccountService.getCurrentAccount(order.getPhotographerAccountId());
    return serviceOrderMapper.toLabResponse(
        order,
        filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(order.getId()),
        new OrderPhotographerResponse(account.id(), account.fullName(), account.phoneNumber()));
  }

  private static BusinessException orderNotFound() {
    return new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn dịch vụ.");
  }
}
