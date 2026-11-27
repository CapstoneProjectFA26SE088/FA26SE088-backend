package com.fa26se088.filmlink.service.order.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.account.response.UserAccountResponse;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.lab.response.FilmLabBriefResponse;
import com.fa26se088.filmlink.dto.lab.response.OrderingFilmLabResponse;
import com.fa26se088.filmlink.dto.order.request.RecordFilmRollDamageRequest;
import com.fa26se088.filmlink.dto.order.request.RecordProcessingStageRequest;
import com.fa26se088.filmlink.dto.order.request.RevertProcessingStageRequest;
import com.fa26se088.filmlink.dto.order.response.AdminOrderStageRevertResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderResponse;
import com.fa26se088.filmlink.dto.payment.ClosedOrderCommission;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.account.enums.UserAccountStatus;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
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
import com.fa26se088.filmlink.repository.order.ProcessingStageRecordRepository;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.service.account.UserAccountService;
import com.fa26se088.filmlink.service.archive.ArchiveService;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.delivery.FilmHandoverService;
import com.fa26se088.filmlink.service.lab.FilmLabService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.machine.CapacitySchedulingService;
import com.fa26se088.filmlink.service.machine.ScheduleChangeService;
import com.fa26se088.filmlink.service.masterdata.MasterdataService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.notification.event.NotificationEvent;
import com.fa26se088.filmlink.service.payment.CommissionService;
import com.fa26se088.filmlink.service.payment.PaymentService;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit test cho ORD-05 (UC24, BR-07, BR-26): kiểm tra không nhảy / lùi công đoạn phía Lab, Admin
 * lùi công đoạn ({@code is_admin_reversion = true}), ghi nhận cuộn hỏng kèm ảnh, miễn phí và tính
 * lại giá, hoàn tiền online dư và loại cuộn hỏng khỏi hoa hồng.
 */
