package com.fa26se088.filmlink.controller.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
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

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.fa26se088.filmlink.service.lab.impl.FilmLabFixture;
import com.fa26se088.filmlink.service.notification.NotificationService;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code POST /api/v1/lab/orders/{id}/receipt} trên DB thật (schema test): Film Lab Owner xác nhận
 * đã nhận phim, ghi bằng chứng bàn giao và chốt giá (UC23, F-11, SPEC-01 T-07).
 *
 * <p>Đơn được tạo qua API: Photographer {@code POST /api/v1/orders} (<b>PAY_AT_LAB</b>,
 * SELF_DROP_OFF) rồi chủ lab {@code POST /api/v1/lab/orders/{id}/accept} → {@code AWAITING_FILM}.
 * Đơn ONLINE chưa tới được {@code AWAITING_FILM} vì thanh toán online (T-05) chưa có trên dev.
 *
 * <p>Gói C-41 35 mm 80.000 VND + scan option 30.000 VND → mỗi cuộn 110.000 VND, 2 cuộn 220.000 VND
 * như TC-F11-01. Storage là {@code LocalStorageClient} ghi vào thư mục tạm (profile test).
 *
 * <p>Dọn dữ liệu: {@code film_handovers}, {@code film_handover_photos} là append-only (trigger chặn
 * DELETE). {@link #cleanUp()} tạm tắt hai trigger trong <b>cùng một transaction</b> với câu DELETE
 * (ALTER TABLE giữ khoá bảng tới khi commit nên session khác không thấy trigger bị tắt).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabFilmReceiptIntegrationTest {

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
    format135Id = fixture.catalogId("film_formats", "135");
    labId = createReadyLab(owner, "Lab Nhận Phim");
    packageId = readyPackage(labId);
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
                "ALTER TABLE film_handover_photos DISABLE TRIGGER film_handover_photos_append_only");
            jdbcTemplate.execute(
                "ALTER TABLE film_handovers DISABLE TRIGGER film_handovers_append_only");
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
          });
      jdbcTemplate.update(
          "DELETE FROM film_rolls WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", id);
    }
    labIds.clear();
    fixture.cleanUp();
  }

  // --- Must ---

  /**
   * TC-F11-01 (Report 5): đơn {@code AWAITING_FILM}, 2 cuộn, tổng 220.000; lab nhận đủ 2 cuộn, 2
   * ảnh → {@code AWAITING_MACHINE_ASSIGNMENT}, {@code final_total = 220.000}, có {@code
   * price_locked_at}, một dòng bàn giao {@code RECEIVED_AT_LAB} 2 cuộn + 2 ảnh, không có top-up /
   * refund (không có dòng {@code payments}).
   *
   * <p>Điều kiện "paid 220,000" của spec sẽ kiểm khi T-05 (thanh toán online) xong; đơn ở đây là
   * PAY_AT_LAB nên chưa trả gì, giá chốt là số Photographer trả tại lab.
   */
  @Test
  @DisplayName("TC-F11-01 Lab confirms receipt of 2/2 rolls with 2 photos; price locked at 220,000")
  void tcF1101_receiveAllRolls() throws Exception {
    long orderId = createAcceptedOrder(2);
    assertThat(estimatedTotal(orderId)).isEqualTo(220_000L);
    List<Long> rolls = rollIds(orderId);
    String orderCode = orderCode(orderId);
    clearInvocations(notificationService);

    receipt(owner, orderId, rollsJson(rolls, true, true), "Phim còn nguyên seal", png("a"), png("b"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_MACHINE_ASSIGNMENT"))
        .andExpect(jsonPath("$.finalTotalVnd").value(220_000))
        .andExpect(jsonPath("$.priceLockedAt", notNullValue()))
        .andExpect(jsonPath("$.rolls[0].status").value("RECEIVED"))
        .andExpect(jsonPath("$.rolls[1].status").value("RECEIVED"))
        .andExpect(jsonPath("$.handovers", hasSize(1)))
        .andExpect(jsonPath("$.handovers[0].handoverPoint").value("RECEIVED_AT_LAB"))
        .andExpect(jsonPath("$.handovers[0].rollCount").value(2))
        .andExpect(jsonPath("$.handovers[0].photos", hasSize(2)));

    Map<String, Object> order =
        jdbcTemplate.queryForMap(
            "SELECT status, final_total_vnd, price_locked_at FROM service_orders WHERE id = ?",
            orderId);
    assertThat(order.get("status")).isEqualTo("AWAITING_MACHINE_ASSIGNMENT");
    assertThat(((Number) order.get("final_total_vnd")).longValue()).isEqualTo(220_000L);
    assertThat(order.get("price_locked_at")).isNotNull();

    Map<String, Object> handover =
        jdbcTemplate.queryForMap(
            "SELECT id, handover_point, roll_count, note, performed_by_account_id, shipment_id"
                + " FROM film_handovers WHERE service_order_id = ?",
            orderId);
    assertThat(handover.get("handover_point")).isEqualTo("RECEIVED_AT_LAB");
    assertThat(((Number) handover.get("roll_count")).intValue()).isEqualTo(2);
    assertThat(handover.get("note")).isEqualTo("Phim còn nguyên seal");
    assertThat(((Number) handover.get("performed_by_account_id")).longValue())
        .isEqualTo(owner.getId());
    assertThat(handover.get("shipment_id")).isNull();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT display_order FROM film_handover_photos WHERE film_handover_id = ?"
                    + " ORDER BY display_order",
                Integer.class,
                handover.get("id")))
        .containsExactly(1, 2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payments WHERE service_order_id = ?", Integer.class, orderId))
        .isZero();

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService)
        .notify(
            eq(photographer.getId()),
            eq(NotificationType.ORDER_STATUS),
            contains(orderCode),
            body.capture(),
            any());
    assertThat(body.getValue()).contains("2/2").contains("220.000 ₫").contains("tại lab");

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "SELECT before_data ->> 'status' AS before_status, after_data ->> 'status' AS after_status,"
                + " after_data ->> 'finalTotalVnd' AS final_total,"
                + " after_data ->> 'receivedRollCount' AS received"
                + " FROM audit_logs WHERE action = 'ORDER_FILM_RECEIVED'"
                + " AND target_type = 'SERVICE_ORDER' AND target_id = ? AND actor_account_id = ?",
            orderId,
            owner.getId());
    assertThat(audit.get("before_status")).isEqualTo("AWAITING_FILM");
    assertThat(audit.get("after_status")).isEqualTo("AWAITING_MACHINE_ASSIGNMENT");
    assertThat(audit.get("final_total")).isEqualTo("220000");
    assertThat(audit.get("received")).isEqualTo("2");
  }

  /** TC-SM05-07 (UC23): lab xác nhận nhận phim, đếm cuộn, chốt giá → AWAITING_MACHINE_ASSIGNMENT. */
  @Test
  @DisplayName("TC-SM05-07 UC23 lab confirms receipt, rolls counted, price locked -> AWAITING_MACHINE_ASSIGNMENT")
  void tcSm0507_receiptMovesToAwaitingMachineAssignment() throws Exception {
    long orderId = createAcceptedOrder(1);
    assertThat(statusOf(orderId)).isEqualTo("AWAITING_FILM");

    receipt(owner, orderId, rollsJson(rollIds(orderId), true), null, png("a"))
        .andExpect(status().isOk());

    assertThat(statusOf(orderId)).isEqualTo("AWAITING_MACHINE_ASSIGNMENT");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM film_rolls WHERE service_order_id = ?", String.class, orderId))
        .isEqualTo("RECEIVED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT price_locked_at IS NOT NULL FROM service_orders WHERE id = ?",
                Boolean.class,
                orderId))
        .isTrue();
  }

  // --- Đếm cuộn, giá chốt ---

  @Test
  void receiveOneOfTwo_finalTotalCountsReceivedRollOnly() throws Exception {
    long orderId = createAcceptedOrder(2);
    List<Long> rolls = rollIds(orderId);
    clearInvocations(notificationService);

    receipt(owner, orderId, rollsJson(rolls, true, false), null, png("a"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.finalTotalVnd").value(110_000))
        .andExpect(jsonPath("$.rolls[0].status").value("RECEIVED"))
        .andExpect(jsonPath("$.rolls[1].status").value("NOT_RECEIVED"))
        .andExpect(jsonPath("$.handovers[0].rollCount").value(1));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT final_total_vnd FROM service_orders WHERE id = ?", Long.class, orderId))
        .isEqualTo(110_000L);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(notificationService)
        .notify(eq(photographer.getId()), eq(NotificationType.ORDER_STATUS), any(), body.capture(), any());
    assertThat(body.getValue()).contains("1/2").contains("110.000 ₫");
  }

  @Test
  void noRollReceived_returns422AndChangesNothing() throws Exception {
    long orderId = createAcceptedOrder(2);
    clearInvocations(notificationService);

    receipt(owner, orderId, rollsJson(rollIds(orderId), false, false), null, png("a"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_OPTION_NOT_SUPPORTED"))
        .andExpect(jsonPath("$.detail", containsString("ít nhất 1 cuộn")));

    assertUnchanged(orderId);
    verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
  }

  @Test
  void missingRoll_returns400NamingTheRoll() throws Exception {
    long orderId = createAcceptedOrder(2);
    List<Long> rolls = rollIds(orderId);

    receipt(owner, orderId, rollsJson(List.of(rolls.get(0)), true), null, png("a"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.missingFilmRollIds[0]").value(rolls.get(1)))
        .andExpect(jsonPath("$.detail", containsString("thiếu cuộn #2")));

    assertUnchanged(orderId);
  }

  @Test
  void duplicateRoll_returns400() throws Exception {
    long orderId = createAcceptedOrder(2);
    List<Long> rolls = rollIds(orderId);

    receipt(
            owner,
            orderId,
            rollsJson(List.of(rolls.get(0), rolls.get(1), rolls.get(0)), true, true, false),
            null,
            png("a"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.duplicateFilmRollIds[0]").value(rolls.get(0)));

    assertUnchanged(orderId);
  }

  @Test
  void rollOfAnotherOrder_returns400() throws Exception {
    long orderId = createAcceptedOrder(1);
    long otherOrderId = createAcceptedOrder(1);
    long foreignRoll = rollIds(otherOrderId).get(0);

    receipt(
            owner,
            orderId,
            rollsJson(List.of(rollIds(orderId).get(0), foreignRoll), true, true),
            null,
            png("a"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.unknownFilmRollIds[0]").value(foreignRoll));

    assertUnchanged(orderId);
    assertThat(statusOf(otherOrderId)).isEqualTo("AWAITING_FILM");
  }

  @Test
  void emptyRollList_returns400() throws Exception {
    long orderId = createAcceptedOrder(1);

    receiptRaw(owner, orderId, "{\"rolls\":[]}", png("a"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

    assertUnchanged(orderId);
  }

  // --- Ảnh bằng chứng (BR-25) ---

  @Test
  void zeroPhotos_returns400() throws Exception {
    long orderId = createAcceptedOrder(1);

    receipt(owner, orderId, rollsJson(rollIds(orderId), true), null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.field").value("photos"));

    assertUnchanged(orderId);
  }

  @Test
  void elevenPhotos_returns400() throws Exception {
    long orderId = createAcceptedOrder(1);
    MockMultipartFile[] photos = new MockMultipartFile[11];
    for (int i = 0; i < photos.length; i++) {
      photos[i] = png("p" + i);
    }

    receipt(owner, orderId, rollsJson(rollIds(orderId), true), null, photos)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.detail", containsString("đã gửi 11")));

    assertUnchanged(orderId);
  }

  @Test
  void textFile_returnsStorageErrorAndChangesNothing() throws Exception {
    long orderId = createAcceptedOrder(1);
    MockMultipartFile text =
        new MockMultipartFile(
            "photos", "note.txt", "image/png", "not an image".getBytes(StandardCharsets.UTF_8));

    receipt(owner, orderId, rollsJson(rollIds(orderId), true), null, png("a"), text)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("STORAGE_FILE_TYPE_NOT_ALLOWED"));

    assertUnchanged(orderId);
  }

  // --- Trạng thái, quyền ---

  @Test
  void orderAwaitingLabConfirmation_returns409() throws Exception {
    long orderId = createOrder(labId, packageId, 1);

    receipt(owner, orderId, rollsJson(rollIds(orderId), true), null, png("a"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"))
        .andExpect(jsonPath("$.detail", containsString("AWAITING_LAB_CONFIRMATION")));

    assertThat(statusOf(orderId)).isEqualTo("AWAITING_LAB_CONFIRMATION");
    assertThat(handoverCount(orderId)).isZero();
  }

  @Test
  void receiveTwice_returns409() throws Exception {
    long orderId = createAcceptedOrder(1);
    List<Long> rolls = rollIds(orderId);
    receipt(owner, orderId, rollsJson(rolls, true), null, png("a")).andExpect(status().isOk());

    receipt(owner, orderId, rollsJson(rolls, true), null, png("b"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));

    assertThat(handoverCount(orderId)).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'ORDER_FILM_RECEIVED'"
                    + " AND target_id = ?",
                Integer.class,
                orderId))
        .isEqualTo(1);
  }

  @Test
  void orderOfOtherLab_returns404() throws Exception {
    UserAccount otherOwner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    long otherLabId = createReadyLab(otherOwner, "Lab Khác");
    long otherPackage = readyPackage(otherLabId);
    long otherOrderId = createOrder(otherLabId, otherPackage, 1);
    accept(otherOwner, otherOrderId);

    receipt(owner, otherOrderId, rollsJson(rollIds(otherOrderId), true), null, png("a"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));

    assertUnchanged(otherOrderId);
  }

  @Test
  void photographerGets403() throws Exception {
    long orderId = createAcceptedOrder(1);

    receipt(photographer, orderId, rollsJson(rollIds(orderId), true), null, png("a"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

    assertUnchanged(orderId);
  }

  // --- Chi tiết đơn ---

  @Test
  void detail_includesHandoverWithSignedPhotoUrls() throws Exception {
    long orderId = createAcceptedOrder(2);
    receipt(owner, orderId, rollsJson(rollIds(orderId), true, false), null, png("a"), png("b"))
        .andExpect(status().isOk());

    String response =
        mockMvc
            .perform(
                get(LAB_ORDERS_PATH + "/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finalTotalVnd").value(110_000))
            .andExpect(jsonPath("$.priceLockedAt", notNullValue()))
            .andExpect(jsonPath("$.rolls[1].status").value("NOT_RECEIVED"))
            .andExpect(jsonPath("$.handovers", hasSize(1)))
            .andExpect(jsonPath("$.handovers[0].handoverPoint").value("RECEIVED_AT_LAB"))
            .andExpect(jsonPath("$.handovers[0].rollCount").value(1))
            .andExpect(jsonPath("$.handovers[0].performedByAccountId").value(owner.getId()))
            .andExpect(jsonPath("$.handovers[0].occurredAt", notNullValue()))
            .andExpect(jsonPath("$.handovers[0].photos[0].displayOrder").value(1))
            .andExpect(jsonPath("$.handovers[0].photos[1].displayOrder").value(2))
            .andExpect(jsonPath("$.handovers[0].photos[0].url", startsWith("http://localhost:8080")))
            .andExpect(jsonPath("$.handovers[0].photos[0].url", containsString("handover-photo")))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    // Link có chữ ký tải được file thật.
    String url = JsonPath.read(response, "$.handovers[0].photos[0].url");
    mockMvc.perform(get(URI.create(url))).andExpect(status().isOk());
  }

  @Test
  void detailBeforeReceipt_hasNoHandoverAndNoFinalPrice() throws Exception {
    long orderId = createAcceptedOrder(1);

    mockMvc
        .perform(get(LAB_ORDERS_PATH + "/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.finalTotalVnd").doesNotExist())
        .andExpect(jsonPath("$.handovers", hasSize(0)));
  }

  // --- helpers ---

  private void assertUnchanged(long orderId) {
    assertThat(statusOf(orderId)).isEqualTo("AWAITING_FILM");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT final_total_vnd IS NULL AND price_locked_at IS NULL FROM service_orders"
                    + " WHERE id = ?",
                Boolean.class,
                orderId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM film_rolls WHERE service_order_id = ? AND status <> 'DECLARED'",
                Integer.class,
                orderId))
        .isZero();
    assertThat(handoverCount(orderId)).isZero();
  }

  private long createReadyLab(UserAccount labOwner, String name) {
    long id = fixture.createLab(labOwner, name);
    labIds.add(id);
    fixture.insertReadyMachines(id);
    return id;
  }

  /** Gói C-41 35 mm 80.000 VND đang bán + scan option 30.000 VND, lab bật nhận đơn. */
  private long readyPackage(long filmLabId) {
    long id =
        fixture.insertPackage(filmLabId, "C-41 35mm", "DEVELOPING", "C41", "135", 80_000, "ON_SALE");
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

  /** Đơn PAY_AT_LAB, SELF_DROP_OFF đã được lab nhận ({@code AWAITING_FILM}). */
  private long createAcceptedOrder(int rollCount) throws Exception {
    long orderId = createOrder(labId, packageId, rollCount);
    accept(owner, orderId);
    return orderId;
  }

  /** Photographer tạo đơn qua API (UC20): {@code rollCount} cuộn 35 mm, scan option của gói. */
  private long createOrder(long filmLabId, long servicePackageId, int rollCount) throws Exception {
    long scanOption = scanOptionOf(servicePackageId);
    List<String> rolls = new ArrayList<>();
    for (int i = 0; i < rollCount; i++) {
      rolls.add(
          """
          {"filmStockNameText":"Kodak Gold 200","filmFormatId":%d,"exposureCount":36,
           "servicePackageId":%d,"servicePackageScanOptionId":%d,"pushPullStops":0}
          """
              .formatted(format135Id, servicePackageId, scanOption));
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

  private ResultActions receipt(
      UserAccount account, long orderId, String rollsJson, String note, MockMultipartFile... photos)
      throws Exception {
    String data =
        note == null
            ? "{\"rolls\":" + rollsJson + "}"
            : "{\"rolls\":" + rollsJson + ",\"note\":\"" + note + "\"}";
    return receiptRaw(account, orderId, data, photos);
  }

  private ResultActions receiptRaw(
      UserAccount account, long orderId, String dataJson, MockMultipartFile... photos)
      throws Exception {
    MockMultipartHttpServletRequestBuilder request =
        multipart(LAB_ORDERS_PATH + "/" + orderId + "/receipt")
            .file(
                new MockMultipartFile(
                    "data",
                    "",
                    MediaType.APPLICATION_JSON_VALUE,
                    dataJson.getBytes(StandardCharsets.UTF_8)));
    for (MockMultipartFile photo : photos) {
      request.file(photo);
    }
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(account)));
  }

  /** {@code [{"filmRollId":..,"received":..}, ...]}; {@code received[i]} ứng với {@code ids[i]}. */
  private static String rollsJson(List<Long> ids, boolean... received) {
    List<String> items = new ArrayList<>();
    for (int i = 0; i < ids.size(); i++) {
      items.add("{\"filmRollId\":" + ids.get(i) + ",\"received\":" + received[i] + "}");
    }
    return "[" + String.join(",", items) + "]";
  }

  private List<Long> rollIds(long orderId) {
    return jdbcTemplate.queryForList(
        "SELECT id FROM film_rolls WHERE service_order_id = ? ORDER BY roll_sequence",
        Long.class,
        orderId);
  }

  private String statusOf(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM service_orders WHERE id = ?", String.class, orderId);
  }

  private String orderCode(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT order_code FROM service_orders WHERE id = ?", String.class, orderId);
  }

  private Long estimatedTotal(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT estimated_total_vnd FROM service_orders WHERE id = ?", Long.class, orderId);
  }

  private Integer handoverCount(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM film_handovers WHERE service_order_id = ?", Integer.class, orderId);
  }

  /** PNG tối thiểu: chỉ cần đúng magic bytes để storage nhận. */
  private static MockMultipartFile png(String name) {
    byte[] content = new byte[256];
    byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    System.arraycopy(header, 0, content, 0, header.length);
    return new MockMultipartFile("photos", name + ".png", "image/png", content);
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }
}
