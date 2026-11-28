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
import com.fa26se088.filmlink.dto.lab.response.FilmLabProfileResponse;
import com.fa26se088.filmlink.dto.lab.response.OrderingFilmLabResponse;
import com.fa26se088.filmlink.dto.marketplace.request.ConditionCheckItemRequest;
import com.fa26se088.filmlink.dto.marketplace.request.CreateConsignmentBookingRequest;
import com.fa26se088.filmlink.dto.marketplace.request.CreateConsignmentItemRequest;
import com.fa26se088.filmlink.dto.marketplace.request.RecordConditionReportRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConditionReportResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentBookingResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.marketplace.ConditionReport;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentBooking;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentItem;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentLabSettings;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionCheckCode;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionCheckResult;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionConclusion;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionGrade;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentBookingStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemType;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.marketplace.ConsignmentMapper;
import com.fa26se088.filmlink.repository.marketplace.ConditionReportCheckRepository;
import com.fa26se088.filmlink.repository.marketplace.ConditionReportRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentBookingRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentItemPhotoRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentItemRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentLabSettingsRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.FilmLabService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.notification.NotificationService;
import java.net.URI;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ConsignmentInspectionServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-28T01:00:00Z");
  private static final Instant SLOT_10_00_VN = Instant.parse("2026-11-28T03:00:00Z");
  private static final long LAB_ID = 10L;
  private static final long OWNER_ID = 20L;
  private static final long SELLER_ID = 30L;

  @Mock private ConsignmentLabSettingsRepository settingsRepository;
  @Mock private ConsignmentBookingRepository bookingRepository;
  @Mock private ConsignmentItemRepository itemRepository;
  @Mock private ConsignmentItemPhotoRepository itemPhotoRepository;
  @Mock private ConditionReportRepository reportRepository;
  @Mock private ConditionReportCheckRepository reportCheckRepository;
  @Mock private FilmLabService filmLabService;
  @Mock private LabAccessService labAccessService;
  @Mock private SystemParameterService systemParameterService;
  @Mock private StorageClient storageClient;
  @Mock private AuditLogService auditLogService;
  @Mock private NotificationService notificationService;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private ConsignmentInspectionServiceImpl service;
  private AuthenticatedAccount ownerAuth;
  private AuthenticatedAccount sellerAuth;

  @BeforeEach
  void setUp() {
    service =
        new ConsignmentInspectionServiceImpl(
            settingsRepository,
            bookingRepository,
            itemRepository,
            itemPhotoRepository,
            reportRepository,
            reportCheckRepository,
            new ConsignmentMapper(storageClient),
            filmLabService,
            labAccessService,
            systemParameterService,
            storageClient,
            auditLogService,
            notificationService,
            clock);
    ownerAuth = new AuthenticatedAccount(OWNER_ID, "owner@test.com", UserAccountRole.FILM_LAB_OWNER);
    sellerAuth = new AuthenticatedAccount(SELLER_ID, "seller@test.com", UserAccountRole.PHOTOGRAPHER);
  }

  @Test
  @DisplayName("TC-SM13-01 UC45 booking created -> booking BOOKED, item AWAITING_DROP_OFF")
  void tcSm1301_createBooking_setsBookedAndAwaitingDropOff() {
    ConsignmentLabSettings settings = acceptingSettings((short) 30, (short) 1);
    when(filmLabService.getLabForOrdering(LAB_ID))
        .thenReturn(new OrderingFilmLabResponse(LAB_ID, OWNER_ID, "Lab B", true));
    when(filmLabService.getMyProfile(OWNER_ID)).thenReturn(emptyHoursProfile());
    when(settingsRepository.findByFilmLabIdForUpdate(LAB_ID)).thenReturn(Optional.of(settings));
    when(systemParameterService.getInteger(SystemParameterKey.CONSIGNMENT_MAX_PENDING_BOOKINGS))
        .thenReturn(5);
    when(bookingRepository.countBySellerAccountIdAndStatus(SELLER_ID, ConsignmentBookingStatus.BOOKED))
        .thenReturn(0L);
    when(bookingRepository.countByFilmLabIdAndSlotStartAtAndStatusIn(eq(LAB_ID), eq(SLOT_10_00_VN), any()))
        .thenReturn(0L);
    when(bookingRepository.save(any(ConsignmentBooking.class)))
        .thenAnswer(
            i -> {
              ConsignmentBooking b = i.getArgument(0);
              ReflectionTestUtils.setField(b, "id", 100L);
              return b;
            });
    when(itemRepository.save(any(ConsignmentItem.class)))
        .thenAnswer(
            i -> {
              ConsignmentItem item = i.getArgument(0);
              ReflectionTestUtils.setField(item, "id", 200L);
              return item;
            });

    CreateConsignmentBookingRequest req =
        new CreateConsignmentBookingRequest(
            LAB_ID,
            SLOT_10_00_VN,
            new CreateConsignmentItemRequest(
                ConsignmentItemType.CAMERA_BODY,
                "Nikon FM2 Chrome",
                "Máy cơ hoạt động hoàn hảo, đo sáng chuẩn, ngoại hình đẹp.",
                3_200_000L));

    ConsignmentBookingResponse res = service.createBooking(req, sellerAuth);

    assertThat(res.status()).isEqualTo(ConsignmentBookingStatus.BOOKED);
    assertThat(res.item().status()).isEqualTo(ConsignmentItemStatus.AWAITING_DROP_OFF);
  }

  @Test
  @DisplayName("TC-SM13-02 UC46 check-in -> booking CHECKED_IN, item AWAITING_INSPECTION")
  void tcSm1302_checkInBooking_setsCheckedInAndAwaitingInspection() {
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", LAB_ID);
    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);

    ConsignmentBooking booking = new ConsignmentBooking();
    ReflectionTestUtils.setField(booking, "id", 100L);
    booking.setBookingCode("CBK1234567890");
    booking.setFilmLabId(LAB_ID);
    booking.setSellerAccountId(SELLER_ID);
    booking.setStatus(ConsignmentBookingStatus.BOOKED);

    ConsignmentItem item = new ConsignmentItem();
    ReflectionTestUtils.setField(item, "id", 200L);
    item.setBooking(booking);
    item.setItemType(ConsignmentItemType.CAMERA_BODY);
    item.setItemName("Nikon FM2");
    item.setSellerDescription("Máy cơ hoạt động tốt, buồng phim sạch sẽ.");
    item.setStatus(ConsignmentItemStatus.AWAITING_DROP_OFF);

    when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
    when(itemRepository.findByBookingId(100L)).thenReturn(Optional.of(item));
    when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(itemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    ConsignmentBookingResponse res = service.checkInBooking(100L, ownerAuth);

    assertThat(res.status()).isEqualTo(ConsignmentBookingStatus.CHECKED_IN);
    assertThat(res.checkedInAt()).isEqualTo(NOW);
    assertThat(res.item().status()).isEqualTo(ConsignmentItemStatus.AWAITING_INSPECTION);
  }

  @Test
  @DisplayName(
      "TC-F20-01 lab checks in and records Condition Report grade B, 5 checklist lines, 4 photos -> ACCEPTED")
  void tcF2001_recordConditionReport_gradeB5Checks4Photos() {
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", LAB_ID);
    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);

    ConsignmentBooking booking = new ConsignmentBooking();
    ReflectionTestUtils.setField(booking, "id", 100L);
    booking.setFilmLabId(LAB_ID);
    booking.setSellerAccountId(SELLER_ID);
    booking.setStatus(ConsignmentBookingStatus.CHECKED_IN);

    ConsignmentItem item = new ConsignmentItem();
    ReflectionTestUtils.setField(item, "id", 200L);
    item.setBooking(booking);
    item.setItemType(ConsignmentItemType.CAMERA_BODY);
    item.setItemName("Nikon FM2");
    item.setSellerDescription("Máy cơ hoạt động tốt, đo sáng chuẩn xác.");
    item.setStatus(ConsignmentItemStatus.AWAITING_INSPECTION);

    when(itemRepository.findByIdWithBooking(200L)).thenReturn(Optional.of(item));
    when(reportRepository.findByConsignmentItemId(200L)).thenReturn(Optional.empty());
    when(reportRepository.save(any(ConditionReport.class)))
        .thenAnswer(
            i -> {
              ConditionReport r = i.getArgument(0);
              ReflectionTestUtils.setField(r, "id", 300L);
              return r;
            });
    when(reportCheckRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    when(itemPhotoRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    when(storageClient.upload(eq(StoragePurpose.CONSIGNMENT_PHOTO), anyString(), any(), anyLong()))
        .thenReturn("consignment-photo/2026/11/p.jpg");
    when(storageClient.signedUrl(anyString()))
        .thenReturn(URI.create("http://localhost:8080/files/p.jpg"));

    RecordConditionReportRequest req =
        new RecordConditionReportRequest(
            ConditionGrade.B,
            ConditionConclusion.ACCEPTED,
            null,
            null,
            null,
            null,
            List.of(
                new ConditionCheckItemRequest(ConditionCheckCode.SHUTTER, ConditionCheckResult.PASS, null),
                new ConditionCheckItemRequest(ConditionCheckCode.LIGHT_METERING, ConditionCheckResult.PASS, null),
                new ConditionCheckItemRequest(ConditionCheckCode.MIRROR, ConditionCheckResult.PASS, null),
                new ConditionCheckItemRequest(ConditionCheckCode.BATTERY_COMPARTMENT, ConditionCheckResult.PASS, null),
                new ConditionCheckItemRequest(ConditionCheckCode.LIGHT_SEALS, ConditionCheckResult.PASS, null)));

    List<MultipartFile> photos =
        List.of(photo("1.jpg"), photo("2.jpg"), photo("3.jpg"), photo("4.jpg"));

    ConditionReportResponse res = service.recordConditionReport(200L, req, photos, ownerAuth);

    assertThat(res.overallGrade()).isEqualTo(ConditionGrade.B);
    assertThat(res.conclusion()).isEqualTo(ConditionConclusion.ACCEPTED);
    assertThat(res.checks()).hasSize(5);
    assertThat(res.photos()).hasSize(4);
  }

  @Test
  @DisplayName("TC-F20-02 slot đầy (appointmentsPerSlot = 1) -> từ chối CONSIGNMENT_SLOT_FULL")
  void tcF2002_slotFull_rejected() {
    ConsignmentLabSettings settings = acceptingSettings((short) 30, (short) 1);
    when(filmLabService.getLabForOrdering(LAB_ID))
        .thenReturn(new OrderingFilmLabResponse(LAB_ID, OWNER_ID, "Lab B", true));
    when(filmLabService.getMyProfile(OWNER_ID)).thenReturn(emptyHoursProfile());
    when(settingsRepository.findByFilmLabIdForUpdate(LAB_ID)).thenReturn(Optional.of(settings));
    when(systemParameterService.getInteger(SystemParameterKey.CONSIGNMENT_MAX_PENDING_BOOKINGS))
        .thenReturn(5);
    when(bookingRepository.countBySellerAccountIdAndStatus(SELLER_ID, ConsignmentBookingStatus.BOOKED))
        .thenReturn(0L);
    when(bookingRepository.countByFilmLabIdAndSlotStartAtAndStatusIn(eq(LAB_ID), eq(SLOT_10_00_VN), any()))
        .thenReturn(1L);

    CreateConsignmentBookingRequest req =
        new CreateConsignmentBookingRequest(
            LAB_ID,
            SLOT_10_00_VN,
            new CreateConsignmentItemRequest(
                ConsignmentItemType.CAMERA_BODY,
                "Canon AE-1",
                "Máy phim hoạt động bình thường, cần kiểm định thêm.",
                2_500_000L));

    assertThatThrownBy(() -> service.createBooking(req, sellerAuth))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONSIGNMENT_SLOT_FULL));
  }

  @Test
  @DisplayName("TC-F20-03 gửi mã check của CAMERA_BODY cho LENS -> từ chối CONSIGNMENT_CHECK_CODE_INVALID")
  void tcF2003_cameraCheckCodeOnLens_rejected() {
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", LAB_ID);
    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);

    ConsignmentBooking booking = new ConsignmentBooking();
    booking.setFilmLabId(LAB_ID);
    ConsignmentItem lensItem = new ConsignmentItem();
    ReflectionTestUtils.setField(lensItem, "id", 201L);
    lensItem.setBooking(booking);
    lensItem.setItemType(ConsignmentItemType.LENS);
    lensItem.setStatus(ConsignmentItemStatus.AWAITING_INSPECTION);

    when(itemRepository.findByIdWithBooking(201L)).thenReturn(Optional.of(lensItem));
    when(reportRepository.findByConsignmentItemId(201L)).thenReturn(Optional.empty());

    RecordConditionReportRequest req =
        new RecordConditionReportRequest(
            ConditionGrade.A,
            ConditionConclusion.ACCEPTED,
            null,
            null,
            null,
            null,
            List.of(
                new ConditionCheckItemRequest(
                    ConditionCheckCode.SHUTTER, ConditionCheckResult.PASS, null)));
    List<MultipartFile> photos =
        List.of(photo("1.jpg"), photo("2.jpg"), photo("3.jpg"), photo("4.jpg"));

    assertThatThrownBy(() -> service.recordConditionReport(201L, req, photos, ownerAuth))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex ->
                assertThat(ex.getErrorCode())
                    .isEqualTo(ErrorCode.CONSIGNMENT_CHECK_CODE_INVALID));
  }

  private static ConsignmentLabSettings acceptingSettings(short slotMinutes, short capacity) {
    ConsignmentLabSettings s = new ConsignmentLabSettings();
    s.setFilmLabId(LAB_ID);
    s.setAcceptingConsignments(true);
    s.setSlotMinutes(slotMinutes);
    s.setAppointmentsPerSlot(capacity);
    return s;
  }

  private static FilmLabProfileResponse emptyHoursProfile() {
    return new FilmLabProfileResponse(
        LAB_ID,
        "Lab B",
        null,
        "12 Nguyễn Huệ",
        "Quận 1",
        "TP.HCM",
        null,
        null,
        "lab@test.com",
        "0901234567",
        true,
        (short) 2,
        null,
        List.of(),
        List.of(),
        NOW,
        NOW);
  }

  private static MockMultipartFile photo(String name) {
    return new MockMultipartFile("photos", name, "image/jpeg", new byte[] {1, 2, 3, 4});
  }
}
