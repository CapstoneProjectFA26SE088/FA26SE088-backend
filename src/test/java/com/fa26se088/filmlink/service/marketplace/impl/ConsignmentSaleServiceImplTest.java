package com.fa26se088.filmlink.service.marketplace.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.lab.response.OrderingFilmLabResponse;
import com.fa26se088.filmlink.dto.marketplace.request.CreateConsignmentPurchaseRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSaleResponse;
import com.fa26se088.filmlink.dto.payment.CompletedSaleCommission;
import com.fa26se088.filmlink.dto.payment.response.PaymentLink;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentBooking;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentHandover;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentItem;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentLabSettings;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentListing;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentSale;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentHandoverMethod;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemType;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentListingStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentPaymentMethod;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentSaleStatus;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
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
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.notification.NotificationService;
import com.fa26se088.filmlink.service.payment.CommissionService;
import com.fa26se088.filmlink.service.payment.PaymentService;
import com.fa26se088.filmlink.service.payment.PayoutService;
import com.fa26se088.filmlink.service.payment.event.PaymentSucceededEvent;
import java.math.BigDecimal;
import java.net.URI;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsignmentSaleServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-28T06:00:00Z");
  private static final long LAB_ID = 10L;
  private static final long OWNER_ID = 20L;
  private static final long SELLER_ID = 30L;
  private static final long BUYER_ID = 40L;

  @Mock private ConsignmentListingRepository listingRepository;
  @Mock private ConsignmentItemRepository itemRepository;
  @Mock private ConsignmentLabSettingsRepository settingsRepository;
  @Mock private ConsignmentSaleRepository saleRepository;
  @Mock private ConsignmentHandoverRepository handoverRepository;
  @Mock private ConsignmentHandoverPhotoRepository handoverPhotoRepository;
  @Mock private PaymentService paymentService;
  @Mock private CommissionService commissionService;
  @Mock private PayoutService payoutService;
  @Mock private FilmLabService filmLabService;
  @Mock private LabAccessService labAccessService;
  @Mock private SystemParameterService systemParameterService;
  @Mock private StorageClient storageClient;
  @Mock private AuditLogService auditLogService;
  @Mock private NotificationService notificationService;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private ConsignmentSaleServiceImpl service;
  private AuthenticatedAccount ownerAuth;
  private AuthenticatedAccount buyerAuth;

  @BeforeEach
  void setUp() {
    service =
        new ConsignmentSaleServiceImpl(
            listingRepository,
            itemRepository,
            settingsRepository,
            saleRepository,
            handoverRepository,
            handoverPhotoRepository,
            new ConsignmentMapper(storageClient),
            paymentService,
            commissionService,
            payoutService,
            filmLabService,
            labAccessService,
            systemParameterService,
            storageClient,
            auditLogService,
            notificationService,
            clock);
    ownerAuth = new AuthenticatedAccount(OWNER_ID, "owner@test.com", UserAccountRole.FILM_LAB_OWNER);
    buyerAuth = new AuthenticatedAccount(BUYER_ID, "buyer@test.com", UserAccountRole.PHOTOGRAPHER);
  }

  @Test
  @DisplayName("TC-SM16-01 UC62 buyer orders ONLINE -> listing LOCKED, sale AWAITING_PAYMENT")
  void tcSm1601_createPurchase_locksListingAndReturnsAwaitingPayment() {
    ConsignmentListing listing = sampleListing(ConsignmentListingStatus.ON_SALE);
    ConsignmentItem item = sampleItem(ConsignmentItemStatus.LISTED);
    ConsignmentLabSettings settings = new ConsignmentLabSettings();
    settings.setFilmLabId(LAB_ID);
    settings.setConsignmentFeeRate(new BigDecimal("10.00"));

    when(saleRepository.findByBuyerAccountIdAndIdempotencyKey(eq(BUYER_ID), any()))
        .thenReturn(Optional.empty());
    when(listingRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(listing));
    when(itemRepository.findByIdWithBooking(200L)).thenReturn(Optional.of(item));
    when(settingsRepository.findById(LAB_ID)).thenReturn(Optional.of(settings));
    when(systemParameterService.getDecimal(SystemParameterKey.CONSIGNMENT_COMMISSION_RATE))
        .thenReturn(new BigDecimal("5.00"));
    when(saleRepository.save(any(ConsignmentSale.class)))
        .thenAnswer(
            i -> {
              ConsignmentSale s = i.getArgument(0);
              ReflectionTestUtils.setField(s, "id", 600L);
              return s;
            });
    when(paymentService.createSalePayment(600L, BUYER_ID, 3_000_000L, "127.0.0.1"))
        .thenReturn(
            new PaymentLink(
                900L, "TXN_SALE_600", "https://sandbox.vnpayment.vn/pay", NOW.plusSeconds(900)));

    ConsignmentSaleResponse res =
        service.createPurchase(
            500L,
            new CreateConsignmentPurchaseRequest(
                UUID.randomUUID(),
                ConsignmentPaymentMethod.ONLINE,
                ConsignmentHandoverMethod.PICKUP_AT_LAB),
            "127.0.0.1",
            buyerAuth);

    assertThat(res.status()).isEqualTo(ConsignmentSaleStatus.AWAITING_PAYMENT);
    assertThat(res.listingStatus()).isEqualTo(ConsignmentListingStatus.LOCKED);
    assertThat(res.consignmentFeeVnd()).isEqualTo(300_000L);
    assertThat(res.commissionVnd()).isEqualTo(150_000L);
    assertThat(res.sellerPayoutVnd()).isEqualTo(2_550_000L);
    assertThat(res.paymentLink()).isNotNull();
  }

  @Test
  @DisplayName("TC-SM16-03 UC30 IPN success -> sale AWAITING_HANDOVER")
  void tcSm1603_onPaymentSucceeded_transitionsSaleToAwaitingHandover() {
    ConsignmentSale sale = sampleSale(ConsignmentSaleStatus.AWAITING_PAYMENT);
    ConsignmentListing listing = sampleListing(ConsignmentListingStatus.LOCKED);
    ConsignmentItem item = sampleItem(ConsignmentItemStatus.LISTED);

    when(saleRepository.findByIdForUpdate(600L)).thenReturn(Optional.of(sale));
    when(saleRepository.save(any(ConsignmentSale.class))).thenAnswer(i -> i.getArgument(0));
    when(listingRepository.findById(500L)).thenReturn(Optional.of(listing));
    when(itemRepository.findByIdWithBooking(200L)).thenReturn(Optional.of(item));
    when(filmLabService.getLabForOrdering(LAB_ID))
        .thenReturn(new OrderingFilmLabResponse(LAB_ID, OWNER_ID, "Lab B", true));

    service.onPaymentSucceeded(
        new PaymentSucceededEvent(
            900L, PaymentPurpose.SALE_PAYMENT, null, 600L, 3_000_000L, NOW));

    assertThat(sale.getStatus()).isEqualTo(ConsignmentSaleStatus.AWAITING_HANDOVER);
  }

  @Test
  @DisplayName(
      "TC-F22-01, TC-SM16-05, TC-SM13-11 handover with 2 photos -> sale COMPLETED, item SOLD, split 300k/150k/2.55M & seller payout after return window")
  void tcF2201_recordHandoverAndSellerPayout() {
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", LAB_ID);
    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);

    ConsignmentSale sale = sampleSale(ConsignmentSaleStatus.AWAITING_HANDOVER);
    ConsignmentListing listing = sampleListing(ConsignmentListingStatus.LOCKED);
    ConsignmentItem item = sampleItem(ConsignmentItemStatus.LISTED);

    when(saleRepository.findByIdForUpdate(600L)).thenReturn(Optional.of(sale));
    when(listingRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(listing));
    when(itemRepository.findByIdWithBooking(200L)).thenReturn(Optional.of(item));
    when(handoverRepository.save(any(ConsignmentHandover.class)))
        .thenAnswer(
            i -> {
              ConsignmentHandover h = i.getArgument(0);
              ReflectionTestUtils.setField(h, "id", 700L);
              return h;
            });
    when(handoverPhotoRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    when(saleRepository.save(any(ConsignmentSale.class))).thenAnswer(i -> i.getArgument(0));
    when(storageClient.upload(eq(StoragePurpose.CONSIGNMENT_PHOTO), anyString(), any(), anyLong()))
        .thenReturn("consignment-photo/2026/11/h.jpg");
    when(storageClient.signedUrl(anyString()))
        .thenReturn(URI.create("http://localhost:8080/files/h.jpg"));

    ConsignmentSaleResponse res =
        service.recordHandover(
            600L,
            null,
            List.of(
                new MockMultipartFile("photos", "h1.jpg", "image/jpeg", new byte[] {1, 2}),
                new MockMultipartFile("photos", "h2.jpg", "image/jpeg", new byte[] {3, 4})),
            ownerAuth);

    assertThat(res.status()).isEqualTo(ConsignmentSaleStatus.COMPLETED);
    assertThat(res.listingStatus()).isEqualTo(ConsignmentListingStatus.SOLD);
    assertThat(res.itemStatus()).isEqualTo(ConsignmentItemStatus.SOLD);
    assertThat(res.consignmentFeeVnd()).isEqualTo(300_000L);
    assertThat(res.commissionVnd()).isEqualTo(150_000L);
    assertThat(res.sellerPayoutVnd()).isEqualTo(2_550_000L);
    assertThat(res.handover().photos()).hasSize(2);

    ArgumentCaptor<CompletedSaleCommission> commCaptor =
        ArgumentCaptor.forClass(CompletedSaleCommission.class);
    verify(commissionService).recordForCompletedSale(commCaptor.capture());
    assertThat(commCaptor.getValue().commissionVnd()).isEqualTo(150_000L);

    // Sau hạn đổi trả: job sinh seller payout PENDING 2,550,000 VND
    when(saleRepository.findByStatusAndReturnWindowEndsAtLessThanEqualOrderByIdAsc(
            ConsignmentSaleStatus.COMPLETED, NOW))
        .thenReturn(List.of(sale));
    when(listingRepository.findById(500L)).thenReturn(Optional.of(listing));
    when(payoutService.createSellerPayoutIfAbsent(SELLER_ID, 600L, 2_550_000L)).thenReturn(true);

    assertThat(service.generateDueSellerPayouts()).isEqualTo(1);
  }

  @Test
  @DisplayName("TC-F22-03 người mua thứ hai mua tin đã LOCKED -> 409 CONSIGNMENT_LISTING_UNAVAILABLE")
  void tcF2203_secondBuyerOnLockedListing_conflict() {
    ConsignmentListing locked = sampleListing(ConsignmentListingStatus.LOCKED);
    when(saleRepository.findByBuyerAccountIdAndIdempotencyKey(eq(BUYER_ID), any()))
        .thenReturn(Optional.empty());
    when(listingRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(locked));

    assertThatThrownBy(
            () ->
                service.createPurchase(
                    500L,
                    new CreateConsignmentPurchaseRequest(
                        UUID.randomUUID(),
                        ConsignmentPaymentMethod.ONLINE,
                        ConsignmentHandoverMethod.PICKUP_AT_LAB),
                    "127.0.0.1",
                    buyerAuth))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex ->
                assertThat(ex.getErrorCode())
                    .isEqualTo(ErrorCode.CONSIGNMENT_LISTING_UNAVAILABLE));
  }

  private static ConsignmentListing sampleListing(ConsignmentListingStatus status) {
    ConsignmentListing l = new ConsignmentListing();
    ReflectionTestUtils.setField(l, "id", 500L);
    l.setConsignmentItemId(200L);
    l.setFilmLabId(LAB_ID);
    l.setPriceVnd(3_000_000L);
    l.setPickupAtLab(true);
    l.setStatus(status);
    return l;
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
    item.setAgreedPriceVnd(3_000_000L);
    item.setPriceAgreedAt(NOW);
    item.setStatus(status);
    return item;
  }

  private static ConsignmentSale sampleSale(ConsignmentSaleStatus status) {
    ConsignmentSale s = new ConsignmentSale();
    ReflectionTestUtils.setField(s, "id", 600L);
    s.setSaleCode("CSL1234567890");
    s.setConsignmentListingId(500L);
    s.setFilmLabId(LAB_ID);
    s.setBuyerAccountId(BUYER_ID);
    s.setPaymentMethod(ConsignmentPaymentMethod.ONLINE);
    s.setHandoverMethod(ConsignmentHandoverMethod.PICKUP_AT_LAB);
    s.setStatus(status);
    s.setItemPriceVnd(3_000_000L);
    s.setShippingFeeVnd(0L);
    s.setTotalVnd(3_000_000L);
    s.setConsignmentFeeRate(new BigDecimal("10.00"));
    s.setConsignmentFeeVnd(300_000L);
    s.setCommissionRate(new BigDecimal("5.00"));
    s.setCommissionVnd(150_000L);
    s.setSellerPayoutVnd(2_550_000L);
    return s;
  }
}
