package com.fa26se088.filmlink.controller.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.storage.FullResolutionLinkVerifier;
import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.payment.ClosedOrderCommission;
import com.fa26se088.filmlink.dto.payment.response.CommissionDebtResponse;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.entity.payment.Payment;
import com.fa26se088.filmlink.entity.payment.enums.CommissionDebtStatus;
import com.fa26se088.filmlink.entity.payment.enums.PaymentDirection;
import com.fa26se088.filmlink.entity.payment.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import com.fa26se088.filmlink.entity.payment.enums.PaymentStatus;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.repository.payment.PaymentRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.fa26se088.filmlink.service.lab.impl.FilmLabFixture;
import com.fa26se088.filmlink.service.machine.CapacitySchedulingService;
import com.fa26se088.filmlink.service.machine.ScheduleChangeService;
import com.fa26se088.filmlink.service.notification.NotificationService;
import com.fa26se088.filmlink.service.payment.CommissionDebtService;
import com.fa26se088.filmlink.service.payment.CommissionService;
import com.fa26se088.filmlink.service.payment.PayoutService;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test cho SPEC-02 P-04 & P-05 (F-18, F-19, WF-02 / DS-03) trên PostgreSQL thật:
 * hoa hồng đơn đóng, chốt kỳ công nợ tháng, sinh đợt Payout sau thời gian re-scan và Admin upload
 * biên lai chuyển khoản qua HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayoutAndCommissionIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private CommissionService commissionService;
  @Autowired private CommissionDebtService commissionDebtService;
  @Autowired private PayoutService payoutService;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageClient storageClient;

  @MockitoBean private NotificationService notificationService;
  @MockitoBean private FullResolutionLinkVerifier fullResolutionLinkVerifier;
  @MockitoSpyBean private CapacitySchedulingService capacitySchedulingService;
  @MockitoSpyBean private ScheduleChangeService scheduleChangeService;

  private FilmLabFixture fixture;
  private final List<Long> labIds = new ArrayList<>();
  private UserAccount admin;
  private UserAccount owner;
  private UserAccount photographer;
  private long labId;
  private long packageId;

  @BeforeEach
  void setUp() {
    fixture =
        new FilmLabFixture(
            jdbcTemplate,
            userAccountRepository,
            eventPublisher,
            transactionTemplate,
            storageClient);
    admin = fixture.createAccount(UserAccountRole.ADMIN);
    owner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    photographer = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);
    labId = fixture.createLab(owner, "Lab Payout WF02");
    labIds.add(labId);
    fixture.insertReadyMachines(labId);
    packageId =
        fixture.insertPackage(
            labId, "C-41 135", "DEVELOPING", "C41", "135", 80_000L, "ON_SALE");
  }

  @AfterEach
  void cleanUp() {
    for (Long id : labIds) {
      String orderIds = "SELECT id FROM service_orders WHERE film_lab_id = " + id;
      jdbcTemplate
          .queryForList(
              "SELECT receipt_blob_key FROM payouts WHERE film_lab_id = ? AND receipt_blob_key IS NOT NULL",
              String.class,
              id)
          .forEach(storageClient::delete);
      jdbcTemplate.update(
          "DELETE FROM payout_items WHERE payout_id IN (SELECT id FROM payouts WHERE film_lab_id = ?)",
          id);
      jdbcTemplate.update("DELETE FROM payouts WHERE film_lab_id = ?", id);
      jdbcTemplate.update("DELETE FROM payout_accounts WHERE film_lab_id = ?", id);
      jdbcTemplate.update("DELETE FROM commissions WHERE film_lab_id = ?", id);
      jdbcTemplate.update(
          "DELETE FROM payments WHERE commission_debt_id IN (SELECT id FROM commission_debts WHERE film_lab_id = ?)",
          id);
      jdbcTemplate.update("DELETE FROM commission_debts WHERE film_lab_id = ?", id);
      jdbcTemplate.update(
          "DELETE FROM payments WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", id);
    }
    labIds.clear();
    fixture.cleanUp();
  }

  @Test
  @DisplayName(
      "TC-F18-01 lab with 2 closed PAY_AT_LAB orders in October (300,000 and 200,000) -> commissions BILLED, debt UNPAID 50,000 due 10/11")
  void tcF1801_monthlyDebtClosingForPayAtLabOrders() {
    Instant oct10 = Instant.parse("2026-10-10T08:00:00Z");
    Instant oct20 = Instant.parse("2026-10-20T08:00:00Z");
    long order1 = insertClosedOrder("PAY_AT_LAB", 300_000L, oct10);
    long order2 = insertClosedOrder("PAY_AT_LAB", 200_000L, oct20);

    commissionService.recordForClosedOrder(
        new ClosedOrderCommission(
            order1, labId, List.of(packageId), "PAY_AT_LAB", 300_000L, oct10));
    commissionService.recordForClosedOrder(
        new ClosedOrderCommission(
            order2, labId, List.of(packageId), "PAY_AT_LAB", 200_000L, oct20));

    CommissionDebtResponse debt =
        commissionDebtService.generateMonthlyDebt(labId, LocalDate.of(2026, 10, 1));

    assertThat(debt).isNotNull();
    assertThat(debt.status()).isEqualTo(CommissionDebtStatus.UNPAID);
    assertThat(debt.totalAmountVnd()).isEqualTo(50_000L);
    assertThat(debt.dueDate()).isEqualTo(LocalDate.of(2026, 11, 10));

    List<String> statuses =
        jdbcTemplate.queryForList(
            "SELECT status FROM commissions WHERE film_lab_id = ? ORDER BY id",
            String.class,
            labId);
    assertThat(statuses).containsExactly("BILLED", "BILLED");
  }

  @Test
  @DisplayName(
      "TC-SM10-01 UC32 period closed with net commission > 0 creates UNPAID commission debt")
  void tcSm1001_periodClosedCreatesUnpaidDebt() {
    Instant oct15 = Instant.parse("2026-10-15T10:00:00Z");
    long orderId = insertClosedOrder("PAY_AT_LAB", 220_000L, oct15);
    commissionService.recordForClosedOrder(
        new ClosedOrderCommission(
            orderId, labId, List.of(packageId), "PAY_AT_LAB", 220_000L, oct15));

    CommissionDebtResponse debt =
        commissionDebtService.generateMonthlyDebt(labId, LocalDate.of(2026, 10, 1));

    assertThat(debt).isNotNull();
    assertThat(debt.status()).isEqualTo(CommissionDebtStatus.UNPAID);
    assertThat(debt.totalAmountVnd()).isEqualTo(22_000L);
  }

  @Test
  @DisplayName(
      "TC-F19-01 online order CLOSED 8 days ago -> payout job PENDING -> Admin uploads receipt -> PAID")
  void tcF1901_payoutJobAndAdminMarkPaid() throws Exception {
    configureLabPayoutAccount();

    Instant closedEightDaysAgo = Instant.now().minus(Duration.ofDays(8));
    long orderId = insertClosedOrder("ONLINE", 220_000L, closedEightDaysAgo);
    insertSucceededOnlinePayment(orderId, 220_000L, closedEightDaysAgo);

    commissionService.recordForClosedOrder(
        new ClosedOrderCommission(
            orderId, labId, List.of(packageId), "ONLINE", 220_000L, closedEightDaysAgo));

    int created = payoutService.generateDuePayouts();
    assertThat(created).isGreaterThanOrEqualTo(1);
    assertThat(payoutService.generateDuePayouts()).isZero();

    String listJson =
        mockMvc
            .perform(
                get("/api/v1/admin/payouts")
                    .param("status", "PENDING")
                    .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    List<Integer> matchingIds =
        JsonPath.read(listJson, "$.content[?(@.filmLabId == " + labId + ")].id");
    assertThat(matchingIds).hasSize(1);
    long payoutId = matchingIds.getFirst().longValue();

    MockMultipartFile receipt =
        new MockMultipartFile("receipt", "receipt.png", "image/png", samplePng());
    mockMvc
        .perform(
            multipart("/api/v1/admin/payouts/" + payoutId + "/paid")
                .file(receipt)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.amountVnd").value(198_000))
        .andExpect(jsonPath("$.bankName").value("Vietcombank"))
        .andExpect(jsonPath("$.bankAccountNumber").value("0123456789"))
        .andExpect(jsonPath("$.receiptBlobKey", notNullValue()))
        .andExpect(jsonPath("$.receiptUrl", notNullValue()))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].serviceOrderId").value(orderId))
        .andExpect(jsonPath("$.items[0].amountVnd").value(198_000));
  }

  @Test
  @DisplayName(
      "TC-WF02 DS-03 end-to-end commission recorded, payout generated, Admin records bank transfer with receipt -> PAID")
  void tcWf02_endToEndCommissionAndPayoutWorkflow() throws Exception {
    configureLabPayoutAccount();

    Instant closedAt = Instant.now().minus(Duration.ofDays(8));
    long orderId = insertClosedOrder("ONLINE", 300_000L, closedAt);
    insertSucceededOnlinePayment(orderId, 300_000L, closedAt);

    // 1. Khi đơn ONLINE đóng: ghi hoa hồng 10% = 30,000 VND (DEDUCTED)
    commissionService.recordForClosedOrder(
        new ClosedOrderCommission(
            orderId, labId, List.of(packageId), "ONLINE", 300_000L, closedAt));

    // 2. Chạy job Payout hằng ngày -> tạo Payout PENDING = 300,000 - 30,000 = 270,000 VND
    assertThat(payoutService.generateDuePayouts()).isGreaterThanOrEqualTo(1);

    String myPayoutsJson =
        mockMvc
            .perform(
                get("/api/v1/film-labs/me/payouts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].status").value("PENDING"))
            .andExpect(jsonPath("$.content[0].amountVnd").value(270_000))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    long payoutId = ((Number) JsonPath.read(myPayoutsJson, "$.content[0].id")).longValue();

    // 3. Admin chuyển khoản kèm biên lai -> Payout PAID, Commission SETTLED, thông báo chủ Lab
    MockMultipartFile receipt =
        new MockMultipartFile("receipt", "wf02-transfer.png", "image/png", samplePng());
    mockMvc
        .perform(
            multipart("/api/v1/admin/payouts/" + payoutId + "/paid")
                .file(receipt)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.amountVnd").value(270_000))
        .andExpect(jsonPath("$.paidAt", notNullValue()));

    Map<String, Object> commRow =
        jdbcTemplate.queryForMap(
            "SELECT status, amount_vnd FROM commissions WHERE service_order_id = ?", orderId);
    assertThat(commRow.get("status")).isEqualTo("SETTLED");
    assertThat(((Number) commRow.get("amount_vnd")).longValue()).isEqualTo(30_000L);

    verify(notificationService)
        .notify(eq(owner.getId()), eq(NotificationType.PAYMENT), any(), any(), any());
  }

  private void configureLabPayoutAccount() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/film-labs/me/payout-account")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "bankName": "Vietcombank",
                      "bankAccountNumber": "0123456789",
                      "accountHolderName": "CONG TY FILM LAB WF02"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bankName").value("Vietcombank"));
  }

  private long insertClosedOrder(String paymentMethod, long totalVnd, Instant closedAt) {
    Timestamp ts = Timestamp.from(closedAt);
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO service_orders
            (order_code, idempotency_key, photographer_account_id, film_lab_id, status,
             service_tier, film_handover_method, payment_method,
             estimated_total_vnd, final_total_vnd, price_locked_at,
             expected_return_at, promised_return_at, machine_assignment_confirmed_at,
             accepted_at, delivered_at, closed_at)
        VALUES (?, ?, ?, ?, 'CLOSED',
                'STANDARD', 'SELF_DROP_OFF', ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?)
        RETURNING id
        """,
        Long.class,
        "ORD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
        UUID.randomUUID(),
        photographer.getId(),
        labId,
        paymentMethod,
        totalVnd,
        totalVnd,
        ts,
        ts,
        ts,
        ts,
        ts,
        ts,
        ts);
  }

  private void insertSucceededOnlinePayment(long orderId, long amountVnd, Instant paidAt) {
    Payment payment = new Payment();
    payment.setTransactionCode(
        "VNP" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase());
    payment.setPurpose(PaymentPurpose.ORDER_PAYMENT);
    payment.setDirection(PaymentDirection.IN);
    payment.setMethod(PaymentMethod.VNPAY);
    payment.setStatus(PaymentStatus.SUCCEEDED);
    payment.setAmountVnd(amountVnd);
    payment.setServiceOrderId(orderId);
    payment.setCounterpartyAccountId(photographer.getId());
    payment.setPaymentUrlExpiresAt(paidAt.plus(Duration.ofMinutes(15)));
    payment.setGatewayTransactionNo("GW-" + orderId);
    payment.setGatewayResponseCode("00");
    payment.setGatewayBankCode("NCB");
    payment.setSucceededAt(paidAt);
    paymentRepository.save(payment);
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }

  private static byte[] samplePng() throws IOException {
    BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}
