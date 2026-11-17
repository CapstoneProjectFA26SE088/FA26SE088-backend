package com.fa26se088.filmlink.service.order.impl;

import static com.fa26se088.filmlink.service.order.impl.ServiceOrderServiceImpl.NOTIFICATION_TIME;
import static com.fa26se088.filmlink.service.order.impl.ServiceOrderServiceImpl.VIETNAM_ZONE;

import com.fa26se088.filmlink.dto.account.response.UserAccountResponse;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.delivery.request.RecordFilmHandoverRequest;
import com.fa26se088.filmlink.dto.order.request.ConfirmFilmReceiptRequest;
import com.fa26se088.filmlink.dto.order.request.FilmRollReceiptRequest;
import com.fa26se088.filmlink.dto.order.request.SearchLabServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderSummaryResponse;
import com.fa26se088.filmlink.dto.order.response.OrderPhotographerResponse;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.delivery.enums.HandoverPoint;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.entity.order.FilmRoll;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.FilmRollStatus;
import com.fa26se088.filmlink.entity.order.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.order.ServiceOrderMapper;
import com.fa26se088.filmlink.repository.order.FilmRollRepository;
import com.fa26se088.filmlink.repository.order.FilmRollRepository.RollCountRow;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.service.account.UserAccountService;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.delivery.FilmHandoverService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.notification.event.NotificationEvent;
import com.fa26se088.filmlink.service.order.LabServiceOrderService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LabServiceOrderServiceImpl implements LabServiceOrderService {

  static final int DEFAULT_PAGE_SIZE = 20;
  static final int MAX_PAGE_SIZE = 100;

  /** BR-25, UC23: 1–10 ảnh tình trạng phim khi lab nhận. */
  static final int MIN_HANDOVER_PHOTOS = 1;

  static final int MAX_HANDOVER_PHOTOS = 10;

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
  private final FilmHandoverService filmHandoverService;
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

  /**
   * UC23: Film Lab Owner xác nhận đã nhận phim, đếm cuộn và chốt giá.
   *
   * <ul>
   *   <li>CR-14: chỉ đơn của lab mình; khác → 404 {@code ORDER_NOT_FOUND}. Đọc đơn bằng {@code
   *       SELECT ... FOR UPDATE} rồi mới kiểm trạng thái.
   *   <li>State machine Report 4 (TC-SM05-07): chỉ từ {@code AWAITING_FILM}; khác (kể cả nhận phim
   *       lần hai) → 409 {@code ORDER_STATUS_CONFLICT}.
   *   <li>UC23: {@code rolls} gồm đúng một lần mỗi cuộn đã khai báo của đơn (không thiếu, không
   *       trùng, không có cuộn của đơn khác) → sai thì 400 {@code VALIDATION_ERROR} kèm danh sách
   *       cuộn sai. Thêm cuộn lúc nhận ({@code is_added_at_receipt}) là Later.
   *   <li>UC23: ít nhất một cuộn được nhận. Không nhận cuộn nào (huỷ đơn + hoàn tiền + gắn cờ
   *       Admin, TC-F11-03) là Later → 422 {@code ORDER_OPTION_NOT_SUPPORTED}.
   *   <li>BR-25: 1–10 ảnh bằng chứng → ngoài khoảng thì 400. Bàn giao ghi qua {@link
   *       FilmHandoverService} ({@code RECEIVED_AT_LAB}, {@code roll_count} = số cuộn nhận, người
   *       thực hiện = chủ lab). Ảnh chỉ được upload sau khi mọi rule trên đã qua.
   *   <li>UC23: cuộn → {@code RECEIVED} / {@code NOT_RECEIVED}. Giá chốt = Σ ({@code
   *       unit_price_vnd + scan_option_price_vnd + push_pull_surcharge_vnd +
   *       express_surcharge_vnd}) của cuộn {@code RECEIVED}, theo giá đã chụp lúc tạo đơn (không
   *       đọc lại bảng giá); ghi {@code final_total_vnd}, {@code price_locked_at = now} ({@code
   *       ck_service_orders_price_lock}). Đơn PAY_AT_LAB: giá chốt là số Photographer trả tại lab.
   *   <li>Trạng thái → {@code AWAITING_MACHINE_ASSIGNMENT}; báo Photographer số cuộn nhận / khai
   *       báo và giá chốt (UC04); BR-32: audit {@code ORDER_FILM_RECEIVED}.
   * </ul>
   */
  @Override
  @Transactional
  public LabServiceOrderResponse confirmFilmReceipt(
      Long ownerAccountId,
      Long orderId,
      ConfirmFilmReceiptRequest request,
      List<MultipartFile> photos) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabIdForUpdate(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    ServiceOrderStatus before = order.getStatus();
    if (before != ServiceOrderStatus.AWAITING_FILM) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Chỉ xác nhận nhận phim được với đơn đang chờ phim; đơn "
              + order.getOrderCode()
              + " đang ở trạng thái "
              + before.name()
              + ".");
    }

    List<FilmRoll> rolls = filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(orderId);
    Map<Long, Boolean> receivedByRollId = validateRollCount(rolls, request.rolls());
    long receivedCount = receivedByRollId.values().stream().filter(Boolean::booleanValue).count();
    if (receivedCount == 0) {
      throw new BusinessException(
          ErrorCode.ORDER_OPTION_NOT_SUPPORTED,
          "Chưa hỗ trợ xác nhận đơn không nhận được cuộn phim nào (huỷ đơn và hoàn tiền sẽ có ở"
              + " giai đoạn sau). Cần ít nhất 1 cuộn được nhận.");
    }
    List<MultipartFile> photoList = photos == null ? List.of() : photos;
    if (photoList.size() < MIN_HANDOVER_PHOTOS || photoList.size() > MAX_HANDOVER_PHOTOS) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Cần "
              + MIN_HANDOVER_PHOTOS
              + "–"
              + MAX_HANDOVER_PHOTOS
              + " ảnh tình trạng phim khi nhận; đã gửi "
              + photoList.size()
              + ".",
          Map.of("field", "photos", "min", MIN_HANDOVER_PHOTOS, "max", MAX_HANDOVER_PHOTOS));
    }

    long finalTotal = 0;
    for (FilmRoll roll : rolls) {
      boolean received = receivedByRollId.get(roll.getId());
      roll.setStatus(received ? FilmRollStatus.RECEIVED : FilmRollStatus.NOT_RECEIVED);
      if (received) {
        finalTotal += rollPrice(roll);
      }
    }
    filmRollRepository.saveAll(rolls);
    order.setFinalTotalVnd(finalTotal);
    order.setPriceLockedAt(clock.instant());
    order.setStatus(ServiceOrderStatus.AWAITING_MACHINE_ASSIGNMENT);
    try {
      order = serviceOrderRepository.saveAndFlush(order);
    } catch (OptimisticLockingFailureException ex) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Đơn đã bị thay đổi bởi người khác. Vui lòng tải lại trang và thử lại.");
    }

    // BR-25: upload ảnh và ghi bàn giao sau khi mọi rule đã qua; rollback thì file bị xoá.
    filmHandoverService.record(
        new RecordFilmHandoverRequest(
            order.getId(),
            null,
            HandoverPoint.RECEIVED_AT_LAB,
            (int) receivedCount,
            request.note(),
            ownerAccountId,
            null,
            photoList));

    // TODO(FA26-157): so final_total với PaymentService.netPaidForOrder → createOrderTopUp /
    // createOrderRefund (SPEC-00 §4.5)

    Map<String, Object> auditBefore = new LinkedHashMap<>();
    auditBefore.put("status", before.name());
    auditBefore.put("finalTotalVnd", null);
    auditBefore.put("receivedRollCount", 0);
    Map<String, Object> auditAfter = new LinkedHashMap<>();
    auditAfter.put("status", order.getStatus().name());
    auditAfter.put("finalTotalVnd", finalTotal);
    auditAfter.put("receivedRollCount", receivedCount);
    auditAfter.put("declaredRollCount", rolls.size());
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_FILM_RECEIVED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            auditBefore,
            auditAfter));
    notifyPhotographerFilmReceived(lab, order, receivedCount, rolls.size());
    return toLabResponse(order);
  }

  /**
   * UC23: {@code rolls} phải phủ đúng một lần mỗi cuộn của đơn. Gom đủ ba loại lỗi (trùng, không
   * thuộc đơn, thiếu) vào một 400 để lab sửa một lần.
   *
   * @return {@code filmRollId → received} cho mọi cuộn của đơn
   */
  private static Map<Long, Boolean> validateRollCount(
      List<FilmRoll> declaredRolls, List<FilmRollReceiptRequest> counted) {
    Map<Long, FilmRoll> declaredById = new LinkedHashMap<>();
    declaredRolls.forEach(roll -> declaredById.put(roll.getId(), roll));

    Map<Long, Boolean> receivedByRollId = new LinkedHashMap<>();
    Set<Long> duplicates = new TreeSet<>();
    Set<Long> unknown = new TreeSet<>();
    for (FilmRollReceiptRequest item : counted) {
      if (!declaredById.containsKey(item.filmRollId())) {
        unknown.add(item.filmRollId());
      } else if (receivedByRollId.putIfAbsent(item.filmRollId(), item.received()) != null) {
        duplicates.add(item.filmRollId());
      }
    }
    List<Long> missing =
        declaredById.keySet().stream().filter(id -> !receivedByRollId.containsKey(id)).toList();
    if (duplicates.isEmpty() && unknown.isEmpty() && missing.isEmpty()) {
      return receivedByRollId;
    }

    List<String> problems = new ArrayList<>();
    if (!missing.isEmpty()) {
      problems.add(
          "thiếu cuộn "
              + missing.stream()
                  .map(id -> "#" + declaredById.get(id).getRollSequence() + " (id " + id + ")")
                  .collect(Collectors.joining(", ")));
    }
    if (!duplicates.isEmpty()) {
      problems.add("cuộn bị gửi trùng: id " + joinIds(duplicates));
    }
    if (!unknown.isEmpty()) {
      problems.add("cuộn không thuộc đơn này: id " + joinIds(unknown));
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("field", "rolls");
    properties.put("missingFilmRollIds", missing);
    properties.put("duplicateFilmRollIds", List.copyOf(duplicates));
    properties.put("unknownFilmRollIds", List.copyOf(unknown));
    throw new BusinessException(
        ErrorCode.VALIDATION_ERROR,
        "Danh sách cuộn phải gồm đúng một lần mỗi cuộn đã khai báo của đơn: "
            + String.join("; ", problems)
            + ".",
        properties);
  }

  /** UC23: giá một cuộn theo các giá đã chụp lúc tạo đơn. */
  private static long rollPrice(FilmRoll roll) {
    return roll.getUnitPriceVnd()
        + roll.getScanOptionPriceVnd()
        + roll.getPushPullSurchargeVnd()
        + roll.getExpressSurchargeVnd();
  }

  private static String joinIds(Set<Long> ids) {
    return ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }

  /** UC23, UC04: báo Photographer số cuộn lab đã nhận và giá chốt (gửi sau khi commit). */
  private void notifyPhotographerFilmReceived(
      FilmLab lab, ServiceOrder order, long receivedCount, int declaredCount) {
    String body =
        "Film Lab "
            + lab.getLabName()
            + " đã nhận "
            + receivedCount
            + "/"
            + declaredCount
            + " cuộn phim của đơn "
            + order.getOrderCode()
            + ". Giá chốt: "
            + formatVnd(order.getFinalTotalVnd())
            + ".";
    if (order.getPaymentMethod() == PaymentMethod.PAY_AT_LAB) {
      body += " Bạn thanh toán số tiền này tại lab.";
    }
    eventPublisher.publishEvent(
        new NotificationEvent(
            order.getPhotographerAccountId(),
            NotificationType.ORDER_STATUS,
            "Lab đã nhận phim của đơn " + order.getOrderCode(),
            body,
            null));
  }

  /** CR-05: VND chẵn, phân cách hàng nghìn bằng dấu chấm, ví dụ {@code 220.000 ₫}. */
  static String formatVnd(long amountVnd) {
    return String.format(Locale.ROOT, "%,d", amountVnd).replace(',', '.') + " ₫";
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
   * Bàn giao phim đọc qua {@link FilmHandoverService} (không inject repository của delivery).
   *
   * <p>TODO: shipment, timeline công đoạn, thanh toán khi các contract tương ứng lên dev.
   */
  private LabServiceOrderResponse toLabResponse(ServiceOrder order) {
    UserAccountResponse account =
        userAccountService.getCurrentAccount(order.getPhotographerAccountId());
    return serviceOrderMapper.toLabResponse(
        order,
        filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(order.getId()),
        new OrderPhotographerResponse(account.id(), account.fullName(), account.phoneNumber()),
        filmHandoverService.getByServiceOrderId(order.getId()));
  }

  private static BusinessException orderNotFound() {
    return new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn dịch vụ.");
  }
}
