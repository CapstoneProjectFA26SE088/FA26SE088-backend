package com.fa26se088.filmlink.service.marketplace.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.lab.response.FilmLabBriefResponse;
import com.fa26se088.filmlink.dto.marketplace.request.CreatePriceOfferRequest;
import com.fa26se088.filmlink.dto.marketplace.request.PublishConsignmentListingRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConditionReportResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentListingDetailResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentListingSummaryResponse;
import com.fa26se088.filmlink.dto.marketplace.response.PriceOfferResponse;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.marketplace.ConditionReport;
import com.fa26se088.filmlink.entity.marketplace.ConditionReportCheck;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentItem;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentItemPhoto;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentListing;
import com.fa26se088.filmlink.entity.marketplace.PriceOffer;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionConclusion;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentListingStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentPhotoSource;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferParty;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferPurpose;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferStatus;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.marketplace.ConsignmentMapper;
import com.fa26se088.filmlink.repository.marketplace.ConditionReportCheckRepository;
import com.fa26se088.filmlink.repository.marketplace.ConditionReportRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentItemPhotoRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentItemRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentListingRepository;
import com.fa26se088.filmlink.repository.marketplace.PriceOfferRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.FilmLabService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.marketplace.ConsignmentListingService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.notification.NotificationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đề xuất giá, chốt giá ký gửi và xuất bản tin đăng công khai (F-21, UC47, UC48, UC61). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsignmentListingServiceImpl implements ConsignmentListingService {

  private static final int DEFAULT_OFFER_RESPONSE_HOURS = 72;
  private static final int DEFAULT_CONSIGNMENT_PERIOD_DAYS = 60;

  private final ConsignmentItemRepository itemRepository;
  private final ConditionReportRepository reportRepository;
  private final ConditionReportCheckRepository reportCheckRepository;
  private final ConsignmentItemPhotoRepository itemPhotoRepository;
  private final PriceOfferRepository priceOfferRepository;
  private final ConsignmentListingRepository listingRepository;
  private final ConsignmentMapper consignmentMapper;
  private final FilmLabService filmLabService;
  private final LabAccessService labAccessService;
  private final SystemParameterService systemParameterService;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;
  private final Clock clock;

  /**
   * UC47: sau khi kiểm định {@code ACCEPTED}, Lab gửi đề xuất giá bán lần đầu; món đồ chuyển sang
   * {@code AWAITING_SELLER_CONFIRMATION}.
   */
  @Override
  @Transactional
  public PriceOfferResponse createInitialOffer(
      Long itemId, CreatePriceOfferRequest request, AuthenticatedAccount ownerAccount) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccount.id());
    ConsignmentItem item =
        itemRepository
            .findByIdWithBooking(itemId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi."));
    if (!Objects.equals(item.getBooking().getFilmLabId(), lab.getId())) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi.");
    }
    if (item.getStatus() != ConsignmentItemStatus.AWAITING_INSPECTION) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_STATUS_CONFLICT,
          "Thiết bị không ở trạng thái chờ gửi đề xuất giá lần đầu.");
    }

    ConditionReport report =
        reportRepository
            .findByConsignmentItemId(item.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_STATUS_CONFLICT,
                        "Cần lập biên bản kiểm định ACCEPTED trước khi đề xuất giá bán."));
    if (report.getConclusion() != ConditionConclusion.ACCEPTED) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_STATUS_CONFLICT,
          "Không thể đề xuất giá cho thiết bị đã bị từ chối kiểm định.");
    }

    final String trimmedJustification =
        stripRequired(request.justification(), 20, 1000, "Lý do đề xuất giá");
    if (priceOfferRepository.existsByConsignmentItemIdAndPurposeAndRoundNumberAndOfferedByParty(
        item.getId(), PriceOfferPurpose.INITIAL, (short) 1, PriceOfferParty.LAB)) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_STATUS_CONFLICT, "Thiết bị đã có đề xuất giá lần đầu.");
    }

    Instant now = clock.instant();
    int responseHours =
        intParam(SystemParameterKey.PRICE_OFFER_RESPONSE_HOURS, DEFAULT_OFFER_RESPONSE_HOURS);

    PriceOffer offer = new PriceOffer();
    offer.setConsignmentItemId(item.getId());
    offer.setPurpose(PriceOfferPurpose.INITIAL);
    offer.setRoundNumber((short) 1);
    offer.setOfferedByParty(PriceOfferParty.LAB);
    offer.setOfferedByAccountId(ownerAccount.id());
    offer.setPriceVnd(request.priceVnd());
    offer.setJustification(trimmedJustification);
    offer.setStatus(PriceOfferStatus.PENDING);
    offer.setResponseDueAt(now.plus(Duration.ofHours(responseHours)));
    PriceOffer savedOffer = priceOfferRepository.save(offer);

    item.setStatus(ConsignmentItemStatus.AWAITING_SELLER_CONFIRMATION);
    ConsignmentItem savedItem = itemRepository.save(item);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.CONSIGNMENT_OFFER_CREATED,
            AuditTargetType.PRICE_OFFER,
            savedOffer.getId(),
            "Lab đề xuất giá " + savedOffer.getPriceVnd() + " VND cho thiết bị #" + item.getId(),
            Map.of("itemStatus", ConsignmentItemStatus.AWAITING_INSPECTION.name()),
            Map.of(
                "itemStatus", savedItem.getStatus().name(),
                "priceVnd", savedOffer.getPriceVnd())));

    notificationService.notify(
        item.getBooking().getSellerAccountId(),
        NotificationType.MARKETPLACE,
        "Đề xuất giá bán cho " + item.getItemName(),
        "Film Lab đề xuất mức giá "
            + savedOffer.getPriceVnd()
            + " VND cho thiết bị ký gửi của bạn.",
        null);

    return consignmentMapper.toPriceOfferResponse(savedOffer, savedItem);
  }

  /**
   * UC47, BR-52: người bán chấp nhận đề xuất giá -> đề xuất sang {@code ACCEPTED}, thiết bị sang
   * {@code READY_TO_LIST} và chốt {@code agreed_price_vnd}.
   */
  @Override
  @Transactional
  public PriceOfferResponse acceptOffer(Long offerId, AuthenticatedAccount sellerAccount) {
    PriceOffer offer =
        priceOfferRepository
            .findById(offerId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_OFFER_NOT_FOUND, "Không tìm thấy đề xuất giá."));

    ConsignmentItem item =
        itemRepository
            .findByIdWithBooking(offer.getConsignmentItemId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi."));
    if (!Objects.equals(item.getBooking().getSellerAccountId(), sellerAccount.id())) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_OFFER_NOT_FOUND, "Không tìm thấy đề xuất giá.");
    }
    if (offer.getStatus() != PriceOfferStatus.PENDING
        || offer.getOfferedByParty() != PriceOfferParty.LAB) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_STATUS_CONFLICT,
          "Đề xuất giá không ở trạng thái chờ người bán chấp nhận.");
    }

    Instant now = clock.instant();
    offer.setStatus(PriceOfferStatus.ACCEPTED);
    offer.setRespondedAt(now);
    PriceOffer savedOffer = priceOfferRepository.save(offer);

    item.setStatus(ConsignmentItemStatus.READY_TO_LIST);
    item.setAgreedPriceVnd(savedOffer.getPriceVnd());
    item.setPriceAgreedAt(now);
    ConsignmentItem savedItem = itemRepository.save(item);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.CONSIGNMENT_OFFER_ACCEPTED,
            AuditTargetType.PRICE_OFFER,
            savedOffer.getId(),
            "Người bán đồng ý giá " + savedOffer.getPriceVnd() + " VND cho #" + item.getId(),
            Map.of("offerStatus", PriceOfferStatus.PENDING.name()),
            Map.of(
                "offerStatus", savedOffer.getStatus().name(),
                "itemStatus", savedItem.getStatus().name(),
                "agreedPriceVnd", savedOffer.getPriceVnd())));

    Long labOwnerId =
        filmLabService.getLabForOrdering(item.getBooking().getFilmLabId()).ownerAccountId();
    notificationService.notify(
        labOwnerId,
        NotificationType.MARKETPLACE,
        "Người bán đã chốt giá cho " + item.getItemName(),
        "Thiết bị #" + item.getId() + " đã sẵn sàng để xuất bản tin đăng (READY_TO_LIST).",
        null);

    return consignmentMapper.toPriceOfferResponse(savedOffer, savedItem);
  }

  /**
   * UC48, BR-51, BR-52, BR-53: xuất bản tin đăng bán công khai với giá đã thống nhất, hạn 60 ngày,
   * kèm biên bản kiểm định.
   */
  @Override
  @Transactional
  public ConsignmentListingDetailResponse publishListing(
      Long itemId, PublishConsignmentListingRequest request, AuthenticatedAccount ownerAccount) {
    if (!Boolean.TRUE.equals(request.isPickupAtLab())
        && !Boolean.TRUE.equals(request.isDeliveryAvailable())) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Tin đăng ký gửi phải hỗ trợ ít nhất một phương thức nhận thiết bị.");
    }

    FilmLab lab = labAccessService.requireOwnedLab(ownerAccount.id());
    ConsignmentItem item =
        itemRepository
            .findByIdWithBooking(itemId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi."));
    if (!Objects.equals(item.getBooking().getFilmLabId(), lab.getId())) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi.");
    }
    if (item.getStatus() != ConsignmentItemStatus.READY_TO_LIST
        || item.getAgreedPriceVnd() == null
        || listingRepository.findByConsignmentItemId(item.getId()).isPresent()) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_STATUS_CONFLICT,
          "Thiết bị chưa chốt giá (READY_TO_LIST) hoặc đã được đăng bán.");
    }

    final ConditionReport report =
        reportRepository
            .findByConsignmentItemId(item.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_STATUS_CONFLICT,
                        "Thiết bị chưa có biên bản kiểm định để công bố."));

    String trimmedTitle = stripRequired(request.title(), 10, 150, "Tiêu đề tin đăng");
    String trimmedDescription = stripRequired(request.description(), 50, 3000, "Mô tả tin đăng");

    Instant now = clock.instant();
    int periodDays =
        intParam(SystemParameterKey.CONSIGNMENT_PERIOD_DAYS, DEFAULT_CONSIGNMENT_PERIOD_DAYS);

    ConsignmentListing listing = new ConsignmentListing();
    listing.setConsignmentItemId(item.getId());
    listing.setFilmLabId(lab.getId());
    listing.setTitle(trimmedTitle);
    listing.setDescription(trimmedDescription);
    listing.setPriceVnd(item.getAgreedPriceVnd());
    listing.setPickupAtLab(Boolean.TRUE.equals(request.isPickupAtLab()));
    listing.setDeliveryAvailable(Boolean.TRUE.equals(request.isDeliveryAvailable()));
    listing.setStatus(ConsignmentListingStatus.ON_SALE);
    listing.setConsignmentStartedAt(now);
    listing.setPublishedAt(now);
    listing.setExpiresAt(now.plus(Duration.ofDays(periodDays)));
    ConsignmentListing savedListing = listingRepository.save(listing);

    item.setStatus(ConsignmentItemStatus.LISTED);
    ConsignmentItem savedItem = itemRepository.save(item);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.CONSIGNMENT_LISTING_PUBLISHED,
            AuditTargetType.CONSIGNMENT_LISTING,
            savedListing.getId(),
            "Xuất bản tin đăng ký gửi #" + savedListing.getId() + " cho thiết bị #" + item.getId(),
            Map.of("itemStatus", ConsignmentItemStatus.READY_TO_LIST.name()),
            Map.of(
                "listingStatus", savedListing.getStatus().name(),
                "itemStatus", savedItem.getStatus().name(),
                "priceVnd", savedListing.getPriceVnd())));

    notificationService.notify(
        item.getBooking().getSellerAccountId(),
        NotificationType.MARKETPLACE,
        "Tin đăng ký gửi cho " + item.getItemName() + " đã lên sàn",
        "Thiết bị đang mở bán công khai với giá " + savedListing.getPriceVnd() + " VND.",
        null);

    return buildDetailResponse(savedListing, savedItem, report);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ConsignmentListingSummaryResponse> listPublicListings(Pageable pageable) {
    Page<ConsignmentListing> page =
        listingRepository.findByStatus(ConsignmentListingStatus.ON_SALE, pageable);
    if (page.isEmpty()) {
      return page.map(l -> null);
    }

    List<ConsignmentListing> listings = page.getContent();
    Set<Long> itemIds =
        listings.stream().map(ConsignmentListing::getConsignmentItemId).collect(Collectors.toSet());
    Set<Long> labIds =
        listings.stream().map(ConsignmentListing::getFilmLabId).collect(Collectors.toSet());

    Map<Long, ConsignmentItem> itemsById =
        itemRepository.findByIdInWithBooking(itemIds).stream()
            .collect(Collectors.toMap(ConsignmentItem::getId, Function.identity()));
    Map<Long, ConditionReport> reportsByItemId =
        reportRepository.findByConsignmentItemIdIn(itemIds).stream()
            .collect(Collectors.toMap(ConditionReport::getConsignmentItemId, Function.identity()));
    Map<Long, FilmLabBriefResponse> labsById =
        filmLabService.getLabBriefs(labIds).stream()
            .collect(Collectors.toMap(FilmLabBriefResponse::id, Function.identity()));
    Map<Long, String> firstPhotoByItem =
        itemPhotoRepository.findByConsignmentItemIdInOrderByDisplayOrderAsc(itemIds).stream()
            .filter(p -> p.getSource() == ConsignmentPhotoSource.INSPECTION)
            .collect(
                Collectors.toMap(
                    ConsignmentItemPhoto::getConsignmentItemId,
                    ConsignmentItemPhoto::getBlobKey,
                    (first, second) -> first));

    return page.map(
        listing ->
            consignmentMapper.toListingSummary(
                listing,
                itemsById.get(listing.getConsignmentItemId()),
                reportsByItemId.get(listing.getConsignmentItemId()),
                labsById.get(listing.getFilmLabId()),
                firstPhotoByItem.get(listing.getConsignmentItemId())));
  }

  @Override
  @Transactional(readOnly = true)
  public ConsignmentListingDetailResponse getPublicListingDetail(Long listingId) {
    ConsignmentListing listing =
        listingRepository
            .findById(listingId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_LISTING_NOT_FOUND,
                        "Không tìm thấy tin đăng ký gửi."));
    if (listing.getStatus() != ConsignmentListingStatus.ON_SALE) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_LISTING_NOT_FOUND, "Không tìm thấy tin đăng ký gửi.");
    }

    ConsignmentItem item =
        itemRepository
            .findByIdWithBooking(listing.getConsignmentItemId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi."));
    ConditionReport report =
        reportRepository
            .findByConsignmentItemId(item.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND,
                        "Không tìm thấy biên bản kiểm định."));
    return buildDetailResponse(listing, item, report);
  }

  private ConsignmentListingDetailResponse buildDetailResponse(
      ConsignmentListing listing, ConsignmentItem item, ConditionReport report) {
    List<ConditionReportCheck> checks =
        reportCheckRepository.findByIdConditionReportId(report.getId());
    List<ConsignmentItemPhoto> photos =
        itemPhotoRepository.findByConsignmentItemIdAndSourceOrderByDisplayOrderAsc(
            item.getId(), ConsignmentPhotoSource.INSPECTION);
    ConditionReportResponse reportDto =
        consignmentMapper.toReportResponse(report, item, checks, photos);
    FilmLabBriefResponse labBrief =
        filmLabService.getLabBriefs(List.of(listing.getFilmLabId())).stream()
            .findFirst()
            .orElse(null);
    return consignmentMapper.toListingDetail(listing, item, labBrief, reportDto);
  }

  private static String stripRequired(String raw, int min, int max, String label) {
    String stripped = raw == null ? "" : raw.strip();
    if (stripped.length() < min || stripped.length() > max) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          label + " phải có độ dài từ " + min + " đến " + max + " ký tự.");
    }
    return stripped;
  }

  private int intParam(SystemParameterKey key, int fallback) {
    Integer val = systemParameterService.getInteger(key);
    return val != null ? val : fallback;
  }
}
