package com.fa26se088.filmlink.controller.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code /api/v1/orders} trên DB thật (schema test): Photographer báo giá và tạo Service Order
 * (UC20, F-08, SPEC-01 T-04). Lab "sẵn sàng" = đã duyệt, có PROCESSOR + SCANNER ACTIVE, gói C-41
 * 35 mm 60.000 VND / 5 ngày đang bán, có mức scan L 50.000 VND, và bật nhận đơn.
 *
 * <p>{@link NotificationService} bị mock để kiểm việc báo chủ lab mà không gửi email.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceOrderControllerIntegrationTest {

  private static final String PATH = "/api/v1/orders";
  private static final String QUOTE_PATH = PATH + "/quote";

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
  private long scanLId;
  private long format135Id;
  private long format120Id;

  @BeforeEach
  void setUp() {
    fixture =
        new FilmLabFixture(
            jdbcTemplate, userAccountRepository, eventPublisher, transactionTemplate, storageClient);
    owner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    photographer = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);
    labId = createReadyLab(owner, "Lab Đặt Đơn");
    packageId = fixture.insertPackage(labId, "C-41 35mm", "DEVELOPING", "C41", "135", 60_000, "ON_SALE");
    fixture.insertScanOption(packageId, "L", 50_000);
    scanLId = scanOptionId(packageId, "L");
    fixture.setAcceptingOrders(labId, true);
    format135Id = fixture.catalogId("film_formats", "135");
    format120Id = fixture.catalogId("film_formats", "120");
  }

  @AfterEach
  void cleanUp() {
    for (Long id : labIds) {
      String orderIds = "SELECT id FROM service_orders WHERE film_lab_id = " + id;
      jdbcTemplate.update("DELETE FROM payments WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM film_rolls WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", id);
    }
    labIds.clear();
    fixture.cleanUp();
  }

  // --- Must ---

  /**
   * TC-F08-01 (Report 5): lab A nhận đơn, gói 60.000 + scan L 50.000; báo giá rồi tạo đơn 2 cuộn,
   * Standard, ONLINE, tự mang phim → {@code AWAITING_PAYMENT}, tổng tạm tính 220.000, có ngày dự
   * kiến trả. Link thanh toán chưa kiểm (contract thanh toán chưa có trên dev).
   */
  @Test
  @DisplayName("TC-F08-01 Quote then create 2-roll Standard ONLINE self drop-off order; total 220,000")
  void tcF0801_quoteThenCreate() throws Exception {
    String body = orderJson(UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD", rolls(2));

    mockMvc
        .perform(post(QUOTE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(photographer))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolls", hasSize(2)))
        .andExpect(jsonPath("$.rolls[0].unitPriceVnd").value(60_000))
        .andExpect(jsonPath("$.rolls[0].scanOptionPriceVnd").value(50_000))
        .andExpect(jsonPath("$.rolls[0].rollPriceVnd").value(110_000))
        .andExpect(jsonPath("$.subtotalVnd").value(220_000))
        .andExpect(jsonPath("$.estimatedTotalVnd").value(220_000))
        .andExpect(jsonPath("$.expectedReturnAt", notNullValue()));

    createOrder(body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
        .andExpect(jsonPath("$.estimatedTotalVnd").value(220_000))
        .andExpect(jsonPath("$.expectedReturnAt", notNullValue()))
        .andExpect(jsonPath("$.orderCode", matchesPattern("^SO\\d{6}[0-9A-HJKMNP-TV-Z]{6}$")))
        .andExpect(jsonPath("$.rolls", hasSize(2)));
  }

  /** TC-SM05-01 (UC20): ONLINE → {@code AWAITING_PAYMENT}; cuộn DECLARED, audit, chưa báo lab. */
  @Test
  @DisplayName("TC-SM05-01 Create ONLINE order -> AWAITING_PAYMENT with side effects recorded")
  void tcSm0501_onlineAwaitingPayment() throws Exception {
    long orderId =
        idOf(
            createOrder(orderJson(UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD", rolls(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.labResponseDueAt", nullValue()))
                .andExpect(jsonPath("$.payment.paymentUrl", notNullValue()))
                .andExpect(jsonPath("$.payment.transactionCode", notNullValue())));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT status, lab_response_due_at, photographer_account_id, estimated_total_vnd"
                + " FROM service_orders WHERE id = ?",
            orderId);
    assertThat(row.get("status")).isEqualTo("AWAITING_PAYMENT");
    assertThat(row.get("lab_response_due_at")).isNull();
    assertThat(row.get("photographer_account_id")).isEqualTo(photographer.getId());
    assertThat(row.get("estimated_total_vnd")).isEqualTo(220_000L);
    assertThat(rollStatuses(orderId)).containsExactly("DECLARED", "DECLARED");
    assertThat(auditCount(orderId)).isEqualTo(1);
    verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
  }

  /**
   * TC-SM05-02 (UC20): PAY_AT_LAB → {@code AWAITING_LAB_CONFIRMATION}; {@code lab_response_due_at
   * = now + LAB_RESPONSE_LIMIT_HOURS}; audit; chủ lab được báo.
   */
  @Test
  @DisplayName("TC-SM05-02 Create PAY_AT_LAB order -> AWAITING_LAB_CONFIRMATION with side effects recorded")
  void tcSm0502_payAtLabAwaitingLabConfirmation() throws Exception {
    long orderId =
        idOf(
            createOrder(
                    orderJson(UUID.randomUUID(), "PAY_AT_LAB", "SELF_DROP_OFF", "STANDARD", rolls(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_LAB_CONFIRMATION"))
                .andExpect(jsonPath("$.labResponseDueAt", notNullValue())));

    Integer limitHours =
        jdbcTemplate.queryForObject(
            "SELECT CAST(param_value AS integer) FROM system_parameters"
                + " WHERE param_key = 'LAB_RESPONSE_LIMIT_HOURS'",
            Integer.class);
    Double dueAfterCreatedSeconds =
        jdbcTemplate.queryForObject(
            "SELECT EXTRACT(EPOCH FROM (lab_response_due_at - created_at))::float8"
                + " FROM service_orders WHERE id = ?",
            Double.class,
            orderId);
    assertThat(dueAfterCreatedSeconds).isCloseTo(limitHours * 3600.0, offset(5.0));
    assertThat(auditCount(orderId)).isEqualTo(1);
    verify(notificationService)
        .notify(eq(owner.getId()), eq(NotificationType.ORDER_STATUS), contains("SO"), any(), any());
  }

  // --- Rule ---

  @Test
  void quote_writesNothing() throws Exception {
    mockMvc
        .perform(post(QUOTE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(photographer))
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderJson(UUID.randomUUID(), "PAY_AT_LAB", "SELF_DROP_OFF", "STANDARD", rolls(3))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimatedTotalVnd").value(330_000));

    assertThat(orderCountOfLab()).isZero();
    verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
  }

  /** NFR-REL-02: gửi lại cùng idempotencyKey → 200, đúng đơn cũ, không tạo thêm. */
  @Test
  void sameIdempotencyKey_returnsExistingOrder() throws Exception {
    String body = orderJson(UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD", rolls(2));
    long firstId = idOf(createOrder(body).andExpect(status().isCreated()));

    createOrder(body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstId))
        .andExpect(jsonPath("$.rolls", hasSize(2)));

    assertThat(orderCountOfLab()).isEqualTo(1);
  }

  @Test
  void createWithoutIdempotencyKey_returns400() throws Exception {
    createOrder(orderJson(null, "ONLINE", "SELF_DROP_OFF", "STANDARD", rolls(1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("idempotencyKey"));
  }

  @Test
  void twentyOneRolls_returns400() throws Exception {
    createOrder(orderJson(UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD", rolls(21)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("rolls"));
  }

  @Test
  void labNotAccepting_returns422() throws Exception {
    fixture.setAcceptingOrders(labId, false);

    createOrder(orderJson(UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD", rolls(1)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_LAB_NOT_ACCEPTING"));
    assertThat(orderCountOfLab()).isZero();
  }

  /** BR-57: cờ nhận đơn bật nhưng máy không còn ACTIVE → lab không nhận đơn. */
  @Test
  void labWithoutReadyMachines_returns422() throws Exception {
    jdbcTemplate.update("UPDATE machines SET status = 'MAINTENANCE' WHERE film_lab_id = ?", labId);

    createOrder(orderJson(UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD", rolls(1)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_LAB_NOT_ACCEPTING"));
  }

  @Test
  void packageOfAnotherLab_returns422() throws Exception {
    UserAccount ownerB = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    long labB = createReadyLab(ownerB, "Lab Khác");
    long packageOfB =
        fixture.insertPackage(labB, "Gói Lab B", "DEVELOPING", "C41", "135", 40_000, "ON_SALE");

    createOrder(
            orderJson(
                UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD",
                "[" + rollJson(packageOfB, null, format135Id, 0) + "]"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_PACKAGE_NOT_ORDERABLE"))
        .andExpect(jsonPath("$.errors[0].field").value("rolls[0].servicePackageId"));
  }

  @Test
  void withdrawnPackage_returns422() throws Exception {
    long withdrawn =
        fixture.insertPackage(labId, "Gói Ngừng", "DEVELOPING", "C41", "135", 40_000, "WITHDRAWN");

    createOrder(
            orderJson(
                UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD",
                "[" + rollJson(withdrawn, null, format135Id, 0) + "]"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_PACKAGE_NOT_ORDERABLE"));
  }

  @Test
  void rollFormatDiffersFromPackage_returns422() throws Exception {
    createOrder(
            orderJson(
                UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD",
                "[" + rollJson(packageId, null, format120Id, 0) + "]"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_FILM_FORMAT_MISMATCH"))
        .andExpect(jsonPath("$.errors[0].field").value("rolls[0].filmFormatId"));
  }

  @Test
  void scanOptionOfAnotherPackage_returns422() throws Exception {
    long other =
        fixture.insertPackage(labId, "Gói Khác", "DEVELOPING", "C41", "135", 40_000, "ON_SALE");

    createOrder(
            orderJson(
                UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD",
                "[" + rollJson(other, scanLId, format135Id, 0) + "]"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_SCAN_OPTION_INVALID"));
  }

  @Test
  void pushPullOnPackageWithoutPushPull_returns422() throws Exception {
    createOrder(
            orderJson(
                UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD",
                "[" + rollJson(packageId, null, format135Id, 1) + "]"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_PUSH_PULL_NOT_AVAILABLE"));
  }

  @Test
  void bothFilmStockIdAndText_returns400() throws Exception {
    long stockId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM film_stocks WHERE status = 'ACTIVE' ORDER BY id LIMIT 1", Long.class);
    String roll =
        """
        {"filmStockId":%d,"filmStockNameText":"Kodak Gold 200","filmFormatId":%d,
         "exposureCount":36,"servicePackageId":%d}
        """
            .formatted(stockId, format135Id, packageId);

    createOrder(orderJson(UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD", "[" + roll + "]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("rolls[0].filmStockId"));
  }

  @Test
  void pickupWithoutAddress_returns400() throws Exception {
    createOrder(orderJson(UUID.randomUUID(), "ONLINE", "PICKUP", "STANDARD", rolls(1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("customerAddressLine"));
  }

  /** PICKUP đủ thông tin được nhận; phí giao phim = 0 ở giai đoạn này. */
  @Test
  void pickupWithAddress_isCreated() throws Exception {
    String body =
        """
        {"idempotencyKey":"%s","filmLabId":%d,"serviceTier":"STANDARD",
         "filmHandoverMethod":"PICKUP","paymentMethod":"ONLINE",
         "customerAddressLine":"12 Lê Lợi","customerDistrict":"Quận 1",
         "customerProvince":"TP. Hồ Chí Minh","customerPhone":"0901234567",
         "pickupWindowStartAt":"2026-12-01T02:00:00Z","pickupWindowEndAt":"2026-12-01T04:00:00Z",
         "rolls":%s}
        """
            .formatted(UUID.randomUUID(), labId, rolls(1));

    createOrder(body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.filmHandoverMethod").value("PICKUP"))
        .andExpect(jsonPath("$.estimatedTotalVnd").value(110_000))
        .andExpect(jsonPath("$.pickupWindowEndAt").value("2026-12-01T04:00:00Z"));
  }

  @Test
  void codExpressParcel_return422NotSupported() throws Exception {
    createOrder(orderJson(UUID.randomUUID(), "COD", "SELF_DROP_OFF", "STANDARD", rolls(1)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_OPTION_NOT_SUPPORTED"))
        .andExpect(jsonPath("$.errors[0].field").value("paymentMethod"));
    createOrder(orderJson(UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "EXPRESS", rolls(1)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_OPTION_NOT_SUPPORTED"))
        .andExpect(jsonPath("$.errors[0].field").value("serviceTier"));
    createOrder(orderJson(UUID.randomUUID(), "ONLINE", "PARCEL_SHIPPING", "STANDARD", rolls(1)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_OPTION_NOT_SUPPORTED"))
        .andExpect(jsonPath("$.errors[0].field").value("filmHandoverMethod"));

    assertThat(orderCountOfLab()).isZero();
  }

  /**
   * Report 4 §3.5: giá được chụp vào {@code film_rolls}; push +2 stop × 15.000. Lab đổi giá gói
   * sau đó không làm đổi cuộn đã tạo.
   */
  @Test
  void pricesAreSnapshottedIntoFilmRolls() throws Exception {
    jdbcTemplate.update(
        "UPDATE service_packages SET is_push_pull_available = true,"
            + " push_pull_surcharge_per_stop_vnd = 15000 WHERE id = ?",
        packageId);
    long stockId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM film_stocks WHERE status = 'ACTIVE' ORDER BY id LIMIT 1", Long.class);
    String roll =
        """
        {"filmStockId":%d,"filmFormatId":%d,"exposureCount":36,"shootingIso":800,
         "servicePackageId":%d,"servicePackageScanOptionId":%d,"pushPullStops":2}
        """
            .formatted(stockId, format135Id, packageId, scanLId);

    long orderId =
        idOf(
            createOrder(
                    orderJson(UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD", "[" + roll + "]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedTotalVnd").value(140_000)));
    jdbcTemplate.update("UPDATE service_packages SET price_vnd = 99000 WHERE id = ?", packageId);

    Map<String, Object> saved =
        jdbcTemplate.queryForMap(
            "SELECT roll_sequence, film_stock_id, film_stock_name_text, film_process_id,"
                + " service_package_scan_option_id, push_pull_stops, unit_price_vnd,"
                + " scan_option_price_vnd, push_pull_surcharge_vnd, express_surcharge_vnd, status"
                + " FROM film_rolls WHERE service_order_id = ?",
            orderId);
    assertThat(saved.get("roll_sequence")).isEqualTo(1);
    assertThat(saved.get("film_stock_id")).isEqualTo(stockId);
    assertThat(saved.get("film_stock_name_text")).isNull();
    assertThat(saved.get("film_process_id")).isEqualTo(fixture.catalogId("film_processes", "C41"));
    assertThat(saved.get("service_package_scan_option_id")).isEqualTo(scanLId);
    assertThat(saved.get("push_pull_stops")).isEqualTo(2);
    assertThat(saved.get("unit_price_vnd")).isEqualTo(60_000L);
    assertThat(saved.get("scan_option_price_vnd")).isEqualTo(50_000L);
    assertThat(saved.get("push_pull_surcharge_vnd")).isEqualTo(30_000L);
    assertThat(saved.get("express_surcharge_vnd")).isEqualTo(0L);
    assertThat(saved.get("status")).isEqualTo("DECLARED");
  }

  // --- Quyền ---

  @Test
  void filmLabOwnerGets403() throws Exception {
    mockMvc
        .perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderJson(UUID.randomUUID(), "ONLINE", "SELF_DROP_OFF", "STANDARD", rolls(1))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    assertThat(orderCountOfLab()).isZero();
  }

  /** BR-05: PHOTOGRAPHY_EXPERT có mọi quyền của PHOTOGRAPHER. */
  @Test
  void photographyExpertCanQuote() throws Exception {
    UserAccount expert = fixture.createAccount(UserAccountRole.PHOTOGRAPHY_EXPERT);

    mockMvc
        .perform(post(QUOTE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(expert))
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderJson(null, "ONLINE", "SELF_DROP_OFF", "STANDARD", rolls(1))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimatedTotalVnd").value(110_000));
  }

  // --- helpers ---

  private long createReadyLab(UserAccount labOwner, String name) {
    long id = fixture.createLab(labOwner, name);
    labIds.add(id);
    fixture.insertReadyMachines(id);
    return id;
  }

  private ResultActions createOrder(String body) throws Exception {
    return mockMvc.perform(
        post(PATH)
            .header(HttpHeaders.AUTHORIZATION, bearer(photographer))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private String orderJson(
      UUID idempotencyKey, String paymentMethod, String handover, String tier, String rolls) {
    String key = idempotencyKey == null ? "null" : "\"" + idempotencyKey + "\"";
    return """
        {"idempotencyKey":%s,"filmLabId":%d,"serviceTier":"%s","filmHandoverMethod":"%s",
         "paymentMethod":"%s","specialRequest":"Scan ấm","rolls":%s}
        """
        .formatted(key, labId, tier, handover, paymentMethod, rolls);
  }

  /** {@code count} cuộn Kodak Gold (nhập tự do), 35 mm, gói mặc định, mức scan L. */
  private String rolls(int count) {
    List<String> items = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      items.add(rollJson(packageId, scanLId, format135Id, 0));
    }
    return "[" + String.join(",", items) + "]";
  }

  private static String rollJson(long servicePackageId, Long scanOptionId, long formatId, int stops) {
    return """
        {"filmStockNameText":"Kodak Gold 200","filmFormatId":%d,"exposureCount":36,
         "servicePackageId":%d,"servicePackageScanOptionId":%s,"pushPullStops":%d}
        """
        .formatted(formatId, servicePackageId, scanOptionId == null ? "null" : scanOptionId, stops);
  }

  private long scanOptionId(long servicePackageId, String label) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM service_package_scan_options"
            + " WHERE service_package_id = ? AND resolution_label = ?",
        Long.class,
        servicePackageId,
        label);
  }

  private static long idOf(ResultActions result) throws Exception {
    String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private Integer orderCountOfLab() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM service_orders WHERE film_lab_id = ?", Integer.class, labId);
  }

  private List<String> rollStatuses(long orderId) {
    return jdbcTemplate.queryForList(
        "SELECT status FROM film_rolls WHERE service_order_id = ? ORDER BY roll_sequence",
        String.class,
        orderId);
  }

  private Integer auditCount(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM audit_logs WHERE action = 'ORDER_CREATED'"
            + " AND target_type = 'SERVICE_ORDER' AND target_id = ? AND actor_account_id = ?",
        Integer.class,
        orderId,
        photographer.getId());
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }
}
