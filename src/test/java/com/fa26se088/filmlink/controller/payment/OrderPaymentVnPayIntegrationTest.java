package com.fa26se088.filmlink.controller.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kiểm thử tích hợp đầu-cuối {@code ServiceOrder ↔ Payment (VNPay)} (F-17, UC30, WF-01 bước 1–3)
 * trên PostgreSQL 18 thật với {@code VnPayGatewayClient} và chữ ký HMAC-SHA512 chuẩn VNPay 2.1.0.
 */
@SpringBootTest(
    properties = {
      "filmlink.payment.provider=vnpay",
      "filmlink.vnpay.tmn-code=TESTTMN1",
      "filmlink.vnpay.hash-secret=test-vnpay-hmac-key-for-f17-integration",
      "filmlink.vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
      "filmlink.vnpay.return-url=http://localhost:5173/payment/vnpay-return"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderPaymentVnPayIntegrationTest {

  private static final String TEST_HMAC_KEY = "test-vnpay-hmac-key-for-f17-integration";
  private static final String ORDERS_PATH = "/api/v1/orders";
  private static final String PAYMENTS_PATH = "/api/v1/payments";

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

  @BeforeEach
  void setUp() {
    fixture =
        new FilmLabFixture(
            jdbcTemplate, userAccountRepository, eventPublisher, transactionTemplate, storageClient);
    owner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    photographer = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);
    labId = fixture.createLab(owner, "Lab VNPay F17");
    labIds.add(labId);
    fixture.insertReadyMachines(labId);
    packageId =
        fixture.insertPackage(labId, "C-41 35mm", "DEVELOPING", "C41", "135", 60_000, "ON_SALE");
    fixture.insertScanOption(packageId, "L", 50_000);
    scanLId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM service_package_scan_options"
                + " WHERE service_package_id = ? AND resolution_label = 'L'",
            Long.class,
            packageId);
    fixture.setAcceptingOrders(labId, true);
    format135Id = fixture.catalogId("film_formats", "135");
  }

  @AfterEach
  void cleanUp() {
    for (Long id : labIds) {
      String orderIds = "SELECT id FROM service_orders WHERE film_lab_id = " + id;
      String paymentIds = "SELECT id FROM payments WHERE service_order_id IN (" + orderIds + ")";
      transactionTemplate.executeWithoutResult(
          tx -> {
            jdbcTemplate.execute(
                "ALTER TABLE payment_gateway_events"
                    + " DISABLE TRIGGER payment_gateway_events_append_only");
            jdbcTemplate.update(
                "DELETE FROM payment_gateway_events WHERE payment_id IN ("
                    + paymentIds
                    + ") OR transaction_code LIKE 'F17%'");
            jdbcTemplate.execute(
                "ALTER TABLE payment_gateway_events"
                    + " ENABLE TRIGGER payment_gateway_events_append_only");
          });
      jdbcTemplate.update(
          "DELETE FROM payments WHERE service_order_id IN ("
              + orderIds
              + ") AND refunded_payment_id IS NOT NULL");
      jdbcTemplate.update("DELETE FROM payments WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM film_rolls WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", id);
    }
    labIds.clear();
    fixture.cleanUp();
  }

  @Test
  @DisplayName("TC-SM08-01 UC30 create ONLINE order -> PENDING payment and VNPay URL returned")
  void tcSm0801_createOnlineOrder_createsPendingPaymentAndVnPayUrl() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post(ORDERS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(photographer))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onlineOrderJson(UUID.randomUUID(), 2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.estimatedTotalVnd").value(220_000))
            .andExpect(jsonPath("$.payment.paymentId", notNullValue()))
            .andExpect(jsonPath("$.payment.transactionCode", notNullValue()))
            .andExpect(
                jsonPath(
                    "$.payment.paymentUrl",
                    startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?")))
            .andExpect(jsonPath("$.payment.paymentUrl", containsString("vnp_Amount=22000000")))
            .andExpect(jsonPath("$.payment.paymentUrl", containsString("vnp_SecureHash=")))
            .andReturn();

    String body = created.getResponse().getContentAsString(StandardCharsets.UTF_8);
    long orderId = ((Number) JsonPath.read(body, "$.id")).longValue();
    String txnRef = JsonPath.read(body, "$.payment.transactionCode");

    mockMvc
        .perform(
            get(ORDERS_PATH + "/" + orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
        .andExpect(jsonPath("$.payment.transactionCode").value(txnRef))
        .andExpect(jsonPath("$.payment.status").value("PENDING"))
        .andExpect(jsonPath("$.payment.amountVnd").value(220_000))
        .andExpect(jsonPath("$.payment.netPaidVnd").value(0))
        .andExpect(jsonPath("$.payment.paymentUrl", startsWith("https://sandbox.vnpayment.vn")));

    mockMvc
        .perform(
            get(PAYMENTS_PATH + "/by-order/" + orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].transactionCode").value(txnRef))
        .andExpect(jsonPath("$[0].status").value("PENDING"))
        .andExpect(jsonPath("$[0].paymentUrl", startsWith("https://sandbox.vnpayment.vn")));
  }

  @Test
  @DisplayName("TC-SM08-02 UC30 valid VNPay IPN -> payment SUCCEEDED")
  void tcSm0802_validVnPayIpn_marksPaymentSucceeded() throws Exception {
    CreatedOrder order = createOnlineOrder(1); // 110,000 VND

    mockMvc
        .perform(signedIpnGet(order.txnRef(), 110_000L, "00", "00", "VNP10001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("00"));

    mockMvc
        .perform(
            get(PAYMENTS_PATH + "/by-code/" + order.txnRef())
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.gatewayTransactionNo").value("VNP10001"))
        .andExpect(jsonPath("$.succeededAt", notNullValue()));
  }

  @Test
  @DisplayName(
      "TC-F17-01 Online order 220,000 VND paid via VNPay IPN -> payment SUCCEEDED and order"
          + " AWAITING_LAB_CONFIRMATION")
  void tcF1701_onlineOrderPaidViaVnPayIpn_transitionsOrderAndHandlesDuplicateIpn()
      throws Exception {
    CreatedOrder order = createOnlineOrder(2); // 220,000 VND
    clearInvocations(notificationService);

    // 1. VNPay gọi IPN lần 1 -> RspCode 00, Payment SUCCEEDED, Order -> AWAITING_LAB_CONFIRMATION
    mockMvc
        .perform(signedIpnGet(order.txnRef(), 220_000L, "00", "00", "VNP220000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("00"))
        .andExpect(jsonPath("$.Message").value("Confirm Success"));

    // 2. VNPay gọi lặp lại đúng IPN đó lần 2 -> không lỗi unique event, trả RspCode 02
    mockMvc
        .perform(signedIpnGet(order.txnRef(), 220_000L, "00", "00", "VNP220000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("02"));

    // 3. Trình duyệt redirect về /vnpay/return -> trả thông tin đơn & trạng thái SUCCEEDED
    mockMvc
        .perform(signedReturnGet(order.txnRef(), 220_000L, "00", "00", "VNP220000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.serviceOrderId").value(order.orderId()))
        .andExpect(jsonPath("$.paymentStatus").value("SUCCEEDED"));

    // 4. GET /api/v1/orders/{id} phản ánh trạng thái AWAITING_LAB_CONFIRMATION & payment SUCCEEDED
    mockMvc
        .perform(
            get(ORDERS_PATH + "/" + order.orderId())
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_LAB_CONFIRMATION"))
        .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.payment.netPaidVnd").value(220_000));

    Map<String, Object> dbOrder =
        jdbcTemplate.queryForMap(
            "SELECT status, lab_response_due_at FROM service_orders WHERE id = ?",
            order.orderId());
    assertThat(dbOrder.get("status")).isEqualTo("AWAITING_LAB_CONFIRMATION");
    assertThat(dbOrder.get("lab_response_due_at")).isNotNull();

    verify(notificationService)
        .notify(eq(owner.getId()), eq(NotificationType.ORDER_STATUS), contains("SO"), any(), any());
  }

  @Test
  @DisplayName("Khách huỷ trên VNPay (24 -> FAILED) tạo lại được link mới; huỷ đơn đã trả tiền hoàn 100%")
  void recreateUrlAfterFailedAndRefundOnCancelPaidOrder() throws Exception {
    CreatedOrder order = createOnlineOrder(2); // 220,000 VND

    // Lần 1: khách bấm huỷ trên cổng VNPay (ResponseCode = 24) -> Payment FAILED, đơn vẫn AWAITING_PAYMENT
    mockMvc
        .perform(signedIpnGet(order.txnRef(), 220_000L, "24", "02", "VNPFAIL01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("00"));

    // Khách bấm "Thanh toán lại" -> sinh lượt PENDING mới
    MvcResult retried =
        mockMvc
            .perform(
                post(PAYMENTS_PATH + "/by-order/" + order.orderId() + "/payment-url")
                    .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentUrl", startsWith("https://sandbox.vnpayment.vn")))
            .andReturn();
    String secondTxnRef =
        JsonPath.read(
            retried.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.transactionCode");
    assertThat(secondTxnRef).isNotEqualTo(order.txnRef());

    // Thanh toán thành công ở lượt 2 -> đơn sang AWAITING_LAB_CONFIRMATION
    mockMvc
        .perform(signedIpnGet(secondTxnRef, 220_000L, "00", "00", "VNPOK02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("00"));

    // Khách huỷ đơn trước khi Lab nhận phim (BR-38) -> hoàn tiền 100% (ORDER_REFUND, OUT, 220,000)
    mockMvc
        .perform(
            post(ORDERS_PATH + "/" + order.orderId() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Đổi lịch chụp nên chưa tráng bộ phim này.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.payment.netPaidVnd").value(0));

    mockMvc
        .perform(
            get(PAYMENTS_PATH + "/by-order/" + order.orderId())
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)));
  }

  private CreatedOrder createOnlineOrder(int rollCount) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post(ORDERS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(photographer))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onlineOrderJson(UUID.randomUUID(), rollCount)))
            .andExpect(status().isCreated())
            .andReturn();
    String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
    long id = ((Number) JsonPath.read(body, "$.id")).longValue();
    String txnRef = JsonPath.read(body, "$.payment.transactionCode");
    return new CreatedOrder(id, txnRef);
  }

  private MockHttpServletRequestBuilder signedIpnGet(
      String txnRef,
      long amountVnd,
      String responseCode,
      String transactionStatus,
      String gatewayTxnNo) {
    return applySignedParams(
        get(PAYMENTS_PATH + "/vnpay/ipn"),
        txnRef,
        amountVnd,
        responseCode,
        transactionStatus,
        gatewayTxnNo);
  }

  private MockHttpServletRequestBuilder signedReturnGet(
      String txnRef,
      long amountVnd,
      String responseCode,
      String transactionStatus,
      String gatewayTxnNo) {
    return applySignedParams(
        get(PAYMENTS_PATH + "/vnpay/return"),
        txnRef,
        amountVnd,
        responseCode,
        transactionStatus,
        gatewayTxnNo);
  }

  private MockHttpServletRequestBuilder applySignedParams(
      MockHttpServletRequestBuilder builder,
      String txnRef,
      long amountVnd,
      String responseCode,
      String transactionStatus,
      String gatewayTxnNo) {
    SortedMap<String, String> params = new TreeMap<>();
    params.put("vnp_Amount", String.valueOf(amountVnd * 100L));
    params.put("vnp_BankCode", "NCB");
    params.put("vnp_Command", "pay");
    params.put("vnp_CurrCode", "VND");
    params.put("vnp_OrderInfo", "Thanh toan don hang FilmLink");
    params.put("vnp_PayDate", "20261128103000");
    params.put("vnp_ResponseCode", responseCode);
    params.put("vnp_TmnCode", "TESTTMN1");
    params.put("vnp_TransactionNo", gatewayTxnNo);
    params.put("vnp_TransactionStatus", transactionStatus);
    params.put("vnp_TxnRef", txnRef);
    params.put("vnp_Version", "2.1.0");

    String queryToSign =
        params.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
            .collect(Collectors.joining("&"));
    String hash = hmacSha512(queryToSign);

    params.forEach(builder::param);
    builder.param("vnp_SecureHash", hash);
    return builder;
  }

  private static String hmacSha512(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA512");
      mac.init(new SecretKeySpec(TEST_HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private String onlineOrderJson(UUID idempotencyKey, int rollCount) {
    List<String> rolls = new ArrayList<>();
    for (int i = 0; i < rollCount; i++) {
      rolls.add(
          """
          {"filmStockNameText":"Kodak Gold 200","filmFormatId":%d,"exposureCount":36,
           "servicePackageId":%d,"servicePackageScanOptionId":%d,"pushPullStops":0}
          """
              .formatted(format135Id, packageId, scanLId));
    }
    return """
        {"idempotencyKey":"%s","filmLabId":%d,"serviceTier":"STANDARD",
         "filmHandoverMethod":"SELF_DROP_OFF","paymentMethod":"ONLINE",
         "specialRequest":"Scan ấm","rolls":[%s]}
        """
        .formatted(idempotencyKey, labId, String.join(",", rolls));
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }

  private record CreatedOrder(long orderId, String txnRef) {}
}
