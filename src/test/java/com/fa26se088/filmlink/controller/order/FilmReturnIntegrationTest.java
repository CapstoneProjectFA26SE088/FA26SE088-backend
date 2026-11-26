package com.fa26se088.filmlink.controller.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.storage.FullResolutionLinkVerifier;
import com.fa26se088.filmlink.client.storage.FullResolutionLinkVerifier.LinkVerification;
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
import java.sql.Timestamp;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SPEC-01 T-11 (UC27, UC28, BR-25) trên DB thật (schema test): lab trả phim tại lab hoặc giữ âm
 * bản → {@code DELIVERED}; Photographer xác nhận đã nhận → {@code CLOSED}.
 *
 * <p>Đơn được dựng qua API tới {@code READY_FOR_HANDOVER} như {@code ScanDeliveryIntegrationTest}
 * (tạo đơn PAY_AT_LAB → nhận đơn → nhận phim → xác nhận lịch → 5 công đoạn tới QC → upload scan +
 * link → giao scan). {@link FullResolutionLinkVerifier} được mock (không gọi Internet); storage là
 * bản local trong thư mục tạm.
 *
 * <p>Dọn dữ liệu: file trong storage và {@code scan_files}, {@code archive_rolls} trước {@code
 * film_rolls}; các bảng append-only tạm tắt trigger trong cùng transaction với câu DELETE.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FilmReturnIntegrationTest {

  private static final String ORDERS_PATH = "/api/v1/orders";
  private static final String LAB_ORDERS_PATH = "/api/v1/lab/orders";
  private static final String ARCHIVE_PATH = "/api/v1/me/archive";
  private static final List<String> NEXT_STAGES =
      List.of("DEVELOPING", "WASHING", "DRYING", "SCANNING", "QC");

  /** Giá gói C-41 35mm và mức scan M của fixture (bảng giá). */
  private static final long UNIT_PRICE_VND = 80_000;

  private static final long SCAN_OPTION_PRICE_VND = 30_000;

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageClient storageClient;

  @MockitoBean private NotificationService notificationService;
  @MockitoBean private FullResolutionLinkVerifier fullResolutionLinkVerifier;

  // Không dùng trực tiếp: khai báo cùng bộ bean override với ScanDeliveryIntegrationTest để dùng lại
  // ApplicationContext đã cache (mỗi context mới mở thêm một connection pool tới PostgreSQL).
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
    labId = createReadyLab(owner, "Lab Return");
    package135Id = readyPackage(labId);
    when(fullResolutionLinkVerifier.verify(anyString())).thenReturn(LinkVerification.ok());
  }

  @AfterEach
  void cleanUp() {
    for (Long id : labIds) {
      String orderIds = "SELECT id FROM service_orders WHERE film_lab_id = " + id;
      String rollIds = "SELECT id FROM film_rolls WHERE service_order_id IN (" + orderIds + ")";
      jdbcTemplate
          .queryForList(
              "SELECT blob_key FROM scan_files WHERE film_roll_id IN (" + rollIds + ")",
              String.class)
          .forEach(storageClient::delete);
      jdbcTemplate.update("DELETE FROM scan_files WHERE film_roll_id IN (" + rollIds + ")");
      jdbcTemplate.update("DELETE FROM archive_rolls WHERE film_roll_id IN (" + rollIds + ")");
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
      jdbcTemplate.update(
          "DELETE FROM commissions WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM film_rolls WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", id);
    }
    labIds.clear();
    fixture.cleanUp();
  }

  // --- Must ---

  /**
   * TC-SM05-15 (UC27, BR-25): đơn {@code READY_FOR_HANDOVER}, 2 cuộn; lab trả phim tại lab kèm 2
   * ảnh → {@code DELIVERED}, {@code delivered_at} có giá trị; 1 bàn giao {@code COLLECTED_AT_LAB},
   * {@code roll_count} = 2, 2 ảnh, người thực hiện = chủ lab; audit; Photographer được báo.
   */
  @Test
  @DisplayName("TC-SM05-15 UC27 film returned at lab (2 photos) -> DELIVERED, COLLECTED_AT_LAB handover")
  void tcSm0515_returnFilmAtLab() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 2);
    clearInvocations(notificationService);

    returnFilm(owner, orderId, false, "Đã trả tận tay", 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.deliveredAt", notNullValue()))
        .andExpect(jsonPath("$.keepNegativesAtLab").value(false))
        .andExpect(jsonPath("$.closedAt").value(nullValue()))
        .andExpect(jsonPath("$.handovers", hasSize(2)))
        .andExpect(jsonPath("$.handovers[1].handoverPoint").value("COLLECTED_AT_LAB"));

    assertThat(statusOf(orderId)).isEqualTo("DELIVERED");
    Map<String, Object> order =
        jdbcTemplate.queryForMap(
            "SELECT delivered_at, keep_negatives_at_lab FROM service_orders WHERE id = ?", orderId);
    assertThat(order.get("delivered_at")).isNotNull();
    assertThat(order.get("keep_negatives_at_lab")).isEqualTo(false);
    Map<String, Object> handover =
        jdbcTemplate.queryForMap(
            "SELECT id, roll_count, performed_by_account_id, note FROM film_handovers"
                + " WHERE service_order_id = ? AND handover_point = 'COLLECTED_AT_LAB'",
            orderId);
    assertThat(((Number) handover.get("roll_count")).intValue()).isEqualTo(2);
    assertThat(((Number) handover.get("performed_by_account_id")).longValue())
        .isEqualTo(owner.getId());
    assertThat(handover.get("note")).isEqualTo("Đã trả tận tay");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM film_handover_photos WHERE film_handover_id = ?",
                Integer.class,
                handover.get("id")))
        .isEqualTo(2);

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "SELECT before_data ->> 'status' AS before_status, after_data ->> 'status' AS"
                + " after_status, after_data ->> 'keepNegativesAtLab' AS keep_negatives"
                + " FROM audit_logs WHERE action = 'ORDER_FILM_RETURNED' AND target_id = ?"
                + " AND actor_account_id = ?",
            orderId,
            owner.getId());
    assertThat(audit.get("before_status")).isEqualTo("READY_FOR_HANDOVER");
    assertThat(audit.get("after_status")).isEqualTo("DELIVERED");
    assertThat(audit.get("keep_negatives")).isEqualTo("false");
    verify(notificationService)
        .notify(
            eq(photographer.getId()),
            eq(NotificationType.ORDER_STATUS),
            eq("Đơn " + orderCode(orderId) + " đã trả phim"),
            any(),
            any());
  }

  /**
   * TC-SM05-15 biến thể (UC27): lab giữ âm bản theo yêu cầu, không gửi ảnh → {@code DELIVERED},
   * {@code keep_negatives_at_lab = true}, không có bàn giao mới (chỉ còn bàn giao lúc nhận phim).
   */
  @Test
  @DisplayName("TC-SM05-15 UC27 negatives kept at lab, no photos -> DELIVERED, no new handover")
  void tcSm0515_keepNegativesAtLab() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);
    int handoversBefore = handoverCount(orderId);
    clearInvocations(notificationService);

    returnFilm(owner, orderId, true, null, 0)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.keepNegativesAtLab").value(true))
        .andExpect(jsonPath("$.deliveredAt", notNullValue()));

    assertThat(statusOf(orderId)).isEqualTo("DELIVERED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT keep_negatives_at_lab FROM service_orders WHERE id = ?",
                Boolean.class,
                orderId))
        .isTrue();
    assertThat(handoverCount(orderId)).isEqualTo(handoversBefore).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM film_handovers WHERE service_order_id = ?"
                    + " AND handover_point = 'COLLECTED_AT_LAB'",
                Integer.class,
                orderId))
        .isZero();
    mockMvc
        .perform(
            get(ORDERS_PATH + "/" + orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.keepNegativesAtLab").value(true));
    verify(notificationService)
        .notify(
            eq(photographer.getId()),
            eq(NotificationType.ORDER_STATUS),
            eq("Lab đã lưu giữ âm bản của đơn " + orderCode(orderId)),
            any(),
            any());
  }

  /** UC27: giữ âm bản mà vẫn gửi ảnh → ảnh bị bỏ qua, không bàn giao, không file nào được lưu. */
  @Test
  void keepNegativesAtLab_withPhotos_photosIgnored() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);
    int handoversBefore = handoverCount(orderId);

    returnFilm(owner, orderId, true, null, 3)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.keepNegativesAtLab").value(true));

    assertThat(handoverCount(orderId)).isEqualTo(handoversBefore);
  }

  /**
   * TC-SM05-16 (UC28): đơn {@code DELIVERED}; Photographer xác nhận → {@code CLOSED}, {@code
   * closed_at} có giá trị; audit {@code ORDER_CLOSED}; chủ lab được báo.
   */
  @Test
  @DisplayName("TC-SM05-16 UC28 Photographer confirms receipt -> CLOSED")
  void tcSm0516_confirmReceipt() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);
    returnFilm(owner, orderId, false, null, 1).andExpect(status().isOk());
    clearInvocations(notificationService);

    confirmReceipt(photographer, orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orderId))
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.closedAt", notNullValue()))
        .andExpect(jsonPath("$.deliveredAt", notNullValue()));

    assertThat(statusOf(orderId)).isEqualTo("CLOSED");
    Map<String, Object> order =
        jdbcTemplate.queryForMap(
            "SELECT delivered_at, closed_at FROM service_orders WHERE id = ?", orderId);
    assertThat(order.get("closed_at")).isNotNull();
    assertThat((Timestamp) order.get("closed_at"))
        .isAfterOrEqualTo((Timestamp) order.get("delivered_at"));
    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "SELECT before_data ->> 'status' AS before_status, after_data ->> 'status' AS"
                + " after_status FROM audit_logs WHERE action = 'ORDER_CLOSED' AND target_id = ?"
                + " AND actor_account_id = ?",
            orderId,
            photographer.getId());
    assertThat(audit.get("before_status")).isEqualTo("DELIVERED");
    assertThat(audit.get("after_status")).isEqualTo("CLOSED");
    verify(notificationService)
        .notify(
            eq(owner.getId()),
            eq(NotificationType.ORDER_STATUS),
            eq("Đơn " + orderCode(orderId) + " đã hoàn tất"),
            any(),
            any());
  }

  /**
   * E2E-PAY-AT-LAB: chạy trọn luồng đơn PAY_AT_LAB qua API, kiểm trạng thái ở mỗi bước. Đơn 2 cuộn
   * 135 C-41, Standard, SELF_DROP_OFF; giá theo bảng giá: 2 × (80.000 gói + 30.000 scan) = 220.000.
   *
   * <p>{@code TC-WF01} (ONLINE + VNPay IPN) sẽ viết khi contract thanh toán (SPEC-00 §4.5) lên dev.
   */
  @Test
  @DisplayName("E2E-PAY-AT-LAB create -> accept -> receive -> schedule -> stages -> scans -> return -> CLOSED")
  void e2ePayAtLab_fullFlow() throws Exception {
    long expectedTotal = 2 * (UNIT_PRICE_VND + SCAN_OPTION_PRICE_VND);

    // 1. Photographer tạo đơn.
    String created =
        createOrderRequest(photographer, 2)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_LAB_CONFIRMATION"))
            .andExpect(jsonPath("$.estimatedTotalVnd").value(expectedTotal))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    long orderId = ((Number) JsonPath.read(created, "$.id")).longValue();
    List<Long> rolls = rollIds(orderId);
    assertThat(rolls).hasSize(2);

    // 2. Lab nhận đơn.
    mockMvc
        .perform(
            post(LAB_ORDERS_PATH + "/" + orderId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_FILM"));

    // 3. Lab nhận phim, 2 ảnh, chốt giá.
    receiveAll(orderId, 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_MACHINE_ASSIGNMENT"))
        .andExpect(jsonPath("$.finalTotalVnd").value(expectedTotal))
        .andExpect(jsonPath("$.priceLockedAt", notNullValue()));

    // 4. Lab xác nhận ngày hẹn trả (ngày đề xuất).
    mockMvc
        .perform(
            post(LAB_ORDERS_PATH + "/" + orderId + "/schedule-confirmation")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROCESSING"))
        .andExpect(jsonPath("$.promisedReturnAt", notNullValue()));

    // 5. 5 công đoạn tới QC; đơn vẫn IN_PROCESSING.
    for (int i = 0; i < NEXT_STAGES.size(); i++) {
      recordStage(orderId, NEXT_STAGES.get(i))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("IN_PROCESSING"))
          .andExpect(jsonPath("$.stages", hasSize(i + 2)))
          .andExpect(jsonPath("$.stages[" + (i + 1) + "].stage").value(NEXT_STAGES.get(i)));
    }

    // 6. Upload ảnh xem trước + link file gốc cho 2 cuộn.
    for (Long rollId : rolls) {
      uploadScans(orderId, rollId, List.of(jpeg(16, 16, 1), jpeg(16, 16, 2)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)));
      setLink(orderId, rollId, "https://drive.example.com/roll-" + rollId)
          .andExpect(status().isOk());
    }
    assertThat(statusOf(orderId)).isEqualTo("IN_PROCESSING");

    // 7. Giao scan.
    deliverScans(orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READY_FOR_HANDOVER"));

    // 8. Trả phim tại lab, 2 ảnh.
    returnFilm(owner, orderId, false, null, 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.deliveredAt", notNullValue()));

    // 9. Photographer xác nhận đã nhận.
    confirmReceipt(photographer, orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.paymentMethod").value("PAY_AT_LAB"))
        .andExpect(jsonPath("$.finalTotalVnd").value(expectedTotal))
        .andExpect(jsonPath("$.closedAt", notNullValue()))
        .andExpect(jsonPath("$.stages", hasSize(6)))
        .andExpect(jsonPath("$.handovers", hasSize(2)))
        .andExpect(jsonPath("$.handovers[0].handoverPoint").value("RECEIVED_AT_LAB"))
        .andExpect(jsonPath("$.handovers[1].handoverPoint").value("COLLECTED_AT_LAB"))
        .andExpect(jsonPath("$.rolls[0].archiveRollId", notNullValue()))
        .andExpect(jsonPath("$.rolls[1].archiveRollId", notNullValue()));

    assertThat(statusOf(orderId)).isEqualTo("CLOSED");
    // final_total = công thức bảng giá: Σ giá đã chụp của các cuộn RECEIVED.
    Long finalTotal =
        jdbcTemplate.queryForObject(
            "SELECT final_total_vnd FROM service_orders WHERE id = ?", Long.class, orderId);
    Long formula =
        jdbcTemplate.queryForObject(
            "SELECT sum(unit_price_vnd + scan_option_price_vnd + push_pull_surcharge_vnd"
                + " + express_surcharge_vnd) FROM film_rolls"
                + " WHERE service_order_id = ? AND status = 'RECEIVED'",
            Long.class,
            orderId);
    assertThat(finalTotal).isEqualTo(formula).isEqualTo(expectedTotal);

    // Archive của Photographer có 2 cuộn.
    mockMvc
        .perform(get(ARCHIVE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].orderCode").value(orderCode(orderId)))
        .andExpect(jsonPath("$.content[1].orderCode").value(orderCode(orderId)));
  }

  // --- Trả phim: lỗi ---

  @Test
  void returnFilm_notReadyForHandover_returns409() throws Exception {
    long orderId = createOrderAtQc(photographer, 1);

    returnFilm(owner, orderId, false, null, 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));

    assertThat(statusOf(orderId)).isEqualTo("IN_PROCESSING");
  }

  /** Trả phim lần hai (đơn đã DELIVERED) → 409, không thêm bàn giao. */
  @Test
  void returnFilm_twice_returns409() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);
    returnFilm(owner, orderId, false, null, 1).andExpect(status().isOk());
    int handovers = handoverCount(orderId);

    returnFilm(owner, orderId, false, null, 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));

    assertThat(handoverCount(orderId)).isEqualTo(handovers);
  }

  @Test
  void returnFilm_noPhotos_returns400() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);
    int handovers = handoverCount(orderId);

    returnFilm(owner, orderId, false, null, 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.field").value("photos"));

    assertThat(statusOf(orderId)).isEqualTo("READY_FOR_HANDOVER");
    assertThat(handoverCount(orderId)).isEqualTo(handovers);
  }

  @Test
  void returnFilm_elevenPhotos_returns400() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);
    int handovers = handoverCount(orderId);

    returnFilm(owner, orderId, false, null, 11)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.max").value(10));

    assertThat(statusOf(orderId)).isEqualTo("READY_FOR_HANDOVER");
    assertThat(handoverCount(orderId)).isEqualTo(handovers);
  }

  /** Thiếu {@code keepNegativesAtLab} → 400 Bean Validation. */
  @Test
  void returnFilm_missingKeepNegatives_returns400() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);

    mockMvc
        .perform(
            multipart(LAB_ORDERS_PATH + "/" + orderId + "/film-return")
                .file(
                    new MockMultipartFile(
                        "data",
                        "",
                        MediaType.APPLICATION_JSON_VALUE,
                        "{}".getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("photos", "a.jpg", "image/jpeg", jpeg(8, 8, 0)))
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

    assertThat(statusOf(orderId)).isEqualTo("READY_FOR_HANDOVER");
  }

  @Test
  void returnFilm_otherLab_returns404() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);
    UserAccount otherOwner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    createReadyLab(otherOwner, "Lab Khác");

    returnFilm(otherOwner, orderId, false, null, 1)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));

    assertThat(statusOf(orderId)).isEqualTo("READY_FOR_HANDOVER");
  }

  @Test
  void photographerCallsFilmReturn_returns403() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);

    returnFilm(photographer, orderId, false, null, 1)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

    assertThat(statusOf(orderId)).isEqualTo("READY_FOR_HANDOVER");
  }

  // --- Xác nhận đã nhận: lỗi ---

  @Test
  void confirmReceipt_notDelivered_returns409() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);

    confirmReceipt(photographer, orderId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));

    assertThat(statusOf(orderId)).isEqualTo("READY_FOR_HANDOVER");
  }

  @Test
  void confirmReceipt_otherPhotographersOrder_returns404() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);
    returnFilm(owner, orderId, false, null, 1).andExpect(status().isOk());
    UserAccount stranger = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);

    confirmReceipt(stranger, orderId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));

    assertThat(statusOf(orderId)).isEqualTo("DELIVERED");
  }

  @Test
  void confirmReceipt_twice_returns409() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);
    returnFilm(owner, orderId, true, null, 0).andExpect(status().isOk());
    confirmReceipt(photographer, orderId).andExpect(status().isOk());
    Timestamp closedAt =
        jdbcTemplate.queryForObject(
            "SELECT closed_at FROM service_orders WHERE id = ?", Timestamp.class, orderId);

    confirmReceipt(photographer, orderId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT closed_at FROM service_orders WHERE id = ?", Timestamp.class, orderId))
        .isEqualTo(closedAt);
  }

  @Test
  void labOwnerCallsConfirmReceipt_returns403() throws Exception {
    long orderId = createOrderReadyForHandover(photographer, 1);
    returnFilm(owner, orderId, false, null, 1).andExpect(status().isOk());

    confirmReceipt(owner, orderId)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

    assertThat(statusOf(orderId)).isEqualTo("DELIVERED");
  }

  // --- helpers: dựng đơn ---

  /** Đơn {@code rollCount} cuộn đã giao scan ({@code READY_FOR_HANDOVER}). */
  private long createOrderReadyForHandover(UserAccount account, int rollCount) throws Exception {
    long orderId = createOrderAtQc(account, rollCount);
    for (Long rollId : rollIds(orderId)) {
      uploadScans(orderId, rollId, List.of(jpeg(8, 8, 1))).andExpect(status().isOk());
      setLink(orderId, rollId, "https://drive.example.com/roll-" + rollId)
          .andExpect(status().isOk());
    }
    deliverScans(orderId).andExpect(status().isOk());
    return orderId;
  }

  /** Đơn {@code rollCount} cuộn đã ghi đủ công đoạn tới QC. */
  private long createOrderAtQc(UserAccount account, int rollCount) throws Exception {
    long orderId = createOrder(account, rollCount);
    mockMvc
        .perform(
            post(LAB_ORDERS_PATH + "/" + orderId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk());
    receiveAll(orderId, 1).andExpect(status().isOk());
    mockMvc
        .perform(
            post(LAB_ORDERS_PATH + "/" + orderId + "/schedule-confirmation")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk());
    for (String stage : NEXT_STAGES) {
      recordStage(orderId, stage).andExpect(status().isOk());
    }
    return orderId;
  }

  private long createReadyLab(UserAccount labOwner, String name) {
    long id = fixture.createLab(labOwner, name);
    labIds.add(id);
    fixture.insertReadyMachines(id);
    return id;
  }

  private long readyPackage(long filmLabId) {
    long id =
        fixture.insertPackage(
            filmLabId, "C-41 35mm", "DEVELOPING", "C41", "135", UNIT_PRICE_VND, "ON_SALE");
    fixture.insertScanOption(id, "M", SCAN_OPTION_PRICE_VND);
    fixture.setAcceptingOrders(filmLabId, true);
    return id;
  }

  private long createOrder(UserAccount account, int rollCount) throws Exception {
    String response =
        createOrderRequest(account, rollCount)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return ((Number) JsonPath.read(response, "$.id")).longValue();
  }

  /** Đơn PAY_AT_LAB, SELF_DROP_OFF, Standard; mỗi cuộn 135 C-41, Kodak Gold 200, scan M. */
  private ResultActions createOrderRequest(UserAccount account, int rollCount) throws Exception {
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
         "filmHandoverMethod":"SELF_DROP_OFF","paymentMethod":"PAY_AT_LAB","rolls":[%s]}
        """
            .formatted(UUID.randomUUID(), labId, String.join(",", rolls));
    return mockMvc.perform(
        post(ORDERS_PATH)
            .header(HttpHeaders.AUTHORIZATION, bearer(account))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions receiveAll(long orderId, int photoCount) throws Exception {
    String items =
        String.join(
            ",",
            rollIds(orderId).stream()
                .map(id -> "{\"filmRollId\":" + id + ",\"received\":true}")
                .toList());
    MockMultipartHttpServletRequestBuilder request =
        multipart(LAB_ORDERS_PATH + "/" + orderId + "/receipt");
    request.file(
        new MockMultipartFile(
            "data",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            ("{\"rolls\":[" + items + "]}").getBytes(StandardCharsets.UTF_8)));
    for (int i = 0; i < photoCount; i++) {
      request.file(new MockMultipartFile("photos", "r" + i + ".jpg", "image/jpeg", jpeg(8, 8, i)));
    }
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner)));
  }

  private ResultActions recordStage(long orderId, String stage) throws Exception {
    return mockMvc.perform(
        post(LAB_ORDERS_PATH + "/" + orderId + "/stages")
            .header(HttpHeaders.AUTHORIZATION, bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"stage\":\"" + stage + "\"}"));
  }

  private ResultActions uploadScans(long orderId, long rollId, List<byte[]> files)
      throws Exception {
    MockMultipartHttpServletRequestBuilder request =
        multipart(LAB_ORDERS_PATH + "/" + orderId + "/rolls/" + rollId + "/scans");
    for (int i = 0; i < files.size(); i++) {
      request.file(new MockMultipartFile("files", "frame-" + i + ".jpg", "image/jpeg", files.get(i)));
    }
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner)));
  }

  private ResultActions setLink(long orderId, long rollId, String url) throws Exception {
    return mockMvc.perform(
        put(LAB_ORDERS_PATH + "/" + orderId + "/rolls/" + rollId + "/full-resolution-link")
            .header(HttpHeaders.AUTHORIZATION, bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"" + url + "\"}"));
  }

  private ResultActions deliverScans(long orderId) throws Exception {
    return mockMvc.perform(
        post(LAB_ORDERS_PATH + "/" + orderId + "/scan-delivery")
            .header(HttpHeaders.AUTHORIZATION, bearer(owner)));
  }

  // --- helpers: API của task ---

  private ResultActions returnFilm(
      UserAccount account, long orderId, boolean keepNegatives, String note, int photoCount)
      throws Exception {
    String data =
        note == null
            ? "{\"keepNegativesAtLab\":" + keepNegatives + "}"
            : "{\"keepNegativesAtLab\":" + keepNegatives + ",\"note\":\"" + note + "\"}";
    MockMultipartHttpServletRequestBuilder request =
        multipart(LAB_ORDERS_PATH + "/" + orderId + "/film-return");
    request.file(
        new MockMultipartFile(
            "data", "", MediaType.APPLICATION_JSON_VALUE, data.getBytes(StandardCharsets.UTF_8)));
    for (int i = 0; i < photoCount; i++) {
      request.file(
          new MockMultipartFile("photos", "ret" + i + ".jpg", "image/jpeg", jpeg(8, 8, 50 + i)));
    }
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(account)));
  }

  private ResultActions confirmReceipt(UserAccount account, long orderId) throws Exception {
    return mockMvc.perform(
        post(ORDERS_PATH + "/" + orderId + "/confirm-receipt")
            .header(HttpHeaders.AUTHORIZATION, bearer(account)));
  }

  // --- helpers: dữ liệu ---

  private List<Long> rollIds(long orderId) {
    return jdbcTemplate.queryForList(
        "SELECT id FROM film_rolls WHERE service_order_id = ? ORDER BY roll_sequence",
        Long.class,
        orderId);
  }

  private int handoverCount(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM film_handovers WHERE service_order_id = ?", Integer.class, orderId);
  }

  private String statusOf(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM service_orders WHERE id = ?", String.class, orderId);
  }

  private String orderCode(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT order_code FROM service_orders WHERE id = ?", String.class, orderId);
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }

  /** JPEG nhỏ sinh bằng {@link BufferedImage}; {@code seed} đổi màu để mỗi ảnh khác nhau. */
  private static byte[] jpeg(int width, int height, int seed) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    int color = (int) ((seed * 2_654_435_761L) & 0xFFFFFF) | 0x010101;
    for (int x = 0; x < Math.min(width, 8); x++) {
      image.setRGB(x, 0, color);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", out);
    return out.toByteArray();
  }
}
