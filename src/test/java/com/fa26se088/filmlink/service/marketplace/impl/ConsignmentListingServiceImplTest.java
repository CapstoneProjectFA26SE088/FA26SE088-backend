package com.fa26se088.filmlink.service.marketplace.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.lab.response.FilmLabBriefResponse;
import com.fa26se088.filmlink.dto.lab.response.OrderingFilmLabResponse;
import com.fa26se088.filmlink.dto.marketplace.request.CreatePriceOfferRequest;
import com.fa26se088.filmlink.dto.marketplace.request.PublishConsignmentListingRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentListingDetailResponse;
import com.fa26se088.filmlink.dto.marketplace.response.PriceOfferResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.marketplace.ConditionReport;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentBooking;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentItem;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentListing;
import com.fa26se088.filmlink.entity.marketplace.PriceOffer;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionConclusion;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionGrade;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemType;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentListingStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferParty;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferPurpose;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferStatus;
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
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.notification.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsignmentListingServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-28T04:00:00Z");
  private static final long LAB_ID = 10L;
  private static final long OWNER_ID = 20L;
  private static final long SELLER_ID = 30L;

  @Mock private ConsignmentItemRepository itemRepository;
  @Mock private ConditionReportRepository reportRepository;
  @Mock private ConditionReportCheckRepository reportCheckRepository;
  @Mock private ConsignmentItemPhotoRepository itemPhotoRepository;
  @Mock private PriceOfferRepository priceOfferRepository;
  @Mock private ConsignmentListingRepository listingRepository;
  @Mock private FilmLabService filmLabService;
  @Mock private LabAccessService labAccessService;
  @Mock private SystemParameterService systemParameterService;
  @Mock private StorageClient storageClient;
  @Mock private AuditLogService auditLogService;
  @Mock private NotificationService notificationService;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private ConsignmentListingServiceImpl service;
  private AuthenticatedAccount ownerAuth;
  private AuthenticatedAccount sellerAuth;

  @BeforeEach
  void setUp() {
    service =
        new ConsignmentListingServiceImpl(
            itemRepository,
            reportRepository,
            reportCheckRepository,
            itemPhotoRepository,
            priceOfferRepository,
            listingRepository,
            new ConsignmentMapper(storageClient),
            filmLabService,
            labAccessService,
            systemParameterService,
            auditLogService,
            notificationService,
            clock);
    ownerAuth = new AuthenticatedAccount(OWNER_ID, "owner@test.com", UserAccountRole.FILM_LAB_OWNER);
    sellerAuth = new AuthenticatedAccount(SELLER_ID, "seller@test.com", UserAccountRole.PHOTOGRAPHER);
  }

  @Test
  @DisplayName(
      "TC-SM13-04 UC46 report ACCEPTED, initial price offered -> item AWAITING_SELLER_CONFIRMATION")
  void tcSm1304_createInitialOffer_transitionsItemToAwaitingSellerConfirmation() {
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", LAB_ID);
    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);

    ConsignmentItem item = sampleItem(ConsignmentItemStatus.AWAITING_INSPECTION);
    ConditionReport report = new ConditionReport();
    ReflectionTestUtils.setField(report, "id", 300L);
    report.setConsignmentItemId(200L);
    report.setOverallGrade(ConditionGrade.B);
    report.setConclusion(ConditionConclusion.ACCEPTED);

    when(itemRepository.findByIdWithBooking(200L)).thenReturn(Optional.of(item));
    when(reportRepository.findByConsignmentItemId(200L)).thenReturn(Optional.of(report));
    when(priceOfferRepository.save(any(PriceOffer.class)))
        .thenAnswer(
            i -> {
              PriceOffer o = i.getArgument(0);
              ReflectionTestUtils.setField(o, "id", 400L);
              return o;
            });
    when(itemRepository.save(any(ConsignmentItem.class))).thenAnswer(i -> i.getArgument(0));

    PriceOfferResponse res =
        service.createInitialOffer(
            200L,
            new CreatePriceOfferRequest(
                3_000_000L, "Máy hạng B ngoại hình tốt, tốc màn trập và đo sáng chuẩn xác."),
            ownerAuth);

    assertThat(res.status()).isEqualTo(PriceOfferStatus.PENDING);
    assertThat(res.priceVnd()).isEqualTo(3_000_000L);
    assertThat(res.itemStatus()).isEqualTo(ConsignmentItemStatus.AWAITING_SELLER_CONFIRMATION);
  }

  @Test
  @DisplayName("TC-SM13-07 UC47 seller accepts the price -> item READY_TO_LIST")
  void tcSm1307_acceptOffer_transitionsItemToReadyToList() {
    ConsignmentItem item = sampleItem(ConsignmentItemStatus.AWAITING_SELLER_CONFIRMATION);
    PriceOffer offer = new PriceOffer();
    ReflectionTestUtils.setField(offer, "id", 400L);
    offer.setConsignmentItemId(200L);
    offer.setPurpose(PriceOfferPurpose.INITIAL);
    offer.setRoundNumber((short) 1);
    offer.setOfferedByParty(PriceOfferParty.LAB);
    offer.setPriceVnd(3_000_000L);
    offer.setStatus(PriceOfferStatus.PENDING);

    when(priceOfferRepository.findById(400L)).thenReturn(Optional.of(offer));
    when(itemRepository.findByIdWithBooking(200L)).thenReturn(Optional.of(item));
    when(priceOfferRepository.save(any(PriceOffer.class))).thenAnswer(i -> i.getArgument(0));
    when(itemRepository.save(any(ConsignmentItem.class))).thenAnswer(i -> i.getArgument(0));
    when(filmLabService.getLabForOrdering(LAB_ID))
        .thenReturn(new OrderingFilmLabResponse(LAB_ID, OWNER_ID, "Lab B", true));

    PriceOfferResponse res = service.acceptOffer(400L, sellerAuth);

    assertThat(res.status()).isEqualTo(PriceOfferStatus.ACCEPTED);
    assertThat(res.respondedAt()).isEqualTo(NOW);
    assertThat(res.itemStatus()).isEqualTo(ConsignmentItemStatus.READY_TO_LIST);
    assertThat(res.agreedPriceVnd()).isEqualTo(3_000_000L);
  }

  @Test
  @DisplayName(
      "TC-F21-01 & TC-SM13-10 lab offers 3,000,000 -> seller accepts -> lab publishes listing -> item LISTED, listing ON_SALE")
  void tcF2101_publishListing_transitionsItemToListedAndOnSale() {
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", LAB_ID);
    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);

    ConsignmentItem item = sampleItem(ConsignmentItemStatus.READY_TO_LIST);
    item.setAgreedPriceVnd(3_000_000L);
    item.setPriceAgreedAt(NOW);

    ConditionReport report = new ConditionReport();
    ReflectionTestUtils.setField(report, "id", 300L);
    report.setConsignmentItemId(200L);
    report.setOverallGrade(ConditionGrade.B);
    report.setConclusion(ConditionConclusion.ACCEPTED);

    when(itemRepository.findByIdWithBooking(200L)).thenReturn(Optional.of(item));
    when(listingRepository.findByConsignmentItemId(200L)).thenReturn(Optional.empty());
    when(reportRepository.findByConsignmentItemId(200L)).thenReturn(Optional.of(report));
    when(reportCheckRepository.findByIdConditionReportId(300L)).thenReturn(List.of());
    when(itemPhotoRepository.findByConsignmentItemIdAndSourceOrderByDisplayOrderAsc(any(), any()))
        .thenReturn(List.of());
    when(filmLabService.getLabBriefs(List.of(LAB_ID)))
        .thenReturn(
            List.of(new FilmLabBriefResponse(LAB_ID, "Lab B", "12 Nguyễn Huệ", "Q1", "TP.HCM")));
    when(listingRepository.save(any(ConsignmentListing.class)))
        .thenAnswer(
            i -> {
              ConsignmentListing l = i.getArgument(0);
              ReflectionTestUtils.setField(l, "id", 500L);
              return l;
            });
    when(itemRepository.save(any(ConsignmentItem.class))).thenAnswer(i -> i.getArgument(0));

    ConsignmentListingDetailResponse res =
        service.publishListing(
            200L,
            new PublishConsignmentListingRequest(
                "Nikon FM2 Chrome Body đã kiểm định hạng B",
                "Thân máy Nikon FM2 cơ hoàn toàn, đã kiểm định đầy đủ tốc màn trập, đo sáng và đệm sáng tại Film Lab B.",
                true,
                false),
            ownerAuth);

    assertThat(res.status()).isEqualTo(ConsignmentListingStatus.ON_SALE);
    assertThat(res.itemStatus()).isEqualTo(ConsignmentItemStatus.LISTED);
    assertThat(res.priceVnd()).isEqualTo(3_000_000L);
    assertThat(res.conditionReport().overallGrade()).isEqualTo(ConditionGrade.B);
  }

  @Test
  @DisplayName("TC-F21-03 đăng tin khi chưa chốt giá bị từ chối CONSIGNMENT_STATUS_CONFLICT")
  void tcF2103_publishWithoutAgreedPrice_rejected() {
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", LAB_ID);
    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);

    ConsignmentItem item = sampleItem(ConsignmentItemStatus.AWAITING_SELLER_CONFIRMATION);
    when(itemRepository.findByIdWithBooking(200L)).thenReturn(Optional.of(item));

    assertThatThrownBy(
            () ->
                service.publishListing(
                    200L,
                    new PublishConsignmentListingRequest(
                        "Nikon FM2 Chrome Body",
                        "Mô tả chi tiết trên 50 ký tự để kiểm tra điều kiện chặn khi chưa chốt giá.",
                        true,
                        false),
                    ownerAuth))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONSIGNMENT_STATUS_CONFLICT));
  }

  private static ConsignmentItem sampleItem(ConsignmentItemStatus status) {
    ConsignmentBooking booking = new ConsignmentBooking();
    ReflectionTestUtils.setField(booking, "id", 100L);
    booking.setFilmLabId(LAB_ID);
    booking.setSellerAccountId(SELLER_ID);

    ConsignmentItem item = new ConsignmentItem();
    ReflectionTestUtils.setField(item, "id", 200L);
    item.setBooking(booking);
    item.setItemType(ConsignmentItemType.CAMERA_BODY);
    item.setItemName("Nikon FM2 Chrome");
    item.setSellerDescription("Máy cơ hoạt động tốt, đo sáng chuẩn xác.");
    item.setStatus(status);
    return item;
  }
}
