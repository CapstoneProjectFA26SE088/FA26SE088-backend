package com.fa26se088.filmlink.controller.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.fa26se088.filmlink.service.notification.NotificationService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code /api/v1/lab/orders} trên DB thật (schema test): Film Lab Owner xem và nhận
 * Service Order (UC22, F-09, SPEC-01 T-06). Đơn được tạo qua API {@code POST /api/v1/orders} của
 * Photographer (không insert tay), trên lab "sẵn sàng" giống {@link
 * ServiceOrderControllerIntegrationTest}.
 *
 * <p>{@link NotificationService} bị mock để kiểm việc báo Photographer mà không gửi email.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabServiceOrderControllerIntegrationTest {

  private static final String ORDERS_PATH = "/api/v1/orders";
  private static final String LAB_ORDERS_PATH = "/api/v1/lab/orders";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageClient storageClient;

  @MockitoBean private NotificationService notificationService;

  private FilmLabFixture fixture;
  private final List<Long> labIds = new ArrayList<>();
  private UserAccount owner;
  private UserAccount photographer;
  private long labId;
  private long packageId;
  private long format135Id;

  @BeforeEach
  void setUp() {
    fixture =
        new FilmLabFixture(
            jdbcTemplate, userAccountRepository, eventPublisher, transactionTemplate, storageClient);
    owner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    photographer = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);
    jdbcTemplate.update(
        "UPDATE user_accounts SET phone_number = '0909000111' WHERE id = ?", photographer.getId());
    labId = createReadyLab(owner, "Lab Nhận Đơn");
    packageId = readyPackage(labId);
    format135Id = fixture.catalogId("film_formats", "135");
  }

  @AfterEach
  void cleanUp() {
    for (Long id : labIds) {
      jdbcTemplate.update(
          "DELETE FROM film_rolls WHERE service_order_id IN"
              + " (SELECT id FROM service_orders WHERE film_lab_id = ?)",
          id);
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", id);
    }
    labIds.clear();
    fixture.cleanUp();
  }

  // --- Must ---

  /**
   * TC-F09-01 (Report 5): đơn PAY_AT_LAB, SELF_DROP_OFF đang {@code AWAITING_LAB_CONFIRMATION};
   * chủ lab nhận đơn → {@code AWAITING_FILM}, có {@code accepted_at}, Photographer được báo (kèm
   * địa chỉ lab, mã đơn), có dòng audit {@code ORDER_ACCEPTED}.
   */
  @Test
  @DisplayName("TC-F09-01 Lab owner accepts PAY_AT_LAB self drop-off order; photographer notified, audited")
  void tcF0901_acceptSelfDropOff() throws Exception {
    long orderId = createOrder(labId, packageId, "PAY_AT_LAB", "SELF_DROP_OFF", 2);
    Timestamp dueBefore = labResponseDueAt(orderId);
    String orderCode = orderCode(orderId);
    clearInvocations(notificationService);

    accept(owner, orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orderId))
        .andExpect(jsonPath("$.status").value("AWAITING_FILM"))
        .andExpect(jsonPath("$.acceptedAt", notNullValue()))
        .andExpect(jsonPath("$.rolls", hasSize(2)))
        .andExpect(jsonPath("$.photographer.fullName").value(photographer.getFullName()));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT status, accepted_at, lab_response_due_at FROM service_orders WHERE id = ?",
            orderId);
    assertThat(row.get("status")).isEqualTo("AWAITING_FILM");
    assertThat(row.get("accepted_at")).isNotNull();
    assertThat(row.get("lab_response_due_at")).isEqualTo(dueBefore);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService)
        .notify(
            eq(photographer.getId()),
            eq(NotificationType.ORDER_STATUS),
            contains(orderCode),
            body.capture(),
            any());
    assertThat(body.getValue())
        .contains("Lab Nhận Đơn")
        .contains(orderCode)
        .contains("mang phim tới lab tại");

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "SELECT before_data ->> 'status' AS before_status, after_data ->> 'status' AS after_status"
                + " FROM audit_logs WHERE action = 'ORDER_ACCEPTED' AND target_type = 'SERVICE_ORDER'"
                + " AND target_id = ? AND actor_account_id = ?",
            orderId,
            owner.getId());
    assertThat(audit.get("before_status")).isEqualTo("AWAITING_LAB_CONFIRMATION");
    assertThat(audit.get("after_status")).isEqualTo("AWAITING_FILM");
  }

  /** TC-SM05-05 (UC22): lab nhận đơn → {@code AWAITING_FILM}. */
  @Test
  @DisplayName("TC-SM05-05 UC22 lab accepts order -> AWAITING_FILM")
  void tcSm0505_acceptMovesToAwaitingFilm() throws Exception {
    long orderId = createOrder(labId, packageId, "PAY_AT_LAB", "SELF_DROP_OFF", 1);
    assertThat(statusOf(orderId)).isEqualTo("AWAITING_LAB_CONFIRMATION");

    accept(owner, orderId).andExpect(status().isOk());

    assertThat(statusOf(orderId)).isEqualTo("AWAITING_FILM");
  }

  // --- Rule nhận đơn ---

  @Test
  void acceptTwice_returns409() throws Exception {
    long orderId = createOrder(labId, packageId, "PAY_AT_LAB", "SELF_DROP_OFF", 1);
    accept(owner, orderId).andExpect(status().isOk());
    clearInvocations(notificationService);

    accept(owner, orderId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"))
        .andExpect(jsonPath("$.detail", containsString("AWAITING_FILM")));

    assertThat(acceptedAuditCount(orderId)).isEqualTo(1);
    verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
  }

  @Test
  void acceptAwaitingPayment_returns409() throws Exception {
    long orderId = createOrder(labId, packageId, "ONLINE", "SELF_DROP_OFF", 1);

    accept(owner, orderId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"))
        .andExpect(jsonPath("$.detail", containsString("AWAITING_PAYMENT")));

    assertThat(statusOf(orderId)).isEqualTo("AWAITING_PAYMENT");
  }

  /** Hai request nhận cùng lúc: khoá dòng đơn → đúng một 200, một 409, một dòng audit. */
  @Test
  void concurrentAccepts_onlyOneSucceeds() throws Exception {
    long orderId = createOrder(labId, packageId, "PAY_AT_LAB", "SELF_DROP_OFF", 1);
    CountDownLatch start = new CountDownLatch(1);
    Callable<Integer> task =
        () -> {
          start.await();
          return accept(owner, orderId).andReturn().getResponse().getStatus();
        };
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> first = executor.submit(task);
      Future<Integer> second = executor.submit(task);
      start.countDown();
      List<Integer> statuses =
          List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
      assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    } finally {
      executor.shutdownNow();
    }
    assertThat(statusOf(orderId)).isEqualTo("AWAITING_FILM");
    assertThat(acceptedAuditCount(orderId)).isEqualTo(1);
  }

  @Test
  void acceptOrderOfOtherLab_returns404() throws Exception {
    UserAccount otherOwner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    long otherLabId = createReadyLab(otherOwner, "Lab Khác");
    long otherOrderId =
        createOrder(otherLabId, readyPackage(otherLabId), "PAY_AT_LAB", "SELF_DROP_OFF", 1);

    accept(owner, otherOrderId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));

    assertThat(statusOf(otherOrderId)).isEqualTo("AWAITING_LAB_CONFIRMATION");
  }

  /** PICKUP nhận được; chưa tạo shipment (contract ShipmentService chưa có trên dev). */
  @Test
  void acceptPickupOrder_succeedsWithoutShipment() throws Exception {
    long orderId = createOrder(labId, packageId, "PAY_AT_LAB", "PICKUP", 1);
    clearInvocations(notificationService);

    accept(owner, orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_FILM"))
        .andExpect(jsonPath("$.filmHandoverMethod").value("PICKUP"))
        .andExpect(jsonPath("$.customerAddressLine").value("12 Lê Lợi"))
        .andExpect(jsonPath("$.pickupWindowStartAt").value("2026-12-01T02:00:00Z"));

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService)
        .notify(eq(photographer.getId()), eq(NotificationType.ORDER_STATUS), any(), body.capture(), any());
    // 02:00Z – 04:00Z theo giờ Việt Nam (GMT+7).
    assertThat(body.getValue()).contains("09:00 01/12/2026").contains("11:00 01/12/2026");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shipments WHERE service_order_id = ?", Integer.class, orderId))
        .isZero();
  }

  // --- Xem đơn ---

  @Test
  void listOnlyOwnLabOrders_andFilterByStatus() throws Exception {
    long pending = createOrder(labId, packageId, "PAY_AT_LAB", "SELF_DROP_OFF", 2);
    long awaitingPayment = createOrder(labId, packageId, "ONLINE", "SELF_DROP_OFF", 3);
    UserAccount otherOwner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    long otherLabId = createReadyLab(otherOwner, "Lab Khác");
    createOrder(otherLabId, readyPackage(otherLabId), "PAY_AT_LAB", "SELF_DROP_OFF", 1);

    mockMvc
        .perform(get(LAB_ORDERS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) pending, (int) awaitingPayment)))
        // Mới nhất trước.
        .andExpect(jsonPath("$.content[0].id").value(awaitingPayment))
        .andExpect(jsonPath("$.content[0].rollCount").value(3))
        .andExpect(jsonPath("$.content[1].rollCount").value(2))
        .andExpect(jsonPath("$.content[1].labResponseDueAt", notNullValue()));

    mockMvc
        .perform(
            get(LAB_ORDERS_PATH)
                .param("status", "AWAITING_LAB_CONFIRMATION")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(pending))
        .andExpect(jsonPath("$.content[0].status").value("AWAITING_LAB_CONFIRMATION"))
        .andExpect(jsonPath("$.content[0].paymentMethod").value("PAY_AT_LAB"));
  }

  @Test
  void listSizeAbove100_isCappedAt100() throws Exception {
    mockMvc
        .perform(
            get(LAB_ORDERS_PATH).param("size", "500").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100));
  }

  @Test
  void detail_includesRollsAndPhotographer() throws Exception {
    long orderId = createOrder(labId, packageId, "PAY_AT_LAB", "SELF_DROP_OFF", 2);

    mockMvc
        .perform(get(LAB_ORDERS_PATH + "/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_LAB_CONFIRMATION"))
        .andExpect(jsonPath("$.specialRequest").value("Scan ấm"))
        .andExpect(jsonPath("$.acceptedAt", nullValue()))
        .andExpect(jsonPath("$.rolls", hasSize(2)))
        .andExpect(jsonPath("$.rolls[0].unitPriceVnd").value(60_000))
        .andExpect(jsonPath("$.photographer.id").value(photographer.getId()))
        .andExpect(jsonPath("$.photographer.phoneNumber").value("0909000111"))
        .andExpect(jsonPath("$.photographer.email").doesNotExist());
  }

  @Test
  void detailOfOtherLabOrder_returns404() throws Exception {
    UserAccount otherOwner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    long otherLabId = createReadyLab(otherOwner, "Lab Khác");
    long otherOrderId =
        createOrder(otherLabId, readyPackage(otherLabId), "PAY_AT_LAB", "SELF_DROP_OFF", 1);

    mockMvc
        .perform(
            get(LAB_ORDERS_PATH + "/" + otherOrderId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
  }

  // --- Quyền ---

  @Test
  void photographerGets403() throws Exception {
    long orderId = createOrder(labId, packageId, "PAY_AT_LAB", "SELF_DROP_OFF", 1);

    mockMvc
        .perform(get(LAB_ORDERS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    accept(photographer, orderId)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

    assertThat(statusOf(orderId)).isEqualTo("AWAITING_LAB_CONFIRMATION");
  }

  // --- helpers ---

  private long createReadyLab(UserAccount labOwner, String name) {
    long id = fixture.createLab(labOwner, name);
    labIds.add(id);
    fixture.insertReadyMachines(id);
    return id;
  }

  /** Gói C-41 35 mm 60.000 VND đang bán, lab bật nhận đơn. */
  private long readyPackage(long filmLabId) {
    long id =
        fixture.insertPackage(filmLabId, "C-41 35mm", "DEVELOPING", "C41", "135", 60_000, "ON_SALE");
    fixture.setAcceptingOrders(filmLabId, true);
    return id;
  }

  /** Photographer tạo đơn qua API (UC20), {@code rollCount} cuộn Kodak Gold 35 mm không scan. */
  private long createOrder(
      long filmLabId, long servicePackageId, String paymentMethod, String handover, int rollCount)
      throws Exception {
    List<String> rolls = new ArrayList<>();
    for (int i = 0; i < rollCount; i++) {
      rolls.add(
          """
          {"filmStockNameText":"Kodak Gold 200","filmFormatId":%d,"exposureCount":36,
           "servicePackageId":%d,"pushPullStops":0}
          """
              .formatted(format135Id, servicePackageId));
    }
    String pickup =
        "PICKUP".equals(handover)
            ? """
              "customerAddressLine":"12 Lê Lợi","customerDistrict":"Quận 1",
              "customerProvince":"TP. Hồ Chí Minh","customerPhone":"0901234567",
              "pickupWindowStartAt":"2026-12-01T02:00:00Z","pickupWindowEndAt":"2026-12-01T04:00:00Z",
              """
            : "";
    String body =
        """
        {"idempotencyKey":"%s","filmLabId":%d,"serviceTier":"STANDARD","filmHandoverMethod":"%s",
         "paymentMethod":"%s","specialRequest":"Scan ấm",%s"rolls":[%s]}
        """
            .formatted(
                UUID.randomUUID(), filmLabId, handover, paymentMethod, pickup, String.join(",", rolls));
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

  private ResultActions accept(UserAccount account, long orderId) throws Exception {
    return mockMvc.perform(
        post(LAB_ORDERS_PATH + "/" + orderId + "/accept")
            .header(HttpHeaders.AUTHORIZATION, bearer(account)));
  }

  private String statusOf(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM service_orders WHERE id = ?", String.class, orderId);
  }

  private String orderCode(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT order_code FROM service_orders WHERE id = ?", String.class, orderId);
  }

  private Timestamp labResponseDueAt(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT lab_response_due_at FROM service_orders WHERE id = ?", Timestamp.class, orderId);
  }

  private Integer acceptedAuditCount(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM audit_logs WHERE action = 'ORDER_ACCEPTED'"
            + " AND target_type = 'SERVICE_ORDER' AND target_id = ?",
        Integer.class,
        orderId);
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }
}
