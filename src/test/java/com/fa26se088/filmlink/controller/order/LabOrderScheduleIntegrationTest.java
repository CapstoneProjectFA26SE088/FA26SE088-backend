package com.fa26se088.filmlink.controller.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.machine.CapacityProposal;
import com.fa26se088.filmlink.dto.machine.CapacityRequest;
import com.fa26se088.filmlink.dto.machine.RollGroup;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.fa26se088.filmlink.service.lab.impl.FilmLabFixture;
import com.fa26se088.filmlink.service.machine.CapacitySchedulingService;
import com.fa26se088.filmlink.service.machine.ScheduleChangeService;
import com.fa26se088.filmlink.service.machine.event.PromisedDateChangedEvent;
import com.fa26se088.filmlink.service.notification.NotificationService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SPEC-01 T-08 (UC69, UC71, UC72) trên DB thật (schema test): lab xem / xác nhận ngày hẹn trả,
 * listener {@link PromisedDateChangedEvent}, Photographer trả lời thay đổi lịch.
 *
 * <p>Đơn được dựng qua API: Photographer tạo đơn PAY_AT_LAB, SELF_DROP_OFF → lab nhận đơn → lab xác
 * nhận nhận phim → {@code AWAITING_MACHINE_ASSIGNMENT}. {@link CapacitySchedulingService} và {@link
 * ScheduleChangeService} là <b>stub thật</b> của domain machine, bọc bằng {@link MockitoSpyBean}
 * để bắt tham số; riêng test {@code feasible = false} ghi đè {@code propose} bằng {@code doReturn}.
 * Stub trả ngày hẹn = {@code price_locked_at + 5 ngày}.
 *
 * <p>Dọn dữ liệu: {@code processing_stage_records}, {@code film_handovers}, {@code
 * film_handover_photos} là append-only; {@link #cleanUp()} tạm tắt trigger trong <b>cùng một
 * transaction</b> với câu DELETE (như {@code LabFilmReceiptIntegrationTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabOrderScheduleIntegrationTest {

  private static final String ORDERS_PATH = "/api/v1/orders";
  private static final String LAB_ORDERS_PATH = "/api/v1/lab/orders";
  private static final Duration STUB_TURNAROUND = Duration.ofDays(5);

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageClient storageClient;

  @MockitoBean private NotificationService notificationService;
  @MockitoSpyBean private CapacitySchedulingService capacitySchedulingService;
  @MockitoSpyBean private ScheduleChangeService scheduleChangeService;

  private FilmLabFixture fixture;
  private final List<Long> labIds = new ArrayList<>();
  private UserAccount owner;
  private UserAccount photographer;
  private long labId;
  private long package135Id;
  private long format135Id;

  @BeforeEach
  void setUp() {
    fixture =
        new FilmLabFixture(
            jdbcTemplate, userAccountRepository, eventPublisher, transactionTemplate, storageClient);
    owner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    photographer = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);
    format135Id = fixture.catalogId("film_formats", "135");
    labId = createReadyLab(owner, "Lab Xếp Lịch");
    package135Id = readyPackage(labId, "C-41 35mm", "C41", "135");
  }

  @AfterEach
  void cleanUp() {
    for (Long id : labIds) {
      String orderIds = "SELECT id FROM service_orders WHERE film_lab_id = " + id;
      jdbcTemplate
          .queryForList(
              "SELECT p.blob_key FROM film_handover_photos p"
                  + " JOIN film_handovers h ON h.id = p.film_handover_id"
                  + " WHERE h.service_order_id IN ("
                  + orderIds
                  + ")",
              String.class)
          .forEach(storageClient::delete);
      transactionTemplate.executeWithoutResult(
          tx -> {
            jdbcTemplate.execute(
                "ALTER TABLE processing_stage_records"
                    + " DISABLE TRIGGER processing_stage_records_append_only");
            jdbcTemplate.execute(
                "ALTER TABLE film_handover_photos DISABLE TRIGGER film_handover_photos_append_only");
            jdbcTemplate.execute(
                "ALTER TABLE film_handovers DISABLE TRIGGER film_handovers_append_only");
            jdbcTemplate.update(
                "DELETE FROM processing_stage_records WHERE service_order_id IN ("
                    + orderIds
                    + ")");
            jdbcTemplate.update(
                "DELETE FROM film_handover_photos WHERE film_handover_id IN"
                    + " (SELECT id FROM film_handovers WHERE service_order_id IN ("
                    + orderIds
                    + "))");
            jdbcTemplate.update(
                "DELETE FROM film_handovers WHERE service_order_id IN (" + orderIds + ")");
            jdbcTemplate.execute(
                "ALTER TABLE film_handovers ENABLE TRIGGER film_handovers_append_only");
            jdbcTemplate.execute(
                "ALTER TABLE film_handover_photos ENABLE TRIGGER film_handover_photos_append_only");
            jdbcTemplate.execute(
                "ALTER TABLE processing_stage_records"
                    + " ENABLE TRIGGER processing_stage_records_append_only");
          });
      jdbcTemplate.update("DELETE FROM film_rolls WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", id);
    }
    labIds.clear();
    fixture.cleanUp();
  }

  // --- Must ---

  /**
   * TC-SM05-09 (UC69): đơn {@code AWAITING_MACHINE_ASSIGNMENT}; lab xác nhận ngày hẹn trả → {@code
   * IN_PROCESSING}; có {@code promised_return_at}, {@code machine_assignment_confirmed_at}; một
   * dòng {@code processing_stage_records} {@code RECEIVED} do chủ lab ghi; Photographer được báo;
   * audit {@code ORDER_SCHEDULE_CONFIRMED}.
   */
  @Test
  @DisplayName("TC-SM05-09 UC69 lab confirms the promised date -> IN_PROCESSING, first stage RECEIVED")
  void tcSm0509_confirmScheduleMovesToInProcessing() throws Exception {
    long orderId = createOrderAwaitingMachine(1);
    Instant expected = priceLockedAt(orderId).plus(STUB_TURNAROUND);
    String orderCode = orderCode(orderId);
    clearInvocations(notificationService);

    confirm(owner, orderId, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROCESSING"))
        .andExpect(jsonPath("$.promisedReturnAt").value(expected.toString()))
        .andExpect(jsonPath("$.machineAssignmentConfirmedAt", notNullValue()))
        .andExpect(jsonPath("$.stages", hasSize(1)))
        .andExpect(jsonPath("$.stages[0].stage").value("RECEIVED"))
        .andExpect(jsonPath("$.stages[0].recordedAt", notNullValue()));

    Map<String, Object> order =
        jdbcTemplate.queryForMap(
            "SELECT status, promised_return_at, machine_assignment_confirmed_at"
                + " FROM service_orders WHERE id = ?",
            orderId);
    assertThat(order.get("status")).isEqualTo("IN_PROCESSING");
    assertThat(((Timestamp) order.get("promised_return_at")).toInstant()).isEqualTo(expected);
    assertThat(order.get("machine_assignment_confirmed_at")).isNotNull();

    List<Map<String, Object>> stages =
        jdbcTemplate.queryForList(
            "SELECT stage, recorded_by_account_id, is_admin_reversion FROM processing_stage_records"
                + " WHERE service_order_id = ?",
            orderId);
    assertThat(stages).hasSize(1);
    assertThat(stages.get(0).get("stage")).isEqualTo("RECEIVED");
    assertThat(((Number) stages.get(0).get("recorded_by_account_id")).longValue())
        .isEqualTo(owner.getId());
    assertThat(stages.get(0).get("is_admin_reversion")).isEqualTo(false);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService)
        .notify(
            eq(photographer.getId()),
            eq(NotificationType.ORDER_STATUS),
            contains(orderCode),
            body.capture(),
            any());
    assertThat(body.getValue()).contains("Ngày hẹn trả").contains(vietnamTime(expected));

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "SELECT before_data ->> 'status' AS before_status, after_data ->> 'status' AS after_status,"
                + " after_data ->> 'promisedReturnAt' AS promised"
                + " FROM audit_logs WHERE action = 'ORDER_SCHEDULE_CONFIRMED'"
                + " AND target_type = 'SERVICE_ORDER' AND target_id = ? AND actor_account_id = ?",
            orderId,
            owner.getId());
    assertThat(audit.get("before_status")).isEqualTo("AWAITING_MACHINE_ASSIGNMENT");
    assertThat(audit.get("after_status")).isEqualTo("IN_PROCESSING");
    assertThat(audit.get("promised")).isEqualTo(expected.toString());
  }

  // --- Đề xuất (GET schedule-proposal) ---

  @Test
  void proposal_returnsStubDateAndGroupsReceivedRolls() throws Exception {
    long packageE6Id = readyPackage(labId, "E-6 120", "E6", "120");
    long format120Id = fixture.catalogId("film_formats", "120");
    // Cuộn 1, 3: C-41 135 (36, 25 kiểu) → 1 nhóm, trung bình 30,5 → làm tròn lên 31.
    // Cuộn 2: E-6 120 (12 kiểu) nhận; cuộn 4: E-6 120 không nhận → bị loại.
    List<RollSpec> specs =
        List.of(
            new RollSpec(package135Id, format135Id, 36),
            new RollSpec(packageE6Id, format120Id, 12),
            new RollSpec(package135Id, format135Id, 25),
            new RollSpec(packageE6Id, format120Id, 10));
    long orderId = createOrder(specs);
    accept(owner, orderId);
    receive(owner, orderId, true, true, true, false);
    Instant expected = priceLockedAt(orderId).plus(STUB_TURNAROUND);
    clearInvocations(capacitySchedulingService);

    proposal(owner, orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feasible").value(true))
        .andExpect(jsonPath("$.promisedReturnAt").value(expected.toString()))
        .andExpect(jsonPath("$.tierCommitmentAt").value(expected.toString()))
        .andExpect(jsonPath("$.allocations", hasSize(0)));

    ArgumentCaptor<CapacityRequest> captor = ArgumentCaptor.forClass(CapacityRequest.class);
    verify(capacitySchedulingService).propose(captor.capture());
    CapacityRequest request = captor.getValue();
    assertThat(request.serviceOrderId()).isEqualTo(orderId);
    assertThat(request.filmLabId()).isEqualTo(labId);
    assertThat(request.tier()).isEqualTo(ServiceTier.STANDARD);
    assertThat(request.receivedAt()).isEqualTo(priceLockedAt(orderId));
    assertThat(request.groups())
        .containsExactly(
            new RollGroup(fixture.catalogId("film_processes", "C41"), format135Id, 2, 31),
            new RollGroup(fixture.catalogId("film_processes", "E6"), format120Id, 1, 12));
    // Chỉ đọc: không ghi gì.
    assertThat(statusOf(orderId)).isEqualTo("AWAITING_MACHINE_ASSIGNMENT");
    assertThat(stageCount(orderId)).isZero();
  }

  @Test
  void proposal_orderNotAwaitingMachine_returns409() throws Exception {
    long orderId = createOrder(List.of(new RollSpec(package135Id, format135Id, 36)));
    accept(owner, orderId);

    proposal(owner, orderId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"))
        .andExpect(jsonPath("$.detail", containsString("AWAITING_FILM")));
  }

  @Test
  void proposal_orderOfOtherLab_returns404() throws Exception {
    UserAccount otherOwner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    long otherLabId = createReadyLab(otherOwner, "Lab Khác");
    long otherPackage = readyPackage(otherLabId, "C-41 35mm", "C41", "135");
    long otherOrderId = createOrder(List.of(new RollSpec(otherPackage, format135Id, 36)));
    accept(otherOwner, otherOrderId);
    receive(otherOwner, otherOrderId, true);

    proposal(owner, otherOrderId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    confirm(owner, otherOrderId, null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));

    assertThat(statusOf(otherOrderId)).isEqualTo("AWAITING_MACHINE_ASSIGNMENT");
  }

  // --- Xác nhận (POST schedule-confirmation) ---

  @Test
  void confirm_laterDate_isKept() throws Exception {
    long orderId = createOrderAwaitingMachine(1);
    Instant later = priceLockedAt(orderId).plus(STUB_TURNAROUND).plus(2, ChronoUnit.DAYS);

    confirm(owner, orderId, "{\"promisedReturnAt\":\"" + later + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.promisedReturnAt").value(later.toString()));

    assertThat(promisedReturnAt(orderId)).isEqualTo(later);
  }

  @Test
  void confirm_emptyBodyObject_usesProposedDate() throws Exception {
    long orderId = createOrderAwaitingMachine(1);
    Instant expected = priceLockedAt(orderId).plus(STUB_TURNAROUND);

    confirm(owner, orderId, "{}").andExpect(status().isOk());

    assertThat(promisedReturnAt(orderId)).isEqualTo(expected);
  }

  /** BR-60: lab chỉ được dời ngày hẹn muộn hơn đề xuất. */
  @Test
  void confirm_dateEarlierThanProposal_returns422() throws Exception {
    long orderId = createOrderAwaitingMachine(1);
    Instant earlier = priceLockedAt(orderId).plus(STUB_TURNAROUND).minus(1, ChronoUnit.HOURS);
    clearInvocations(notificationService);

    confirm(owner, orderId, "{\"promisedReturnAt\":\"" + earlier + "\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_PROMISED_DATE_TOO_EARLY"))
        .andExpect(jsonPath("$.field").value("promisedReturnAt"));

    assertNotConfirmed(orderId);
    verify(capacitySchedulingService, never()).confirm(any(), any());
    verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
  }

  @Test
  void confirm_twice_returns409() throws Exception {
    long orderId = createOrderAwaitingMachine(1);
    confirm(owner, orderId, null).andExpect(status().isOk());

    confirm(owner, orderId, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"))
        .andExpect(jsonPath("$.detail", containsString("IN_PROCESSING")));

    assertThat(stageCount(orderId)).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'ORDER_SCHEDULE_CONFIRMED'"
                    + " AND target_id = ?",
                Integer.class,
                orderId))
        .isEqualTo(1);
  }

  /** UH-51: không xếp được lịch → 422; huỷ + hoàn tiền là Later (TC-SM05-10 không viết). */
  @Test
  void confirm_infeasible_returns422() throws Exception {
    long orderId = createOrderAwaitingMachine(1);
    doReturn(new CapacityProposal(false, "Không có máy E-6 đang hoạt động.", List.of(), null, null))
        .when(capacitySchedulingService)
        .propose(any());

    confirm(owner, orderId, null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_SCHEDULE_INFEASIBLE"))
        .andExpect(jsonPath("$.detail", containsString("Không có máy E-6")));

    assertNotConfirmed(orderId);
    verify(capacitySchedulingService, never()).confirm(any(), any());
  }

  @Test
  void confirm_photographerGets403() throws Exception {
    long orderId = createOrderAwaitingMachine(1);

    confirm(photographer, orderId, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

    assertNotConfirmed(orderId);
  }

  @Test
  void detail_showsPromisedDateAndStages() throws Exception {
    long orderId = createOrderAwaitingMachine(1);
    mockMvc
        .perform(get(LAB_ORDERS_PATH + "/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.promisedReturnAt").doesNotExist())
        .andExpect(jsonPath("$.stages", hasSize(0)));

    confirm(owner, orderId, null).andExpect(status().isOk());

    mockMvc
        .perform(get(LAB_ORDERS_PATH + "/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.promisedReturnAt", notNullValue()))
        .andExpect(jsonPath("$.stages[0].stage").value("RECEIVED"));
  }

  // --- Listener PromisedDateChangedEvent (UC71) ---

  @Test
  void promisedDateChanged_noResponseRequired_updatesDateAndNotifies() throws Exception {
    long orderId = createConfirmedOrder();
    Instant oldDate = promisedReturnAt(orderId);
    Instant newDate = oldDate.plus(1, ChronoUnit.DAYS);
    clearInvocations(notificationService);

    publishInTransaction(new PromisedDateChangedEvent(orderId, 99L, oldDate, newDate, false));

    assertThat(promisedReturnAt(orderId)).isEqualTo(newDate);
    assertThat(statusOf(orderId)).isEqualTo("IN_PROCESSING");
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService)
        .notify(
            eq(photographer.getId()),
            eq(NotificationType.ORDER_STATUS),
            contains(orderCode(orderId)),
            body.capture(),
            any());
    assertThat(body.getValue())
        .contains(vietnamTime(oldDate))
        .contains(vietnamTime(newDate))
        .doesNotContain("Chuyển lab");

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "SELECT actor_account_id, before_data ->> 'promisedReturnAt' AS before_date,"
                + " after_data ->> 'promisedReturnAt' AS after_date,"
                + " after_data ->> 'machineIncidentId' AS incident"
                + " FROM audit_logs WHERE action = 'ORDER_PROMISED_DATE_CHANGED' AND target_id = ?",
            orderId);
    assertThat(audit.get("actor_account_id")).isNull();
    assertThat(audit.get("before_date")).isEqualTo(oldDate.toString());
    assertThat(audit.get("after_date")).isEqualTo(newDate.toString());
    assertThat(audit.get("incident")).isEqualTo("99");
  }

  @Test
  void promisedDateChanged_responseRequired_asksForChoice() throws Exception {
    long orderId = createConfirmedOrder();
    Instant oldDate = promisedReturnAt(orderId);
    Instant newDate = oldDate.plus(4, ChronoUnit.DAYS);
    clearInvocations(notificationService);

    publishInTransaction(new PromisedDateChangedEvent(orderId, 99L, oldDate, newDate, true));

    assertThat(promisedReturnAt(orderId)).isEqualTo(newDate);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService)
        .notify(eq(photographer.getId()), eq(NotificationType.ORDER_STATUS), any(), body.capture(), any());
    assertThat(body.getValue()).contains("Chờ").contains("Huỷ đơn").contains("Chuyển lab");
  }

  @Test
  void promisedDateChanged_cancelledOrder_isIgnored() throws Exception {
    long orderId = createConfirmedOrder();
    Instant oldDate = promisedReturnAt(orderId);
    jdbcTemplate.update(
        "UPDATE service_orders SET status = 'CANCELLED', cancelled_at = now(),"
            + " cancellation_reason = 'Test huỷ' WHERE id = ?",
        orderId);
    clearInvocations(notificationService);

    publishInTransaction(
        new PromisedDateChangedEvent(orderId, 99L, oldDate, oldDate.plus(1, ChronoUnit.DAYS), true));

    assertThat(promisedReturnAt(orderId)).isEqualTo(oldDate);
    verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'ORDER_PROMISED_DATE_CHANGED'"
                    + " AND target_id = ?",
                Integer.class,
                orderId))
        .isZero();
  }

  @Test
  void promisedDateChanged_unknownOrder_isIgnored() {
    Instant now = Instant.now();
    publishInTransaction(
        new PromisedDateChangedEvent(Long.MAX_VALUE, 99L, now, now.plus(1, ChronoUnit.DAYS), false));

    verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
  }

  /** {@code MANDATORY}: listener chỉ chạy trong transaction của bên phát (domain machine). */
  @Test
  void promisedDateChanged_outsideTransaction_fails() throws Exception {
    long orderId = createConfirmedOrder();
    Instant oldDate = promisedReturnAt(orderId);

    assertThatThrownBy(
            () ->
                eventPublisher.publishEvent(
                    new PromisedDateChangedEvent(
                        orderId, 99L, oldDate, oldDate.plus(1, ChronoUnit.DAYS), false)))
        .isInstanceOf(IllegalTransactionStateException.class);

    assertThat(promisedReturnAt(orderId)).isEqualTo(oldDate);
  }

  // --- Photographer trả lời thay đổi lịch (UC72) ---

  @Test
  void answerWait_callsScheduleChangeService() throws Exception {
    long orderId = createConfirmedOrder();

    answer(photographer, orderId, 77L, "{\"choice\":\"WAIT\",\"note\":\"Tôi chờ được\"}")
        .andExpect(status().isNoContent());

    verify(scheduleChangeService).answer(77L, orderId, "WAIT", "Tôi chờ được");
  }

  @Test
  void answer_orderOfAnotherPhotographer_returns404() throws Exception {
    long orderId = createConfirmedOrder();
    UserAccount stranger = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);

    answer(stranger, orderId, 77L, "{\"choice\":\"WAIT\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));

    verify(scheduleChangeService, never()).answer(any(), any(), any(), any());
  }

  @Test
  void answerCancel_returns422() throws Exception {
    long orderId = createConfirmedOrder();

    answer(photographer, orderId, 77L, "{\"choice\":\"CANCEL\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_OPTION_NOT_SUPPORTED"));

    verify(scheduleChangeService, never()).answer(any(), any(), any(), any());
  }

  @Test
  void answer_invalidChoiceOrLongNote_returns400() throws Exception {
    long orderId = createConfirmedOrder();

    answer(photographer, orderId, 77L, "{\"choice\":\"LATER\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    answer(photographer, orderId, 77L, "{\"choice\":\"WAIT\",\"note\":\"" + "x".repeat(301) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

    verify(scheduleChangeService, never()).answer(any(), any(), any(), any());
  }

  // --- helpers ---

  private record RollSpec(long servicePackageId, long filmFormatId, int exposureCount) {}

  private void publishInTransaction(PromisedDateChangedEvent event) {
    transactionTemplate.executeWithoutResult(tx -> eventPublisher.publishEvent(event));
  }

  private void assertNotConfirmed(long orderId) {
    assertThat(statusOf(orderId)).isEqualTo("AWAITING_MACHINE_ASSIGNMENT");
    assertThat(promisedReturnAt(orderId)).isNull();
    assertThat(stageCount(orderId)).isZero();
  }

  /** Đơn 1 cuộn đã được lab xác nhận ngày hẹn ({@code IN_PROCESSING}). */
  private long createConfirmedOrder() throws Exception {
    long orderId = createOrderAwaitingMachine(1);
    confirm(owner, orderId, null).andExpect(status().isOk());
    return orderId;
  }

  /** Đơn {@code rollCount} cuộn C-41 35 mm, lab đã nhận đơn và nhận đủ phim. */
  private long createOrderAwaitingMachine(int rollCount) throws Exception {
    List<RollSpec> specs = new ArrayList<>();
    for (int i = 0; i < rollCount; i++) {
      specs.add(new RollSpec(package135Id, format135Id, 36));
    }
    long orderId = createOrder(specs);
    accept(owner, orderId);
    boolean[] received = new boolean[rollCount];
    java.util.Arrays.fill(received, true);
    receive(owner, orderId, received);
    return orderId;
  }

  private long createReadyLab(UserAccount labOwner, String name) {
    long id = fixture.createLab(labOwner, name);
    labIds.add(id);
    fixture.insertReadyMachines(id);
    return id;
  }

  /** Gói đang bán + scan option 30.000 VND, lab bật nhận đơn. */
  private long readyPackage(long filmLabId, String name, String processCode, String formatCode) {
    long id =
        fixture.insertPackage(
            filmLabId, name, "DEVELOPING", processCode, formatCode, 80_000, "ON_SALE");
    fixture.insertScanOption(id, "M", 30_000);
    fixture.setAcceptingOrders(filmLabId, true);
    return id;
  }

  private long scanOptionOf(long servicePackageId) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM service_package_scan_options WHERE service_package_id = ?",
        Long.class,
        servicePackageId);
  }

  /** Photographer tạo đơn PAY_AT_LAB, SELF_DROP_OFF qua API (UC20). Mọi gói phải cùng một lab. */
  private long createOrder(List<RollSpec> specs) throws Exception {
    long filmLabId =
        jdbcTemplate.queryForObject(
            "SELECT film_lab_id FROM service_packages WHERE id = ?",
            Long.class,
            specs.get(0).servicePackageId());
    List<String> rolls = new ArrayList<>();
    for (RollSpec spec : specs) {
      rolls.add(
          """
          {"filmStockNameText":"Kodak Gold 200","filmFormatId":%d,"exposureCount":%d,
           "servicePackageId":%d,"servicePackageScanOptionId":%d,"pushPullStops":0}
          """
              .formatted(
                  spec.filmFormatId(),
                  spec.exposureCount(),
                  spec.servicePackageId(),
                  scanOptionOf(spec.servicePackageId())));
    }
    String body =
        """
        {"idempotencyKey":"%s","filmLabId":%d,"serviceTier":"STANDARD",
         "filmHandoverMethod":"SELF_DROP_OFF","paymentMethod":"PAY_AT_LAB","rolls":[%s]}
        """
            .formatted(UUID.randomUUID(), filmLabId, String.join(",", rolls));
    String response =
        mockMvc
            .perform(
                post(ORDERS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(photographer))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return ((Number) JsonPath.read(response, "$.id")).longValue();
  }

  private void accept(UserAccount labOwner, long orderId) throws Exception {
    mockMvc
        .perform(
            post(LAB_ORDERS_PATH + "/" + orderId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, bearer(labOwner)))
        .andExpect(status().isOk());
  }

  /** UC23: lab xác nhận nhận phim; {@code received[i]} ứng với cuộn thứ i theo roll_sequence. */
  private void receive(UserAccount labOwner, long orderId, boolean... received) throws Exception {
    List<Long> ids =
        jdbcTemplate.queryForList(
            "SELECT id FROM film_rolls WHERE service_order_id = ? ORDER BY roll_sequence",
            Long.class,
            orderId);
    List<String> items = new ArrayList<>();
    for (int i = 0; i < ids.size(); i++) {
      items.add("{\"filmRollId\":" + ids.get(i) + ",\"received\":" + received[i] + "}");
    }
    byte[] png = new byte[256];
    byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    System.arraycopy(header, 0, png, 0, header.length);
    mockMvc
        .perform(
            multipart(LAB_ORDERS_PATH + "/" + orderId + "/receipt")
                .file(
                    new MockMultipartFile(
                        "data",
                        "",
                        MediaType.APPLICATION_JSON_VALUE,
                        ("{\"rolls\":[" + String.join(",", items) + "]}")
                            .getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("photos", "a.png", "image/png", png))
                .header(HttpHeaders.AUTHORIZATION, bearer(labOwner)))
        .andExpect(status().isOk());
  }

  private ResultActions proposal(UserAccount account, long orderId) throws Exception {
    return mockMvc.perform(
        get(LAB_ORDERS_PATH + "/" + orderId + "/schedule-proposal")
            .header(HttpHeaders.AUTHORIZATION, bearer(account)));
  }

  /** {@code body = null}: không gửi body. */
  private ResultActions confirm(UserAccount account, long orderId, String body) throws Exception {
    var request =
        post(LAB_ORDERS_PATH + "/" + orderId + "/schedule-confirmation")
            .header(HttpHeaders.AUTHORIZATION, bearer(account));
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(request);
  }

  private ResultActions answer(UserAccount account, long orderId, long changeId, String body)
      throws Exception {
    return mockMvc.perform(
        post(ORDERS_PATH + "/" + orderId + "/schedule-changes/" + changeId + "/answer")
            .header(HttpHeaders.AUTHORIZATION, bearer(account))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private Instant priceLockedAt(long orderId) {
    return jdbcTemplate
        .queryForObject(
            "SELECT price_locked_at FROM service_orders WHERE id = ?", Timestamp.class, orderId)
        .toInstant();
  }

  private Instant promisedReturnAt(long orderId) {
    Timestamp value =
        jdbcTemplate.queryForObject(
            "SELECT promised_return_at FROM service_orders WHERE id = ?", Timestamp.class, orderId);
    return value == null ? null : value.toInstant();
  }

  private String statusOf(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM service_orders WHERE id = ?", String.class, orderId);
  }

  private String orderCode(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT order_code FROM service_orders WHERE id = ?", String.class, orderId);
  }

  private Integer stageCount(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM processing_stage_records WHERE service_order_id = ?",
        Integer.class,
        orderId);
  }

  /** Giờ Việt Nam như trong thông báo ({@code HH:mm dd/MM/yyyy}, CR-04). */
  private static String vietnamTime(Instant instant) {
    return java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
        .format(instant.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }
}
