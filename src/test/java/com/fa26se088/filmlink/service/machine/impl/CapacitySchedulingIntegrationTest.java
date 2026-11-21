package com.fa26se088.filmlink.service.machine.impl;

import static com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine.VIETNAM_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.lab.FilmLabOpeningHour;
import com.fa26se088.filmlink.entity.machine.Machine;
import com.fa26se088.filmlink.entity.machine.MachineWorkingHour;
import com.fa26se088.filmlink.entity.machine.OrderCapacityAllocation;
import com.fa26se088.filmlink.entity.machine.ScheduleChange;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.entity.machine.enums.ScheduleChangeChoice;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.masterdata.FilmFormat;
import com.fa26se088.filmlink.entity.masterdata.FilmProcess;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.repository.lab.FilmLabOpeningHourRepository;
import com.fa26se088.filmlink.repository.lab.FilmLabRepository;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.repository.machine.OrderCapacityAllocationRepository;
import com.fa26se088.filmlink.repository.machine.ScheduleChangeRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmFormatRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmProcessRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.fa26se088.filmlink.service.lab.impl.FilmLabFixture;
import com.fa26se088.filmlink.service.notification.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CapacitySchedulingIntegrationTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
  private static final LocalDate TUESDAY = MONDAY.plusDays(1);
  private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
  private static final LocalDate THURSDAY = MONDAY.plusDays(3);
  private static final LocalDate FRIDAY = MONDAY.plusDays(4);
  private static final LocalDate SATURDAY = MONDAY.plusDays(5);
  private static final LocalDate NEXT_MONDAY = MONDAY.plusDays(7);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageClient storageClient;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private FilmLabRepository filmLabRepository;
  @Autowired private FilmLabOpeningHourRepository openingHourRepository;
  @Autowired private MachineRepository machineRepository;
  @Autowired private FilmProcessRepository filmProcessRepository;
  @Autowired private FilmFormatRepository filmFormatRepository;
  @Autowired private OrderCapacityAllocationRepository allocationRepository;
  @Autowired private ScheduleChangeRepository scheduleChangeRepository;

  @MockitoBean private NotificationService notificationService;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  private FilmLabFixture fixture;
  private Long labId;
  private Long p1Id;
  private UserAccount owner;
  private UserAccount photographer;
  private Long processC41Id;
  private Long format135Id;
  private Long packageId;

  @BeforeEach
  void setUp() {
    fixture =
        new FilmLabFixture(
            jdbcTemplate, userAccountRepository, eventPublisher, transactionTemplate, storageClient);

    owner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    photographer = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);
    labId = fixture.createLab(owner, "Lab D DS-05");

    FilmLab lab = filmLabRepository.findById(labId).orElseThrow();
    lab.setQcBufferHours((short) 4);
    lab.setExpressDailyCapPercent((short) 25);
    filmLabRepository.saveAndFlush(lab);

    for (short day = 1; day <= 6; day++) {
      FilmLabOpeningHour oh = new FilmLabOpeningHour();
      oh.setFilmLabId(labId);
      oh.setDayOfWeek(day);
      oh.setClosed(false);
      oh.setOpensAt(LocalTime.of(8, 0));
      oh.setClosesAt(LocalTime.of(16, 0));
      openingHourRepository.save(oh);
    }
    FilmLabOpeningHour sunday = new FilmLabOpeningHour();
    sunday.setFilmLabId(labId);
    sunday.setDayOfWeek((short) 7);
    sunday.setClosed(true);
    openingHourRepository.save(sunday);

    processC41Id = fixture.catalogId("film_processes", "C41");
    format135Id = fixture.catalogId("film_formats", "135");
    FilmProcess c41 = filmProcessRepository.findById(processC41Id).orElseThrow();
    FilmFormat f135 = filmFormatRepository.findById(format135Id).orElseThrow();

    Machine p1 = new Machine();
    p1.setFilmLabId(labId);
    p1.setMachineCode("P1-DS05");
    p1.setMachineType(MachineType.PROCESSOR);
    p1.setCapacityPerHour(2);
    p1.setStatus(MachineStatus.ACTIVE);
    p1.setFilmProcesses(Set.of(c41));
    p1.setFilmFormats(Set.of(f135));
    addStandardWorkingHours(p1);
    p1Id = machineRepository.saveAndFlush(p1).getId();

    Machine s1 = new Machine();
    s1.setFilmLabId(labId);
    s1.setMachineCode("S1-DS05");
    s1.setMachineType(MachineType.SCANNER);
    s1.setCapacityPerHour(72);
    s1.setStatus(MachineStatus.ACTIVE);
    s1.setFilmFormats(Set.of(f135));
    addStandardWorkingHours(s1);
    machineRepository.saveAndFlush(s1);

    packageId = fixture.insertPackage(labId, "C-41 Standard 5d", "DEVELOPING", "C41", "135", 80_000, "ON_SALE");
    fixture.setAcceptingOrders(labId, true);
  }

  @AfterEach
  void cleanUp() {
    if (labId != null) {
      String orderIds = "SELECT id FROM service_orders WHERE film_lab_id = " + labId;
      jdbcTemplate.update(
          "DELETE FROM schedule_changes WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update(
          "DELETE FROM schedule_changes WHERE machine_incident_id IN"
              + " (SELECT id FROM machine_incidents WHERE machine_id IN"
              + " (SELECT id FROM machines WHERE film_lab_id = ?))",
          labId);
      jdbcTemplate.update(
          "DELETE FROM machine_incidents WHERE machine_id IN"
              + " (SELECT id FROM machines WHERE film_lab_id = ?)",
          labId);
      jdbcTemplate.update("DELETE FROM order_capacity_allocations WHERE film_lab_id = ?", labId);
      transactionTemplate.executeWithoutResult(
          tx -> {
            jdbcTemplate.execute(
                "ALTER TABLE processing_stage_records"
                    + " DISABLE TRIGGER processing_stage_records_append_only");
            jdbcTemplate.update(
                "DELETE FROM processing_stage_records WHERE service_order_id IN ("
                    + orderIds
                    + ")");
            jdbcTemplate.execute(
                "ALTER TABLE processing_stage_records"
                    + " ENABLE TRIGGER processing_stage_records_append_only");
          });
      jdbcTemplate.update("DELETE FROM film_rolls WHERE service_order_id IN (" + orderIds + ")");
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", labId);
    }
    fixture.cleanUp();
  }

  @Test
  @DisplayName(
      "TC-F12-01: Tích hợp đề xuất & khóa lịch đơn Standard 3 cuộn (DS-05) -> IN_PROCESSING, promisedReturnAt = 12:00 Thứ 5, lưu allocation Thứ 4")
  void tcF1201_proposeAndConfirm_ds05Standard3Rolls_movesOrderToInProcessing() throws Exception {
    Instant receivedAt = vnTime(MONDAY, 10, 0);
    Instant expectedPromisedAt = vnTime(THURSDAY, 12, 0);
    Instant expectedCommitmentAt = vnTime(SATURDAY, 10, 0);

    // Chèn sẵn một đơn đã chiếm hết 12 cuộn Standard của ngày Thứ 3
    long busyOrderId = insertOrderAwaitingMachine(receivedAt, "IN_PROCESSING");
    OrderCapacityAllocation tuesdayBusy = new OrderCapacityAllocation();
    tuesdayBusy.setServiceOrderId(busyOrderId);
    tuesdayBusy.setFilmLabId(labId);
    tuesdayBusy.setWorkDate(TUESDAY);
    tuesdayBusy.setFilmProcessId(processC41Id);
    tuesdayBusy.setFilmFormatId(format135Id);
    tuesdayBusy.setServiceTier(ServiceTier.STANDARD);
    tuesdayBusy.setRollCount((short) 12);
    tuesdayBusy.setOrderReceivedAt(receivedAt.minusSeconds(3600));
    allocationRepository.saveAndFlush(tuesdayBusy);

    // Tạo đơn mục tiêu 3 cuộn đang ở AWAITING_MACHINE_ASSIGNMENT
    long targetOrderId = insertOrderAwaitingMachine(receivedAt, "AWAITING_MACHINE_ASSIGNMENT");
    insertReceivedRolls(targetOrderId, 3);

    String ownerBearer =
        "Bearer "
            + jwtTokenProvider.generateAccessToken(
                owner.getId(), owner.getEmail(), owner.getRole());

    // 1. Kiểm tra GET schedule-proposal trả về đúng Thứ 4 và 12:00 Thứ 5
    mockMvc
        .perform(
            get("/api/v1/lab/orders/" + targetOrderId + "/schedule-proposal")
                .header(HttpHeaders.AUTHORIZATION, ownerBearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feasible", is(true)))
        .andExpect(jsonPath("$.promisedReturnAt", is(expectedPromisedAt.toString())))
        .andExpect(jsonPath("$.tierCommitmentAt", is(expectedCommitmentAt.toString())))
        .andExpect(jsonPath("$.allocations", hasSize(1)))
        .andExpect(jsonPath("$.allocations[0].workDate", is(WEDNESDAY.toString())))
        .andExpect(jsonPath("$.allocations[0].rollCount", is(3)));

    // 2. Kiểm tra POST schedule-confirmation khóa lịch & chuyển đơn sang IN_PROCESSING
    mockMvc
        .perform(
            post("/api/v1/lab/orders/" + targetOrderId + "/schedule-confirmation")
                .header(HttpHeaders.AUTHORIZATION, ownerBearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("IN_PROCESSING")))
        .andExpect(jsonPath("$.promisedReturnAt", is(expectedPromisedAt.toString())));

    List<OrderCapacityAllocation> savedAllocations =
        allocationRepository.findByServiceOrderIdOrderByWorkDateAsc(targetOrderId);
    assertThat(savedAllocations).hasSize(1);
    assertThat(savedAllocations.getFirst().getWorkDate()).isEqualTo(WEDNESDAY);
    assertThat(savedAllocations.getFirst().getRollCount()).isEqualTo((short) 3);
    assertThat(savedAllocations.getFirst().getServiceTier()).isEqualTo(ServiceTier.STANDARD);
  }

  @Test
  @DisplayName(
      "TC-WF03: Luồng End-to-End WF-03 (Đề xuất lịch -> Khóa lịch -> Báo sự cố P1 T4-T6 -> Đẩy Thứ 7/T2 tuần sau -> Khách chọn WAIT -> Khôi phục P1 ACTIVE)")
  void tcWf03_endToEndSchedulingIncidentRescheduleAndCustomerResponse() throws Exception {
    Instant receivedAt = vnTime(MONDAY, 10, 0);
    Instant initialPromisedAt = vnTime(THURSDAY, 12, 0);
    Instant commitmentAt = vnTime(SATURDAY, 10, 0);
    Instant rescheduledPromisedAt = vnTime(NEXT_MONDAY, 12, 0);

    // Chèn đơn đã chiếm hết 12 cuộn Standard của Thứ 3
    long busyOrderId = insertOrderAwaitingMachine(receivedAt, "IN_PROCESSING");
    OrderCapacityAllocation tuesdayBusy = new OrderCapacityAllocation();
    tuesdayBusy.setServiceOrderId(busyOrderId);
    tuesdayBusy.setFilmLabId(labId);
    tuesdayBusy.setWorkDate(TUESDAY);
    tuesdayBusy.setFilmProcessId(processC41Id);
    tuesdayBusy.setFilmFormatId(format135Id);
    tuesdayBusy.setServiceTier(ServiceTier.STANDARD);
    tuesdayBusy.setRollCount((short) 12);
    tuesdayBusy.setOrderReceivedAt(receivedAt.minusSeconds(3600));
    allocationRepository.saveAndFlush(tuesdayBusy);

    // Bước 1: Đơn mục tiêu 3 cuộn đang ở AWAITING_MACHINE_ASSIGNMENT
    long targetOrderId = insertOrderAwaitingMachine(receivedAt, "AWAITING_MACHINE_ASSIGNMENT");
    insertReceivedRolls(targetOrderId, 3);

    String ownerBearer =
        "Bearer "
            + jwtTokenProvider.generateAccessToken(
                owner.getId(), owner.getEmail(), owner.getRole());
    String photographerBearer =
        "Bearer "
            + jwtTokenProvider.generateAccessToken(
                photographer.getId(), photographer.getEmail(), photographer.getRole());

    // Bước 2: Đề xuất lịch -> Thứ 4, hẹn trả 12:00 Thứ 5
    mockMvc
        .perform(
            get("/api/v1/lab/orders/" + targetOrderId + "/schedule-proposal")
                .header(HttpHeaders.AUTHORIZATION, ownerBearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feasible", is(true)))
        .andExpect(jsonPath("$.promisedReturnAt", is(initialPromisedAt.toString())))
        .andExpect(jsonPath("$.tierCommitmentAt", is(commitmentAt.toString())))
        .andExpect(jsonPath("$.allocations[0].workDate", is(WEDNESDAY.toString())));

    // Bước 3: Khóa lịch -> IN_PROCESSING
    mockMvc
        .perform(
            post("/api/v1/lab/orders/" + targetOrderId + "/schedule-confirmation")
                .header(HttpHeaders.AUTHORIZATION, ownerBearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("IN_PROCESSING")))
        .andExpect(jsonPath("$.promisedReturnAt", is(initialPromisedAt.toString())));

    // Bước 4: Khai báo sự cố máy P1 hỏng từ 08:00 Thứ 4 đến 16:00 Thứ 6 (DS-06)
    String incidentRequest =
        """
        {
          "cause": "MACHINE_FAILURE",
          "description": "Hỏng trục hóa chất máy P1 cần thay linh kiện",
          "unavailableFrom": "%s",
          "expectedAvailableAt": "%s"
        }
        """
            .formatted(vnTime(WEDNESDAY, 8, 0), vnTime(FRIDAY, 16, 0));

    String incidentJson =
        mockMvc
            .perform(
                post("/api/v1/film-labs/me/machines/" + p1Id + "/incidents")
                    .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(incidentRequest))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.machineStatusAfter", is("MAINTENANCE")))
            .andExpect(jsonPath("$.affectedOrderCount", is(1)))
            .andExpect(jsonPath("$.scheduleChanges", hasSize(1)))
            .andExpect(jsonPath("$.scheduleChanges[0].serviceOrderId", is((int) targetOrderId)))
            .andExpect(jsonPath("$.scheduleChanges[0].oldPromisedAt", is(initialPromisedAt.toString())))
            .andExpect(
                jsonPath("$.scheduleChanges[0].newPromisedAt", is(rescheduledPromisedAt.toString())))
            .andExpect(jsonPath("$.scheduleChanges[0].requiresResponse", is(true)))
            .andExpect(jsonPath("$.scheduleChanges[0].responseDueAt", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    JsonNode incidentNode = objectMapper.readTree(incidentJson);
    long incidentId = incidentNode.get("id").asLong();
    long scheduleChangeId = incidentNode.get("scheduleChanges").get(0).get("id").asLong();

    // Bước 5: Photographer chọn WAIT -> 204 No Content, lưu DB, gọi lại lần 2 -> 409 Conflict
    mockMvc
        .perform(
            post("/api/v1/orders/" + targetOrderId + "/schedule-changes/" + scheduleChangeId + "/answer")
                .header(HttpHeaders.AUTHORIZATION, photographerBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"choice\":\"WAIT\",\"note\":\"Tôi đồng ý chờ đến Thứ 2 tuần sau\"}"))
        .andExpect(status().isNoContent());

    ScheduleChange savedChange = scheduleChangeRepository.findById(scheduleChangeId).orElseThrow();
    assertThat(savedChange.getCustomerChoice()).isEqualTo(ScheduleChangeChoice.WAIT);
    assertThat(savedChange.getCustomerNote()).isEqualTo("Tôi đồng ý chờ đến Thứ 2 tuần sau");
    assertThat(savedChange.getRespondedAt()).isNotNull();

    mockMvc
        .perform(
            post("/api/v1/orders/" + targetOrderId + "/schedule-changes/" + scheduleChangeId + "/answer")
                .header(HttpHeaders.AUTHORIZATION, photographerBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"choice\":\"WAIT\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode", is("ORDER_STATUS_CONFLICT")));

    // Bước 6: Lab giải quyết xong sự cố -> P1 khôi phục ACTIVE
    mockMvc
        .perform(
            post("/api/v1/film-labs/me/machines/" + p1Id + "/incidents/" + incidentId + "/resolve")
                .header(HttpHeaders.AUTHORIZATION, ownerBearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.machineStatusAfter", is("ACTIVE")))
        .andExpect(jsonPath("$.resolvedAt", notNullValue()));
  }

  private long insertOrderAwaitingMachine(Instant priceLockedAt, String status) {
    String code = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO service_orders
            (order_code, idempotency_key, photographer_account_id, film_lab_id,
             status, service_tier, film_handover_method, payment_method,
             estimated_total_vnd, final_total_vnd, price_locked_at, expected_return_at)
        VALUES (?, ?, ?, ?, ?, 'STANDARD', 'SELF_DROP_OFF', 'PAY_AT_LAB',
                240000, 240000, ?, ?)
        RETURNING id
        """,
        Long.class,
        code,
        UUID.randomUUID(),
        photographer.getId(),
        labId,
        status,
        Timestamp.from(priceLockedAt),
        Timestamp.from(priceLockedAt.plusSeconds(5L * 86400)));
  }

  private void insertReceivedRolls(long orderId, int count) {
    for (int seq = 1; seq <= count; seq++) {
      jdbcTemplate.update(
          """
          INSERT INTO film_rolls
              (service_order_id, roll_sequence, film_stock_name_text, film_format_id,
               film_process_id, service_package_id, exposure_count, unit_price_vnd, status)
          VALUES (?, ?, 'Kodak Gold 200', ?, ?, ?, 36, 80000, 'RECEIVED')
          """,
          orderId,
          seq,
          format135Id,
          processC41Id,
          packageId);
    }
  }

  private static void addStandardWorkingHours(Machine machine) {
    for (short day = 1; day <= 6; day++) {
      MachineWorkingHour wh = new MachineWorkingHour();
      wh.setDayOfWeek(day);
      wh.setClosed(false);
      wh.setOpensAt(LocalTime.of(8, 0));
      wh.setClosesAt(LocalTime.of(16, 0));
      machine.addWorkingHour(wh);
    }
    MachineWorkingHour sunday = new MachineWorkingHour();
    sunday.setDayOfWeek((short) 7);
    sunday.setClosed(true);
    machine.addWorkingHour(sunday);
  }

  private static Instant vnTime(LocalDate date, int hour, int minute) {
    return ZonedDateTime.of(date, LocalTime.of(hour, minute), VIETNAM_ZONE).toInstant();
  }
}
