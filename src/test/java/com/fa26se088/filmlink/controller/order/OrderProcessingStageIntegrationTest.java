package com.fa26se088.filmlink.controller.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.fa26se088.filmlink.service.lab.impl.FilmLabFixture;
import com.fa26se088.filmlink.service.machine.CapacitySchedulingService;
import com.fa26se088.filmlink.service.machine.ScheduleChangeService;
import com.fa26se088.filmlink.service.notification.NotificationService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SPEC-01 T-09 (UC24, UC25, BR-07) trên DB thật (schema test): lab ghi công đoạn xử lý,
 * Photographer xem danh sách và chi tiết đơn của mình kèm dòng thời gian.
 *
 * <p>Đơn được dựng qua API: Photographer tạo đơn PAY_AT_LAB, SELF_DROP_OFF → lab nhận đơn → lab xác
 * nhận nhận phim → lab xác nhận lịch ({@code IN_PROCESSING}, công đoạn {@code RECEIVED}). {@link
 * CapacitySchedulingService} là stub thật của domain machine; bean override giống {@code
 * LabOrderScheduleIntegrationTest} để dùng chung Spring context.
 *
 * <p>Dọn dữ liệu: {@code processing_stage_records}, {@code film_handovers}, {@code
 * film_handover_photos} là append-only; {@link #cleanUp()} tạm tắt trigger trong <b>cùng một
 * transaction</b> với câu DELETE.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderProcessingStageIntegrationTest {

  private static final String ORDERS_PATH = "/api/v1/orders";
  private static final String LAB_ORDERS_PATH = "/api/v1/lab/orders";
  private static final List<String> NEXT_STAGES =
      List.of("DEVELOPING", "WASHING", "DRYING", "SCANNING", "QC");

  /** BR-25: ghi chú tình trạng phim khi lab nhận. */
  private static final String RECEIPT_NOTE = "Cuộn 1 hơi móp vỏ";

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
    labId = createReadyLab(owner, "Lab Công Đoạn");
    package135Id = readyPackage(labId);
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
   * TC-F13-01 (UC24, UC25, BR-07): đơn {@code IN_PROCESSING}; lab ghi lần lượt DEVELOPING → WASHING
   * → DRYING → SCANNING → QC. Kỳ vọng: 6 dòng công đoạn (gồm RECEIVED); dòng thời gian của
   * Photographer đủ 6 bước có thời điểm; 5 thông báo; audit mỗi lần; trạng thái đơn không đổi.
   */
  @Test
  @DisplayName("TC-F13-01 UC24 lab advances all stages -> 6 records, photographer timeline, 5 notifications")
  void tcF1301_advanceAllStages() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    String orderCode = orderCode(orderId);
    clearInvocations(notificationService);

    for (int i = 0; i < NEXT_STAGES.size(); i++) {
      String stage = NEXT_STAGES.get(i);
      recordStage(owner, orderId, stage, "Ghi chú " + stage)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("IN_PROCESSING"))
          .andExpect(jsonPath("$.stages", hasSize(i + 2)))
          .andExpect(jsonPath("$.stages[" + (i + 1) + "].stage").value(stage));
    }

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT stage, recorded_by_account_id, is_admin_reversion, note"
                + " FROM processing_stage_records WHERE service_order_id = ? ORDER BY id",
            orderId);
    assertThat(rows)
        .extracting(row -> row.get("stage"))
        .containsExactly("RECEIVED", "DEVELOPING", "WASHING", "DRYING", "SCANNING", "QC");
    assertThat(rows)
        .allSatisfy(
            row -> {
              assertThat(((Number) row.get("recorded_by_account_id")).longValue())
                  .isEqualTo(owner.getId());
              assertThat(row.get("is_admin_reversion")).isEqualTo(false);
            });
    assertThat(rows.get(1).get("note")).isEqualTo("Ghi chú DEVELOPING");
    assertThat(statusOf(orderId)).isEqualTo("IN_PROCESSING");

    ResultActions detail =
        getOrder(photographer, orderId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stages", hasSize(6)));
    List<String> all = new ArrayList<>(List.of("RECEIVED"));
    all.addAll(NEXT_STAGES);
    for (int i = 0; i < all.size(); i++) {
      detail
          .andExpect(jsonPath("$.stages[" + i + "].stage").value(all.get(i)))
          .andExpect(jsonPath("$.stages[" + i + "].recordedAt", notNullValue()));
    }
    detail.andExpect(jsonPath("$.stages[5].note").value("Ghi chú QC"));

    ArgumentCaptor<String> titles = ArgumentCaptor.forClass(String.class);
    verify(notificationService, times(5))
        .notify(
            eq(photographer.getId()), eq(NotificationType.ORDER_STATUS), titles.capture(), any(), any());
    assertThat(titles.getAllValues())
        .allSatisfy(title -> assertThat(title).contains(orderCode))
        .containsExactly(
            "Đơn " + orderCode + " đang ở công đoạn Tráng phim",
            "Đơn " + orderCode + " đang ở công đoạn Rửa phim",
            "Đơn " + orderCode + " đang ở công đoạn Sấy phim",
            "Đơn " + orderCode + " đang ở công đoạn Scan",
            "Đơn " + orderCode + " đang ở công đoạn Kiểm tra chất lượng (QC)");

    List<Map<String, Object>> audits =
        jdbcTemplate.queryForList(
            "SELECT before_data ->> 'stage' AS before_stage, after_data ->> 'stage' AS after_stage"
                + " FROM audit_logs WHERE action = 'ORDER_STAGE_RECORDED'"
                + " AND target_type = 'SERVICE_ORDER' AND target_id = ? AND actor_account_id = ?"
                + " ORDER BY id",
            orderId,
            owner.getId());
    assertThat(audits).extracting(row -> row.get("after_stage")).containsExactlyElementsOf(NEXT_STAGES);
    assertThat(audits.get(0).get("before_stage")).isEqualTo("RECEIVED");

    // Đã tới QC: không còn công đoạn kế tiếp.
    recordStage(owner, orderId, "QC", null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STAGE_OUT_OF_ORDER"));
  }

  // --- Lab ghi công đoạn: lỗi ---

  @Test
  void recordStage_orderNotInProcessing_returns409() throws Exception {
    long orderId = createOrderAwaitingMachine(photographer, 1);

    recordStage(owner, orderId, "DEVELOPING", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"))
        .andExpect(jsonPath("$.detail", containsString("AWAITING_MACHINE_ASSIGNMENT")));

    assertThat(stageCount(orderId)).isZero();
  }

  @Test
  void recordStage_orderOfOtherLab_returns404() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    UserAccount otherOwner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    createReadyLab(otherOwner, "Lab Khác");

    recordStage(otherOwner, orderId, "DEVELOPING", null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));

    assertThat(stageCount(orderId)).isEqualTo(1);
  }

  @Test
  void recordStage_photographerGets403() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);

    recordStage(photographer, orderId, "DEVELOPING", null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

    assertThat(stageCount(orderId)).isEqualTo(1);
  }

  /** BR-07: lặp công đoạn hiện tại (kể cả RECEIVED đã ghi lúc xác nhận lịch) → 422. */
  @Test
  void recordStage_repeatCurrentStage_returns422() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    clearInvocations(notificationService);

    recordStage(owner, orderId, "RECEIVED", null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STAGE_OUT_OF_ORDER"))
        .andExpect(jsonPath("$.expectedStage").value("DEVELOPING"));
    recordStage(owner, orderId, "DEVELOPING", null).andExpect(status().isOk());

    recordStage(owner, orderId, "DEVELOPING", null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STAGE_OUT_OF_ORDER"))
        .andExpect(jsonPath("$.currentStage").value("DEVELOPING"))
        .andExpect(jsonPath("$.expectedStage").value("WASHING"))
        .andExpect(jsonPath("$.detail", containsString("WASHING")));

    assertThat(stageCount(orderId)).isEqualTo(2);
    verify(notificationService, times(1)).notify(anyLong(), any(), any(), any(), any());
  }

  @Test
  void recordStage_noteTooLongOrMissingStage_returns400() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);

    recordStage(owner, orderId, "DEVELOPING", "x".repeat(301))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    mockMvc
        .perform(
            post(LAB_ORDERS_PATH + "/" + orderId + "/stages")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"thiếu stage\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

    assertThat(stageCount(orderId)).isEqualTo(1);
    verify(notificationService, never())
        .notify(anyLong(), any(), org.mockito.ArgumentMatchers.contains("công đoạn"), any(), any());
  }

  // --- Photographer theo dõi đơn ---

  @Test
  void myOrders_onlyOwnOrders_filterByStatus_withLabNameAndRollCount() throws Exception {
    long inProcessing = createOrderInProcessing(photographer, 2);
    long waiting = createOrder(photographer, 1);
    UserAccount stranger = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);
    long strangerOrder = createOrder(stranger, 1);

    mockMvc
        .perform(get(ORDERS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) inProcessing, (int) waiting)))
        // Mới nhất trước: đơn chờ lab xác nhận được tạo sau.
        .andExpect(jsonPath("$.content[0].id").value(waiting))
        .andExpect(jsonPath("$.content[0].status").value("AWAITING_LAB_CONFIRMATION"))
        .andExpect(jsonPath("$.content[0].labName").value("Lab Công Đoạn"))
        .andExpect(jsonPath("$.content[0].rollCount").value(1))
        .andExpect(jsonPath("$.content[0].finalTotalVnd").doesNotExist())
        .andExpect(jsonPath("$.content[1].id").value(inProcessing))
        .andExpect(jsonPath("$.content[1].labName").value("Lab Công Đoạn"))
        .andExpect(jsonPath("$.content[1].rollCount").value(2))
        .andExpect(jsonPath("$.content[1].estimatedTotalVnd", notNullValue()))
        .andExpect(jsonPath("$.content[1].finalTotalVnd").value(220_000))
        .andExpect(jsonPath("$.content[1].promisedReturnAt", notNullValue()))
        .andExpect(jsonPath("$.content[1].expectedReturnAt", notNullValue()))
        .andExpect(jsonPath("$.content[1].createdAt", notNullValue()));

    mockMvc
        .perform(
            get(ORDERS_PATH)
                .param("status", "IN_PROCESSING")
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(inProcessing));

    mockMvc
        .perform(get(ORDERS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(strangerOrder));

    mockMvc
        .perform(
            get(ORDERS_PATH)
                .param("size", "500")
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100));
  }

  @Test
  void myOrder_ofAnotherPhotographer_returns404() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    UserAccount stranger = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);

    getOrder(stranger, orderId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
  }

  @Test
  void myOrder_labOwnerGets403() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);

    getOrder(owner, orderId)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    mockMvc
        .perform(get(ORDERS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden());
  }

  /** UC25: chi tiết đủ thông tin cho Photographer, không lộ dữ liệu nội bộ của lab. */
  @Test
  void myOrder_detailHasTrackingDataButNoLabInternals() throws Exception {
    long orderId = createOrderInProcessing(photographer, 2);
    recordStage(owner, orderId, "DEVELOPING", "Đang tráng").andExpect(status().isOk());

    String json =
        getOrder(photographer, orderId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(orderId))
            .andExpect(jsonPath("$.orderCode").value(orderCode(orderId)))
            .andExpect(jsonPath("$.status").value("IN_PROCESSING"))
            .andExpect(jsonPath("$.lab.id").value(labId))
            .andExpect(jsonPath("$.lab.labName").value("Lab Công Đoạn"))
            .andExpect(jsonPath("$.lab.addressLine", notNullValue()))
            .andExpect(jsonPath("$.promisedReturnAt", notNullValue()))
            .andExpect(jsonPath("$.finalTotalVnd").value(220_000))
            .andExpect(jsonPath("$.rolls", hasSize(2)))
            .andExpect(jsonPath("$.rolls[0].status").value("RECEIVED"))
            .andExpect(jsonPath("$.rolls[0].unitPriceVnd").value(80_000))
            .andExpect(jsonPath("$.rolls[0].scanOptionPriceVnd").value(30_000))
            .andExpect(jsonPath("$.stages", hasSize(2)))
            .andExpect(jsonPath("$.stages[1].stage").value("DEVELOPING"))
            .andExpect(jsonPath("$.stages[1].note").value("Đang tráng"))
            .andExpect(jsonPath("$.handovers", hasSize(1)))
            .andExpect(jsonPath("$.handovers[0].handoverPoint").value("RECEIVED_AT_LAB"))
            .andExpect(jsonPath("$.handovers[0].rollCount").value(2))
            .andExpect(jsonPath("$.handovers[0].note").value(RECEIPT_NOTE))
            .andExpect(jsonPath("$.handovers[0].occurredAt", notNullValue()))
            .andExpect(jsonPath("$.handovers[0].photos", hasSize(1)))
            .andExpect(jsonPath("$.handovers[0].photos[0].url", notNullValue()))
            .andExpect(jsonPath("$.payment").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(json)
        .doesNotContain("performedByAccountId")
        .doesNotContain("performedByName")
        .doesNotContain("recordedByAccountId")
        .doesNotContain("labResponseDueAt")
        .doesNotContain("machineAssignmentConfirmedAt")
        .doesNotContain("ownerAccountId")
        .doesNotContain("\"photographer\"")
        .doesNotContain(owner.getEmail());
  }

  // --- helpers ---

  /** Đơn {@code rollCount} cuộn C-41 35 mm của {@code account}, đã tới {@code IN_PROCESSING}. */
  private long createOrderInProcessing(UserAccount account, int rollCount) throws Exception {
    long orderId = createOrderAwaitingMachine(account, rollCount);
    mockMvc
        .perform(
            post(LAB_ORDERS_PATH + "/" + orderId + "/schedule-confirmation")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk());
    return orderId;
  }

  /** Đơn lab đã nhận và đã nhận đủ phim ({@code AWAITING_MACHINE_ASSIGNMENT}). */
  private long createOrderAwaitingMachine(UserAccount account, int rollCount) throws Exception {
    long orderId = createOrder(account, rollCount);
    mockMvc
        .perform(
            post(LAB_ORDERS_PATH + "/" + orderId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk());
    receiveAll(orderId);
    return orderId;
  }

  private long createReadyLab(UserAccount labOwner, String name) {
    long id = fixture.createLab(labOwner, name);
    labIds.add(id);
    fixture.insertReadyMachines(id);
    return id;
  }

  /** Gói C-41 35 mm 80.000 VND + scan option 30.000 VND, lab bật nhận đơn. */
  private long readyPackage(long filmLabId) {
    long id =
        fixture.insertPackage(
            filmLabId, "C-41 35mm", "DEVELOPING", "C41", "135", 80_000, "ON_SALE");
    fixture.insertScanOption(id, "M", 30_000);
    fixture.setAcceptingOrders(filmLabId, true);
    return id;
  }

  /** {@code account} tạo đơn PAY_AT_LAB, SELF_DROP_OFF qua API (UC20) với gói của lab chính. */
  private long createOrder(UserAccount account, int rollCount) throws Exception {
    long scanOptionId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM service_package_scan_options WHERE service_package_id = ?",
            Long.class,
            package135Id);
    List<String> rolls = new ArrayList<>();
    for (int i = 0; i < rollCount; i++) {
      rolls.add(
          """
          {"filmStockNameText":"Kodak Gold 200","filmFormatId":%d,"exposureCount":36,
           "servicePackageId":%d,"servicePackageScanOptionId":%d,"pushPullStops":0}
          """
              .formatted(format135Id, package135Id, scanOptionId));
    }
    String body =
        """
        {"idempotencyKey":"%s","filmLabId":%d,"serviceTier":"STANDARD",
         "filmHandoverMethod":"SELF_DROP_OFF","paymentMethod":"PAY_AT_LAB","rolls":[%s]}
        """
            .formatted(UUID.randomUUID(), labId, String.join(",", rolls));
    String response =
        mockMvc
            .perform(
                post(ORDERS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(account))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return ((Number) JsonPath.read(response, "$.id")).longValue();
  }

  /** UC23: lab nhận đủ mọi cuộn, 1 ảnh PNG, ghi chú {@link #RECEIPT_NOTE}. */
  private void receiveAll(long orderId) throws Exception {
    List<Long> ids =
        jdbcTemplate.queryForList(
            "SELECT id FROM film_rolls WHERE service_order_id = ? ORDER BY roll_sequence",
            Long.class,
            orderId);
    String items =
        String.join(
            ",", ids.stream().map(id -> "{\"filmRollId\":" + id + ",\"received\":true}").toList());
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
                        ("{\"rolls\":[" + items + "],\"note\":\"" + RECEIPT_NOTE + "\"}")
                            .getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("photos", "a.png", "image/png", png))
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk());
  }

  /** {@code note = null}: không gửi note. */
  private ResultActions recordStage(UserAccount account, long orderId, String stage, String note)
      throws Exception {
    String body =
        note == null
            ? "{\"stage\":\"" + stage + "\"}"
            : "{\"stage\":\"" + stage + "\",\"note\":\"" + note + "\"}";
    return mockMvc.perform(
        post(LAB_ORDERS_PATH + "/" + orderId + "/stages")
            .header(HttpHeaders.AUTHORIZATION, bearer(account))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions getOrder(UserAccount account, long orderId) throws Exception {
    return mockMvc.perform(
        get(ORDERS_PATH + "/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(account)));
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

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }
}
