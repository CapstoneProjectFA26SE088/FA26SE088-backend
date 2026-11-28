package com.fa26se088.filmlink.controller.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.storage.FullResolutionLinkVerifier;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * SPEC-01 T-11b (UC60, BR-38) trên DB thật (schema test): Photographer huỷ đơn trước khi lab nhận
 * phim → {@code CANCELLED}, phí huỷ 0.
 *
 * <p>Đơn được dựng qua API: PAY_AT_LAB → {@code AWAITING_LAB_CONFIRMATION}, lab nhận đơn → {@code
 * AWAITING_FILM}; ONLINE → {@code AWAITING_PAYMENT}. Hoàn tiền (SPEC-00 §4.5) chưa kiểm vì contract
 * thanh toán chưa lên dev (TODO FA26-157).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CancelServiceOrderIntegrationTest {

  private static final String ORDERS_PATH = "/api/v1/orders";
  private static final String LAB_ORDERS_PATH = "/api/v1/lab/orders";
  private static final String REASON = "Tôi đổi ý, không tráng cuộn này nữa.";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageClient storageClient;

  @MockitoBean private NotificationService notificationService;

  // Không dùng trực tiếp: cùng bộ bean override với FilmReturnIntegrationTest để dùng lại
  // ApplicationContext đã cache (mỗi context mới mở thêm một connection pool tới PostgreSQL).
  @MockitoBean private FullResolutionLinkVerifier fullResolutionLinkVerifier;
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
    labId = fixture.createLab(owner, "Lab Cancel");
    labIds.add(labId);
    fixture.insertReadyMachines(labId);
    package135Id =
        fixture.insertPackage(labId, "C-41 35mm", "DEVELOPING", "C41", "135", 80_000, "ON_SALE");
    fixture.insertScanOption(package135Id, "M", 30_000);
    fixture.setAcceptingOrders(labId, true);
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
      jdbcTemplate.update("DELETE FROM payments WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM film_rolls WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", id);
    }
    labIds.clear();
    fixture.cleanUp();
  }

  // --- Must ---

  /**
   * TC-F16-01 (UC60, BR-38): đơn PAY_AT_LAB {@code AWAITING_FILM} (2 cuộn, 220.000); Photographer
   * huỷ kèm lý do → {@code CANCELLED}, {@code cancelled_at}, {@code cancelled_by_account_id},
   * {@code cancellation_reason} đúng, {@code cancellation_fee_vnd = 0}; chủ lab được báo; có audit.
   *
   * <p>Phần "refund 220,000" của spec sẽ kiểm khi contract thanh toán (SPEC-00 §4.5) lên dev (TODO
   * FA26-157).
   */
  @Test
  @DisplayName("TC-F16-01 UC60 cancel AWAITING_FILM order with reason -> CANCELLED, fee 0, lab notified")
  void tcF1601_cancelAwaitingFilm() throws Exception {
    long orderId = createPayAtLabOrder(2);
    accept(orderId).andExpect(status().isOk());
    clearInvocations(notificationService);

    cancel(photographer, orderId, REASON)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orderId))
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.cancelledAt", notNullValue()))
        .andExpect(jsonPath("$.cancellationReason").value(REASON))
        .andExpect(jsonPath("$.cancellationFeeVnd").value(0));

    Map<String, Object> order =
        jdbcTemplate.queryForMap(
            "SELECT status, cancelled_at, cancelled_by_account_id, cancellation_reason,"
                + " cancellation_fee_vnd FROM service_orders WHERE id = ?",
            orderId);
    assertThat(order.get("status")).isEqualTo("CANCELLED");
    assertThat(order.get("cancelled_at")).isNotNull();
    assertThat(((Number) order.get("cancelled_by_account_id")).longValue())
        .isEqualTo(photographer.getId());
    assertThat(order.get("cancellation_reason")).isEqualTo(REASON);
    assertThat(((Number) order.get("cancellation_fee_vnd")).longValue()).isZero();

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "SELECT before_data ->> 'status' AS before_status, after_data ->> 'status' AS"
                + " after_status, after_data ->> 'cancellationFeeVnd' AS fee,"
                + " after_data ->> 'reason' AS reason"
                + " FROM audit_logs WHERE action = 'ORDER_CANCELLED' AND target_id = ?"
                + " AND actor_account_id = ?",
            orderId,
            photographer.getId());
    assertThat(audit.get("before_status")).isEqualTo("AWAITING_FILM");
    assertThat(audit.get("after_status")).isEqualTo("CANCELLED");
    assertThat(audit.get("fee")).isEqualTo("0");
    assertThat(audit.get("reason")).isEqualTo(REASON);

    verify(notificationService)
        .notify(
            eq(owner.getId()),
            eq(NotificationType.ORDER_STATUS),
            eq("Đơn " + orderCode(orderId) + " đã bị huỷ"),
            contains(REASON),
            any());
  }

  /** TC-SM05-08 (UC60): Photographer huỷ trước khi lab nhận phim → {@code CANCELLED}. */
  @Test
  @DisplayName("TC-SM05-08 UC60 Photographer cancels before film receipt -> CANCELLED")
  void tcSm0508_cancelBeforeReceipt() throws Exception {
    long orderId = createPayAtLabOrder(1);
    accept(orderId).andExpect(status().isOk());

    cancel(photographer, orderId, REASON)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
  }

  // --- Thêm ---

  /** UC60: đơn ONLINE {@code AWAITING_PAYMENT} chưa tới lab → huỷ được, chủ lab không được báo. */
  @Test
  void cancelAwaitingPayment_cancelledWithoutNotifyingLab() throws Exception {
    long orderId = createOrder("ONLINE", 1);
    assertThat(statusOf(orderId)).isEqualTo("AWAITING_PAYMENT");
    clearInvocations(notificationService);

    cancel(photographer, orderId, REASON)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.cancellationFeeVnd").value(0));

    assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
    verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
  }

  /** UC60: đơn {@code AWAITING_LAB_CONFIRMATION} đã tới lab → huỷ được, chủ lab được báo. */
  @Test
  void cancelAwaitingLabConfirmation_cancelledAndLabNotified() throws Exception {
    long orderId = createPayAtLabOrder(1);
    clearInvocations(notificationService);

    cancel(photographer, orderId, REASON)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
    verify(notificationService)
        .notify(
            eq(owner.getId()),
            eq(NotificationType.ORDER_STATUS),
            eq("Đơn " + orderCode(orderId) + " đã bị huỷ"),
            contains(REASON),
            any());
  }

  /** UC60: lab đã nhận phim ({@code AWAITING_MACHINE_ASSIGNMENT}) → 409, đơn không đổi. */
  @Test
  void cancelAfterFilmReceived_returns409() throws Exception {
    long orderId = createPayAtLabOrder(1);
    accept(orderId).andExpect(status().isOk());
    receiveAll(orderId).andExpect(status().isOk());
    assertThat(statusOf(orderId)).isEqualTo("AWAITING_MACHINE_ASSIGNMENT");

    cancel(photographer, orderId, REASON)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));

    assertThat(statusOf(orderId)).isEqualTo("AWAITING_MACHINE_ASSIGNMENT");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT cancelled_at FROM service_orders WHERE id = ?", Object.class, orderId))
        .isNull();
  }

  /** UC60: huỷ lần hai → 409, giữ nguyên lần huỷ đầu. */
  @Test
  void cancelTwice_returns409() throws Exception {
    long orderId = createPayAtLabOrder(1);
    cancel(photographer, orderId, REASON).andExpect(status().isOk());
    Object cancelledAt =
        jdbcTemplate.queryForObject(
            "SELECT cancelled_at FROM service_orders WHERE id = ?", Object.class, orderId);

    cancel(photographer, orderId, "Một lý do khác để huỷ lần hai")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));

    Map<String, Object> order =
        jdbcTemplate.queryForMap(
            "SELECT cancelled_at, cancellation_reason FROM service_orders WHERE id = ?", orderId);
    assertThat(order.get("cancelled_at")).isEqualTo(cancelledAt);
    assertThat(order.get("cancellation_reason")).isEqualTo(REASON);
  }

  /** Lý do dưới 10 ký tự, toàn khoảng trắng hoặc thiếu → 400, đơn không đổi. */
  @Test
  void invalidReason_returns400() throws Exception {
    long orderId = createPayAtLabOrder(1);

    cancel(photographer, orderId, "ngắn").andExpect(status().isBadRequest());
    cancel(photographer, orderId, "  ngắn quá     ").andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post(ORDERS_PATH + "/" + orderId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    assertThat(statusOf(orderId)).isEqualTo("AWAITING_LAB_CONFIRMATION");
  }

  /** CR-14: đơn của Photographer khác → 404 {@code ORDER_NOT_FOUND}. */
  @Test
  void otherPhotographersOrder_returns404() throws Exception {
    long orderId = createPayAtLabOrder(1);
    UserAccount other = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);

    cancel(other, orderId, REASON)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));

    assertThat(statusOf(orderId)).isEqualTo("AWAITING_LAB_CONFIRMATION");
  }

  /** Film Lab Owner gọi endpoint của Photographer → 403. */
  @Test
  void labOwnerCancels_returns403() throws Exception {
    long orderId = createPayAtLabOrder(1);

    cancel(owner, orderId, REASON)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

    assertThat(statusOf(orderId)).isEqualTo("AWAITING_LAB_CONFIRMATION");
  }

  /** UC22: lab nhận đơn đã huỷ → 409 (kiểm trạng thái có sẵn). */
  @Test
  void labAcceptsCancelledOrder_returns409() throws Exception {
    long orderId = createPayAtLabOrder(1);
    cancel(photographer, orderId, REASON).andExpect(status().isOk());

    accept(orderId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));

    assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
  }

  /** UC23: lab xác nhận nhận phim cho đơn đã huỷ → 409, không có bàn giao. */
  @Test
  void labReceivesFilmOfCancelledOrder_returns409() throws Exception {
    long orderId = createPayAtLabOrder(1);
    accept(orderId).andExpect(status().isOk());
    cancel(photographer, orderId, REASON).andExpect(status().isOk());

    receiveAll(orderId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));

    assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM film_handovers WHERE service_order_id = ?",
                Integer.class,
                orderId))
        .isZero();
  }

  /** UC25: danh sách đơn của Photographer lọc {@code status=CANCELLED} có đơn vừa huỷ. */
  @Test
  void myOrdersFilteredByCancelled_containsCancelledOrder() throws Exception {
    long cancelledId = createPayAtLabOrder(1);
    long openId = createPayAtLabOrder(1);
    cancel(photographer, cancelledId, REASON).andExpect(status().isOk());

    String body =
        mockMvc
            .perform(
                get(ORDERS_PATH)
                    .param("status", "CANCELLED")
                    .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id", hasItem((int) cancelledId)))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    List<Object> ids = JsonPath.read(body, "$.content[*].id");
    assertThat(ids.stream().map(id -> ((Number) id).longValue())).doesNotContain(openId);
    List<String> statuses = JsonPath.read(body, "$.content[*].status");
    assertThat(statuses).containsOnly("CANCELLED");
  }

  // --- helpers ---

  private long createPayAtLabOrder(int rollCount) throws Exception {
    long orderId = createOrder("PAY_AT_LAB", rollCount);
    assertThat(statusOf(orderId)).isEqualTo("AWAITING_LAB_CONFIRMATION");
    return orderId;
  }

  /** Đơn SELF_DROP_OFF, Standard; mỗi cuộn 135 C-41, Kodak Gold 200, scan M. */
  private long createOrder(String paymentMethod, int rollCount) throws Exception {
    long scanOptionId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM service_package_scan_options WHERE service_package_id = ?",
            Long.class,
            package135Id);
    long goldId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM film_stocks WHERE manufacturer = 'Kodak' AND stock_name = 'Gold 200'",
            Long.class);
    List<String> rolls = new ArrayList<>();
    for (int i = 0; i < rollCount; i++) {
      rolls.add(
          """
          {"filmStockId":%d,"filmFormatId":%d,"exposureCount":36,
           "servicePackageId":%d,"servicePackageScanOptionId":%d,"pushPullStops":0}
          """
              .formatted(goldId, format135Id, package135Id, scanOptionId));
    }
    String body =
        """
        {"idempotencyKey":"%s","filmLabId":%d,"serviceTier":"STANDARD",
         "filmHandoverMethod":"SELF_DROP_OFF","paymentMethod":"%s","rolls":[%s]}
        """
            .formatted(UUID.randomUUID(), labId, paymentMethod, String.join(",", rolls));
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

  private ResultActions cancel(UserAccount account, long orderId, String reason) throws Exception {
    return mockMvc.perform(
        post(ORDERS_PATH + "/" + orderId + "/cancel")
            .header(HttpHeaders.AUTHORIZATION, bearer(account))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"" + reason + "\"}"));
  }

  private ResultActions accept(long orderId) throws Exception {
    return mockMvc.perform(
        post(LAB_ORDERS_PATH + "/" + orderId + "/accept")
            .header(HttpHeaders.AUTHORIZATION, bearer(owner)));
  }

  /** Lab nhận đủ mọi cuộn, 1 ảnh bằng chứng (BR-25). */
  private ResultActions receiveAll(long orderId) throws Exception {
    String items =
        String.join(
            ",",
            jdbcTemplate
                .queryForList(
                    "SELECT id FROM film_rolls WHERE service_order_id = ? ORDER BY roll_sequence",
                    Long.class,
                    orderId)
                .stream()
                .map(id -> "{\"filmRollId\":" + id + ",\"received\":true}")
                .toList());
    return mockMvc.perform(
        multipart(LAB_ORDERS_PATH + "/" + orderId + "/receipt")
            .file(
                new MockMultipartFile(
                    "data",
                    "",
                    MediaType.APPLICATION_JSON_VALUE,
                    ("{\"rolls\":[" + items + "]}").getBytes(StandardCharsets.UTF_8)))
            .file(new MockMultipartFile("photos", "r0.jpg", "image/jpeg", jpeg()))
            .header(HttpHeaders.AUTHORIZATION, bearer(owner)));
  }

  private String statusOf(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM service_orders WHERE id = ?", String.class, orderId);
  }

  private String orderCode(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT order_code FROM service_orders WHERE id = ?", String.class, orderId);
  }

  /** JPEG 8×8 sinh bằng {@link BufferedImage}. */
  private static byte[] jpeg() throws IOException {
    BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < 8; x++) {
      image.setRGB(x, 0, 0x336699);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", out);
    return out.toByteArray();
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }
}
