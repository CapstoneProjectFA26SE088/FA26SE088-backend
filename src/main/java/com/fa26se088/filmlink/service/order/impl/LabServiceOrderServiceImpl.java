package com.fa26se088.filmlink.service.order.impl;

import static com.fa26se088.filmlink.service.order.impl.ServiceOrderServiceImpl.NOTIFICATION_TIME;
import static com.fa26se088.filmlink.service.order.impl.ServiceOrderServiceImpl.VIETNAM_ZONE;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.account.response.UserAccountResponse;
import com.fa26se088.filmlink.dto.archive.request.SetFullResolutionLinkRequest;
import com.fa26se088.filmlink.dto.archive.response.RollScanStatusResponse;
import com.fa26se088.filmlink.dto.archive.response.ScanFileResponse;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.delivery.request.RecordFilmHandoverRequest;
import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverResponse;
import com.fa26se088.filmlink.dto.machine.CapacityProposal;
import com.fa26se088.filmlink.dto.machine.CapacityRequest;
import com.fa26se088.filmlink.dto.machine.RollGroup;
import com.fa26se088.filmlink.dto.order.request.ConfirmFilmReceiptRequest;
import com.fa26se088.filmlink.dto.order.request.ConfirmScheduleRequest;
import com.fa26se088.filmlink.dto.order.request.FilmRollReceiptRequest;
import com.fa26se088.filmlink.dto.order.request.RecordFilmReturnRequest;
import com.fa26se088.filmlink.dto.order.request.RecordFilmRollDamageRequest;
import com.fa26se088.filmlink.dto.order.request.RecordProcessingStageRequest;
import com.fa26se088.filmlink.dto.order.request.RejectServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.SearchLabServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.FilmRollDamagePhotoResponse;
import com.fa26se088.filmlink.dto.order.response.FilmRollResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderSummaryResponse;
import com.fa26se088.filmlink.dto.order.response.OrderPhotographerResponse;
import com.fa26se088.filmlink.dto.order.response.ScheduleAllocationResponse;
import com.fa26se088.filmlink.dto.order.response.ScheduleProposalResponse;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.delivery.enums.HandoverPoint;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.entity.order.FilmRoll;
import com.fa26se088.filmlink.entity.order.FilmRollDamagePhoto;
import com.fa26se088.filmlink.entity.order.ProcessingStageRecord;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.FilmRollStatus;
import com.fa26se088.filmlink.entity.order.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ProcessingStage;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.order.ServiceOrderMapper;
import com.fa26se088.filmlink.repository.order.FilmRollDamagePhotoRepository;
import com.fa26se088.filmlink.repository.order.FilmRollRepository;
import com.fa26se088.filmlink.repository.order.FilmRollRepository.RollCountRow;
import com.fa26se088.filmlink.repository.order.ProcessingStageRecordRepository;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.service.account.UserAccountService;
import com.fa26se088.filmlink.service.archive.ArchiveService;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.delivery.FilmHandoverService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.machine.CapacitySchedulingService;
import com.fa26se088.filmlink.service.notification.event.NotificationEvent;
import com.fa26se088.filmlink.service.order.LabServiceOrderService;
import com.fa26se088.filmlink.service.payment.PaymentService;
import java.io.IOException;
import java.io.InputStream;
import com.fa26se088.filmlink.service.order.event.OrderAcceptedEvent;
import com.fa26se088.filmlink.service.order.event.OrderRejectedEvent;
import com.fa26se088.filmlink.service.payment.PaymentService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabServiceOrderServiceImpl implements LabServiceOrderService {

  static final int DEFAULT_PAGE_SIZE = 20;
  static final int MAX_PAGE_SIZE = 100;

  /** BR-25, UC23: 1–10 ảnh tình trạng phim khi lab nhận. */
  static final int MIN_HANDOVER_PHOTOS = 1;

  static final int MAX_HANDOVER_PHOTOS = 10;

  static final int MAX_DAMAGE_NOTE_LENGTH = 500;

  /** CR-02: mới nhất trước; {@code id} để thứ tự ổn định khi trùng {@code created_at}. */
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  private static final Set<ServiceOrderStatus> REJECTABLE_BY_LAB =
      Set.of(ServiceOrderStatus.AWAITING_LAB_CONFIRMATION, ServiceOrderStatus.AWAITING_FILM);

  private final ServiceOrderRepository serviceOrderRepository;
  private final FilmRollRepository filmRollRepository;
  private final FilmRollDamagePhotoRepository filmRollDamagePhotoRepository;
  private final LabAccessService labAccessService;
  private final UserAccountService userAccountService;
  private final AuditLogService auditLogService;
  private final ApplicationEventPublisher eventPublisher;
  private final ServiceOrderMapper serviceOrderMapper;
  private final FilmHandoverService filmHandoverService;
  private final ProcessingStageRecordRepository processingStageRecordRepository;
  private final CapacitySchedulingService capacitySchedulingService;
  private final ArchiveService archiveService;
  private final StorageClient storageClient;
  private final PaymentService paymentService;
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

    if (order.getFilmHandoverMethod() == FilmHandoverMethod.PICKUP
        || order.getFilmHandoverMethod() == FilmHandoverMethod.PARCEL_SHIPPING) {
      eventPublisher.publishEvent(
          new OrderAcceptedEvent(
              order.getId(),
              order.getOrderCode(),
              order.getFilmLabId(),
              order.getFilmHandoverMethod(),
              order.getAcceptedAt()));
    }

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
   * UC22, BR-39, UH-09: Film Lab Owner từ chối đơn.
   *
   * <ul>
   *   <li>CR-14: chỉ đơn của lab mình; đơn của lab khác → 404 {@code ORDER_NOT_FOUND}.
   *   <li>Chỉ từ chối được khi đơn ở trạng thái {@code AWAITING_LAB_CONFIRMATION} hoặc {@code
   *       AWAITING_FILM}. Trạng thái khác → 409 {@code ORDER_STATUS_CONFLICT}.
   *   <li>Khoá dòng bằng {@code SELECT ... FOR UPDATE}.
   *   <li>Lý do từ chối 10–500 ký tự.
   *   <li>Cập nhật trạng thái → {@code CANCELLED}, {@code cancelled_at = now}, {@code
   *       cancelled_by_account_id = ownerAccountId}, {@code rejection_reason = reason}, {@code
   *       cancellation_reason = reason}, {@code cancellation_fee_vnd = 0}.
   *   <li>BR-39: Hoàn tiền 100% tự động nếu đã thanh toán trước (netPaid > 0): gọi {@code
   *       createOrderRefund(orderId, netPaid, ...)}.
   *   <li>UH-09: Nếu phim đã gửi đi (kiểm tra qua {@link FilmHandoverService#getByServiceOrderId}),
   *       ghi nhận phí lab chịu trong audit log ({@code returnFeePayer = FILM_LAB}).
   *   <li>Audit log {@code ORDER_CANCELLED}.
   *   <li>Phát {@link OrderRejectedEvent} cho các domain liên quan.
   *   <li>Báo Photographer qua {@link NotificationEvent}.
   * </ul>
   */
  @Override
  @Transactional
  public LabServiceOrderResponse reject(
      Long ownerAccountId, Long orderId, RejectServiceOrderRequest request) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabIdForUpdate(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    ServiceOrderStatus before = order.getStatus();
    if (!REJECTABLE_BY_LAB.contains(before)) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Chỉ từ chối được đơn đang chờ xác nhận hoặc chờ nhận phim; đơn "
              + order.getOrderCode()
              + " đang ở trạng thái "
              + before.name()
              + ".");
    }

    String reason = request.reason() == null ? "" : request.reason().trim();
    if (reason.length() < 10 || reason.length() > 500) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Lý do từ chối phải có độ dài từ 10 đến 500 ký tự.",
          Map.of("field", "reason", "min", 10, "max", 500));
    }

    Instant now = clock.instant();
    order.setStatus(ServiceOrderStatus.CANCELLED);
    order.setCancelledAt(now);
    order.setCancelledByAccountId(ownerAccountId);
    order.setRejectionReason(reason);
    order.setCancellationReason(reason);
    order.setCancellationFeeVnd(0L);

    try {
      order = serviceOrderRepository.saveAndFlush(order);
    } catch (OptimisticLockingFailureException ex) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Đơn đã bị thay đổi bởi người khác. Vui lòng tải lại trang và thử lại.");
    }

    boolean filmAlreadyShipped = isFilmAlreadyShipped(order.getId());

    long netPaid = paymentService.netPaidForOrder(order.getId());
    if (netPaid > 0) {
      paymentService.createOrderRefund(order.getId(), netPaid, "Lab từ chối đơn: " + reason);
    }

    eventPublisher.publishEvent(
        new OrderRejectedEvent(
            order.getId(),
            order.getOrderCode(),
            order.getPhotographerAccountId(),
            order.getFilmLabId(),
            netPaid,
            reason,
            filmAlreadyShipped,
            now));

    Map<String, Object> auditAfter = new LinkedHashMap<>();
    auditAfter.put("status", order.getStatus().name());
    auditAfter.put("rejectionReason", reason);
    auditAfter.put("filmAlreadyShipped", filmAlreadyShipped);
    if (filmAlreadyShipped) {
      auditAfter.put("returnFeePayer", "FILM_LAB");
    }

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_CANCELLED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            Map.of("status", before.name()),
            auditAfter));

    notifyPhotographerRejected(lab, order, reason, filmAlreadyShipped);
    return toLabResponse(order);
  }

  /**
   * UC69, BR-58–BR-61: ngày hẹn trả đề xuất. Chỉ đọc, không ghi DB.
   *
   * <ul>
   *   <li>CR-14: đơn của lab khác → 404 {@code ORDER_NOT_FOUND}.
   *   <li>Chỉ đơn {@code AWAITING_MACHINE_ASSIGNMENT}; khác → 409 {@code ORDER_STATUS_CONFLICT}.
   *   <li>Thuật toán công suất thuộc domain machine ({@link CapacitySchedulingService#propose});
   *       order chỉ dựng {@link CapacityRequest} (xem {@link #capacityRequest}).
   * </ul>
   */
  @Override
  @Transactional(readOnly = true)
  public ScheduleProposalResponse getScheduleProposal(Long ownerAccountId, Long orderId) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabId(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    requireAwaitingMachineAssignment(order);
    return toProposalResponse(capacitySchedulingService.propose(capacityRequest(order)));
  }

  /**
   * UC69: Film Lab Owner xác nhận ngày hẹn trả.
   *
   * <ul>
   *   <li>CR-14: đơn của lab khác → 404. Đọc đơn bằng {@code SELECT ... FOR UPDATE} rồi mới kiểm
   *       trạng thái: chỉ {@code AWAITING_MACHINE_ASSIGNMENT}, khác (kể cả xác nhận lần hai) → 409.
   *   <li>Gọi lại {@code propose} (công suất có thể đã đổi từ lúc lab xem đề xuất). UH-51: không
   *       khả thi → 422 {@code ORDER_SCHEDULE_INFEASIBLE} (huỷ + hoàn tiền 100% là Later).
   *   <li>BR-60: ngày lab chọn sớm hơn ngày đề xuất → 422 {@code ORDER_PROMISED_DATE_TOO_EARLY};
   *       không gửi ngày = dùng ngày đề xuất.
   *   <li>BR-59: {@code confirm} ghi phân bổ công suất (domain machine); {@code promised_return_at}
   *       lấy theo giá trị {@code confirm} trả về, {@code machine_assignment_confirmed_at = now}
   *       ({@code ck_service_orders_promised}), trạng thái → {@code IN_PROCESSING}.
   *   <li>BR-07: ghi công đoạn đầu tiên {@code RECEIVED}, người ghi là chủ lab.
   *   <li>UC04: báo Photographer ngày hẹn trả (giờ Việt Nam, CR-04). BR-32: audit {@code
   *       ORDER_SCHEDULE_CONFIRMED}.
   * </ul>
   */
  @Override
  @Transactional
  public LabServiceOrderResponse confirmSchedule(
      Long ownerAccountId, Long orderId, ConfirmScheduleRequest request) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabIdForUpdate(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    final ServiceOrderStatus before = requireAwaitingMachineAssignment(order);

    CapacityRequest capacityRequest = capacityRequest(order);
    CapacityProposal proposal = capacitySchedulingService.propose(capacityRequest);
    if (!proposal.feasible()) {
      throw new BusinessException(
          ErrorCode.ORDER_SCHEDULE_INFEASIBLE,
          "Lab chưa xếp được lịch xử lý cho đơn "
              + order.getOrderCode()
              + (proposal.infeasibleReason() == null ? "." : ": " + proposal.infeasibleReason())
              + " Huỷ đơn và hoàn tiền sẽ có ở giai đoạn sau.");
    }
    Instant requested = request == null ? null : request.promisedReturnAt();
    Instant chosen = requested == null ? proposal.promisedReturnAt() : requested;
    if (chosen.isBefore(proposal.promisedReturnAt())) {
      throw new BusinessException(
          ErrorCode.ORDER_PROMISED_DATE_TOO_EARLY,
          "Ngày hẹn trả không được sớm hơn ngày đề xuất "
              + NOTIFICATION_TIME.format(proposal.promisedReturnAt().atZone(VIETNAM_ZONE))
              + ".",
          Map.of(
              "field",
              "promisedReturnAt",
              "proposedPromisedReturnAt",
              proposal.promisedReturnAt().toString()));
    }

    CapacityProposal confirmed = capacitySchedulingService.confirm(capacityRequest, chosen);
    Instant now = clock.instant();
    order.setPromisedReturnAt(confirmed.promisedReturnAt());
    order.setMachineAssignmentConfirmedAt(now);
    order.setStatus(ServiceOrderStatus.IN_PROCESSING);
    try {
      order = serviceOrderRepository.saveAndFlush(order);
    } catch (OptimisticLockingFailureException ex) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Đơn đã bị thay đổi bởi người khác. Vui lòng tải lại trang và thử lại.");
    }

    ProcessingStageRecord received = new ProcessingStageRecord();
    received.setServiceOrderId(order.getId());
    received.setStage(ProcessingStage.RECEIVED);
    received.setRecordedByAccountId(ownerAccountId);
    received.setRecordedAt(now);
    processingStageRecordRepository.save(received);

    Map<String, Object> auditBefore = new LinkedHashMap<>();
    auditBefore.put("status", before.name());
    auditBefore.put("promisedReturnAt", null);
    Map<String, Object> auditAfter = new LinkedHashMap<>();
    auditAfter.put("status", order.getStatus().name());
    auditAfter.put("promisedReturnAt", order.getPromisedReturnAt().toString());
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_SCHEDULE_CONFIRMED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            auditBefore,
            auditAfter));
    notifyPhotographerScheduleConfirmed(lab, order);
    return toLabResponse(order);
  }

  /**
   * UC24: Film Lab Owner ghi công đoạn xử lý kế tiếp.
   *
   * <ul>
   *   <li>CR-14: đơn của lab khác → 404 {@code ORDER_NOT_FOUND}. Đọc đơn bằng {@code SELECT ... FOR
   *       UPDATE} rồi mới kiểm: hai request ghi công đoạn cùng lúc được tuần tự hoá, request sau
   *       thấy công đoạn mới và nhận 422.
   *   <li>Chỉ đơn {@code IN_PROCESSING}; khác → 409 {@code ORDER_STATUS_CONFLICT}.
   *   <li>BR-07: {@code stage} phải đúng công đoạn kế tiếp của công đoạn mới nhất (thứ tự RECEIVED →
   *       DEVELOPING → WASHING → DRYING → SCANNING → QC); nhảy cóc, lùi, lặp, hoặc đã tới QC → 422
   *       {@code ORDER_STAGE_OUT_OF_ORDER}. {@code RECEIVED} đã ghi lúc xác nhận lịch (UC69) nên
   *       không ghi lại được.
   *   <li>Insert một dòng {@code processing_stage_records} (append-only), người ghi = chủ lab. Không
   *       đổi trạng thái đơn (giao scan sau QC là T-10).
   *   <li>BR-32: audit {@code ORDER_STAGE_RECORDED}. UC04: báo Photographer tên công đoạn tiếng Việt.
   * </ul>
   */
  @Override
  @Transactional
  public LabServiceOrderResponse recordStage(
      Long ownerAccountId, Long orderId, RecordProcessingStageRequest request) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabIdForUpdate(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    if (order.getStatus() != ServiceOrderStatus.IN_PROCESSING) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Chỉ ghi công đoạn được với đơn đang xử lý; đơn "
              + order.getOrderCode()
              + " đang ở trạng thái "
              + order.getStatus().name()
              + ".");
    }
    ProcessingStage current = currentStage(order.getId());
    requireNextStage(order, current, request.stage());

    ProcessingStageRecord record = new ProcessingStageRecord();
    record.setServiceOrderId(order.getId());
    record.setStage(request.stage());
    record.setNote(request.note());
    record.setRecordedByAccountId(ownerAccountId);
    record.setRecordedAt(clock.instant());
    processingStageRecordRepository.save(record);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_STAGE_RECORDED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            Map.of("stage", current.name()),
            Map.of("stage", request.stage().name())));
    notifyPhotographerStageRecorded(lab, order, request.stage());
    return toLabResponse(order);
  }

  /**
   * UC24, BR-26: Film Lab Owner ghi nhận một cuộn phim {@code RECEIVED} bị hỏng khi xử lý kèm 1–10
   * ảnh bằng chứng → cuộn chuyển {@code DAMAGED}, miễn phí cuộn hỏng, tính lại {@code
   * final_total_vnd}, hoàn tiền chênh lệch nếu khách đã thanh toán online dư, ghi audit và báo
   * Photographer.
   */
  @Override
  @Transactional
  public LabServiceOrderResponse recordRollDamage(
      Long ownerAccountId,
      Long orderId,
      Long rollId,
      RecordFilmRollDamageRequest request,
      List<MultipartFile> photos) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabIdForUpdate(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    requireInProcessing(order, "ghi nhận cuộn phim hỏng");
    String orderCode = order.getOrderCode();

    FilmRoll roll =
        filmRollRepository
            .findByIdAndServiceOrderId(rollId, order.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.ORDER_ROLL_NOT_FOUND,
                        "Không tìm thấy cuộn phim trong đơn " + orderCode + "."));
    if (roll.getStatus() == FilmRollStatus.DAMAGED) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Cuộn phim #"
              + roll.getRollSequence()
              + " của đơn "
              + order.getOrderCode()
              + " đã được ghi nhận hỏng trước đó.");
    }
    if (roll.getStatus() != FilmRollStatus.RECEIVED) {
      throw new BusinessException(
          ErrorCode.ORDER_ROLL_NOT_FOUND,
          "Chỉ ghi nhận hỏng được với cuộn phim lab đã nhận trong đơn "
              + order.getOrderCode()
              + ".");
    }

    String damageNote = request.damageNote() == null ? "" : request.damageNote().strip();
    if (damageNote.isEmpty() || damageNote.length() > MAX_DAMAGE_NOTE_LENGTH) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Ghi chú tình trạng cuộn hỏng bắt buộc và tối đa " + MAX_DAMAGE_NOTE_LENGTH + " ký tự.",
          Map.of("field", "damageNote"));
    }

    List<MultipartFile> photoList = photos == null ? List.of() : photos;
    if (photoList.size() < MIN_HANDOVER_PHOTOS || photoList.size() > MAX_HANDOVER_PHOTOS) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Cần "
              + MIN_HANDOVER_PHOTOS
              + "–"
              + MAX_HANDOVER_PHOTOS
              + " ảnh bằng chứng cuộn phim hỏng; đã gửi "
              + photoList.size()
              + ".",
          Map.of("field", "photos", "min", MIN_HANDOVER_PHOTOS, "max", MAX_HANDOVER_PHOTOS));
    }

    List<String> blobKeys = uploadDamagePhotos(photoList);
    registerDamagePhotoRollbackCleanup(blobKeys);

    List<FilmRollDamagePhoto> photoEntities = new ArrayList<>(blobKeys.size());
    for (String blobKey : blobKeys) {
      FilmRollDamagePhoto photo = new FilmRollDamagePhoto();
      photo.setFilmRollId(roll.getId());
      photo.setBlobKey(blobKey);
      photoEntities.add(photo);
    }
    filmRollDamagePhotoRepository.saveAll(photoEntities);

    roll.setStatus(FilmRollStatus.DAMAGED);
    roll.setDamageNote(damageNote);
    filmRollRepository.saveAndFlush(roll);

    long previousFinalTotal =
        order.getFinalTotalVnd() != null ? order.getFinalTotalVnd() : order.getEstimatedTotalVnd();
    long newFinalTotal =
        filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(order.getId()).stream()
            .filter(r -> r.getStatus() == FilmRollStatus.RECEIVED)
            .mapToLong(LabServiceOrderServiceImpl::rollPrice)
            .sum();
    order.setFinalTotalVnd(newFinalTotal);
    order.setPriceLockedAt(clock.instant());
    try {
      order = serviceOrderRepository.saveAndFlush(order);
    } catch (OptimisticLockingFailureException ex) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Đơn đã bị thay đổi bởi người khác. Vui lòng tải lại trang và thử lại.");
    }

    long netPaid = paymentService.netPaidForOrder(order.getId());
    long refundAmount = Math.max(0L, netPaid - newFinalTotal);
    if (refundAmount > 0) {
      paymentService.createOrderRefund(
          order.getId(),
          refundAmount,
          "Hoàn tiền cuộn phim #"
              + roll.getRollSequence()
              + " bị hỏng của đơn "
              + order.getOrderCode()
              + " (BR-26)");
    }

    Map<String, Object> auditBefore = new LinkedHashMap<>();
    auditBefore.put("finalTotalVnd", previousFinalTotal);
    auditBefore.put("rollStatus", FilmRollStatus.RECEIVED.name());
    Map<String, Object> auditAfter = new LinkedHashMap<>();
    auditAfter.put("filmRollId", roll.getId());
    auditAfter.put("rollSequence", roll.getRollSequence());
    auditAfter.put("rollStatus", FilmRollStatus.DAMAGED.name());
    auditAfter.put("damageNote", damageNote);
    auditAfter.put("photoCount", blobKeys.size());
    auditAfter.put("finalTotalVnd", newFinalTotal);
    auditAfter.put("refundAmountVnd", refundAmount);
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_ROLL_DAMAGED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            auditBefore,
            auditAfter));

    notifyPhotographerRollDamaged(lab, order, roll, damageNote, refundAmount);
    return toLabResponse(order);
  }

  private List<String> uploadDamagePhotos(List<MultipartFile> photos) {
    List<String> blobKeys = new ArrayList<>(photos.size());
    try {
      for (MultipartFile photo : photos) {
        try (InputStream content = photo.getInputStream()) {
          blobKeys.add(
              storageClient.upload(
                  StoragePurpose.DAMAGE_PHOTO,
                  photo.getOriginalFilename(),
                  content,
                  photo.getSize()));
        } catch (IOException ex) {
          throw new BusinessException(
              ErrorCode.VALIDATION_ERROR, "Không đọc được file ảnh bằng chứng gửi lên.");
        }
      }
    } catch (RuntimeException ex) {
      deleteDamagePhotosQuietly(blobKeys);
      throw ex;
    }
    return blobKeys;
  }

  private void registerDamagePhotoRollbackCleanup(List<String> blobKeys) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_ROLLED_BACK) {
                deleteDamagePhotosQuietly(blobKeys);
              }
            }
          });
    }
  }

  private void deleteDamagePhotosQuietly(List<String> blobKeys) {
    for (String blobKey : blobKeys) {
      try {
        storageClient.delete(blobKey);
      } catch (RuntimeException ex) {
        log.warn("Could not delete damage photo blob {}", blobKey, ex);
      }
    }
  }

  private void notifyPhotographerRollDamaged(
      FilmLab lab, ServiceOrder order, FilmRoll roll, String damageNote, long refundAmount) {
    String body =
        "Film Lab "
            + lab.getLabName()
            + " ghi nhận cuộn #"
            + roll.getRollSequence()
            + " của đơn "
            + order.getOrderCode()
            + " bị hỏng khi xử lý ("
            + damageNote
            + "). Cuộn này được miễn phí (BR-26); giá chốt mới của đơn là "
            + formatVnd(order.getFinalTotalVnd())
            + ".";
    if (refundAmount > 0) {
      body += " Hệ thống đã tạo hoàn tiền " + formatVnd(refundAmount) + ".";
    }
    eventPublisher.publishEvent(
        new NotificationEvent(
            order.getPhotographerAccountId(),
            NotificationType.ORDER_STATUS,
            "Cuộn #" + roll.getRollSequence() + " của đơn " + order.getOrderCode() + " bị hỏng",
            body,
            null));
  }

  /**
   * BR-07: công đoạn mới nhất của đơn. Đơn {@code IN_PROCESSING} luôn có dòng {@code RECEIVED} (ghi
   * lúc xác nhận lịch); nếu thiếu (dữ liệu cũ) thì coi như đang ở {@code RECEIVED}.
   */
  private ProcessingStage currentStage(Long orderId) {
    return processingStageRecordRepository
        .findFirstByServiceOrderIdOrderByRecordedAtDescIdDesc(orderId)
        .map(ProcessingStageRecord::getStage)
        .orElse(ProcessingStage.RECEIVED);
  }

  /** BR-07: {@code requested} phải đúng công đoạn kế tiếp của {@code current}; khác → 422. */
  private static void requireNextStage(
      ServiceOrder order, ProcessingStage current, ProcessingStage requested) {
    ProcessingStage expected = current.next().orElse(null);
    if (requested == expected) {
      return;
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("field", "stage");
    properties.put("currentStage", current.name());
    if (expected != null) {
      // Map.copyOf trong BusinessException không nhận giá trị null: đã tới QC thì bỏ trường này.
      properties.put("expectedStage", expected.name());
    }
    String message =
        expected == null
            ? "Đơn "
                + order.getOrderCode()
                + " đã ở công đoạn cuối "
                + current.name()
                + "; không còn công đoạn kế tiếp."
            : "Công đoạn kế tiếp hợp lệ của đơn "
                + order.getOrderCode()
                + " là "
                + expected.name()
                + " ("
                + expected.getVietnameseName()
                + "); đơn đang ở "
                + current.name()
                + ", không ghi được "
                + requested.name()
                + ".";
    throw new BusinessException(ErrorCode.ORDER_STAGE_OUT_OF_ORDER, message, properties);
  }

  /** UC24, UC04: báo Photographer đơn sang công đoạn mới (gửi sau khi commit). */
  private void notifyPhotographerStageRecorded(
      FilmLab lab, ServiceOrder order, ProcessingStage stage) {
    eventPublisher.publishEvent(
        new NotificationEvent(
            order.getPhotographerAccountId(),
            NotificationType.ORDER_STATUS,
            "Đơn " + order.getOrderCode() + " đang ở công đoạn " + stage.getVietnameseName(),
            "Film Lab "
                + lab.getLabName()
                + " đã cập nhật đơn "
                + order.getOrderCode()
                + ": đang ở công đoạn "
                + stage.getVietnameseName()
                + ".",
            null));
  }

  /** UC69: chỉ đơn đang chờ xếp máy; trả trạng thái hiện tại để ghi audit. */
  private static ServiceOrderStatus requireAwaitingMachineAssignment(ServiceOrder order) {
    ServiceOrderStatus status = order.getStatus();
    if (status != ServiceOrderStatus.AWAITING_MACHINE_ASSIGNMENT) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Chỉ xếp lịch được với đơn đang chờ xếp máy; đơn "
              + order.getOrderCode()
              + " đang ở trạng thái "
              + status.name()
              + ".");
    }
    return status;
  }

  /**
   * BR-58: dựng yêu cầu xếp lịch từ các cuộn {@code RECEIVED} của đơn, gom theo (quy trình, khổ
   * phim) theo thứ tự cuộn. {@code framesPerRoll} = trung bình {@code exposure_count} của nhóm, làm
   * tròn lên. {@code receivedAt} = thời điểm chốt giá khi nhận phim (UC23).
   */
  private CapacityRequest capacityRequest(ServiceOrder order) {
    Map<List<Long>, List<FilmRoll>> byGroup = new LinkedHashMap<>();
    for (FilmRoll roll :
        filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(order.getId())) {
      if (roll.getStatus() == FilmRollStatus.RECEIVED) {
        byGroup
            .computeIfAbsent(
                List.of(roll.getFilmProcessId(), roll.getFilmFormatId()), key -> new ArrayList<>())
            .add(roll);
      }
    }
    List<RollGroup> groups =
        byGroup.entrySet().stream()
            .map(
                entry -> {
                  List<FilmRoll> rolls = entry.getValue();
                  int frames = rolls.stream().mapToInt(FilmRoll::getExposureCount).sum();
                  return new RollGroup(
                      entry.getKey().get(0),
                      entry.getKey().get(1),
                      rolls.size(),
                      Math.ceilDiv(frames, rolls.size()));
                })
            .toList();
    return new CapacityRequest(
        order.getId(),
        order.getFilmLabId(),
        order.getServiceTier(),
        order.getPriceLockedAt(),
        groups);
  }

  private static ScheduleProposalResponse toProposalResponse(CapacityProposal proposal) {
    return new ScheduleProposalResponse(
        proposal.feasible(),
        proposal.infeasibleReason(),
        proposal.promisedReturnAt(),
        proposal.tierCommitmentAt(),
        proposal.allocations().stream()
            .map(
                allocation ->
                    new ScheduleAllocationResponse(
                        allocation.workDate(),
                        allocation.filmProcessId(),
                        allocation.filmFormatId(),
                        allocation.rollCount()))
            .toList());
  }

  /** UC69, UC04: báo Photographer ngày hẹn trả (giờ Việt Nam; gửi sau khi commit). */
  private void notifyPhotographerScheduleConfirmed(FilmLab lab, ServiceOrder order) {
    eventPublisher.publishEvent(
        new NotificationEvent(
            order.getPhotographerAccountId(),
            NotificationType.ORDER_STATUS,
            "Đơn " + order.getOrderCode() + " đã có ngày hẹn trả",
            "Film Lab "
                + lab.getLabName()
                + " đã xếp lịch xử lý đơn "
                + order.getOrderCode()
                + ". Ngày hẹn trả: "
                + NOTIFICATION_TIME.format(order.getPromisedReturnAt().atZone(VIETNAM_ZONE))
                + ".",
            null));
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
   * <p>TODO: shipment, thanh toán khi các contract tương ứng lên dev.
   */
  private LabServiceOrderResponse toLabResponse(ServiceOrder order) {
    UserAccountResponse account =
        userAccountService.getCurrentAccount(order.getPhotographerAccountId());
    List<FilmRoll> rolls =
        filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(order.getId());
    return serviceOrderMapper.toLabResponse(
        order,
        rolls,
        new OrderPhotographerResponse(account.id(), account.fullName(), account.phoneNumber()),
        filmHandoverService.getByServiceOrderId(order.getId()),
        processingStageRecordRepository.findByServiceOrderIdOrderByRecordedAtAscIdAsc(
            order.getId()),
        archiveService.getScanStatuses(rolls.stream().map(FilmRoll::getId).toList()),
        loadDamagePhotos(rolls));
  }

  private Map<Long, List<FilmRollDamagePhotoResponse>> loadDamagePhotos(List<FilmRoll> rolls) {
    List<Long> damagedRollIds =
        rolls.stream()
            .filter(roll -> roll.getStatus() == FilmRollStatus.DAMAGED)
            .map(FilmRoll::getId)
            .toList();
    if (damagedRollIds.isEmpty()) {
      return Map.of();
    }
    return filmRollDamagePhotoRepository
        .findByFilmRollIdInOrderByFilmRollIdAscIdAsc(damagedRollIds)
        .stream()
        .collect(
            Collectors.groupingBy(
                FilmRollDamagePhoto::getFilmRollId,
                LinkedHashMap::new,
                Collectors.mapping(
                    photo ->
                        serviceOrderMapper.toDamagePhotoResponse(
                            photo, storageClient.signedUrl(photo.getBlobKey())),
                    Collectors.toList())));
  }

  /**
   * UC26: Film Lab Owner thêm ảnh xem trước cho một cuộn.
   *
   * <ul>
   *   <li>CR-14: đơn của lab khác → 404 {@code ORDER_NOT_FOUND}. Khoá dòng đơn ({@code SELECT ...
   *       FOR UPDATE}) để hai lần upload cùng cuộn không trùng số khung và không vượt 100 ảnh.
   *   <li>Chỉ đơn {@code IN_PROCESSING}; khác (kể cả đã giao scan) → 409 {@code
   *       ORDER_STATUS_CONFLICT}.
   *   <li>Cuộn phải thuộc đơn và {@code RECEIVED}; khác → 404 {@code ORDER_ROLL_NOT_FOUND}.
   *   <li>BR-08: kiểm file, upload, số khung, giới hạn 100 ảnh do {@link ArchiveService}.
   * </ul>
   */
  @Override
  @Transactional
  public List<ScanFileResponse> addPreviewScans(
      Long ownerAccountId, Long orderId, Long rollId, List<MultipartFile> files) {
    ServiceOrder order = lockOrderInProcessing(ownerAccountId, orderId, "thêm ảnh xem trước");
    FilmRoll roll = requireReceivedRoll(order, rollId);
    return archiveService.addPreviewScans(roll.getId(), files);
  }

  /**
   * UC26: Film Lab Owner gửi link file độ phân giải gốc của một cuộn. Cùng kiểm tra đơn / cuộn như
   * {@link #addPreviewScans}; kiểm link (chống SSRF, cần 2xx) và lưu do {@link ArchiveService}.
   * Gửi lại thì thay link cũ.
   */
  @Override
  @Transactional
  public FilmRollResponse setFullResolutionLink(
      Long ownerAccountId, Long orderId, Long rollId, SetFullResolutionLinkRequest request) {
    ServiceOrder order = lockOrderInProcessing(ownerAccountId, orderId, "gửi link file gốc");
    FilmRoll roll = requireReceivedRoll(order, rollId);
    RollScanStatusResponse status =
        archiveService.setFullResolutionLink(roll.getId(), request.url());
    return serviceOrderMapper.toLabRollResponse(roll, status);
  }

  /**
   * UC26: Film Lab Owner giao scan.
   *
   * <ul>
   *   <li>CR-14: đơn của lab khác → 404. Khoá dòng đơn rồi kiểm: chỉ {@code IN_PROCESSING}; khác
   *       (kể cả giao lần hai) → 409 {@code ORDER_STATUS_CONFLICT}.
   *   <li>BR-07: công đoạn mới nhất phải là {@code QC}; khác → 422 {@code
   *       ORDER_STAGE_OUT_OF_ORDER}.
   *   <li>BR-08: <b>mọi</b> cuộn {@code RECEIVED} có ≥ 1 ảnh xem trước và link file gốc đã kiểm;
   *       thiếu → 422 {@code ARCHIVE_DELIVERY_INCOMPLETE}, liệt kê từng cuộn thiếu gì.
   *   <li>Đánh dấu đã giao ({@code archive_rolls.delivered_at = now}) qua {@link ArchiveService};
   *       đơn → {@code READY_FOR_HANDOVER}.
   *   <li>UC04: báo Photographer scan đã có trong Digital Film Archive. BR-32: audit {@code
   *       ORDER_SCANS_DELIVERED}.
   * </ul>
   */
  @Override
  @Transactional
  public LabServiceOrderResponse deliverScans(Long ownerAccountId, Long orderId) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabIdForUpdate(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    final ServiceOrderStatus before = requireInProcessing(order, "giao scan");
    ProcessingStage current = currentStage(order.getId());
    if (current != ProcessingStage.QC) {
      throw new BusinessException(
          ErrorCode.ORDER_STAGE_OUT_OF_ORDER,
          "Chỉ giao scan được sau công đoạn QC (Kiểm tra chất lượng); đơn "
              + order.getOrderCode()
              + " đang ở công đoạn "
              + current.name()
              + " ("
              + current.getVietnameseName()
              + ").",
          Map.of("currentStage", current.name(), "expectedStage", ProcessingStage.QC.name()));
    }

    List<FilmRoll> receivedRolls =
        filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(order.getId()).stream()
            .filter(roll -> roll.getStatus() == FilmRollStatus.RECEIVED)
            .toList();
    List<Long> receivedIds = receivedRolls.stream().map(FilmRoll::getId).toList();
    Map<Long, RollScanStatusResponse> statuses = archiveService.getScanStatuses(receivedIds);
    requireAllRollsReady(order, receivedRolls, statuses);

    if (!receivedIds.isEmpty()) {
      archiveService.markDelivered(receivedIds);
    }
    // TODO(FA26-157): final_total > PaymentService.netPaidForOrder → AWAITING_TOP_UP (SPEC-00 §4.5)
    order.setStatus(ServiceOrderStatus.READY_FOR_HANDOVER);
    try {
      order = serviceOrderRepository.saveAndFlush(order);
    } catch (OptimisticLockingFailureException ex) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Đơn đã bị thay đổi bởi người khác. Vui lòng tải lại trang và thử lại.");
    }

    long previewCount =
        statuses.values().stream().mapToLong(RollScanStatusResponse::previewCount).sum();
    Map<String, Object> auditAfter = new LinkedHashMap<>();
    auditAfter.put("status", order.getStatus().name());
    auditAfter.put("deliveredRollCount", receivedRolls.size());
    auditAfter.put("previewScanCount", previewCount);
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_SCANS_DELIVERED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            Map.of("status", before.name()),
            auditAfter));
    notifyPhotographerScansDelivered(lab, order, receivedRolls.size(), previewCount);
    return toLabResponse(order);
  }

  /** UC26: khoá đơn của lab mình và yêu cầu {@code IN_PROCESSING}. */
  private ServiceOrder lockOrderInProcessing(Long ownerAccountId, Long orderId, String action) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabIdForUpdate(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    requireInProcessing(order, action);
    return order;
  }

  private static ServiceOrderStatus requireInProcessing(ServiceOrder order, String action) {
    if (order.getStatus() != ServiceOrderStatus.IN_PROCESSING) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Chỉ "
              + action
              + " được với đơn đang xử lý; đơn "
              + order.getOrderCode()
              + " đang ở trạng thái "
              + order.getStatus().name()
              + ".");
    }
    return order.getStatus();
  }

  /** UC26: cuộn phải thuộc đơn và lab đã nhận ({@code RECEIVED}); khác → 404. */
  private FilmRoll requireReceivedRoll(ServiceOrder order, Long rollId) {
    return filmRollRepository
        .findByIdAndServiceOrderId(rollId, order.getId())
        .filter(roll -> roll.getStatus() == FilmRollStatus.RECEIVED)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.ORDER_ROLL_NOT_FOUND,
                    "Không tìm thấy cuộn phim đã nhận trong đơn " + order.getOrderCode() + "."));
  }

  /** UC26, BR-08: mọi cuộn RECEIVED có ≥ 1 ảnh xem trước và link đã kiểm; gom mọi cuộn thiếu. */
  private static void requireAllRollsReady(
      ServiceOrder order, List<FilmRoll> rolls, Map<Long, RollScanStatusResponse> statuses) {
    List<String> problems = new ArrayList<>();
    List<Map<String, Object>> incomplete = new ArrayList<>();
    for (FilmRoll roll : rolls) {
      RollScanStatusResponse status = statuses.get(roll.getId());
      boolean missingPreview = status == null || status.previewCount() == 0;
      boolean missingLink = status == null || !status.hasVerifiedLink();
      if (!missingPreview && !missingLink) {
        continue;
      }
      List<String> missing = new ArrayList<>();
      if (missingPreview) {
        missing.add("ảnh xem trước");
      }
      if (missingLink) {
        missing.add("link file gốc");
      }
      problems.add("cuộn #" + roll.getRollSequence() + " thiếu " + String.join(" và ", missing));
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("filmRollId", roll.getId());
      item.put("rollSequence", roll.getRollSequence());
      item.put("missingPreviewScan", missingPreview);
      item.put("missingFullResolutionLink", missingLink);
      incomplete.add(item);
    }
    if (!incomplete.isEmpty()) {
      throw new BusinessException(
          ErrorCode.ARCHIVE_DELIVERY_INCOMPLETE,
          "Chưa giao scan được đơn "
              + order.getOrderCode()
              + ": "
              + String.join("; ", problems)
              + ".",
          Map.of("incompleteRolls", incomplete));
    }
  }

  /** UC26, UC04: báo Photographer scan đã có trong Digital Film Archive (gửi sau khi commit). */
  private void notifyPhotographerScansDelivered(
      FilmLab lab, ServiceOrder order, int rollCount, long previewCount) {
    eventPublisher.publishEvent(
        new NotificationEvent(
            order.getPhotographerAccountId(),
            NotificationType.ORDER_STATUS,
            "Đơn " + order.getOrderCode() + " đã có ảnh scan",
            "Film Lab "
                + lab.getLabName()
                + " đã giao ảnh scan của đơn "
                + order.getOrderCode()
                + ": "
                + rollCount
                + " cuộn, "
                + previewCount
                + " ảnh xem trước kèm link file gốc. Xem trong Kho phim số (Digital Film Archive).",
            null));
  }

  /**
   * UC27: Film Lab Owner trả phim hoặc giữ âm bản theo yêu cầu của Photographer.
   *
   * <ul>
   *   <li>CR-14: đơn của lab khác → 404 {@code ORDER_NOT_FOUND}. Đọc đơn bằng {@code SELECT ... FOR
   *       UPDATE} rồi mới kiểm trạng thái: chỉ {@code READY_FOR_HANDOVER}; khác (kể cả trả phim lần
   *       hai) → 409 {@code ORDER_STATUS_CONFLICT}.
   *   <li>Trả phim tại lab ({@code keepNegativesAtLab = false}), BR-25: 1–10 ảnh bằng chứng, ngoài
   *       khoảng → 400 {@code VALIDATION_ERROR}. Bàn giao ghi qua {@link FilmHandoverService}
   *       ({@code COLLECTED_AT_LAB}, {@code roll_count} = số cuộn {@code RECEIVED}, người thực
   *       hiện = chủ lab). Ảnh chỉ được upload sau khi mọi rule đã qua.
   *   <li>Lab giữ âm bản ({@code keepNegativesAtLab = true}): không có bàn giao vật lý nên
   *       <b>không</b> ghi {@code film_handovers}; ảnh không bắt buộc, nếu có gửi thì bị bỏ qua
   *       (không upload, không lưu).
   *   <li>Gửi phim về bằng vận chuyển là Later (không có lựa chọn trong body).
   *   <li>{@code delivered_at = now}, {@code keep_negatives_at_lab} theo body, trạng thái → {@code
   *       DELIVERED}.
   *   <li>UC04: báo Photographer đã trả phim / lab giữ âm bản, nhắc xác nhận đã nhận (UC28). BR-32:
   *       audit {@code ORDER_FILM_RETURNED}.
   * </ul>
   */
  @Override
  @Transactional
  public LabServiceOrderResponse returnFilm(
      Long ownerAccountId,
      Long orderId,
      RecordFilmReturnRequest request,
      List<MultipartFile> photos) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndFilmLabIdForUpdate(orderId, lab.getId())
            .orElseThrow(LabServiceOrderServiceImpl::orderNotFound);
    final ServiceOrderStatus before = order.getStatus();
    if (before != ServiceOrderStatus.READY_FOR_HANDOVER) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Chỉ trả phim được với đơn đã giao scan, đang chờ bàn giao; đơn "
              + order.getOrderCode()
              + " đang ở trạng thái "
              + before.name()
              + ".");
    }

    boolean keepNegatives = Boolean.TRUE.equals(request.keepNegativesAtLab());
    List<MultipartFile> photoList = photos == null ? List.of() : photos;
    if (!keepNegatives
        && (photoList.size() < MIN_HANDOVER_PHOTOS || photoList.size() > MAX_HANDOVER_PHOTOS)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Cần "
              + MIN_HANDOVER_PHOTOS
              + "–"
              + MAX_HANDOVER_PHOTOS
              + " ảnh bằng chứng khi trả phim; đã gửi "
              + photoList.size()
              + ".",
          Map.of("field", "photos", "min", MIN_HANDOVER_PHOTOS, "max", MAX_HANDOVER_PHOTOS));
    }

    long receivedCount =
        filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(order.getId()).stream()
            .filter(roll -> roll.getStatus() == FilmRollStatus.RECEIVED)
            .count();
    order.setDeliveredAt(clock.instant());
    order.setKeepNegativesAtLab(keepNegatives);
    order.setStatus(ServiceOrderStatus.DELIVERED);
    try {
      order = serviceOrderRepository.saveAndFlush(order);
    } catch (OptimisticLockingFailureException ex) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Đơn đã bị thay đổi bởi người khác. Vui lòng tải lại trang và thử lại.");
    }

    if (!keepNegatives) {
      // BR-25: upload ảnh và ghi bàn giao sau khi mọi rule đã qua; rollback thì file bị xoá.
      filmHandoverService.record(
          new RecordFilmHandoverRequest(
              order.getId(),
              null,
              HandoverPoint.COLLECTED_AT_LAB,
              (int) receivedCount,
              request.note(),
              ownerAccountId,
              null,
              photoList));
    }

    // TODO(FA26-157): đơn PAY_AT_LAB — ghi nhận lab đã thu tiền khi contract thanh toán lên dev

    Map<String, Object> auditAfter = new LinkedHashMap<>();
    auditAfter.put("status", order.getStatus().name());
    auditAfter.put("keepNegativesAtLab", keepNegatives);
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_FILM_RETURNED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            Map.of("status", before.name()),
            auditAfter));
    notifyPhotographerFilmReturned(lab, order, receivedCount);
    return toLabResponse(order);
  }

  /** UC27, UC04: báo Photographer đã trả phim / lab giữ âm bản, nhắc xác nhận (gửi sau commit). */
  private void notifyPhotographerFilmReturned(FilmLab lab, ServiceOrder order, long rollCount) {
    String title;
    String body;
    if (order.isKeepNegativesAtLab()) {
      title = "Lab đã lưu giữ âm bản của đơn " + order.getOrderCode();
      body =
          "Film Lab "
              + lab.getLabName()
              + " đã lưu giữ âm bản "
              + rollCount
              + " cuộn phim của đơn "
              + order.getOrderCode()
              + " tại lab theo yêu cầu của bạn.";
    } else {
      title = "Đơn " + order.getOrderCode() + " đã trả phim";
      body =
          "Film Lab "
              + lab.getLabName()
              + " đã trả "
              + rollCount
              + " cuộn phim của đơn "
              + order.getOrderCode()
              + " tại lab.";
    }
    body += " Vui lòng xác nhận đã nhận trong mục đơn hàng để hoàn tất đơn.";
    eventPublisher.publishEvent(
        new NotificationEvent(
            order.getPhotographerAccountId(), NotificationType.ORDER_STATUS, title, body, null));
  }

  private boolean isFilmAlreadyShipped(Long orderId) {
    List<FilmHandoverResponse> handovers = filmHandoverService.getByServiceOrderId(orderId);
    if (handovers == null || handovers.isEmpty()) {
      return false;
    }
    return handovers.stream()
        .anyMatch(
            h ->
                h.handoverPoint() == HandoverPoint.COLLECTED_FROM_PHOTOGRAPHER
                    || h.handoverPoint() == HandoverPoint.HANDED_TO_PARTNER);
  }

  private void notifyPhotographerRejected(
      FilmLab lab, ServiceOrder order, String reason, boolean filmAlreadyShipped) {
    String title = "Đơn " + order.getOrderCode() + " đã bị lab từ chối";
    StringBuilder body =
        new StringBuilder("Film Lab ")
            .append(lab.getLabName())
            .append(" đã từ chối tiếp nhận đơn ")
            .append(order.getOrderCode())
            .append(". Lý do: ")
            .append(reason)
            .append(".");
    if (filmAlreadyShipped) {
      body.append(
          " Do phim đã được gửi đi, hệ thống sẽ phối hợp chuyển trả lại phim cho bạn (chi phí vận chuyển do lab chịu).");
    }
    eventPublisher.publishEvent(
        new NotificationEvent(
            order.getPhotographerAccountId(),
            NotificationType.ORDER_STATUS,
            title,
            body.toString(),
            null));
  }

  private static BusinessException orderNotFound() {
    return new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn dịch vụ.");
  }
}
