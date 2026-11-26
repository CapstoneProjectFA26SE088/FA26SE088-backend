package com.fa26se088.filmlink.controller.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
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
 * SPEC-01 T-10 (UC26, UC28, BR-08, F-15) trên DB thật (schema test): lab upload ảnh xem trước, gửi
 * link file gốc, giao scan; Photographer xem Digital Film Archive.
 *
 * <p>Đơn được dựng qua API như {@code OrderProcessingStageIntegrationTest} rồi ghi đủ công đoạn tới
 * QC. {@link FullResolutionLinkVerifier} được mock (không gọi Internet); storage là bản local trong
 * thư mục tạm.
 *
 * <p>Dọn dữ liệu: file trong storage và {@code scan_files}, {@code archive_rolls} trước {@code
 * film_rolls}; các bảng append-only tạm tắt trigger trong cùng transaction với câu DELETE.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanDeliveryIntegrationTest {

  private static final String ORDERS_PATH = "/api/v1/orders";
  private static final String LAB_ORDERS_PATH = "/api/v1/lab/orders";
  private static final String ARCHIVE_PATH = "/api/v1/me/archive";
  private static final List<String> NEXT_STAGES =
      List.of("DEVELOPING", "WASHING", "DRYING", "SCANNING", "QC");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageClient storageClient;

  @MockitoBean private NotificationService notificationService;
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
    labId = createReadyLab(owner, "Lab Scan");
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
      jdbcTemplate.update("DELETE FROM film_rolls WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", id);
    }
    labIds.clear();
    fixture.cleanUp();
  }

  // --- Must ---

  /**
   * TC-F15-01 (UC26, UC28, BR-08): đơn ở QC, 2 cuộn; upload 72 ảnh (36/cuộn, mỗi cuộn 2 request
   * 20 + 16 file) và 2 link hợp lệ; giao scan. Kỳ vọng: {@code READY_FOR_HANDOVER}; archive của
   * Photographer có 2 cuộn; ảnh xem trước tải được qua link ký, đúng checksum.
   */
  @Test
  @DisplayName("TC-F15-01 UC26 upload 72 previews + 2 links, deliver -> READY_FOR_HANDOVER, archive has 2 rolls")
  void tcF1501_uploadPreviewsAndDeliver() throws Exception {
    long orderId = createOrderAtQc(photographer, 2);
    List<Long> rolls = rollIds(orderId);
    byte[] firstPreview = jpeg(320, 213, 1);

    for (Long rollId : rolls) {
      List<byte[]> batch1 = new ArrayList<>();
      batch1.add(firstPreview);
      for (int i = 1; i < 20; i++) {
        batch1.add(jpeg(32, 24, i));
      }
      uploadScans(owner, orderId, rollId, batch1)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(20)));
      List<byte[]> batch2 = new ArrayList<>();
      for (int i = 0; i < 16; i++) {
        batch2.add(jpeg(24, 32, 100 + i));
      }
      uploadScans(owner, orderId, rollId, batch2)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(36)))
          .andExpect(jsonPath("$[35].frameNumber").value(36))
          .andExpect(jsonPath("$[0].url", notNullValue()));
      setLink(owner, orderId, rollId, "https://drive.example.com/roll-" + rollId)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(rollId))
          .andExpect(jsonPath("$.previewScanCount").value(36))
          .andExpect(jsonPath("$.fullResolutionLink").value("https://drive.example.com/roll-" + rollId));
    }
    assertThat(scanCount(orderId)).isEqualTo(72);

    deliver(owner, orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READY_FOR_HANDOVER"))
        .andExpect(jsonPath("$.rolls[0].previewScanCount").value(36))
        .andExpect(jsonPath("$.rolls[1].fullResolutionLink", notNullValue()));
    assertThat(statusOf(orderId)).isEqualTo("READY_FOR_HANDOVER");

    String list =
        mockMvc
            .perform(get(ARCHIVE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.content[0].orderCode").value(orderCode(orderId)))
            .andExpect(jsonPath("$.content[0].labName").value("Lab Scan"))
            .andExpect(jsonPath("$.content[0].filmStockName").value("Kodak Gold 200"))
            .andExpect(jsonPath("$.content[0].filmFormatName").value("35mm"))
            .andExpect(jsonPath("$.content[0].previewCount").value(36))
            .andExpect(jsonPath("$.content[0].deliveredAt", notNullValue()))
            .andExpect(jsonPath("$.content[1].previewCount").value(36))
            .andExpect(jsonPath("$.content[1].coverPreviewUrl", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    // Ảnh bìa = khung 1 của cuộn, tải được qua link ký, đúng nội dung.
    String coverUrl = JsonPath.read(list, "$.content[0].coverPreviewUrl");
    byte[] downloaded = download(coverUrl);
    assertThat(downloaded).isEqualTo(firstPreview);

    long archiveRollId = ((Number) JsonPath.read(list, "$.content[0].archiveRollId")).longValue();
    String detail =
        getArchiveRoll(photographer, archiveRollId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previews", hasSize(36)))
            .andExpect(jsonPath("$.previews[0].frameNumber").value(1))
            .andExpect(jsonPath("$.previews[0].widthPx").value(320))
            .andExpect(jsonPath("$.previews[0].heightPx").value(213))
            .andExpect(jsonPath("$.previews[0].checksumSha256").value(sha256(firstPreview)))
            .andExpect(jsonPath("$.fullResolutionLink", containsString("https://drive.example.com/roll-")))
            .andExpect(jsonPath("$.linkVerifiedAt", notNullValue()))
            .andExpect(jsonPath("$.orderCode").value(orderCode(orderId)))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    String lastUrl = JsonPath.read(detail, "$.previews[35].url");
    assertThat(download(lastUrl)).isNotEmpty();
  }

  /**
   * TC-SM05-12 (UC26): đơn {@code IN_PROCESSING} ở QC, ảnh xem trước + link đã gửi, không còn nợ →
   * {@code READY_FOR_HANDOVER}; ghi {@code delivered_at}, audit, báo Photographer.
   */
  @Test
  @DisplayName("TC-SM05-12 UC26 scans delivered, nothing owed -> READY_FOR_HANDOVER")
  void tcSm0512_deliverScans() throws Exception {
    long orderId = createOrderAtQc(photographer, 1);
    long rollId = rollIds(orderId).get(0);
    uploadScans(owner, orderId, rollId, List.of(jpeg(16, 16, 1), jpeg(16, 16, 2)))
        .andExpect(status().isOk());
    setLink(owner, orderId, rollId, "https://drive.example.com/a").andExpect(status().isOk());
    Timestamp linkTime =
        jdbcTemplate.queryForObject(
            "SELECT link_verified_at FROM archive_rolls WHERE film_roll_id = ?",
            Timestamp.class,
            rollId);
    clearInvocations(notificationService);

    deliver(owner, orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READY_FOR_HANDOVER"));

    assertThat(statusOf(orderId)).isEqualTo("READY_FOR_HANDOVER");
    Timestamp deliveredAt =
        jdbcTemplate.queryForObject(
            "SELECT delivered_at FROM archive_rolls WHERE film_roll_id = ?", Timestamp.class, rollId);
    assertThat(deliveredAt).isAfterOrEqualTo(linkTime);
    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "SELECT before_data ->> 'status' AS before_status, after_data ->> 'status' AS after_status,"
                + " after_data ->> 'previewScanCount' AS previews"
                + " FROM audit_logs WHERE action = 'ORDER_SCANS_DELIVERED' AND target_id = ?"
                + " AND actor_account_id = ?",
            orderId,
            owner.getId());
    assertThat(audit.get("before_status")).isEqualTo("IN_PROCESSING");
    assertThat(audit.get("after_status")).isEqualTo("READY_FOR_HANDOVER");
    assertThat(audit.get("previews")).isEqualTo("2");
    verify(notificationService)
        .notify(
            eq(photographer.getId()),
            eq(NotificationType.ORDER_STATUS),
            eq("Đơn " + orderCode(orderId) + " đã có ảnh scan"),
            any(),
            any());

    // Giao lần hai → 409.
    deliver(owner, orderId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));
  }

  // --- Giao scan: lỗi ---

  @Test
  void deliver_beforeQc_returns422() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    long rollId = rollIds(orderId).get(0);
    uploadScans(owner, orderId, rollId, List.of(jpeg(16, 16, 1))).andExpect(status().isOk());
    setLink(owner, orderId, rollId, "https://drive.example.com/a").andExpect(status().isOk());

    deliver(owner, orderId)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STAGE_OUT_OF_ORDER"))
        .andExpect(jsonPath("$.currentStage").value("RECEIVED"))
        .andExpect(jsonPath("$.detail", containsString("QC")));

    assertThat(statusOf(orderId)).isEqualTo("IN_PROCESSING");
  }

  /** Cuộn 1 thiếu link, cuộn 2 thiếu ảnh → 422 liệt kê cả hai. */
  @Test
  void deliver_missingPreviewOrLink_returns422ListingRolls() throws Exception {
    long orderId = createOrderAtQc(photographer, 2);
    List<Long> rolls = rollIds(orderId);
    uploadScans(owner, orderId, rolls.get(0), List.of(jpeg(16, 16, 1))).andExpect(status().isOk());
    setLink(owner, orderId, rolls.get(1), "https://drive.example.com/b").andExpect(status().isOk());

    deliver(owner, orderId)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ARCHIVE_DELIVERY_INCOMPLETE"))
        .andExpect(jsonPath("$.incompleteRolls", hasSize(2)))
        .andExpect(jsonPath("$.incompleteRolls[0].filmRollId").value(rolls.get(0)))
        .andExpect(jsonPath("$.incompleteRolls[0].missingPreviewScan").value(false))
        .andExpect(jsonPath("$.incompleteRolls[0].missingFullResolutionLink").value(true))
        .andExpect(jsonPath("$.incompleteRolls[1].filmRollId").value(rolls.get(1)))
        .andExpect(jsonPath("$.incompleteRolls[1].missingPreviewScan").value(true))
        .andExpect(jsonPath("$.incompleteRolls[1].missingFullResolutionLink").value(false))
        .andExpect(jsonPath("$.detail", containsString("cuộn #1 thiếu link file gốc")))
        .andExpect(jsonPath("$.detail", containsString("cuộn #2 thiếu ảnh xem trước")));

    assertThat(statusOf(orderId)).isEqualTo("IN_PROCESSING");
  }

  // --- Upload ảnh xem trước: lỗi ---

  /** PNG / JPEG > 2048 px / file không phải ảnh → lỗi, không file nào được lưu (DB lẫn storage). */
  @Test
  void upload_invalidFiles_rejectedAndNothingStored() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    long rollId = rollIds(orderId).get(0);
    byte[] png = image(16, 16, "png", 1);
    byte[] tooWide = jpeg(2049, 8, 1);
    byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
    long blobsBefore = previewBlobCount();

    uploadScans(owner, orderId, rollId, List.of(jpeg(16, 16, 1), png))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("STORAGE_FILE_TYPE_NOT_ALLOWED"))
        .andExpect(jsonPath("$.position").value(2));
    uploadScans(owner, orderId, rollId, List.of(jpeg(16, 16, 1), tooWide))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ARCHIVE_PREVIEW_INVALID"))
        .andExpect(jsonPath("$.detail", containsString("2049")));
    uploadScans(owner, orderId, rollId, List.of(text))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("STORAGE_FILE_TYPE_NOT_ALLOWED"));

    assertThat(scanCount(orderId)).isZero();
    assertThat(previewBlobCount()).isEqualTo(blobsBefore);
  }

  @Test
  void upload_noFiles_returns400() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    long rollId = rollIds(orderId).get(0);

    uploadScans(owner, orderId, rollId, List.of())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
  }

  /** Số khung tăng liên tục qua hai lần upload. */
  @Test
  void upload_twice_frameNumbersContinue() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    long rollId = rollIds(orderId).get(0);

    uploadScans(owner, orderId, rollId, List.of(jpeg(8, 8, 1), jpeg(8, 8, 2)))
        .andExpect(status().isOk());
    uploadScans(owner, orderId, rollId, List.of(jpeg(8, 8, 3), jpeg(8, 8, 4), jpeg(8, 8, 5)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(5)));

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT frame_number FROM scan_files WHERE film_roll_id = ? ORDER BY id",
                Integer.class,
                rollId))
        .containsExactly(1, 2, 3, 4, 5);
    // Chi tiết đơn của lab: số ảnh của cuộn.
    mockMvc
        .perform(
            get(LAB_ORDERS_PATH + "/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolls[0].previewScanCount").value(5))
        .andExpect(jsonPath("$.rolls[0].fullResolutionLink").value(nullValue()));
  }

  // --- Link file gốc ---

  @Test
  void setLink_unreachable_returns422AndNothingSaved() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    long rollId = rollIds(orderId).get(0);
    when(fullResolutionLinkVerifier.verify("https://drive.example.com/missing"))
        .thenReturn(LinkVerification.failed("Link trả mã HTTP 404, không truy cập được."));

    setLink(owner, orderId, rollId, "https://drive.example.com/missing")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ARCHIVE_LINK_UNREACHABLE"))
        .andExpect(jsonPath("$.detail", containsString("404")));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM archive_rolls WHERE film_roll_id = ?", Integer.class, rollId))
        .isZero();
  }

  @Test
  void setLink_notHttp_returns400WithoutCallingVerifier() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    long rollId = rollIds(orderId).get(0);

    setLink(owner, orderId, rollId, "ftp://files.example.com/a.zip")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    verify(fullResolutionLinkVerifier, never()).verify(anyString());
  }

  // --- Quyền và trạng thái ---

  @Test
  void upload_orderOfOtherLab_returns404() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    long rollId = rollIds(orderId).get(0);
    UserAccount otherOwner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    createReadyLab(otherOwner, "Lab Khác");

    uploadScans(otherOwner, orderId, rollId, List.of(jpeg(8, 8, 1)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    deliver(otherOwner, orderId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    assertThat(scanCount(orderId)).isZero();
  }

  @Test
  void upload_orderNotInProcessing_returns409() throws Exception {
    long orderId = createOrderAwaitingMachine(photographer, 1);
    long rollId = rollIds(orderId).get(0);

    uploadScans(owner, orderId, rollId, List.of(jpeg(8, 8, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_CONFLICT"));
    setLink(owner, orderId, rollId, "https://drive.example.com/a")
        .andExpect(status().isConflict());
    assertThat(scanCount(orderId)).isZero();
  }

  @Test
  void upload_rollOfAnotherOrder_returns404() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    long otherOrderId = createOrderInProcessing(photographer, 1);
    long otherRollId = rollIds(otherOrderId).get(0);

    uploadScans(owner, orderId, otherRollId, List.of(jpeg(8, 8, 1)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_ROLL_NOT_FOUND"));
    setLink(owner, orderId, otherRollId, "https://drive.example.com/a")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORDER_ROLL_NOT_FOUND"));
  }

  @Test
  void photographerCallsLabEndpoints_returns403() throws Exception {
    long orderId = createOrderInProcessing(photographer, 1);
    long rollId = rollIds(orderId).get(0);

    uploadScans(photographer, orderId, rollId, List.of(jpeg(8, 8, 1)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    setLink(photographer, orderId, rollId, "https://drive.example.com/a")
        .andExpect(status().isForbidden());
    deliver(photographer, orderId).andExpect(status().isForbidden());
  }

  @Test
  void labOwnerCallsMyArchive_returns403() throws Exception {
    mockMvc
        .perform(get(ARCHIVE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    getArchiveRoll(owner, 1L).andExpect(status().isForbidden());
  }

  /**
   * Trước khi giao scan: dòng archive đã tạo (lab gửi link) nhưng Photographer không thấy trong
   * danh sách, chi tiết 404, chi tiết đơn không có archiveRollId. Sau khi giao: Photographer khác
   * vẫn 404.
   */
  @Test
  void archive_hiddenBeforeDelivery_andFromOtherPhotographers() throws Exception {
    long orderId = createOrderAtQc(photographer, 1);
    long rollId = rollIds(orderId).get(0);
    uploadScans(owner, orderId, rollId, List.of(jpeg(8, 8, 1))).andExpect(status().isOk());
    setLink(owner, orderId, rollId, "https://drive.example.com/a").andExpect(status().isOk());
    long archiveRollId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM archive_rolls WHERE film_roll_id = ?", Long.class, rollId);

    mockMvc
        .perform(get(ARCHIVE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    getArchiveRoll(photographer, archiveRollId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ARCHIVE_ROLL_NOT_FOUND"));
    getOrder(photographer, orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolls[0].archiveRollId").value(nullValue()))
        .andExpect(jsonPath("$.rolls[0].fullResolutionLink").value(nullValue()));

    deliver(owner, orderId).andExpect(status().isOk());

    getOrder(photographer, orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolls[0].archiveRollId").value(archiveRollId));
    getArchiveRoll(photographer, archiveRollId).andExpect(status().isOk());
    UserAccount stranger = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);
    getArchiveRoll(stranger, archiveRollId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ARCHIVE_ROLL_NOT_FOUND"));
    mockMvc
        .perform(get(ARCHIVE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    getArchiveRoll(photographer, archiveRollId + 1_000_000)
        .andExpect(status().isNotFound());
  }

  // --- helpers: dựng đơn ---

  /** Đơn {@code rollCount} cuộn đã ghi đủ công đoạn tới QC. */
  private long createOrderAtQc(UserAccount account, int rollCount) throws Exception {
    long orderId = createOrderInProcessing(account, rollCount);
    for (String stage : NEXT_STAGES) {
      mockMvc
          .perform(
              post(LAB_ORDERS_PATH + "/" + orderId + "/stages")
                  .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"stage\":\"" + stage + "\"}"))
          .andExpect(status().isOk());
    }
    return orderId;
  }

  private long createOrderInProcessing(UserAccount account, int rollCount) throws Exception {
    long orderId = createOrderAwaitingMachine(account, rollCount);
    mockMvc
        .perform(
            post(LAB_ORDERS_PATH + "/" + orderId + "/schedule-confirmation")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk());
    return orderId;
  }

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

  private long readyPackage(long filmLabId) {
    long id =
        fixture.insertPackage(
            filmLabId, "C-41 35mm", "DEVELOPING", "C41", "135", 80_000, "ON_SALE");
    fixture.insertScanOption(id, "M", 30_000);
    fixture.setAcceptingOrders(filmLabId, true);
    return id;
  }

  /** Dòng phim chọn từ danh mục (Kodak Gold 200) để kiểm tên dòng phim trong archive. */
  private long createOrder(UserAccount account, int rollCount) throws Exception {
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

  private void receiveAll(long orderId) throws Exception {
    String items =
        String.join(
            ",",
            rollIds(orderId).stream()
                .map(id -> "{\"filmRollId\":" + id + ",\"received\":true}")
                .toList());
    mockMvc
        .perform(
            multipart(LAB_ORDERS_PATH + "/" + orderId + "/receipt")
                .file(
                    new MockMultipartFile(
                        "data",
                        "",
                        MediaType.APPLICATION_JSON_VALUE,
                        ("{\"rolls\":[" + items + "]}").getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("photos", "a.jpg", "image/jpeg", jpeg(8, 8, 0)))
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk());
  }

  // --- helpers: API của task ---

  private ResultActions uploadScans(
      UserAccount account, long orderId, long rollId, List<byte[]> files) throws Exception {
    MockMultipartHttpServletRequestBuilder request =
        multipart(LAB_ORDERS_PATH + "/" + orderId + "/rolls/" + rollId + "/scans");
    for (int i = 0; i < files.size(); i++) {
      request.file(new MockMultipartFile("files", "frame-" + i + ".jpg", "image/jpeg", files.get(i)));
    }
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(account)));
  }

  private ResultActions setLink(UserAccount account, long orderId, long rollId, String url)
      throws Exception {
    return mockMvc.perform(
        put(LAB_ORDERS_PATH + "/" + orderId + "/rolls/" + rollId + "/full-resolution-link")
            .header(HttpHeaders.AUTHORIZATION, bearer(account))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"" + url + "\"}"));
  }

  private ResultActions deliver(UserAccount account, long orderId) throws Exception {
    return mockMvc.perform(
        post(LAB_ORDERS_PATH + "/" + orderId + "/scan-delivery")
            .header(HttpHeaders.AUTHORIZATION, bearer(account)));
  }

  private ResultActions getArchiveRoll(UserAccount account, long archiveRollId) throws Exception {
    return mockMvc.perform(
        get(ARCHIVE_PATH + "/rolls/" + archiveRollId)
            .header(HttpHeaders.AUTHORIZATION, bearer(account)));
  }

  private ResultActions getOrder(UserAccount account, long orderId) throws Exception {
    return mockMvc.perform(
        get(ORDERS_PATH + "/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(account)));
  }

  /** Tải file qua link ký (không gửi token: link ký tự đủ quyền). */
  private byte[] download(String signedUrl) throws Exception {
    return mockMvc
        .perform(get(URI.create(signedUrl)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
  }

  // --- helpers: dữ liệu ---

  private List<Long> rollIds(long orderId) {
    return jdbcTemplate.queryForList(
        "SELECT id FROM film_rolls WHERE service_order_id = ? ORDER BY roll_sequence",
        Long.class,
        orderId);
  }

  private int scanCount(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM scan_files WHERE film_roll_id IN"
            + " (SELECT id FROM film_rolls WHERE service_order_id = ?)",
        Integer.class,
        orderId);
  }

  /** Số file preview-scan trong storage local (thư mục tạm của profile test). */
  private long previewBlobCount() throws IOException {
    Path root =
        Path.of(System.getProperty("java.io.tmpdir"), "filmlink-test-storage", "preview-scan");
    if (!Files.exists(root)) {
      return 0;
    }
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(Files::isRegularFile).count();
    }
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
    return image(width, height, "jpg", seed);
  }

  private static byte[] image(int width, int height, String format, int seed) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    int color = (int) ((seed * 2_654_435_761L) & 0xFFFFFF) | 0x010101;
    for (int x = 0; x < Math.min(width, 8); x++) {
      image.setRGB(x, 0, color);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }

  private static String sha256(byte[] content) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
  }
}