@ExtendWith(MockitoExtension.class)
class Ord05ProcessingStageUnitTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-11-26T10:00:00Z");
  private static final Long LAB_ID = 10L;
  private static final Long OWNER_ID = 20L;
  private static final Long PHOTOGRAPHER_ID = 30L;
  private static final Long ADMIN_ID = 99L;
  private static final Long ORDER_ID = 500L;

  @Mock private ServiceOrderRepository serviceOrderRepository;
  @Mock private FilmRollRepository filmRollRepository;
  @Mock private FilmRollDamagePhotoRepository filmRollDamagePhotoRepository;
  @Mock private LabAccessService labAccessService;
  @Mock private FilmLabService filmLabService;
  @Mock private MasterdataService masterdataService;
  @Mock private SystemParameterService systemParameterService;
  @Mock private UserAccountService userAccountService;
  @Mock private AuditLogService auditLogService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private FilmHandoverService filmHandoverService;
  @Mock private ProcessingStageRecordRepository processingStageRecordRepository;
  @Mock private CapacitySchedulingService capacitySchedulingService;
  @Mock private ScheduleChangeService scheduleChangeService;
  @Mock private ArchiveService archiveService;
  @Mock private StorageClient storageClient;
  @Mock private PaymentService paymentService;
  @Mock private CommissionService commissionService;

  private final ServiceOrderMapper serviceOrderMapper = new ServiceOrderMapper();
  private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private LabServiceOrderServiceImpl labService;
  private AdminServiceOrderServiceImpl adminService;
  private ServiceOrderServiceImpl photographerService;

  @BeforeEach
  void setUp() {
    labService =
        new LabServiceOrderServiceImpl(
            serviceOrderRepository,
            filmRollRepository,
            filmRollDamagePhotoRepository,
            labAccessService,
            userAccountService,
            auditLogService,
            eventPublisher,
            serviceOrderMapper,
            filmHandoverService,
            processingStageRecordRepository,
            capacitySchedulingService,
            archiveService,
            storageClient,
            paymentService,
            clock);
    adminService =
        new AdminServiceOrderServiceImpl(
            serviceOrderRepository,
            processingStageRecordRepository,
            filmLabService,
            auditLogService,
            eventPublisher,
            serviceOrderMapper,
            clock);
    photographerService =
        new ServiceOrderServiceImpl(
            serviceOrderRepository,
            filmRollRepository,
            filmRollDamagePhotoRepository,
            filmLabService,
            masterdataService,
            systemParameterService,
            auditLogService,
            eventPublisher,
            serviceOrderMapper,
            scheduleChangeService,
            processingStageRecordRepository,
            filmHandoverService,
            archiveService,
            storageClient,
            commissionService,
            clock);
  }

  @Test
  @DisplayName("TC-ORD05-01 Lab skipping or reverting processing stage is rejected with 422")
  void tcOrd0501_labSkipOrRevertStage_rejected() {
    FilmLab lab = buildLab();
    ServiceOrder order = buildOrder(ServiceOrderStatus.IN_PROCESSING, PaymentMethod.PAY_AT_LAB);
    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);
    when(serviceOrderRepository.findByIdAndFilmLabIdForUpdate(ORDER_ID, LAB_ID))
        .thenReturn(Optional.of(order));

    // Đang ở RECEIVED -> nhảy sang WASHING bị chặn.
    when(processingStageRecordRepository.findFirstByServiceOrderIdOrderByRecordedAtDescIdDesc(
            ORDER_ID))
        .thenReturn(Optional.of(stageRecord(1L, ProcessingStage.RECEIVED, false)));

    assertThatThrownBy(
            () ->
                labService.recordStage(
                    OWNER_ID,
                    ORDER_ID,
                    new RecordProcessingStageRequest(ProcessingStage.WASHING, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ORDER_STAGE_OUT_OF_ORDER);
              assertThat(be.getProperties())
                  .containsEntry("currentStage", "RECEIVED")
                  .containsEntry("expectedStage", "DEVELOPING");
            });

    // Đang ở WASHING -> Lab tự lùi về DEVELOPING bị chặn.
    when(processingStageRecordRepository.findFirstByServiceOrderIdOrderByRecordedAtDescIdDesc(
            ORDER_ID))
        .thenReturn(Optional.of(stageRecord(2L, ProcessingStage.WASHING, false)));

    assertThatThrownBy(
            () ->
                labService.recordStage(
                    OWNER_ID,
                    ORDER_ID,
                    new RecordProcessingStageRequest(ProcessingStage.DEVELOPING, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ORDER_STAGE_OUT_OF_ORDER);
              assertThat(be.getProperties())
                  .containsEntry("currentStage", "WASHING")
                  .containsEntry("expectedStage", "DRYING");
            });

    verify(processingStageRecordRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("TC-ORD05-02 Admin reverts stage -> appends is_admin_reversion=true, audits and notifies")
  void tcOrd0502_adminRevertStage_succeeds() {
    ServiceOrder order = buildOrder(ServiceOrderStatus.IN_PROCESSING, PaymentMethod.PAY_AT_LAB);
    when(serviceOrderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
    ProcessingStageRecord r1 = stageRecord(1L, ProcessingStage.RECEIVED, false);
    ProcessingStageRecord r2 = stageRecord(2L, ProcessingStage.DEVELOPING, false);
    ProcessingStageRecord r3 = stageRecord(3L, ProcessingStage.WASHING, false);
    ProcessingStageRecord r4 = stageRecord(4L, ProcessingStage.DRYING, false);

    when(processingStageRecordRepository.findFirstByServiceOrderIdOrderByRecordedAtDescIdDesc(
            ORDER_ID))
        .thenReturn(Optional.of(r4));
    List<ProcessingStageRecord> timeline = new ArrayList<>(List.of(r1, r2, r3, r4));
    when(processingStageRecordRepository.save(any(ProcessingStageRecord.class)))
        .thenAnswer(
            invocation -> {
              ProcessingStageRecord saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", 5L);
              timeline.add(saved);
              return saved;
            });
    when(processingStageRecordRepository.findByServiceOrderIdOrderByRecordedAtAscIdAsc(ORDER_ID))
        .thenAnswer(invocation -> List.copyOf(timeline));
    when(filmLabService.getLabForOrdering(LAB_ID))
        .thenReturn(new OrderingFilmLabResponse(LAB_ID, OWNER_ID, "Lab Sài Gòn", true));

    AdminOrderStageRevertResponse response =
        adminService.revertStage(
            ADMIN_ID,
            ORDER_ID,
            new RevertProcessingStageRequest(
                ProcessingStage.DEVELOPING, "  Cần kiểm tra lại bước tráng  "));

    assertThat(response.orderId()).isEqualTo(ORDER_ID);
    assertThat(response.previousStage()).isEqualTo(ProcessingStage.DRYING);
    assertThat(response.currentStage()).isEqualTo(ProcessingStage.DEVELOPING);
    assertThat(response.stages()).hasSize(5);
    assertThat(response.stages().get(4).stage()).isEqualTo(ProcessingStage.DEVELOPING);
    assertThat(response.stages().get(4).adminReversion()).isTrue();
    assertThat(response.stages().get(4).note()).isEqualTo("Cần kiểm tra lại bước tráng");

    ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(auditCaptor.capture());
    assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.ORDER_STAGE_REVERTED);
    assertThat(auditCaptor.getValue().before()).containsEntry("stage", "DRYING");
    assertThat(auditCaptor.getValue().after())
        .containsEntry("stage", "DEVELOPING")
        .containsEntry("adminReversion", true);

    ArgumentCaptor<NotificationEvent> notifCaptor =
        ArgumentCaptor.forClass(NotificationEvent.class);
    verify(eventPublisher, times(2)).publishEvent(notifCaptor.capture());
    assertThat(notifCaptor.getAllValues())
        .extracting(NotificationEvent::accountId)
        .containsExactlyInAnyOrder(PHOTOGRAPHER_ID, OWNER_ID);
    assertThat(notifCaptor.getAllValues())
        .allSatisfy(n -> assertThat(n.type()).isEqualTo(NotificationType.ORDER_STATUS));
  }

  @Test
  @DisplayName("TC-ORD05-03 Admin cannot revert from RECEIVED or move stage forward/same")
  void tcOrd0503_adminInvalidRevert_rejected() {
    ServiceOrder order = buildOrder(ServiceOrderStatus.IN_PROCESSING, PaymentMethod.PAY_AT_LAB);
    when(serviceOrderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

    // Đang ở RECEIVED -> không còn bước trước để lùi.
    when(processingStageRecordRepository.findFirstByServiceOrderIdOrderByRecordedAtDescIdDesc(
            ORDER_ID))
        .thenReturn(Optional.of(stageRecord(1L, ProcessingStage.RECEIVED, false)));
    assertThatThrownBy(
            () ->
                adminService.revertStage(
                    ADMIN_ID,
                    ORDER_ID,
                    new RevertProcessingStageRequest(ProcessingStage.RECEIVED, "Lý do hợp lệ")))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.ORDER_STAGE_OUT_OF_ORDER);

    // Đang ở DEVELOPING -> gửi WASHING (tiến tới) bị chặn.
    when(processingStageRecordRepository.findFirstByServiceOrderIdOrderByRecordedAtDescIdDesc(
            ORDER_ID))
        .thenReturn(Optional.of(stageRecord(2L, ProcessingStage.DEVELOPING, false)));
    assertThatThrownBy(
            () ->
                adminService.revertStage(
                    ADMIN_ID,
                    ORDER_ID,
                    new RevertProcessingStageRequest(ProcessingStage.WASHING, "Lý do hợp lệ")))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.ORDER_STAGE_OUT_OF_ORDER);
  }

  @Test
  @DisplayName("TC-ORD05-04 BR-26 Lab records damaged roll with photos -> DAMAGED, free, recalculates finalTotalVnd")
  void tcOrd0504_recordRollDamage_payAtLab_recalculatesPrice() {
    FilmLab lab = buildLab();
    ServiceOrder order = buildOrder(ServiceOrderStatus.IN_PROCESSING, PaymentMethod.PAY_AT_LAB);
    order.setEstimatedTotalVnd(220_000L);
    order.setFinalTotalVnd(220_000L);
    order.setPriceLockedAt(FIXED_NOW.minusSeconds(600));

    FilmRoll roll1 = buildRoll(1001L, (short) 1, 101L, 80_000L, 30_000L, FilmRollStatus.RECEIVED);
    FilmRoll roll2 = buildRoll(1002L, (short) 2, 101L, 80_000L, 30_000L, FilmRollStatus.RECEIVED);

    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);
    when(serviceOrderRepository.findByIdAndFilmLabIdForUpdate(ORDER_ID, LAB_ID))
        .thenReturn(Optional.of(order));
    when(filmRollRepository.findByIdAndServiceOrderId(1001L, ORDER_ID))
        .thenReturn(Optional.of(roll1));
    when(filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(ORDER_ID))
        .thenReturn(List.of(roll1, roll2));
    when(serviceOrderRepository.saveAndFlush(any(ServiceOrder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(paymentService.netPaidForOrder(ORDER_ID)).thenReturn(0L);

    when(storageClient.upload(
            eq(StoragePurpose.DAMAGE_PHOTO), anyString(), any(InputStream.class), anyLong()))
        .thenReturn("damage-photo/2026/11/11111111-1111-1111-1111-111111111111.jpg")
        .thenReturn("damage-photo/2026/11/22222222-2222-2222-2222-222222222222.jpg");
    when(storageClient.signedUrl(anyString()))
        .thenAnswer(
            inv -> URI.create("https://storage.example.com/" + inv.getArgument(0, String.class)));

    List<FilmRollDamagePhoto> savedPhotos = new ArrayList<>();
    when(filmRollDamagePhotoRepository.saveAll(any()))
        .thenAnswer(
            inv -> {
              List<FilmRollDamagePhoto> input = inv.getArgument(0);
              long seq = 1L;
              for (FilmRollDamagePhoto p : input) {
                ReflectionTestUtils.setField(p, "id", seq++);
                ReflectionTestUtils.setField(p, "createdAt", FIXED_NOW);
                savedPhotos.add(p);
              }
              return input;
            });
    when(filmRollDamagePhotoRepository.findByFilmRollIdInOrderByFilmRollIdAscIdAsc(List.of(1001L)))
        .thenAnswer(inv -> List.copyOf(savedPhotos));
    stubToLabResponseDependencies();

    List<MultipartFile> photos =
        List.of(
            new MockMultipartFile("photos", "dmg1.jpg", "image/jpeg", new byte[] {1, 2, 3}),
            new MockMultipartFile("photos", "dmg2.jpg", "image/jpeg", new byte[] {4, 5, 6}));

    LabServiceOrderResponse response =
        labService.recordRollDamage(
            OWNER_ID,
            ORDER_ID,
            1001L,
            new RecordFilmRollDamageRequest("  Phim cháy sáng toàn bộ không lên hình  "),
            photos);

    assertThat(roll1.getStatus()).isEqualTo(FilmRollStatus.DAMAGED);
    assertThat(roll1.getDamageNote()).isEqualTo("Phim cháy sáng toàn bộ không lên hình");
    assertThat(roll1.getUnitPriceVnd()).isEqualTo(80_000L); // Snapshot giữ nguyên (ck_film_rolls_prices)
    assertThat(order.getFinalTotalVnd()).isEqualTo(110_000L);
    assertThat(response.finalTotalVnd()).isEqualTo(110_000L);
    assertThat(response.rolls().get(0).status()).isEqualTo(FilmRollStatus.DAMAGED);
    assertThat(response.rolls().get(0).damageNote())
        .isEqualTo("Phim cháy sáng toàn bộ không lên hình");
    assertThat(response.rolls().get(0).damagePhotos()).hasSize(2);

    verify(paymentService, never()).createOrderRefund(anyLong(), anyLong(), anyString());

    ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(auditCaptor.capture());
    assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.ORDER_ROLL_DAMAGED);
    assertThat(auditCaptor.getValue().before()).containsEntry("finalTotalVnd", 220_000L);
    assertThat(auditCaptor.getValue().after())
        .containsEntry("finalTotalVnd", 110_000L)
        .containsEntry("refundAmountVnd", 0L);

    ArgumentCaptor<NotificationEvent> notifCaptor =
        ArgumentCaptor.forClass(NotificationEvent.class);
    verify(eventPublisher).publishEvent(notifCaptor.capture());
    assertThat(notifCaptor.getValue().accountId()).isEqualTo(PHOTOGRAPHER_ID);
    assertThat(notifCaptor.getValue().body()).contains("110.000 ₫");
  }

  @Test
  @DisplayName("TC-ORD05-05 BR-26 Damaged roll on ONLINE paid order triggers automatic refund for excess paid")
  void tcOrd0505_recordRollDamage_onlinePaid_triggersRefund() {
    FilmLab lab = buildLab();
    ServiceOrder order = buildOrder(ServiceOrderStatus.IN_PROCESSING, PaymentMethod.ONLINE);
    order.setEstimatedTotalVnd(220_000L);
    order.setFinalTotalVnd(220_000L);
    order.setPriceLockedAt(FIXED_NOW.minusSeconds(600));

    FilmRoll roll1 = buildRoll(1001L, (short) 1, 101L, 80_000L, 30_000L, FilmRollStatus.RECEIVED);
    FilmRoll roll2 = buildRoll(1002L, (short) 2, 101L, 80_000L, 30_000L, FilmRollStatus.RECEIVED);

    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);
    when(serviceOrderRepository.findByIdAndFilmLabIdForUpdate(ORDER_ID, LAB_ID))
        .thenReturn(Optional.of(order));
    when(filmRollRepository.findByIdAndServiceOrderId(1001L, ORDER_ID))
        .thenReturn(Optional.of(roll1));
    when(filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(ORDER_ID))
        .thenReturn(List.of(roll1, roll2));
    when(serviceOrderRepository.saveAndFlush(any(ServiceOrder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(paymentService.netPaidForOrder(ORDER_ID)).thenReturn(220_000L);
    when(storageClient.upload(
            eq(StoragePurpose.DAMAGE_PHOTO), anyString(), any(InputStream.class), anyLong()))
        .thenReturn("damage-photo/2026/11/11111111-1111-1111-1111-111111111111.jpg");
    when(filmRollDamagePhotoRepository.findByFilmRollIdInOrderByFilmRollIdAscIdAsc(List.of(1001L)))
        .thenReturn(List.of());
    stubToLabResponseDependencies();

    labService.recordRollDamage(
        OWNER_ID,
        ORDER_ID,
        1001L,
        new RecordFilmRollDamageRequest("Phim tuột đầu không có ảnh"),
        List.of(new MockMultipartFile("photos", "dmg1.jpg", "image/jpeg", new byte[] {1, 2, 3})));

    verify(paymentService)
        .createOrderRefund(
            eq(ORDER_ID),
            eq(110_000L),
            org.mockito.ArgumentMatchers.contains("BR-26"));
  }

  @Test
  @DisplayName("TC-ORD05-06 BR-26 Missing damage photos or duplicate damage mark is rejected without uploading")
  void tcOrd0506_recordRollDamage_missingPhotosOrAlreadyDamaged_rejected() {
    FilmLab lab = buildLab();
    ServiceOrder order = buildOrder(ServiceOrderStatus.IN_PROCESSING, PaymentMethod.PAY_AT_LAB);
    FilmRoll receivedRoll =
        buildRoll(1001L, (short) 1, 101L, 80_000L, 30_000L, FilmRollStatus.RECEIVED);

    when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);
    when(serviceOrderRepository.findByIdAndFilmLabIdForUpdate(ORDER_ID, LAB_ID))
        .thenReturn(Optional.of(order));
    when(filmRollRepository.findByIdAndServiceOrderId(1001L, ORDER_ID))
        .thenReturn(Optional.of(receivedRoll));

    // Không gửi ảnh -> 400 VALIDATION_ERROR.
    assertThatThrownBy(
            () ->
                labService.recordRollDamage(
                    OWNER_ID,
                    ORDER_ID,
                    1001L,
                    new RecordFilmRollDamageRequest("Hỏng toàn bộ"),
                    List.of()))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);

    // Đã DAMAGED -> 409 ORDER_STATUS_CONFLICT.
    receivedRoll.setStatus(FilmRollStatus.DAMAGED);
    receivedRoll.setDamageNote("Đã hỏng");
    assertThatThrownBy(
            () ->
                labService.recordRollDamage(
                    OWNER_ID,
                    ORDER_ID,
                    1001L,
                    new RecordFilmRollDamageRequest("Ghi hỏng lần hai"),
                    List.of(
                        new MockMultipartFile(
                            "photos", "dmg1.jpg", "image/jpeg", new byte[] {1, 2}))))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.ORDER_STATUS_CONFLICT);

    verify(storageClient, never()).upload(any(), any(), any(), anyLong());
  }

  @Test
  @DisplayName("TC-ORD05-07 BR-26 Closing order excludes DAMAGED rolls from commission packages and value")
  void tcOrd0507_confirmReceipt_excludesDamagedRollFromCommission() {
    ServiceOrder order = buildOrder(ServiceOrderStatus.DELIVERED, PaymentMethod.PAY_AT_LAB);
    order.setEstimatedTotalVnd(220_000L);
    order.setFinalTotalVnd(110_000L);
    order.setDeliveredAt(FIXED_NOW.minusSeconds(300));

    FilmRoll damagedRoll =
        buildRoll(1001L, (short) 1, 202L, 80_000L, 30_000L, FilmRollStatus.DAMAGED);
    damagedRoll.setDamageNote("Loá sáng toàn bộ");
    FilmRoll goodRoll =
        buildRoll(1002L, (short) 2, 101L, 80_000L, 30_000L, FilmRollStatus.RECEIVED);

    when(serviceOrderRepository.findByIdAndPhotographerAccountIdForUpdate(
            ORDER_ID, PHOTOGRAPHER_ID))
        .thenReturn(Optional.of(order));
    when(serviceOrderRepository.saveAndFlush(any(ServiceOrder.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(filmRollRepository.findByServiceOrderIdOrderByRollSequenceAsc(ORDER_ID))
        .thenReturn(List.of(damagedRoll, goodRoll));
    when(filmLabService.getLabForOrdering(LAB_ID))
        .thenReturn(new OrderingFilmLabResponse(LAB_ID, OWNER_ID, "Lab Sài Gòn", true));
    when(filmLabService.getLabBriefs(List.of(LAB_ID)))
        .thenReturn(
            List.of(new FilmLabBriefResponse(LAB_ID, "Lab Sài Gòn", "123 Lê Lợi", "Q1", "TP.HCM")));
    when(processingStageRecordRepository.findByServiceOrderIdOrderByRecordedAtAscIdAsc(ORDER_ID))
        .thenReturn(List.of());
    when(filmHandoverService.getByServiceOrderId(ORDER_ID)).thenReturn(List.of());
    when(filmRollDamagePhotoRepository.findByFilmRollIdInOrderByFilmRollIdAscIdAsc(List.of(1001L)))
        .thenReturn(List.of());
    when(archiveService.getScanStatuses(List.of(1002L))).thenReturn(Map.of());

    photographerService.confirmReceipt(PHOTOGRAPHER_ID, ORDER_ID);

    ArgumentCaptor<ClosedOrderCommission> commissionCaptor =
        ArgumentCaptor.forClass(ClosedOrderCommission.class);
    verify(commissionService).recordForClosedOrder(commissionCaptor.capture());
    ClosedOrderCommission recorded = commissionCaptor.getValue();
    assertThat(recorded.serviceValueVnd()).isEqualTo(110_000L);
    assertThat(recorded.servicePackageIds()).containsExactly(101L);
  }

  private void stubToLabResponseDependencies() {
    when(userAccountService.getCurrentAccount(PHOTOGRAPHER_ID))
        .thenReturn(
            new UserAccountResponse(
                PHOTOGRAPHER_ID,
                "photo@example.com",
                "Nguyễn Nhiếp Ảnh",
                "0901234567",
                null,
                UserAccountRole.PHOTOGRAPHER,
                UserAccountStatus.ACTIVE,
                FIXED_NOW,
                FIXED_NOW));
    when(filmHandoverService.getByServiceOrderId(ORDER_ID)).thenReturn(List.of());
    when(processingStageRecordRepository.findByServiceOrderIdOrderByRecordedAtAscIdAsc(ORDER_ID))
        .thenReturn(List.of(stageRecord(1L, ProcessingStage.RECEIVED, false)));
    when(archiveService.getScanStatuses(any())).thenReturn(Map.of());
  }

  private static FilmLab buildLab() {
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", LAB_ID);
    lab.setOwnerAccountId(OWNER_ID);
    lab.setLabName("Lab Sài Gòn");
    lab.setAddressLine("123 Lê Lợi");
    lab.setDistrict("Quận 1");
    lab.setProvince("TP. Hồ Chí Minh");
    return lab;
  }

  private static ServiceOrder buildOrder(ServiceOrderStatus status, PaymentMethod paymentMethod) {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", ORDER_ID);
    ReflectionTestUtils.setField(order, "createdAt", FIXED_NOW.minusSeconds(3600));
    order.setOrderCode("SO261126ABCDEF");
    order.setPhotographerAccountId(PHOTOGRAPHER_ID);
    order.setFilmLabId(LAB_ID);
    order.setStatus(status);
    order.setServiceTier(ServiceTier.STANDARD);
    order.setFilmHandoverMethod(FilmHandoverMethod.SELF_DROP_OFF);
    order.setPaymentMethod(paymentMethod);
    order.setEstimatedTotalVnd(110_000L);
    order.setFinalTotalVnd(110_000L);
    order.setPriceLockedAt(FIXED_NOW.minusSeconds(1800));
    order.setExpectedReturnAt(FIXED_NOW.plusSeconds(86400));
    order.setPromisedReturnAt(FIXED_NOW.plusSeconds(86400));
    order.setMachineAssignmentConfirmedAt(FIXED_NOW.minusSeconds(1200));
    return order;
  }

  private static FilmRoll buildRoll(
      Long id,
      Short sequence,
      Long packageId,
      Long unitPrice,
      Long scanPrice,
      FilmRollStatus status) {
    FilmRoll roll = new FilmRoll();
    ReflectionTestUtils.setField(roll, "id", id);
    roll.setServiceOrderId(ORDER_ID);
    roll.setRollSequence(sequence);
    roll.setFilmStockNameText("Kodak Gold 200");
    roll.setFilmFormatId(1L);
    roll.setFilmProcessId(1L);
    roll.setServicePackageId(packageId);
    roll.setExposureCount((short) 36);
    roll.setPushPullStops((short) 0);
    roll.setUnitPriceVnd(unitPrice);
    roll.setScanOptionPriceVnd(scanPrice);
    roll.setPushPullSurchargeVnd(0L);
    roll.setExpressSurchargeVnd(0L);
    roll.setStatus(status);
    return roll;
  }

  private static ProcessingStageRecord stageRecord(
      Long id, ProcessingStage stage, boolean adminReversion) {
    ProcessingStageRecord record = new ProcessingStageRecord();
    ReflectionTestUtils.setField(record, "id", id);
    record.setServiceOrderId(ORDER_ID);
    record.setStage(stage);
    record.setAdminReversion(adminReversion);
    record.setRecordedByAccountId(adminReversion ? ADMIN_ID : OWNER_ID);
    record.setRecordedAt(FIXED_NOW);
    return record;
  }
}
