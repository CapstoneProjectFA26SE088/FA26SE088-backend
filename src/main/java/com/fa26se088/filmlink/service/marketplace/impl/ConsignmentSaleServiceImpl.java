package com.fa26se088.filmlink.service.marketplace.impl;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.lab.response.OrderingFilmLabResponse;
import com.fa26se088.filmlink.dto.marketplace.request.CreateConsignmentPurchaseRequest;
import com.fa26se088.filmlink.dto.marketplace.request.RecordConsignmentHandoverRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentHandoverResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSaleResponse;
import com.fa26se088.filmlink.dto.payment.CompletedSaleCommission;
import com.fa26se088.filmlink.dto.payment.response.PaymentLink;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentHandover;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentHandoverPhoto;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentItem;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentLabSettings;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentListing;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentSale;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentHandoverMethod;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentListingStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentPaymentMethod;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentSaleStatus;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.marketplace.ConsignmentMapper;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentHandoverPhotoRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentHandoverRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentItemRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentLabSettingsRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentListingRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentSaleRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.FilmLabService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.marketplace.ConsignmentSaleService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.notification.NotificationService;
import com.fa26se088.filmlink.service.payment.CommissionService;
import com.fa26se088.filmlink.service.payment.PaymentService;
import com.fa26se088.filmlink.service.payment.PayoutService;
import com.fa26se088.filmlink.service.payment.event.PaymentSucceededEvent;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Thực thi mua thiết bị ký gửi, bàn giao bằng chứng và phân chia doanh thu (F-22, UC62–UC64). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsignmentSaleServiceImpl implements ConsignmentSaleService {

  private static final BigDecimal DEFAULT_CONSIGNMENT_FEE_RATE = new BigDecimal("10.00");
  private static final BigDecimal DEFAULT_CONSIGNMENT_COMMISSION_RATE = new BigDecimal("5.00");
  private static final int DEFAULT_PAYMENT_TTL_MINUTES = 15;
  private static final int DEFAULT_RETURN_WINDOW_DAYS = 7;
  private static final int MAX_HANDOVER_PHOTOS = 10;

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String CODE_ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

  private final ConsignmentListingRepository listingRepository;
  private final ConsignmentItemRepository itemRepository;
  private final ConsignmentLabSettingsRepository settingsRepository;
  private final ConsignmentSaleRepository saleRepository;
  private final ConsignmentHandoverRepository handoverRepository;
  private final ConsignmentHandoverPhotoRepository handoverPhotoRepository;
  private final ConsignmentMapper consignmentMapper;
  private final PaymentService paymentService;
  private final CommissionService commissionService;
  private final PayoutService payoutService;
  private final FilmLabService filmLabService;
  private final LabAccessService labAccessService;
  private final SystemParameterService systemParameterService;
  private final StorageClient storageClient;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;
  private final Clock clock;

  /**
   * UC62, UH-46, BR-54: khoá dòng tin đăng bằng {@code PESSIMISTIC_WRITE}, chuyển {@code ON_SALE ->
   * LOCKED}, tạo bản ghi {@code ConsignmentSale(AWAITING_PAYMENT)} kèm snapshot tỷ lệ chia tiền và
   * trả về link thanh toán VNPay.
   */
  @Override
  @Transactional
  public ConsignmentSaleResponse createPurchase(
      Long listingId,
      CreateConsignmentPurchaseRequest request,
      String clientIp,
      AuthenticatedAccount buyerAccount) {
    if (request.paymentMethod() != ConsignmentPaymentMethod.ONLINE
        || request.handoverMethod() != ConsignmentHandoverMethod.PICKUP_AT_LAB) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Giai đoạn hiện tại chỉ hỗ trợ thanh toán ONLINE và nhận tại Lab (PICKUP_AT_LAB).");
    }

    Optional<ConsignmentSale> idempotent =
        saleRepository.findByBuyerAccountIdAndIdempotencyKey(
            buyerAccount.id(), request.idempotencyKey());
    if (idempotent.isPresent()) {
      return buildExistingSaleResponse(idempotent.get(), clientIp);
    }

    ConsignmentListing listing =
        listingRepository
            .findByIdForUpdate(listingId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_LISTING_NOT_FOUND,
                        "Không tìm thấy tin đăng ký gửi."));
    if (listing.getStatus() != ConsignmentListingStatus.ON_SALE) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_LISTING_UNAVAILABLE,
          "Tin đăng ký gửi hiện không còn ở trạng thái mở bán (ON_SALE).");
    }
    if (!listing.isPickupAtLab()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Tin đăng này không hỗ trợ nhận thiết bị trực tiếp tại Lab.");
    }

    ConsignmentItem item =
        itemRepository
            .findByIdWithBooking(listing.getConsignmentItemId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi."));
    if (Objects.equals(item.getBooking().getSellerAccountId(), buyerAccount.id())) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_SELF_PURCHASE_FORBIDDEN,
          "Người bán không thể tự đặt mua thiết bị ký gửi của chính mình.");
    }

    final Instant now = clock.instant();
    final int ttlMinutes =
        intParam(SystemParameterKey.PAYMENT_LINK_TTL_MINUTES, DEFAULT_PAYMENT_TTL_MINUTES);

    BigDecimal feeRate = resolveConsignmentFeeRate(listing.getFilmLabId());
    BigDecimal commRate = resolveCommissionRate();
    long itemPrice = listing.getPriceVnd();
    long feeVnd = percentOf(itemPrice, feeRate);
    long commVnd = percentOf(itemPrice, commRate);
    final long sellerPayoutVnd = itemPrice - feeVnd - commVnd;

    listing.setStatus(ConsignmentListingStatus.LOCKED);
    listingRepository.save(listing);

    ConsignmentSale sale = new ConsignmentSale();
    sale.setSaleCode(generateUniqueSaleCode());
    sale.setIdempotencyKey(request.idempotencyKey());
    sale.setConsignmentListingId(listing.getId());
    sale.setFilmLabId(listing.getFilmLabId());
    sale.setBuyerAccountId(buyerAccount.id());
    sale.setPaymentMethod(ConsignmentPaymentMethod.ONLINE);
    sale.setHandoverMethod(ConsignmentHandoverMethod.PICKUP_AT_LAB);
    sale.setStatus(ConsignmentSaleStatus.AWAITING_PAYMENT);
    sale.setItemPriceVnd(itemPrice);
    sale.setShippingFeeVnd(0L);
    sale.setTotalVnd(itemPrice);
    sale.setPaymentDueAt(now.plus(Duration.ofMinutes(ttlMinutes)));
    sale.setConsignmentFeeRate(feeRate);
    sale.setConsignmentFeeVnd(feeVnd);
    sale.setCommissionRate(commRate);
    sale.setCommissionVnd(commVnd);
    sale.setSellerPayoutVnd(sellerPayoutVnd);

    ConsignmentSale savedSale = saleRepository.save(sale);
    PaymentLink link =
        paymentService.createSalePayment(
            savedSale.getId(), buyerAccount.id(), savedSale.getTotalVnd(), clientIp);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.CONSIGNMENT_SALE_CREATED,
            AuditTargetType.CONSIGNMENT_SALE,
            savedSale.getId(),
            "Người mua #"
                + buyerAccount.id()
                + " đặt mua tin ký gửi #"
                + listing.getId()
                + " mã "
                + savedSale.getSaleCode(),
            Map.of("listingStatus", ConsignmentListingStatus.ON_SALE.name()),
            Map.of(
                "saleStatus", savedSale.getStatus().name(),
                "listingStatus", listing.getStatus().name(),
                "totalVnd", savedSale.getTotalVnd())));

    return consignmentMapper.toSaleResponse(savedSale, listing, item, link, null);
  }

  /**
   * UC30, UC62: chạy đồng bộ trong cùng transaction với VNPay IPN ({@code MANDATORY}); khi khoản
   * {@code SALE_PAYMENT} thành công, chuyển giao dịch sang {@code AWAITING_HANDOVER}.
   */
  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onPaymentSucceeded(PaymentSucceededEvent event) {
    if (event.purpose() != PaymentPurpose.SALE_PAYMENT || event.consignmentSaleId() == null) {
      return;
    }

    ConsignmentSale sale =
        saleRepository
            .findByIdForUpdate(event.consignmentSaleId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_SALE_NOT_FOUND,
                        "Không tìm thấy giao dịch mua ký gửi."));
    if (sale.getStatus() != ConsignmentSaleStatus.AWAITING_PAYMENT) {
      return;
    }

    sale.setStatus(ConsignmentSaleStatus.AWAITING_HANDOVER);
    ConsignmentSale saved = saleRepository.save(sale);

    ConsignmentListing listing =
        listingRepository
            .findById(saved.getConsignmentListingId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_LISTING_NOT_FOUND,
                        "Không tìm thấy tin đăng ký gửi."));
    ConsignmentItem item =
        itemRepository
            .findByIdWithBooking(listing.getConsignmentItemId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi."));

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.CONSIGNMENT_SALE_PAID,
            AuditTargetType.CONSIGNMENT_SALE,
            saved.getId(),
            "Thanh toán thành công cho giao dịch ký gửi " + saved.getSaleCode(),
            Map.of("status", ConsignmentSaleStatus.AWAITING_PAYMENT.name()),
            Map.of("status", saved.getStatus().name())));

    OrderingFilmLabResponse lab = filmLabService.getLabForOrdering(saved.getFilmLabId());
    notificationService.notify(
        lab.ownerAccountId(),
        NotificationType.MARKETPLACE,
        "Đơn ký gửi " + saved.getSaleCode() + " đã thanh toán",
        "Người mua đã thanh toán trực tuyến; thiết bị sẵn sàng bàn giao tại quầy.",
        null);
    notificationService.notify(
        item.getBooking().getSellerAccountId(),
        NotificationType.MARKETPLACE,
        "Thiết bị " + item.getItemName() + " đã có người mua thanh toán",
        "Đơn mua " + saved.getSaleCode() + " đang chờ bàn giao cho người mua tại Lab.",
        null);
  }

  /**
   * UC63, UC64, BR-54, BR-55: bàn giao thiết bị tại quầy Lab kèm tối thiểu 1 ảnh bằng chứng;
   * chuyển giao dịch sang {@code COMPLETED}, tin đăng & thiết bị sang {@code SOLD}, và ghi nhận
   * {@code Commission(kind = SALE)}.
   */
  @Override
  @Transactional
  public ConsignmentSaleResponse recordHandover(
      Long saleId,
      RecordConsignmentHandoverRequest request,
      List<MultipartFile> photos,
      AuthenticatedAccount ownerAccount) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccount.id());
    ConsignmentSale sale =
        saleRepository
            .findByIdForUpdate(saleId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_SALE_NOT_FOUND,
                        "Không tìm thấy giao dịch mua ký gửi."));
    if (!Objects.equals(sale.getFilmLabId(), lab.getId())) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_SALE_NOT_FOUND, "Không tìm thấy giao dịch mua ký gửi.");
    }
    if (sale.getStatus() != ConsignmentSaleStatus.AWAITING_HANDOVER) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_STATUS_CONFLICT,
          "Chỉ được bàn giao cho giao dịch đang ở trạng thái AWAITING_HANDOVER.");
    }

    if (request != null
        && request.saleCode() != null
        && !request.saleCode().isBlank()
        && !sale.getSaleCode().equalsIgnoreCase(request.saleCode().strip())) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Mã nhận hàng (saleCode) không khớp với giao dịch.");
    }

    List<MultipartFile> nonEmptyPhotos =
        photos == null
            ? List.of()
            : photos.stream().filter(f -> f != null && !f.isEmpty()).toList();
    if (nonEmptyPhotos.isEmpty() || nonEmptyPhotos.size() > MAX_HANDOVER_PHOTOS) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Bàn giao thiết bị ký gửi bắt buộc đính kèm từ 1 đến 10 ảnh bằng chứng.");
    }

    ConsignmentListing listing =
        listingRepository
            .findByIdForUpdate(sale.getConsignmentListingId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_LISTING_NOT_FOUND,
                        "Không tìm thấy tin đăng ký gửi."));
    final ConsignmentItem item =
        itemRepository
            .findByIdWithBooking(listing.getConsignmentItemId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi."));

    Instant now = clock.instant();
    int returnDays =
        intParam(SystemParameterKey.CONSIGNMENT_RETURN_WINDOW_DAYS, DEFAULT_RETURN_WINDOW_DAYS);
    final Instant returnWindowEndsAt = now.plus(Duration.ofDays(returnDays));

    ConsignmentHandover handover = new ConsignmentHandover();
    handover.setConsignmentSaleId(sale.getId());
    handover.setHandoverMethod(ConsignmentHandoverMethod.PICKUP_AT_LAB);
    handover.setRecipientCodeVerified(true);
    handover.setNote(request != null ? blankToNull(request.note()) : null);
    handover.setPerformedByAccountId(ownerAccount.id());
    handover.setOccurredAt(now);
    ConsignmentHandover savedHandover = handoverRepository.save(handover);

    final List<ConsignmentHandoverPhoto> savedPhotos =
        uploadHandoverPhotos(savedHandover.getId(), nonEmptyPhotos, now);

    sale.setStatus(ConsignmentSaleStatus.COMPLETED);
    sale.setHandedOverAt(now);
    sale.setReturnWindowEndsAt(returnWindowEndsAt);
    final ConsignmentSale savedSale = saleRepository.save(sale);

    listing.setStatus(ConsignmentListingStatus.SOLD);
    listingRepository.save(listing);

    item.setStatus(ConsignmentItemStatus.SOLD);
    itemRepository.save(item);

    commissionService.recordForCompletedSale(
        new CompletedSaleCommission(
            savedSale.getId(),
            savedSale.getFilmLabId(),
            savedSale.getItemPriceVnd(),
            savedSale.getCommissionRate(),
            savedSale.getCommissionVnd(),
            returnWindowEndsAt));

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.CONSIGNMENT_SALE_COMPLETED,
            AuditTargetType.CONSIGNMENT_SALE,
            savedSale.getId(),
            "Bàn giao hoàn tất giao dịch ký gửi " + savedSale.getSaleCode(),
            Map.of("status", ConsignmentSaleStatus.AWAITING_HANDOVER.name()),
            Map.of(
                "status", savedSale.getStatus().name(),
                "consignmentFeeVnd", savedSale.getConsignmentFeeVnd(),
                "commissionVnd", savedSale.getCommissionVnd(),
                "sellerPayoutVnd", savedSale.getSellerPayoutVnd())));

    notificationService.notify(
        item.getBooking().getSellerAccountId(),
        NotificationType.MARKETPLACE,
        "Đã bàn giao thiết bị " + item.getItemName(),
        "Người mua đã nhận thiết bị; khoản thanh toán "
            + savedSale.getSellerPayoutVnd()
            + " VND sẽ được giải ngân sau thời hạn đổi trả.",
        null);

    ConsignmentHandoverResponse handoverDto =
        consignmentMapper.toHandoverResponse(savedHandover, savedPhotos);
    return consignmentMapper.toSaleResponse(savedSale, listing, item, null, handoverDto);
  }

  @Override
  @Transactional
  public int generateDueSellerPayouts() {
    Instant now = clock.instant();
    List<ConsignmentSale> dueSales =
        saleRepository.findByStatusAndReturnWindowEndsAtLessThanEqualOrderByIdAsc(
            ConsignmentSaleStatus.COMPLETED, now);
    int created = 0;
    for (ConsignmentSale sale : dueSales) {
      Optional<ConsignmentListing> listing =
          listingRepository.findById(sale.getConsignmentListingId());
      if (listing.isEmpty()) {
        continue;
      }
      Optional<ConsignmentItem> item =
          itemRepository.findByIdWithBooking(listing.get().getConsignmentItemId());
      if (item.isEmpty()) {
        continue;
      }
      Long sellerAccountId = item.get().getBooking().getSellerAccountId();
      if (payoutService.createSellerPayoutIfAbsent(
          sellerAccountId, sale.getId(), sale.getSellerPayoutVnd())) {
        created++;
      }
    }
    log.info(
        "[ConsignmentSaleService] Quét {} giao dịch đủ hạn đổi trả, tạo {} đợt payout người bán.",
        dueSales.size(),
        created);
    return created;
  }

  private ConsignmentSaleResponse buildExistingSaleResponse(
      ConsignmentSale sale, String clientIp) {
    ConsignmentListing listing =
        listingRepository
            .findById(sale.getConsignmentListingId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_LISTING_NOT_FOUND,
                        "Không tìm thấy tin đăng ký gửi."));
    ConsignmentItem item =
        itemRepository
            .findByIdWithBooking(listing.getConsignmentItemId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi."));
    PaymentLink link =
        sale.getStatus() == ConsignmentSaleStatus.AWAITING_PAYMENT
            ? paymentService.createSalePayment(
                sale.getId(), sale.getBuyerAccountId(), sale.getTotalVnd(), clientIp)
            : null;
    ConsignmentHandoverResponse handoverDto =
        handoverRepository
            .findByConsignmentSaleId(sale.getId())
            .map(
                h ->
                    consignmentMapper.toHandoverResponse(
                        h,
                        handoverPhotoRepository.findByConsignmentHandoverIdOrderByDisplayOrderAsc(
                            h.getId())))
            .orElse(null);
    return consignmentMapper.toSaleResponse(sale, listing, item, link, handoverDto);
  }

  private List<ConsignmentHandoverPhoto> uploadHandoverPhotos(
      Long handoverId, List<MultipartFile> files, Instant now) {
    List<ConsignmentHandoverPhoto> entities = new ArrayList<>(files.size());
    short order = 1;
    for (MultipartFile file : files) {
      String blobKey = uploadFile(file);
      ConsignmentHandoverPhoto photo = new ConsignmentHandoverPhoto();
      photo.setConsignmentHandoverId(handoverId);
      photo.setBlobKey(blobKey);
      photo.setDisplayOrder(order++);
      photo.setCreatedAt(now);
      entities.add(photo);
    }
    return handoverPhotoRepository.saveAll(entities);
  }

  private String uploadFile(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      return storageClient.upload(
          StoragePurpose.CONSIGNMENT_PHOTO, file.getOriginalFilename(), in, file.getSize());
    } catch (IOException ex) {
      throw new BusinessException(
          ErrorCode.STORAGE_UNAVAILABLE, "Không đọc được file ảnh bàn giao.");
    }
  }

  private BigDecimal resolveConsignmentFeeRate(Long filmLabId) {
    Optional<ConsignmentLabSettings> settings = settingsRepository.findById(filmLabId);
    if (settings.isPresent() && settings.get().getConsignmentFeeRate() != null) {
      return settings.get().getConsignmentFeeRate();
    }
    BigDecimal param = systemParameterService.getDecimal(SystemParameterKey.CONSIGNMENT_FEE_RATE);
    return param != null ? param : DEFAULT_CONSIGNMENT_FEE_RATE;
  }

  private BigDecimal resolveCommissionRate() {
    BigDecimal param =
        systemParameterService.getDecimal(SystemParameterKey.CONSIGNMENT_COMMISSION_RATE);
    return param != null ? param : DEFAULT_CONSIGNMENT_COMMISSION_RATE;
  }

  private static long percentOf(long baseVnd, BigDecimal ratePercent) {
    return BigDecimal.valueOf(baseVnd)
        .multiply(ratePercent)
        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
        .longValue();
  }

  private String generateUniqueSaleCode() {
    for (int attempt = 0; attempt < 5; attempt++) {
      String code = "CSL" + randomChars(10);
      if (!saleRepository.existsBySaleCode(code)) {
        return code;
      }
    }
    return "CSL" + randomChars(12);
  }

  private static String randomChars(int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
    }
    return sb.toString();
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  private int intParam(SystemParameterKey key, int fallback) {
    Integer val = systemParameterService.getInteger(key);
    return val != null ? val : fallback;
  }
}
