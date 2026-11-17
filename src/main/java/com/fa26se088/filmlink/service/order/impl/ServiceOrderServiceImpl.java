package com.fa26se088.filmlink.service.order.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.lab.response.OrderingFilmLabResponse;
import com.fa26se088.filmlink.dto.lab.response.ServicePackageResponse;
import com.fa26se088.filmlink.dto.lab.response.ServicePackageScanOptionResponse;
import com.fa26se088.filmlink.dto.masterdata.response.FilmFormatResponse;
import com.fa26se088.filmlink.dto.masterdata.response.FilmProcessResponse;
import com.fa26se088.filmlink.dto.masterdata.response.FilmStockResponse;
import com.fa26se088.filmlink.dto.masterdata.response.ServiceTypeResponse;
import com.fa26se088.filmlink.dto.order.request.AnswerScheduleChangeRequest;
import com.fa26se088.filmlink.dto.order.request.CreateServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.FilmRollRequest;
import com.fa26se088.filmlink.dto.order.response.FilmRollQuoteResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderCreationResult;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderQuoteResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.enums.ServicePackageStatus;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.entity.order.FilmRoll;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.FilmRollStatus;
import com.fa26se088.filmlink.entity.order.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.exception.GlobalExceptionHandler.FieldViolation;
import com.fa26se088.filmlink.mapper.order.ServiceOrderMapper;
import com.fa26se088.filmlink.repository.order.FilmRollRepository;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.FilmLabService;
import com.fa26se088.filmlink.service.machine.ScheduleChangeService;
import com.fa26se088.filmlink.service.machine.event.PromisedDateChangedEvent;
import com.fa26se088.filmlink.service.masterdata.MasterdataService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.notification.event.NotificationEvent;
import com.fa26se088.filmlink.service.order.ServiceOrderService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceOrderServiceImpl implements ServiceOrderService {

  /** {@code order_code}: {@code SO} + {@code yyMMdd} (giờ Việt Nam) + 6 ký tự base-32 = 14 ký tự. */
  static final String ORDER_CODE_PREFIX = "SO";

  static final int ORDER_CODE_RANDOM_LENGTH = 6;

  /** Base-32 Crockford: bỏ I, L, O, U để mã đọc qua điện thoại không nhầm. */
  private static final char[] BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

  private static final int ORDER_CODE_ATTEMPTS = 5;
  /** Giờ Việt Nam (GMT+7, CR-04): ngày trong mã đơn và thời điểm hiển thị trong thông báo. */
  static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final DateTimeFormatter ORDER_CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
  static final DateTimeFormatter NOTIFICATION_TIME =
      DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
  private static final String ERRORS = "errors";
  private static final String ROLLS = "rolls";

  /** UC72: lựa chọn Chờ; giá trị của {@code schedule_changes.customer_choice}. */
  static final String SCHEDULE_CHANGE_WAIT = "WAIT";

  /** UC71: đơn ở các trạng thái này không còn đổi ngày hẹn trả. */
  private static final Set<ServiceOrderStatus> PROMISED_DATE_FINAL_STATUSES =
      Set.of(ServiceOrderStatus.DELIVERED, ServiceOrderStatus.CLOSED, ServiceOrderStatus.CANCELLED);

  private final SecureRandom random = new SecureRandom();

  private final ServiceOrderRepository serviceOrderRepository;
  private final FilmRollRepository filmRollRepository;
  private final FilmLabService filmLabService;
  private final MasterdataService masterdataService;
  private final SystemParameterService systemParameterService;
  private final AuditLogService auditLogService;
  private final ApplicationEventPublisher eventPublisher;
  private final ServiceOrderMapper serviceOrderMapper;
  private final ScheduleChangeService scheduleChangeService;
  private final Clock clock;

  /**
   * UC20: cùng các rule như khi tạo đơn (xem {@link #create}), không ghi DB. Giá theo Report 4
   * §3.5; {@code expectedReturnAt} tính từ thời điểm báo giá.
   */
  @Override
  @Transactional(readOnly = true)
  public ServiceOrderQuoteResponse quote(
      Long photographerAccountId, CreateServiceOrderRequest request) {
    Pricing pricing = price(request, clock.instant());
    List<FilmRollQuoteResponse> rolls =
        pricing.rolls().stream()
            .map(
                roll ->
                    new FilmRollQuoteResponse(
                        roll.sequence(),
                        roll.servicePackage().id(),
                        roll.servicePackage().packageName(),
                        roll.scanOption() == null ? null : roll.scanOption().id(),
                        roll.scanOption() == null ? null : roll.scanOption().resolutionLabel(),
                        roll.pushPullStops(),
                        roll.unitPriceVnd(),
                        roll.scanOptionPriceVnd(),
                        roll.pushPullSurchargeVnd(),
                        roll.rollPriceVnd()))
            .toList();
    return new ServiceOrderQuoteResponse(
        rolls,
        pricing.subtotalVnd(),
        pricing.expressSurchargeVnd(),
        pricing.deliveryFeeVnd(),
        pricing.estimatedTotalVnd(),
        pricing.expectedReturnAt());
  }

  /**
   * UC20: tạo Service Order.
   *
   * <ul>
   *   <li>NFR-REL-02: {@code idempotencyKey} bắt buộc; đã có đơn cùng {@code (photographer,
   *       idempotencyKey)} thì trả đơn đó, không kiểm lại rule, không tạo thêm.
   *   <li>UC20: 1–20 cuộn (Bean Validation); mỗi cuộn đúng một trong {@code filmStockId} / {@code
   *       filmStockNameText}.
   *   <li>BR-34, BR-57: lab đang bật nhận đơn và đủ điều kiện nhận đơn ({@link
   *       FilmLabService#isReadyToAcceptOrders}), nếu không 422.
   *   <li>BR-16: gói của cuộn thuộc lab, {@code ON_SALE}, master data còn ACTIVE, cùng khổ phim với
   *       cuộn; mức scan thuộc gói; push/pull chỉ khi gói cho phép.
   *   <li>BR-09: COD không đi với SELF_DROP_OFF; giai đoạn này COD, EXPRESS, PARCEL_SHIPPING đều bị
   *       từ chối 422 "chưa hỗ trợ". PICKUP bắt buộc địa chỉ, quận, tỉnh, số điện thoại và khung giờ
   *       lấy {@code start < end} (400); phí giao phim = 0.
   *   <li>Report 4 §3.5: giá cuộn chụp vào {@code film_rolls}; {@code estimated_total_vnd} = Σ giá
   *       cuộn. BR-60: {@code expected_return_at} = now + số ngày trả lớn nhất trong các gói.
   *   <li>BR-17: ONLINE → {@code AWAITING_PAYMENT}; PAY_AT_LAB → {@code AWAITING_LAB_CONFIRMATION},
   *       {@code lab_response_due_at = now + LAB_RESPONSE_LIMIT_HOURS}, báo chủ lab.
   *   <li>BR-32: ghi audit log.
   * </ul>
   */
  @Override
  @Transactional
  public ServiceOrderCreationResult create(
      Long photographerAccountId, CreateServiceOrderRequest request) {
    if (request.idempotencyKey() == null) {
      throw fieldError("idempotencyKey", "Thiếu khoá idempotency (UUID) khi tạo đơn.");
    }
    Optional<ServiceOrder> existing =
        serviceOrderRepository.findByPhotographerAccountIdAndIdempotencyKey(
            photographerAccountId, request.idempotencyKey());
    if (existing.isPresent()) {
      return new ServiceOrderCreationResult(toResponse(existing.get()), false);
    }

    Instant now = clock.instant();
    Pricing pricing = price(request, now);

    ServiceOrder order = new ServiceOrder();
    order.setOrderCode(generateOrderCode(now));
    order.setIdempotencyKey(request.idempotencyKey());
    order.setPhotographerAccountId(photographerAccountId);
    order.setFilmLabId(pricing.lab().id());
    order.setServiceTier(request.serviceTier());
    order.setFilmHandoverMethod(request.filmHandoverMethod());
    order.setPaymentMethod(request.paymentMethod());
    order.setSpecialRequest(blankToNull(request.specialRequest()));
    order.setCustomerAddressLine(blankToNull(request.customerAddressLine()));
    order.setCustomerDistrict(blankToNull(request.customerDistrict()));
    order.setCustomerProvince(blankToNull(request.customerProvince()));
    order.setCustomerPhone(blankToNull(request.customerPhone()));
    if (request.filmHandoverMethod() == FilmHandoverMethod.PICKUP) {
      order.setPickupWindowStartAt(request.pickupWindowStartAt());
      order.setPickupWindowEndAt(request.pickupWindowEndAt());
    }
    order.setEstimatedTotalVnd(pricing.estimatedTotalVnd());
    order.setExpectedReturnAt(pricing.expectedReturnAt());
    if (request.paymentMethod() == PaymentMethod.PAY_AT_LAB) {
      order.setStatus(ServiceOrderStatus.AWAITING_LAB_CONFIRMATION);
      order.setLabResponseDueAt(now.plus(labResponseLimit()));
    } else {
      order.setStatus(ServiceOrderStatus.AWAITING_PAYMENT);
    }
    serviceOrderRepository.saveAndFlush(order);

    List<FilmRoll> rolls = new ArrayList<>(pricing.rolls().size());
    for (PricedRoll priced : pricing.rolls()) {
      rolls.add(toFilmRoll(order.getId(), priced));
    }
    filmRollRepository.saveAllAndFlush(rolls);

    Map<String, Object> after = new LinkedHashMap<>();
    after.put("orderCode", order.getOrderCode());
    after.put("filmLabId", order.getFilmLabId());
    after.put("status", order.getStatus().name());
    after.put("paymentMethod", order.getPaymentMethod().name());
    after.put("estimatedTotalVnd", order.getEstimatedTotalVnd());
    after.put("rollCount", rolls.size());
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_CREATED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            null,
            after));

    if (order.getStatus() == ServiceOrderStatus.AWAITING_LAB_CONFIRMATION) {
      notifyLabOwner(pricing.lab(), order);
    }

    // TODO(FA26-157): gọi PaymentService.createOrderPayment khi contract (SPEC-00 §4.5) lên dev
    Object payment = null;
    return new ServiceOrderCreationResult(
        serviceOrderMapper.toResponse(order, rolls, payment), true);
  }

  /** Kiểm toàn bộ rule của UC20 và tính giá; không ghi DB. Dùng chung cho báo giá và tạo đơn. */
  private Pricing price(CreateServiceOrderRequest request, Instant now) {
    validateUnsupportedOptions(request);
    validateHandoverDetails(request);
    validateFilmStockChoice(request.rolls());

    OrderingFilmLabResponse lab = filmLabService.getLabForOrdering(request.filmLabId());
    if (!lab.acceptingOrders() || !filmLabService.isReadyToAcceptOrders(lab.id())) {
      throw new BusinessException(
          ErrorCode.ORDER_LAB_NOT_ACCEPTING, "Film Lab hiện không nhận đơn mới.");
    }

    Set<Long> packageIds = new LinkedHashSet<>();
    request.rolls().forEach(roll -> packageIds.add(roll.servicePackageId()));
    Map<Long, ServicePackageResponse> packages =
        filmLabService.getPackagesForOrdering(lab.id(), packageIds).stream()
            .collect(Collectors.toMap(ServicePackageResponse::id, Function.identity()));
    ActiveMasterdata active = loadActiveMasterdata(request.rolls());

    List<PricedRoll> priced = new ArrayList<>(request.rolls().size());
    long subtotal = 0;
    int maxTurnaroundDays = 0;
    for (int i = 0; i < request.rolls().size(); i++) {
      FilmRollRequest roll = request.rolls().get(i);
      String prefix = ROLLS + "[" + i + "].";
      ServicePackageResponse servicePackage = packages.get(roll.servicePackageId());
      requireOrderablePackage(servicePackage, active, prefix);
      if (!servicePackage.filmFormatId().equals(roll.filmFormatId())) {
        throw rollError(
            ErrorCode.ORDER_FILM_FORMAT_MISMATCH,
            prefix + "filmFormatId",
            "Khổ phim của cuộn " + (i + 1) + " khác khổ phim của gói đã chọn.");
      }
      if (roll.filmStockId() != null && !active.filmStockIds().contains(roll.filmStockId())) {
        throw rollError(
            ErrorCode.ORDER_FILM_STOCK_INVALID,
            prefix + "filmStockId",
            "Dòng phim không tồn tại hoặc đã ngừng sử dụng.");
      }
      ServicePackageScanOptionResponse scanOption =
          findScanOption(servicePackage, roll.servicePackageScanOptionId(), prefix);
      short stops = roll.pushPullStops() == null ? 0 : roll.pushPullStops();
      if (stops != 0 && !servicePackage.pushPullAvailable()) {
        throw rollError(
            ErrorCode.ORDER_PUSH_PULL_NOT_AVAILABLE,
            prefix + "pushPullStops",
            "Gói đã chọn không nhận push/pull.");
      }

      long unitPrice = servicePackage.priceVnd();
      long scanPrice = scanOption == null ? 0 : scanOption.priceVnd();
      // Push (+) và pull (-) đều tính phụ phí theo số stop; cột phụ phí phải >= 0.
      long pushPullSurcharge =
          stops == 0 ? 0 : Math.abs(stops) * servicePackage.pushPullSurchargePerStopVnd();
      PricedRoll pricedRoll =
          new PricedRoll(
              i + 1, roll, servicePackage, scanOption, stops, unitPrice, scanPrice,
              pushPullSurcharge);
      priced.add(pricedRoll);
      subtotal += pricedRoll.rollPriceVnd();
      maxTurnaroundDays = Math.max(maxTurnaroundDays, servicePackage.turnaroundDays());
    }
    Instant expectedReturnAt = now.plus(Duration.ofDays(maxTurnaroundDays));
    return new Pricing(lab, priced, subtotal, expectedReturnAt);
  }

  /** Giai đoạn này chỉ STANDARD, SELF_DROP_OFF / PICKUP, ONLINE / PAY_AT_LAB (SPEC-01 T-04). */
  private static void validateUnsupportedOptions(CreateServiceOrderRequest request) {
    if (request.serviceTier() == ServiceTier.EXPRESS) {
      throw notSupported("serviceTier", "Hạng Express chưa được hỗ trợ.");
    }
    if (request.filmHandoverMethod() == FilmHandoverMethod.PARCEL_SHIPPING) {
      throw notSupported("filmHandoverMethod", "Gửi phim liên tỉnh chưa được hỗ trợ.");
    }
    if (request.paymentMethod() == PaymentMethod.COD) {
      String message =
          request.filmHandoverMethod() == FilmHandoverMethod.SELF_DROP_OFF
              ? "COD không áp dụng cho đơn tự mang phim tới lab (BR-09)."
              : "Thanh toán COD chưa được hỗ trợ.";
      throw notSupported("paymentMethod", message);
    }
  }

  /**
   * UC20: PICKUP bắt buộc địa chỉ, quận, tỉnh, số điện thoại, khung giờ lấy ({@code
   * ck_service_orders_customer_address}); có khung giờ thì {@code end > start} ({@code
   * ck_service_orders_pickup_window}). Lỗi định dạng / thiếu trường → 400.
   */
  private static void validateHandoverDetails(CreateServiceOrderRequest request) {
    if (request.filmHandoverMethod() != FilmHandoverMethod.PICKUP) {
      return;
    }
    List<FieldViolation> violations = new ArrayList<>();
    String required = "Bắt buộc khi chọn lấy phim tận nơi (PICKUP).";
    if (blankToNull(request.customerAddressLine()) == null) {
      violations.add(new FieldViolation("customerAddressLine", required));
    }
    if (blankToNull(request.customerDistrict()) == null) {
      violations.add(new FieldViolation("customerDistrict", required));
    }
    if (blankToNull(request.customerProvince()) == null) {
      violations.add(new FieldViolation("customerProvince", required));
    }
    if (blankToNull(request.customerPhone()) == null) {
      violations.add(new FieldViolation("customerPhone", required));
    }
    if (request.pickupWindowStartAt() == null) {
      violations.add(new FieldViolation("pickupWindowStartAt", required));
    }
    if (request.pickupWindowEndAt() == null) {
      violations.add(new FieldViolation("pickupWindowEndAt", required));
    }
    if (request.pickupWindowStartAt() != null
        && request.pickupWindowEndAt() != null
        && !request.pickupWindowEndAt().isAfter(request.pickupWindowStartAt())) {
      violations.add(
          new FieldViolation(
              "pickupWindowEndAt", "Giờ kết thúc khung lấy phim phải sau giờ bắt đầu."));
    }
    if (!violations.isEmpty()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Thông tin lấy phim tận nơi chưa hợp lệ.",
          Map.of(ERRORS, violations));
    }
  }

  /** UC20, {@code ck_film_rolls_stock}: mỗi cuộn đúng một trong dòng phim danh mục / tên tự nhập. */
  private static void validateFilmStockChoice(List<FilmRollRequest> rolls) {
    for (int i = 0; i < rolls.size(); i++) {
      FilmRollRequest roll = rolls.get(i);
      boolean hasId = roll.filmStockId() != null;
      boolean hasText = blankToNull(roll.filmStockNameText()) != null;
      if (hasId == hasText) {
        throw fieldError(
            ROLLS + "[" + i + "].filmStockId",
            "Chọn dòng phim từ danh mục hoặc nhập tên dòng phim (chỉ một trong hai).");
      }
    }
  }

  /** BR-16: gói thuộc lab, đang bán, Service Type / Film Process / Film Format còn ACTIVE. */
  private static void requireOrderablePackage(
      ServicePackageResponse servicePackage, ActiveMasterdata active, String prefix) {
    String field = prefix + "servicePackageId";
    if (servicePackage == null) {
      throw rollError(
          ErrorCode.ORDER_PACKAGE_NOT_ORDERABLE, field, "Gói dịch vụ không thuộc Film Lab đã chọn.");
    }
    if (servicePackage.status() != ServicePackageStatus.ON_SALE
        || !active.serviceTypeIds().contains(servicePackage.serviceTypeId())
        || !active.filmProcessIds().contains(servicePackage.filmProcessId())
        || !active.filmFormatIds().contains(servicePackage.filmFormatId())) {
      throw rollError(
          ErrorCode.ORDER_PACKAGE_NOT_ORDERABLE, field, "Gói dịch vụ hiện không còn được bán.");
    }
  }

  private static ServicePackageScanOptionResponse findScanOption(
      ServicePackageResponse servicePackage, Long scanOptionId, String prefix) {
    if (scanOptionId == null) {
      return null;
    }
    return servicePackage.scanOptions().stream()
        .filter(option -> option.id().equals(scanOptionId))
        .findFirst()
        .orElseThrow(
            () ->
                rollError(
                    ErrorCode.ORDER_SCAN_OPTION_INVALID,
                    prefix + "servicePackageScanOptionId",
                    "Mức scan không thuộc gói dịch vụ đã chọn."));
  }

  /**
   * Master data ACTIVE đọc qua {@link MasterdataService} (có cache), không inject repository của
   * masterdata (R4). Danh mục dòng phim chỉ đọc khi có cuộn chọn từ danh mục.
   */
  private ActiveMasterdata loadActiveMasterdata(List<FilmRollRequest> rolls) {
    Set<Long> filmStockIds =
        rolls.stream().anyMatch(roll -> roll.filmStockId() != null)
            ? masterdataService.getFilmStocks(null).stream()
                .map(FilmStockResponse::id)
                .collect(Collectors.toSet())
            : Set.of();
    return new ActiveMasterdata(
        masterdataService.getServiceTypes().stream()
            .map(ServiceTypeResponse::id)
            .collect(Collectors.toSet()),
        masterdataService.getFilmProcesses().stream()
            .map(FilmProcessResponse::id)
            .collect(Collectors.toSet()),
        masterdataService.getFilmFormats().stream()
            .map(FilmFormatResponse::id)
            .collect(Collectors.toSet()),
        filmStockIds);
  }

  private static FilmRoll toFilmRoll(Long serviceOrderId, PricedRoll priced) {
    FilmRollRequest request = priced.request();
    FilmRoll roll = new FilmRoll();
    roll.setServiceOrderId(serviceOrderId);
    roll.setRollSequence((short) priced.sequence());
    roll.setFilmStockId(request.filmStockId());
    roll.setFilmStockNameText(
        request.filmStockId() == null ? blankToNull(request.filmStockNameText()) : null);
    roll.setFilmFormatId(request.filmFormatId());
    roll.setFilmProcessId(priced.servicePackage().filmProcessId());
    roll.setServicePackageId(priced.servicePackage().id());
    roll.setServicePackageScanOptionId(
        priced.scanOption() == null ? null : priced.scanOption().id());
    roll.setExposureCount(request.exposureCount());
    roll.setShootingIso(request.shootingIso());
    roll.setPushPullStops(priced.pushPullStops());
    roll.setUnitPriceVnd(priced.unitPriceVnd());
    roll.setScanOptionPriceVnd(priced.scanOptionPriceVnd());
    roll.setPushPullSurchargeVnd(priced.pushPullSurchargeVnd());
    roll.setExpressSurchargeVnd(0L);
    roll.setStatus(FilmRollStatus.DECLARED);
    return roll;
  }

  /**
   * UC72: Photographer trả lời thay đổi lịch do sự cố máy.
   *
   * <ul>
   *   <li>CR-14: đơn không tồn tại hoặc không thuộc Photographer → 404 {@code ORDER_NOT_FOUND}.
   *   <li>Giai đoạn này chỉ {@code WAIT}; {@code CANCEL} (huỷ + hoàn tiền 100%) và {@code
   *       TRANSFER} (chuyển lab) là Later → 422 {@code ORDER_OPTION_NOT_SUPPORTED}.
   *   <li>Việc kiểm {@code scheduleChangeId} thuộc đơn, còn chờ trả lời và ghi câu trả lời thuộc
   *       domain machine ({@link ScheduleChangeService#answer}); order không đọc {@code
   *       schedule_changes}.
   * </ul>
   */
  @Override
  @Transactional
  public void answerScheduleChange(
      Long photographerAccountId,
      Long orderId,
      Long scheduleChangeId,
      AnswerScheduleChangeRequest request) {
    ServiceOrder order =
        serviceOrderRepository
            .findByIdAndPhotographerAccountId(orderId, photographerAccountId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn dịch vụ."));
    if (!SCHEDULE_CHANGE_WAIT.equals(request.choice())) {
      throw new BusinessException(
          ErrorCode.ORDER_OPTION_NOT_SUPPORTED,
          "Chưa hỗ trợ lựa chọn "
              + request.choice()
              + " (huỷ đơn hoặc chuyển lab sẽ có ở giai đoạn sau). Hiện chỉ chọn được Chờ (WAIT).");
    }
    scheduleChangeService.answer(scheduleChangeId, order.getId(), request.choice(), request.note());
  }

  /**
   * UC71, BR-61: ngày hẹn trả đổi do sự cố máy (domain machine phát {@link
   * PromisedDateChangedEvent}, mỗi đơn bị ảnh hưởng một event).
   *
   * <ul>
   *   <li>Đồng bộ, {@code MANDATORY}: chạy trong transaction tái xếp lịch của domain machine; cập
   *       nhật đơn lỗi thì việc tái xếp lịch cũng rollback. Publish ngoài transaction → {@code
   *       IllegalTransactionStateException}.
   *   <li>Bỏ qua (chỉ ghi log, idempotent): đơn không tồn tại; đơn đã {@code DELIVERED}, {@code
   *       CLOSED}, {@code CANCELLED}; đơn chưa xác nhận ngày hẹn ({@code
   *       ck_service_orders_promised}); ngày hẹn đã bằng ngày mới (event gửi lại).
   *   <li>Còn lại: {@code promised_return_at = newPromisedAt}; báo Photographer ngày cũ, ngày mới
   *       (giờ Việt Nam, CR-04) và, nếu {@code requiresCustomerResponse}, yêu cầu chọn Chờ / Huỷ /
   *       Chuyển lab (UC72).
   *   <li>BR-32: audit {@code ORDER_PROMISED_DATE_CHANGED}. Actor do {@code AuditLogService} lấy
   *       từ {@code SecurityContext} của thread phát event (không có thì System).
   * </ul>
   */
  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onPromisedDateChanged(PromisedDateChangedEvent event) {
    Optional<ServiceOrder> found = serviceOrderRepository.findById(event.serviceOrderId());
    if (found.isEmpty()) {
      log.warn(
          "PromisedDateChangedEvent for unknown service order {}, skipping",
          event.serviceOrderId());
      return;
    }
    ServiceOrder order = found.get();
    if (PROMISED_DATE_FINAL_STATUSES.contains(order.getStatus())) {
      log.info(
          "PromisedDateChangedEvent for service order {} in status {}, skipping",
          order.getId(),
          order.getStatus());
      return;
    }
    if (order.getMachineAssignmentConfirmedAt() == null) {
      log.warn(
          "PromisedDateChangedEvent for service order {} without confirmed promised date, skipping",
          order.getId());
      return;
    }
    if (event.newPromisedAt().equals(order.getPromisedReturnAt())) {
      log.info(
          "Service order {} already has promised date {}, skipping",
          order.getId(),
          event.newPromisedAt());
      return;
    }

    Instant previous = order.getPromisedReturnAt();
    order.setPromisedReturnAt(event.newPromisedAt());
    order = serviceOrderRepository.saveAndFlush(order);

    Map<String, Object> auditBefore = new LinkedHashMap<>();
    auditBefore.put("promisedReturnAt", previous == null ? null : previous.toString());
    Map<String, Object> auditAfter = new LinkedHashMap<>();
    auditAfter.put("promisedReturnAt", order.getPromisedReturnAt().toString());
    auditAfter.put("machineIncidentId", event.machineIncidentId());
    auditAfter.put("requiresCustomerResponse", event.requiresCustomerResponse());
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_PROMISED_DATE_CHANGED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            auditBefore,
            auditAfter));
    notifyPhotographerPromisedDateChanged(order, event);
  }

  /** UC71, UC04: báo Photographer ngày hẹn trả mới (gửi sau khi commit). */
  private void notifyPhotographerPromisedDateChanged(
      ServiceOrder order, PromisedDateChangedEvent event) {
    String body =
        "Do sự cố máy tại lab, ngày hẹn trả của đơn "
            + order.getOrderCode()
            + " đổi từ "
            + NOTIFICATION_TIME.format(event.oldPromisedAt().atZone(VIETNAM_ZONE))
            + " sang "
            + NOTIFICATION_TIME.format(event.newPromisedAt().atZone(VIETNAM_ZONE))
            + ".";
    if (event.requiresCustomerResponse()) {
      body +=
          " Ngày mới muộn hơn cam kết của dịch vụ. Vui lòng chọn Chờ, Huỷ đơn hoặc Chuyển lab"
              + " trong mục đơn hàng.";
    }
    eventPublisher.publishEvent(
        new NotificationEvent(
            order.getPhotographerAccountId(),
            NotificationType.ORDER_STATUS,
            "Đơn " + order.getOrderCode() + " đổi ngày hẹn trả",
            body,
            null));
  }

  /** UC22: báo chủ lab có đơn mới cần nhận / từ chối (gửi sau khi transaction commit). */
  private void notifyLabOwner(OrderingFilmLabResponse lab, ServiceOrder order) {
    eventPublisher.publishEvent(
        new NotificationEvent(
            lab.ownerAccountId(),
            NotificationType.ORDER_STATUS,
            "Đơn mới " + order.getOrderCode(),
            "Film Lab "
                + lab.labName()
                + " có đơn dịch vụ mới "
                + order.getOrderCode()
                + ". Vui lòng nhận hoặc từ chối đơn trước "
                + NOTIFICATION_TIME.format(order.getLabResponseDueAt().atZone(VIETNAM_ZONE))
                + ".",
            null));
  }

  private Duration labResponseLimit() {
    return Duration.ofHours(
        systemParameterService.getInteger(SystemParameterKey.LAB_RESPONSE_LIMIT_HOURS));
  }

  /** {@code SO} + {@code yyMMdd} + 6 ký tự base-32 ngẫu nhiên; thử lại nếu trùng. */
  private String generateOrderCode(Instant now) {
    String datePart = ORDER_CODE_DATE.format(now.atZone(VIETNAM_ZONE));
    for (int attempt = 0; attempt < ORDER_CODE_ATTEMPTS; attempt++) {
      StringBuilder code = new StringBuilder(ORDER_CODE_PREFIX).append(datePart);
      for (int i = 0; i < ORDER_CODE_RANDOM_LENGTH; i++) {
        code.append(BASE32[random.nextInt(BASE32.length)]);
      }
      String candidate = code.toString();
      if (!serviceOrderRepository.existsByOrderCode(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("Không sinh được mã đơn duy nhất sau nhiều lần thử.");
  }

  private ServiceOrderResponse toResponse(ServiceOrder order) {
    // TODO(FA26-157): trả lại link thanh toán còn hạn khi contract (SPEC-00 §4.5) lên dev
    return serviceOrderMapper.toResponse(
        order, filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(order.getId()), null);
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  private static BusinessException notSupported(String field, String message) {
    return rollError(ErrorCode.ORDER_OPTION_NOT_SUPPORTED, field, message);
  }

  private static BusinessException rollError(ErrorCode errorCode, String field, String message) {
    return new BusinessException(
        errorCode, message, Map.of(ERRORS, List.of(new FieldViolation(field, message))));
  }

  private static BusinessException fieldError(String field, String message) {
    return rollError(ErrorCode.VALIDATION_ERROR, field, message);
  }

  /** Một cuộn đã kiểm rule và tính giá. */
  private record PricedRoll(
      int sequence,
      FilmRollRequest request,
      ServicePackageResponse servicePackage,
      ServicePackageScanOptionResponse scanOption,
      short pushPullStops,
      long unitPriceVnd,
      long scanOptionPriceVnd,
      long pushPullSurchargeVnd) {

    long rollPriceVnd() {
      return unitPriceVnd + scanOptionPriceVnd + pushPullSurchargeVnd;
    }
  }

  /** Giá cả đơn. Giai đoạn này phụ phí Express và phí giao phim luôn 0. */
  private record Pricing(
      OrderingFilmLabResponse lab,
      List<PricedRoll> rolls,
      long subtotalVnd,
      Instant expectedReturnAt) {

    long expressSurchargeVnd() {
      return 0;
    }

    long deliveryFeeVnd() {
      return 0;
    }

    long estimatedTotalVnd() {
      return subtotalVnd + expressSurchargeVnd() + deliveryFeeVnd();
    }
  }

  private record ActiveMasterdata(
      Set<Long> serviceTypeIds,
      Set<Long> filmProcessIds,
      Set<Long> filmFormatIds,
      Set<Long> filmStockIds) {}
}
