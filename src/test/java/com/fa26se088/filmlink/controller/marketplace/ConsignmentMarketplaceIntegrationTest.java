package com.fa26se088.filmlink.controller.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.payment.PaymentGatewayClient;
import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.scheduler.PayoutGenerationJob;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.fa26se088.filmlink.service.lab.impl.FilmLabFixture;
import com.fa26se088.filmlink.service.notification.NotificationService;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test trên PostgreSQL thật cho toàn bộ chuỗi Ký gửi Thiết bị (SPEC-02 P-06, P-07,
 * P-08 — F-20, F-21, F-22, WF-05 / DS-09).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsignmentMarketplaceIntegrationTest {

  private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageClient storageClient;
  @Autowired private PayoutGenerationJob payoutGenerationJob;

  @MockitoBean private NotificationService notificationService;
  @MockitoBean private PaymentGatewayClient paymentGatewayClient;

  private FilmLabFixture fixture;
  private final List<Long> labIds = new ArrayList<>();
  private UserAccount owner;
  private UserAccount seller;
  private UserAccount buyer;
  private long labId;

  @BeforeEach
  void setUp() {
    fixture =
        new FilmLabFixture(
            jdbcTemplate,
            userAccountRepository,
            eventPublisher,
            transactionTemplate,
            storageClient);
    owner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    seller = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);
    buyer = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);
    labId = fixture.createLab(owner, "Lab Consignment B");
    labIds.add(labId);

    when(paymentGatewayClient.provider()).thenReturn("VNPAY");
    when(paymentGatewayClient.isConfigured()).thenReturn(true);
    when(paymentGatewayClient.buildPaymentUrl(
            anyString(), anyLong(), anyString(), any(), anyString(), anyString()))
        .thenAnswer(
            i ->
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef="
                    + i.getArgument(0));
    when(paymentGatewayClient.verifySignature(any()))
        .thenAnswer(
            i -> {
              Map<?, ?> params = i.getArgument(0);
              return !"invalid".equals(params.get("vnp_SecureHash"));
            });
  }

  @AfterEach
  void cleanUp() {
    for (Long id : labIds) {
      String bookingIds = "SELECT id FROM consignment_bookings WHERE film_lab_id = " + id;
      String itemIds =
          "SELECT id FROM consignment_items WHERE consignment_booking_id IN (" + bookingIds + ")";
      String saleIds = "SELECT id FROM consignment_sales WHERE film_lab_id = " + id;
      String handoverIds =
          "SELECT id FROM consignment_handovers WHERE consignment_sale_id IN (" + saleIds + ")";

      jdbcTemplate
          .queryForList(
              "SELECT blob_key FROM consignment_item_photos WHERE consignment_item_id IN ("
                  + itemIds
                  + ")",
              String.class)
          .forEach(storageClient::delete);
      jdbcTemplate
          .queryForList(
              "SELECT blob_key FROM consignment_handover_photos WHERE consignment_handover_id IN ("
                  + handoverIds
                  + ")",
              String.class)
          .forEach(storageClient::delete);

      transactionTemplate.executeWithoutResult(
          tx -> {
            jdbcTemplate.execute(
                "ALTER TABLE payment_gateway_events DISABLE TRIGGER payment_gateway_events_append_only");
            jdbcTemplate.execute(
                "ALTER TABLE consignment_handover_photos DISABLE TRIGGER consignment_handover_photos_append_only");
            jdbcTemplate.execute(
                "ALTER TABLE consignment_handovers DISABLE TRIGGER consignment_handovers_append_only");
            jdbcTemplate.execute(
                "ALTER TABLE price_offers DISABLE TRIGGER price_offers_immutable");

            jdbcTemplate.update(
                "DELETE FROM payout_items WHERE consignment_sale_id IN (" + saleIds + ")");
            if (seller != null) {
              jdbcTemplate.update(
                  "DELETE FROM payouts WHERE recipient_account_id = ?", seller.getId());
              jdbcTemplate.update(
                  "DELETE FROM payout_accounts WHERE user_account_id = ?", seller.getId());
            }
            jdbcTemplate.update(
                "DELETE FROM commissions WHERE consignment_sale_id IN (" + saleIds + ")");
            jdbcTemplate.update(
                "DELETE FROM payment_gateway_events WHERE payment_id IN"
                    + " (SELECT id FROM payments WHERE consignment_sale_id IN ("
                    + saleIds
                    + "))");
            jdbcTemplate.update(
                "DELETE FROM payments WHERE consignment_sale_id IN (" + saleIds + ")");
            jdbcTemplate.update(
                "DELETE FROM consignment_handover_photos WHERE consignment_handover_id IN ("
                    + handoverIds
                    + ")");
            jdbcTemplate.update(
                "DELETE FROM consignment_handovers WHERE consignment_sale_id IN (" + saleIds + ")");
            jdbcTemplate.update("DELETE FROM consignment_sales WHERE film_lab_id = ?", id);
            jdbcTemplate.update("DELETE FROM consignment_listings WHERE film_lab_id = ?", id);
            jdbcTemplate.update(
                "DELETE FROM price_offers WHERE consignment_item_id IN (" + itemIds + ")");
            jdbcTemplate.update(
                "DELETE FROM condition_report_checks WHERE condition_report_id IN"
                    + " (SELECT id FROM condition_reports WHERE consignment_item_id IN ("
                    + itemIds
                    + "))");
            jdbcTemplate.update(
                "DELETE FROM condition_reports WHERE consignment_item_id IN (" + itemIds + ")");
            jdbcTemplate.update(
                "DELETE FROM consignment_item_photos WHERE consignment_item_id IN ("
                    + itemIds
                    + ")");
            jdbcTemplate.update(
                "DELETE FROM consignment_items WHERE consignment_booking_id IN ("
                    + bookingIds
                    + ")");
            jdbcTemplate.update("DELETE FROM consignment_bookings WHERE film_lab_id = ?", id);
            jdbcTemplate.update("DELETE FROM consignment_lab_settings WHERE film_lab_id = ?", id);

            jdbcTemplate.execute("ALTER TABLE price_offers ENABLE TRIGGER price_offers_immutable");
            jdbcTemplate.execute(
                "ALTER TABLE consignment_handovers ENABLE TRIGGER consignment_handovers_append_only");
            jdbcTemplate.execute(
                "ALTER TABLE consignment_handover_photos ENABLE TRIGGER consignment_handover_photos_append_only");
            jdbcTemplate.execute(
                "ALTER TABLE payment_gateway_events ENABLE TRIGGER payment_gateway_events_append_only");
          });
    }
    labIds.clear();
    fixture.cleanUp();
  }

  @Test
  @DisplayName("TC-SM13-01 UC45 booking created -> item AWAITING_DROP_OFF")
  void tcSm1301_bookingCreated_itemAwaitingDropOff() throws Exception {
    enableLabConsignment();
    bookAt10am()
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("BOOKED"))
        .andExpect(jsonPath("$.item.status").value("AWAITING_DROP_OFF"));
  }

  @Test
  @DisplayName("TC-SM13-02 UC46 check-in -> booking CHECKED_IN, item AWAITING_INSPECTION")
  void tcSm1302_checkIn_itemAwaitingInspection() throws Exception {
    enableLabConsignment();
    String bookedJson =
        bookAt10am()
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    long bookingId = ((Number) JsonPath.read(bookedJson, "$.id")).longValue();

    checkIn(bookingId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CHECKED_IN"))
        .andExpect(jsonPath("$.item.status").value("AWAITING_INSPECTION"));
  }

  @Test
  @DisplayName(
      "TC-F20-01 & TC-SM13-04 lab accepts consignment, seller books 10:00, lab checks in, report grade B + 5 checks + 4 photos, initial offer -> AWAITING_SELLER_CONFIRMATION")
  void tcF2001_bookCheckInInspectAndInitialOffer() throws Exception {
    enableLabConsignment();
    LocalDate targetDate = LocalDate.now(VIETNAM_ZONE).plusDays(2);
    mockMvc
        .perform(
            get("/api/v1/public/film-labs/" + labId + "/consignment-slots")
                .param("date", targetDate.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

    String bookedJson =
        bookAt(targetDate, LocalTime.of(10, 0))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    long bookingId = ((Number) JsonPath.read(bookedJson, "$.id")).longValue();
    long itemId = ((Number) JsonPath.read(bookedJson, "$.item.id")).longValue();

    checkIn(bookingId).andExpect(status().isOk());

    inspectGradeB5Checks4Photos(itemId)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.overallGrade").value("B"))
        .andExpect(jsonPath("$.conclusion").value("ACCEPTED"))
        .andExpect(jsonPath("$.checks", hasSize(5)))
        .andExpect(jsonPath("$.photos", hasSize(4)));

    offerPrice(itemId, 3_000_000L)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.itemStatus").value("AWAITING_SELLER_CONFIRMATION"));
  }

  @Test
  @DisplayName(
      "TC-F21-01, TC-SM13-07, TC-SM13-10 lab offers 3,000,000 -> seller accepts -> lab publishes listing -> item LISTED, listing ON_SALE with public Condition Report")
  void tcF2101_offerAcceptAndPublishPublicListing() throws Exception {
    long itemId = createInspectedItem();

    String offerJson =
        offerPrice(itemId, 3_000_000L)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    long offerId = ((Number) JsonPath.read(offerJson, "$.id")).longValue();

    mockMvc
        .perform(
            post("/api/v1/consignments/offers/" + offerId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, bearer(seller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.itemStatus").value("READY_TO_LIST"))
        .andExpect(jsonPath("$.agreedPriceVnd").value(3_000_000));

    String listingJson =
        publishListing(itemId)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ON_SALE"))
            .andExpect(jsonPath("$.itemStatus").value("LISTED"))
            .andExpect(jsonPath("$.priceVnd").value(3_000_000))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    long listingId = ((Number) JsonPath.read(listingJson, "$.id")).longValue();

    mockMvc
        .perform(get("/api/v1/public/consignment-listings/" + listingId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ON_SALE"))
        .andExpect(jsonPath("$.conditionReport.overallGrade").value("B"))
        .andExpect(jsonPath("$.conditionReport.checks", hasSize(5)))
        .andExpect(jsonPath("$.conditionReport.photos", hasSize(4)));
  }

  @Test
  @DisplayName(
      "TC-WF05, TC-F22-01, TC-SM16-01, TC-SM16-03, TC-SM16-05, TC-SM13-11 DS-09 full consignment flow: book -> inspect -> offer accepted -> list -> buyer pays online -> handover -> split 300k/150k/2.55M -> seller payout PENDING")
  void tcWf05_endToEndConsignmentMarketplaceFlow() throws Exception {
    // Người bán khai báo tài khoản nhận tiền trước
    mockMvc
        .perform(
            put("/api/v1/me/payout-account")
                .header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "bankName": "Techcombank",
                      "bankAccountNumber": "1903999888777",
                      "accountHolderName": "SELLER CONSIGNMENT"
                    }
                    """))
        .andExpect(status().isOk());

    // 1. Đặt lịch -> Check-in -> Kiểm định hạng B, 5 check, 4 ảnh
    long itemId = createInspectedItem();

    // 2. Lab đề xuất 3,000,000 VND -> Seller đồng ý -> Lab đăng tin ON_SALE
    String offerJson =
        offerPrice(itemId, 3_000_000L)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    long offerId = ((Number) JsonPath.read(offerJson, "$.id")).longValue();

    mockMvc
        .perform(
            post("/api/v1/consignments/offers/" + offerId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, bearer(seller)))
        .andExpect(status().isOk());

    String listingJson =
        publishListing(itemId)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    long listingId = ((Number) JsonPath.read(listingJson, "$.id")).longValue();

    // 3. Buyer mua ONLINE -> listing LOCKED, sale AWAITING_PAYMENT, trả link VNPay (TC-SM16-01)
    String purchaseJson =
        mockMvc
            .perform(
                post("/api/v1/consignment-listings/" + listingId + "/purchases")
                    .header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "idempotencyKey": "%s",
                          "paymentMethod": "ONLINE",
                          "handoverMethod": "PICKUP_AT_LAB"
                        }
                        """
                            .formatted(UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.listingStatus").value("LOCKED"))
            .andExpect(jsonPath("$.paymentLink.transactionCode", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    long saleId = ((Number) JsonPath.read(purchaseJson, "$.id")).longValue();
    String txnRef = JsonPath.read(purchaseJson, "$.paymentLink.transactionCode");

    // 4. VNPay IPN báo thanh toán thành công -> sale AWAITING_HANDOVER (TC-SM16-03)
    mockMvc
        .perform(
            get("/api/v1/payments/vnpay/ipn")
                .param("vnp_TxnRef", txnRef)
                .param("vnp_Amount", "300000000")
                .param("vnp_ResponseCode", "00")
                .param("vnp_TransactionStatus", "00")
                .param("vnp_TransactionNo", "VNPSALE" + saleId)
                .param("vnp_BankCode", "NCB")
                .param("vnp_SecureHash", "valid-test-signature"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("00"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM consignment_sales WHERE id = ?", String.class, saleId))
        .isEqualTo("AWAITING_HANDOVER");

    // 5. Lab bàn giao tại quầy kèm 2 ảnh -> COMPLETED, SOLD, chia 300k / 150k / 2.55M
    MockMultipartHttpServletRequestBuilder handoverReq =
        multipart("/api/v1/lab/consignments/sales/" + saleId + "/handover")
            .file(new MockMultipartFile("photos", "h1.jpg", "image/jpeg", sampleJpeg(1)))
            .file(new MockMultipartFile("photos", "h2.jpg", "image/jpeg", sampleJpeg(2)));

    mockMvc
        .perform(handoverReq.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.listingStatus").value("SOLD"))
        .andExpect(jsonPath("$.itemStatus").value("SOLD"))
        .andExpect(jsonPath("$.consignmentFeeVnd").value(300_000))
        .andExpect(jsonPath("$.commissionVnd").value(150_000))
        .andExpect(jsonPath("$.sellerPayoutVnd").value(2_550_000))
        .andExpect(jsonPath("$.handover.photos", hasSize(2)));

    Map<String, Object> commRow =
        jdbcTemplate.queryForMap(
            "SELECT kind, amount_vnd, status FROM commissions WHERE consignment_sale_id = ?",
            saleId);
    assertThat(commRow.get("kind")).isEqualTo("SALE");
    assertThat(((Number) commRow.get("amount_vnd")).longValue()).isEqualTo(150_000L);
    assertThat(commRow.get("status")).isEqualTo("SETTLED");

    // 6. Đưa return_window_ends_at về quá khứ (hết hạn đổi trả) -> chạy job Payout -> Seller Payout PENDING
    Timestamp past = Timestamp.from(Instant.now().minus(Duration.ofDays(1)));
    jdbcTemplate.update(
        "UPDATE consignment_sales SET handed_over_at = ?, return_window_ends_at = ? WHERE id = ?",
        Timestamp.from(Instant.now().minus(Duration.ofDays(8))),
        past,
        saleId);

    payoutGenerationJob.run();

    Map<String, Object> sellerPayout =
        jdbcTemplate.queryForMap(
            """
            SELECT p.recipient_type, p.status, p.amount_vnd, p.bank_account_number
            FROM payouts p
            JOIN payout_items pi ON pi.payout_id = p.id
            WHERE pi.consignment_sale_id = ?
            """,
            saleId);
    assertThat(sellerPayout.get("recipient_type")).isEqualTo("SELLER");
    assertThat(sellerPayout.get("status")).isEqualTo("PENDING");
    assertThat(((Number) sellerPayout.get("amount_vnd")).longValue()).isEqualTo(2_550_000L);
    assertThat(sellerPayout.get("bank_account_number")).isEqualTo("1903999888777");
  }

  private long createInspectedItem() throws Exception {
    enableLabConsignment();
    String bookedJson =
        bookAt10am()
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    long bookingId = ((Number) JsonPath.read(bookedJson, "$.id")).longValue();
    long itemId = ((Number) JsonPath.read(bookedJson, "$.item.id")).longValue();
    checkIn(bookingId).andExpect(status().isOk());
    inspectGradeB5Checks4Photos(itemId).andExpect(status().isCreated());
    return itemId;
  }

  private void enableLabConsignment() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/film-labs/me/consignment-settings")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "isAcceptingConsignments": true,
                      "consignmentFeeRate": 10.00,
                      "slotMinutes": 30,
                      "appointmentsPerSlot": 1
                    }
                    """))
        .andExpect(status().isOk());
  }

  private ResultActions bookAt10am() throws Exception {
    return bookAt(LocalDate.now(VIETNAM_ZONE).plusDays(1), LocalTime.of(10, 0));
  }

  private ResultActions bookAt(LocalDate date, LocalTime time) throws Exception {
    Instant slotStart = date.atTime(time).atZone(VIETNAM_ZONE).toInstant();
    return mockMvc.perform(
        post("/api/v1/consignments/bookings")
            .header(HttpHeaders.AUTHORIZATION, bearer(seller))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "filmLabId": %d,
                  "slotStartAt": "%s",
                  "item": {
                    "itemType": "CAMERA_BODY",
                    "itemName": "Nikon FM2 Chrome Body",
                    "sellerDescription": "Máy cơ hoạt động tốt, đo sáng chuẩn xác, ngoại hình sạch đẹp.",
                    "desiredPriceVnd": 3200000
                  }
                }
                """
                    .formatted(labId, slotStart)));
  }

  private ResultActions checkIn(long bookingId) throws Exception {
    return mockMvc.perform(
        post("/api/v1/lab/consignments/bookings/" + bookingId + "/check-in")
            .header(HttpHeaders.AUTHORIZATION, bearer(owner)));
  }

  private ResultActions inspectGradeB5Checks4Photos(long itemId) throws Exception {
    String json =
        """
        {
          "overallGrade": "B",
          "conclusion": "ACCEPTED",
          "checks": [
            {"checkCode": "SHUTTER", "result": "PASS"},
            {"checkCode": "LIGHT_METERING", "result": "PASS"},
            {"checkCode": "MIRROR", "result": "PASS"},
            {"checkCode": "BATTERY_COMPARTMENT", "result": "PASS"},
            {"checkCode": "LIGHT_SEALS", "result": "PASS"}
          ]
        }
        """;
    MockMultipartHttpServletRequestBuilder req =
        multipart("/api/v1/lab/consignments/items/" + itemId + "/condition-report")
            .file(
                new MockMultipartFile(
                    "data", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8)));
    for (int i = 0; i < 4; i++) {
      req.file(
          new MockMultipartFile("photos", "insp-" + i + ".jpg", "image/jpeg", sampleJpeg(10 + i)));
    }
    return mockMvc.perform(req.header(HttpHeaders.AUTHORIZATION, bearer(owner)));
  }

  private ResultActions offerPrice(long itemId, long priceVnd) throws Exception {
    return mockMvc.perform(
        post("/api/v1/lab/consignments/items/" + itemId + "/offers")
            .header(HttpHeaders.AUTHORIZATION, bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "priceVnd": %d,
                  "justification": "Thân máy hạng B ngoại hình sáng đẹp, cơ tốc màn trập và đo sáng chuẩn xác."
                }
                """
                    .formatted(priceVnd)));
  }

  private ResultActions publishListing(long itemId) throws Exception {
    return mockMvc.perform(
        post("/api/v1/lab/consignments/items/" + itemId + "/listing")
            .header(HttpHeaders.AUTHORIZATION, bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "title": "Nikon FM2 Chrome Body đã kiểm định hạng B tại Lab B",
                  "description": "Thân máy Nikon FM2 cơ hoàn toàn, đã được kiểm định kỹ lưỡng 5 hạng mục kỹ thuật và đính kèm biên bản chính thức tại Film Lab B.",
                  "isPickupAtLab": true,
                  "isDeliveryAvailable": false
                }
                """));
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }

  private static byte[] sampleJpeg(int seed) throws IOException {
    BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, seed * 1000);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", out);
    return out.toByteArray();
  }
}
