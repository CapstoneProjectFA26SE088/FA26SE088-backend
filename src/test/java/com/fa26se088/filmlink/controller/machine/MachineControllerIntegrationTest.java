package com.fa26se088.filmlink.controller.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.account.request.SubmitLabApplicationRequest;
import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.account.enums.UserAccountStatus;
import com.fa26se088.filmlink.entity.audit.AuditLog;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.machine.Machine;
import com.fa26se088.filmlink.entity.machine.MachineWorkingHour;
import com.fa26se088.filmlink.entity.machine.OrderCapacityAllocation;
import com.fa26se088.filmlink.entity.machine.ScheduleChange;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.entity.machine.enums.ScheduleChangeStep;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.masterdata.FilmFormat;
import com.fa26se088.filmlink.entity.masterdata.FilmProcess;
import com.fa26se088.filmlink.entity.masterdata.enums.CatalogStatus;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.repository.audit.AuditLogRepository;
import com.fa26se088.filmlink.repository.lab.FilmLabRepository;
import com.fa26se088.filmlink.repository.machine.MachineIncidentRepository;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.repository.machine.OrderCapacityAllocationRepository;
import com.fa26se088.filmlink.repository.machine.ScheduleChangeRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmFormatRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmProcessRepository;
import com.fa26se088.filmlink.repository.masterdata.ServiceTypeRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.fa26se088.filmlink.service.machine.impl.CapacitySchedulingEngine;
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
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MachineControllerIntegrationTest {

  private static final String API_PATH = "/api/v1/film-labs/me/machines";
  private static final String OWNER_EMAIL = "labowner.machine@filmlink.vn";
  private static final String PHOTOGRAPHER_EMAIL = "photographer.machine@filmlink.vn";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private LabRegistrationApplicationRepository applicationRepository;
  @Autowired private FilmLabRepository filmLabRepository;
  @Autowired private MachineRepository machineRepository;
  @Autowired private MachineIncidentRepository machineIncidentRepository;
  @Autowired private ScheduleChangeRepository scheduleChangeRepository;
  @Autowired private OrderCapacityAllocationRepository orderCapacityAllocationRepository;
  @Autowired private FilmProcessRepository filmProcessRepository;
  @Autowired private FilmFormatRepository filmFormatRepository;
  @Autowired private ServiceTypeRepository serviceTypeRepository;
  @Autowired private StorageClient storageClient;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private NotificationService notificationService;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  private String ownerToken;
  private String photographerToken;
  private Long photographerId;
  private Long labId;
  private Long savedP1Id;
  private Long savedS1Id;
  private Long activeProcessId;
  private Long activeFormatId;

  @BeforeEach
  void setUp() {
    cleanupTestData();

    activeProcessId =
        filmProcessRepository.findAll().stream()
            .filter(p -> p.getStatus() == CatalogStatus.ACTIVE)
            .findFirst()
            .orElseThrow()
            .getId();

    activeFormatId =
        filmFormatRepository.findAll().stream()
            .filter(f -> f.getStatus() == CatalogStatus.ACTIVE)
            .findFirst()
            .orElseThrow()
            .getId();

    UserAccount owner =
        userAccountRepository
            .findByEmail(OWNER_EMAIL)
            .orElseGet(
                () -> {
                  UserAccount u = new UserAccount();
                  u.setEmail(OWNER_EMAIL);
                  u.setFullName("Machine Lab Owner");
                  u.setPasswordHash(passwordEncoder.encode("Password123@"));
                  u.setRole(UserAccountRole.FILM_LAB_OWNER);
                  u.setStatus(UserAccountStatus.ACTIVE);
                  u.setTermsAcceptedAt(Instant.now());
                  u.setEmailVerifiedAt(Instant.now());
                  return userAccountRepository.save(u);
                });
    ownerToken =
        jwtTokenProvider.generateAccessToken(owner.getId(), owner.getEmail(), owner.getRole());

    UserAccount photographer =
        userAccountRepository
            .findByEmail(PHOTOGRAPHER_EMAIL)
            .orElseGet(
                () -> {
                  UserAccount u = new UserAccount();
                  u.setEmail(PHOTOGRAPHER_EMAIL);
                  u.setFullName("Photographer Machine Test");
                  u.setPasswordHash(passwordEncoder.encode("Password123@"));
                  u.setRole(UserAccountRole.PHOTOGRAPHER);
                  u.setStatus(UserAccountStatus.ACTIVE);
                  u.setTermsAcceptedAt(Instant.now());
                  u.setEmailVerifiedAt(Instant.now());
                  return userAccountRepository.save(u);
                });
    photographerId = photographer.getId();
    photographerToken =
        jwtTokenProvider.generateAccessToken(
            photographer.getId(), photographer.getEmail(), photographer.getRole());

    LabRegistrationApplication app =
        applicationRepository
            .findByApplicationCode("LA261119MCH001")
            .orElseGet(
                () -> {
                  LabRegistrationApplication a = new LabRegistrationApplication();
                  a.setApplicationCode("LA261119MCH001");
                  a.setLabName("Machine Test Film Lab");
                  a.setTaxCode("0399887766");
                  a.setAddressLine("100 Pasteur");
                  a.setDistrict("Quan 1");
                  a.setProvince("TP Ho Chi Minh");
                  a.setContactEmail(OWNER_EMAIL);
                  a.setContactPhone("0909112233");
                  a.setStatus(LabApplicationStatus.APPROVED);
                  a.setReviewedAt(Instant.now());
                  return applicationRepository.save(a);
                });

    Long ownerId = owner.getId();
    Long appId = app.getId();
    FilmLab lab =
        filmLabRepository
            .findByOwnerAccountId(ownerId)
            .orElseGet(
                () -> {
                  FilmLab l = new FilmLab();
                  l.setOwnerAccountId(ownerId);
                  l.setLabRegistrationApplicationId(appId);
                  l.setLabName("Machine Test Film Lab");
                  l.setAddressLine("100 Pasteur");
                  l.setDistrict("Quan 1");
                  l.setProvince("TP Ho Chi Minh");
                  l.setContactEmail(OWNER_EMAIL);
                  l.setContactPhone("0909112233");
                  return filmLabRepository.save(l);
                });
    labId = lab.getId();

    Machine p1 = new Machine();
    p1.setFilmLabId(labId);
    p1.setMachineCode("P1");
    p1.setModelName("Noritsu V30");
    p1.setMachineType(MachineType.PROCESSOR);
    p1.setCapacityPerHour(26);
    p1.setStatus(MachineStatus.ACTIVE);
    addEightHourWorkDays(p1);
    savedP1Id = machineRepository.save(p1).getId();

    Machine s1 = new Machine();
    s1.setFilmLabId(labId);
    s1.setMachineCode("S1");
    s1.setModelName("Frontier SP-3000");
    s1.setMachineType(MachineType.SCANNER);
    s1.setCapacityPerHour(327);
    s1.setStatus(MachineStatus.ACTIVE);
    addEightHourWorkDays(s1);
    savedS1Id = machineRepository.save(s1).getId();
  }

  @AfterEach
  void tearDown() {
    cleanupTestData();
  }

  private void cleanupTestData() {
    scheduleChangeRepository.deleteAll();
    orderCapacityAllocationRepository.deleteAll();
    Long targetLabId = labId;
    if (targetLabId == null) {
      targetLabId =
          userAccountRepository
              .findByEmail(OWNER_EMAIL)
              .flatMap(owner -> filmLabRepository.findByOwnerAccountId(owner.getId()))
              .map(FilmLab::getId)
              .orElse(null);
    }
    if (targetLabId != null) {
      jdbcTemplate.update(
          "DELETE FROM film_rolls WHERE service_order_id IN"
              + " (SELECT id FROM service_orders WHERE film_lab_id = ?)",
          targetLabId);
      jdbcTemplate.update("DELETE FROM service_orders WHERE film_lab_id = ?", targetLabId);
    }
    machineIncidentRepository.deleteAll();
    machineRepository.deleteAll();
  }

  private void addEightHourWorkDays(Machine machine) {
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

  @Test
  @DisplayName(
      "TC-F06-01: Film Lab Owner lấy danh sách máy kèm công suất ngày -> 200 OK (P1=208 cuộn, S1=72 cuộn)")
  void getMyMachines_asLabOwner_returnsMachinesWithCalculatedDailyCapacity() throws Exception {
    mockMvc
        .perform(get(API_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].machineCode", is("P1")))
        .andExpect(jsonPath("$[0].machineType", is("PROCESSOR")))
        .andExpect(jsonPath("$[0].capacityPerHour", is(26)))
        .andExpect(jsonPath("$[0].dailyCapacityRolls", is(208)))
        .andExpect(jsonPath("$[0].workingHours", hasSize(7)))
        .andExpect(jsonPath("$[0].workingHours[0].dayOfWeek", is(1)))
        .andExpect(jsonPath("$[0].workingHours[0].dailyCapacityRolls", is(208)))
        .andExpect(jsonPath("$[0].workingHours[6].dayOfWeek", is(7)))
        .andExpect(jsonPath("$[0].workingHours[6].closed", is(true)))
        .andExpect(jsonPath("$[0].workingHours[6].dailyCapacityRolls", is(0)))
        .andExpect(jsonPath("$[1].machineCode", is("S1")))
        .andExpect(jsonPath("$[1].machineType", is("SCANNER")))
        .andExpect(jsonPath("$[1].capacityPerHour", is(327)))
        .andExpect(jsonPath("$[1].dailyCapacityRolls", is(72)))
        .andExpect(jsonPath("$[1].workingHours[0].dailyCapacityRolls", is(72)))
        .andExpect(jsonPath("$[1].workingHours[6].dailyCapacityRolls", is(0)));
  }

  @Test
  @DisplayName(
      "TC-F06-01 & TC-SM04-01: Film Lab Owner tạo máy mới P2 (PROCESSOR) -> 201 Created, ACTIVE, tính công suất ngày = 208, ghi audit")
  void createMachine_asLabOwner_processor_returns201AndRecordsAudit() throws Exception {
    String requestBody =
        """
        {
          "machineCode": "P2",
          "modelName": "Jobo CPP-3",
          "machineType": "PROCESSOR",
          "capacityPerHour": 26,
          "filmProcessIds": [%d],
          "filmFormatIds": [%d],
          "workingHours": [
            {"dayOfWeek": 1, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 2, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 3, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 4, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 5, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 6, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 7, "closed": true}
          ]
        }
        """
            .formatted(activeProcessId, activeFormatId);

    String responseBody =
        mockMvc
            .perform(
                post(API_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.machineCode", is("P2")))
            .andExpect(jsonPath("$.modelName", is("Jobo CPP-3")))
            .andExpect(jsonPath("$.machineType", is("PROCESSOR")))
            .andExpect(jsonPath("$.status", is("ACTIVE")))
            .andExpect(jsonPath("$.capacityPerHour", is(26)))
            .andExpect(jsonPath("$.dailyCapacityRolls", is(208)))
            .andExpect(jsonPath("$.workingHours", hasSize(7)))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    Long createdMachineId = objectMapper.readTree(responseBody).get("id").asLong();

    List<AuditLog> logs =
        auditLogRepository
            .findAll(
                AuditLogRepository.byTarget(AuditTargetType.MACHINE, createdMachineId),
                PageRequest.of(0, 10))
            .getContent();
    assertThat(logs).isNotEmpty();
    assertThat(logs.getFirst().getAction()).isEqualTo(AuditAction.MACHINE_CREATED);
  }

  @Test
  @DisplayName("Tạo máy trùng machineCode (không phân biệt hoa thường) -> 409 Conflict")
  void createMachine_duplicateCode_returns409() throws Exception {
    String requestBody =
        """
        {
          "machineCode": "p1",
          "modelName": "Duplicate P1",
          "machineType": "PROCESSOR",
          "capacityPerHour": 26,
          "filmProcessIds": [%d],
          "filmFormatIds": [%d],
          "workingHours": [
            {"dayOfWeek": 1, "closed": false, "opensAt": "08:00", "closesAt": "16:00"}
          ]
        }
        """
            .formatted(activeProcessId, activeFormatId);

    mockMvc
        .perform(
            post(API_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode", is("MACHINE_CODE_DUPLICATE")));
  }

  @Test
  @DisplayName(
      "Film Lab Owner cập nhật thông tin máy P1 -> 200 OK, giữ nguyên machineCode, tính lại công suất ngày = 300, ghi audit MACHINE_UPDATED")
  void updateMachine_asLabOwner_success_returns200AndRecalculatesCapacity() throws Exception {
    String requestBody =
        """
        {
          "modelName": "Noritsu V30 Turbo",
          "capacityPerHour": 30,
          "filmProcessIds": [%d],
          "filmFormatIds": [%d],
          "workingHours": [
            {"dayOfWeek": 1, "closed": false, "opensAt": "08:00", "closesAt": "18:00"},
            {"dayOfWeek": 2, "closed": false, "opensAt": "08:00", "closesAt": "18:00"},
            {"dayOfWeek": 3, "closed": true}
          ]
        }
        """
            .formatted(activeProcessId, activeFormatId);

    mockMvc
        .perform(
            put(API_PATH + "/" + savedP1Id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(savedP1Id.intValue())))
        .andExpect(jsonPath("$.machineCode", is("P1")))
        .andExpect(jsonPath("$.modelName", is("Noritsu V30 Turbo")))
        .andExpect(jsonPath("$.capacityPerHour", is(30)))
        .andExpect(jsonPath("$.dailyCapacityRolls", is(300)))
        .andExpect(jsonPath("$.workingHours", hasSize(3)));

    List<AuditLog> logs =
        auditLogRepository
            .findAll(
                AuditLogRepository.byTarget(AuditTargetType.MACHINE, savedP1Id),
                PageRequest.of(0, 10))
            .getContent();
    assertThat(logs).isNotEmpty();
    assertThat(logs.getFirst().getAction()).isEqualTo(AuditAction.MACHINE_UPDATED);
  }

  @Test
  @DisplayName("Cập nhật máy không tồn tại -> 404 Not Found (MACHINE_NOT_FOUND)")
  void updateMachine_notFound_returns404() throws Exception {
    String requestBody =
        """
        {
          "modelName": "Not Exist",
          "capacityPerHour": 25,
          "filmProcessIds": [%d],
          "filmFormatIds": [%d],
          "workingHours": [
            {"dayOfWeek": 1, "closed": false, "opensAt": "08:00", "closesAt": "16:00"}
          ]
        }
        """
            .formatted(activeProcessId, activeFormatId);

    mockMvc
        .perform(
            put(API_PATH + "/999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode", is("MACHINE_NOT_FOUND")));
  }

  @Test
  @DisplayName(
      "Film Lab Owner cập nhật scheduling-settings -> 200 OK, lưu qcBufferHours, expressDailyCapPercent, expressCommitmentWorkingDays, ghi audit")
  void updateSchedulingSettings_asLabOwner_success_returns200() throws Exception {
    String requestBody =
        """
        {
          "qcBufferHours": 6,
          "expressDailyCapPercent": 25,
          "expressCommitmentWorkingDays": 1
        }
        """;

    mockMvc
        .perform(
            put("/api/v1/film-labs/me/scheduling-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filmLabId", is(labId.intValue())))
        .andExpect(jsonPath("$.qcBufferHours", is(6)))
        .andExpect(jsonPath("$.expressDailyCapPercent", is(25)))
        .andExpect(jsonPath("$.expressCommitmentWorkingDays", is(1)));

    FilmLab updatedLab = filmLabRepository.findById(labId).orElseThrow();
    assertThat(updatedLab.getQcBufferHours()).isEqualTo((short) 6);
    assertThat(updatedLab.getExpressDailyCapPercent()).isEqualTo((short) 25);
    assertThat(updatedLab.getExpressCommitmentWorkingDays()).isEqualTo((short) 1);
  }

  @Test
  @DisplayName("Cập nhật scheduling-settings với giá trị ngoài miền cho phép -> 400 Bad Request")
  void updateSchedulingSettings_invalidValues_returns400() throws Exception {
    String requestBody =
        """
        {
          "qcBufferHours": 100,
          "expressDailyCapPercent": 150,
          "expressCommitmentWorkingDays": 5
        }
        """;

    mockMvc
        .perform(
            put("/api/v1/film-labs/me/scheduling-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
  }

  @Test
  @DisplayName("Chưa đăng nhập gọi API máy của lab -> 401 Unauthorized")
  void getMyMachines_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get(API_PATH)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Role không phải FILM_LAB_OWNER gọi API máy của lab -> 403 Forbidden")
  void getMyMachines_forbiddenRole_returns403() throws Exception {
    mockMvc
        .perform(get(API_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + photographerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "TC-F14-01 & TC-SM04-02: Báo cáo sự cố P1 hỏng ngày Thứ 4 -> 201 Created, máy chuyển MAINTENANCE, tái xếp lịch đơn IN_PROCESSING sang Thứ 5, cập nhật promised_return_at sang 12:00 Thứ 6 và ghi audit")
  void tcF1401_reportIncident_withExpectedAvailableAt_maintenanceAndReschedulesOrders()
      throws Exception {
    FilmLab lab = filmLabRepository.findById(labId).orElseThrow();
    lab.setQcBufferHours((short) 4);
    lab.setExpressDailyCapPercent((short) 25);
    filmLabRepository.saveAndFlush(lab);

    FilmProcess activeProcess = filmProcessRepository.findById(activeProcessId).orElseThrow();
    FilmFormat activeFormat = filmFormatRepository.findById(activeFormatId).orElseThrow();

    Machine p1 = machineRepository.findById(savedP1Id).orElseThrow();
    p1.setCapacityPerHour(2);
    p1.setFilmProcesses(Set.of(activeProcess));
    p1.setFilmFormats(Set.of(activeFormat));
    machineRepository.saveAndFlush(p1);

    Machine s1 = machineRepository.findById(savedS1Id).orElseThrow();
    s1.setCapacityPerHour(72);
    s1.setFilmFormats(Set.of(activeFormat));
    machineRepository.saveAndFlush(s1);

    LocalDate monday = LocalDate.of(2026, 11, 16);
    LocalDate tuesday = monday.plusDays(1);
    LocalDate wednesday = monday.plusDays(2);
    LocalDate thursday = monday.plusDays(3);
    LocalDate friday = monday.plusDays(4);

    Instant receivedAt = vnTime(monday, 10, 0);
    Instant oldPromisedAt = vnTime(thursday, 12, 0);
    Instant expectedNewPromisedAt = vnTime(friday, 12, 0);

    // Đơn đã chiếm hết 12 cuộn Standard ngày Thứ 3
    long busyOrderId = insertInProcessingOrder(receivedAt, vnTime(wednesday, 12, 0));
    saveAllocation(busyOrderId, tuesday, ServiceTier.STANDARD, (short) 12, receivedAt.minusSeconds(3600));

    // Đơn mục tiêu đang xử lý 3 cuộn vào Thứ 4, hẹn trả 12:00 Thứ 5
    long targetOrderId = insertInProcessingOrder(receivedAt, oldPromisedAt);
    saveAllocation(targetOrderId, wednesday, ServiceTier.STANDARD, (short) 3, receivedAt);

    String requestBody =
        """
        {
          "cause": "MACHINE_FAILURE",
          "description": "Hỏng motor kéo phim máy P1",
          "unavailableFrom": "%s",
          "expectedAvailableAt": "%s"
        }
        """
            .formatted(vnTime(wednesday, 8, 0), vnTime(wednesday, 16, 0));

    String responseBody =
        mockMvc
            .perform(
                post(API_PATH + "/" + savedP1Id + "/incidents")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.machineId", is(savedP1Id.intValue())))
            .andExpect(jsonPath("$.machineCode", is("P1")))
            .andExpect(jsonPath("$.cause", is("MACHINE_FAILURE")))
            .andExpect(jsonPath("$.machineStatusAfter", is("MAINTENANCE")))
            .andExpect(jsonPath("$.affectedOrderCount", is(1)))
            .andExpect(jsonPath("$.scheduleChanges", hasSize(1)))
            .andExpect(jsonPath("$.scheduleChanges[0].serviceOrderId", is((int) targetOrderId)))
            .andExpect(jsonPath("$.scheduleChanges[0].stepApplied", is("RESCHEDULED")))
            .andExpect(
                jsonPath("$.scheduleChanges[0].oldPromisedAt", is(oldPromisedAt.toString())))
            .andExpect(
                jsonPath("$.scheduleChanges[0].newPromisedAt", is(expectedNewPromisedAt.toString())))
            .andExpect(jsonPath("$.scheduleChanges[0].requiresResponse", is(false)))
            .andExpect(jsonPath("$.scheduleChanges[0].responseDueAt").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    long incidentId = objectMapper.readTree(responseBody).get("id").asLong();

    Machine updatedP1 = machineRepository.findById(savedP1Id).orElseThrow();
    assertThat(updatedP1.getStatus()).isEqualTo(MachineStatus.MAINTENANCE);

    List<OrderCapacityAllocation> targetAllocations =
        orderCapacityAllocationRepository.findByServiceOrderIdOrderByWorkDateAsc(targetOrderId);
    assertThat(targetAllocations).hasSize(1);
    assertThat(targetAllocations.getFirst().getWorkDate()).isEqualTo(thursday);
    assertThat(targetAllocations.getFirst().getRollCount()).isEqualTo((short) 3);

    List<ScheduleChange> changes =
        scheduleChangeRepository.findByMachineIncidentIdOrderByIdAsc(incidentId);
    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst().getStepApplied()).isEqualTo(ScheduleChangeStep.RESCHEDULED);

    Timestamp dbPromisedAt =
        jdbcTemplate.queryForObject(
            "SELECT promised_return_at FROM service_orders WHERE id = ?",
            Timestamp.class,
            targetOrderId);
    assertThat(dbPromisedAt).isNotNull();
    assertThat(dbPromisedAt.toInstant()).isEqualTo(expectedNewPromisedAt);

    List<AuditLog> incidentLogs =
        auditLogRepository
            .findAll(
                AuditLogRepository.byTarget(AuditTargetType.MACHINE_INCIDENT, incidentId),
                PageRequest.of(0, 10))
            .getContent();
    assertThat(incidentLogs).isNotEmpty();
    assertThat(incidentLogs.getFirst().getAction()).isEqualTo(AuditAction.MACHINE_INCIDENT_REPORTED);

    List<AuditLog> orderLogs =
        auditLogRepository
            .findAll(
                AuditLogRepository.byTarget(AuditTargetType.SERVICE_ORDER, targetOrderId),
                PageRequest.of(0, 10))
            .getContent();
    assertThat(orderLogs)
        .anySatisfy(
            logEntry ->
                assertThat(logEntry.getAction())
                    .isEqualTo(AuditAction.ORDER_PROMISED_DATE_CHANGED));

    verify(notificationService)
        .notify(
            eq(photographerId),
            eq(NotificationType.ORDER_STATUS),
            contains("đổi ngày hẹn trả"),
            any(),
            any());
  }

  @Test
  @DisplayName(
      "Báo cáo sự cố không xác định thời điểm khắc phục (expectedAvailableAt = null) -> 201 Created, máy chuyển DOWN")
  void reportIncident_withoutExpectedAvailableAt_returns201AndSetsMachineDown() throws Exception {
    String requestBody =
        """
        {
          "cause": "POWER_OUTAGE",
          "description": "Mất điện khu vực chưa xác định thời điểm có lại",
          "unavailableFrom": "2026-11-18T01:00:00Z"
        }
        """;

    mockMvc
        .perform(
            post(API_PATH + "/" + savedP1Id + "/incidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.machineId", is(savedP1Id.intValue())))
        .andExpect(jsonPath("$.machineStatusAfter", is("DOWN")))
        .andExpect(jsonPath("$.expectedAvailableAt").value(nullValue()));

    Machine updatedP1 = machineRepository.findById(savedP1Id).orElseThrow();
    assertThat(updatedP1.getStatus()).isEqualTo(MachineStatus.DOWN);
  }

  @Test
  @DisplayName(
      "TC-SM04-06: Giải quyết sự cố P1 -> 200 OK, resolvedAt != null, máy khôi phục ACTIVE, ghi audit; gọi lại lần 2 -> 409 MACHINE_INCIDENT_ALREADY_RESOLVED")
  void tcSm0406_resolveIncident_restoresMachineActiveAndRejectsSecondResolve() throws Exception {
    String reportBody =
        """
        {
          "cause": "MACHINE_FAILURE",
          "description": "Thay sensor P1",
          "unavailableFrom": "2026-11-18T01:00:00Z",
          "expectedAvailableAt": "2026-11-18T09:00:00Z"
        }
        """;

    String reportResponse =
        mockMvc
            .perform(
                post(API_PATH + "/" + savedP1Id + "/incidents")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reportBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.machineStatusAfter", is("MAINTENANCE")))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    long incidentId = objectMapper.readTree(reportResponse).get("id").asLong();

    mockMvc
        .perform(
            post(API_PATH + "/" + savedP1Id + "/incidents/" + incidentId + "/resolve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is((int) incidentId)))
        .andExpect(jsonPath("$.machineId", is(savedP1Id.intValue())))
        .andExpect(jsonPath("$.resolvedAt", notNullValue()))
        .andExpect(jsonPath("$.machineStatusAfter", is("ACTIVE")));

    Machine restoredP1 = machineRepository.findById(savedP1Id).orElseThrow();
    assertThat(restoredP1.getStatus()).isEqualTo(MachineStatus.ACTIVE);

    List<AuditLog> auditLogs =
        auditLogRepository
            .findAll(
                AuditLogRepository.byTarget(AuditTargetType.MACHINE_INCIDENT, incidentId),
                PageRequest.of(0, 10))
            .getContent();
    assertThat(auditLogs)
        .anySatisfy(
            logEntry ->
                assertThat(logEntry.getAction()).isEqualTo(AuditAction.MACHINE_INCIDENT_RESOLVED));

    mockMvc
        .perform(
            post(API_PATH + "/" + savedP1Id + "/incidents/" + incidentId + "/resolve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode", is("MACHINE_INCIDENT_ALREADY_RESOLVED")));
  }

  @Test
  @DisplayName(
      "TC-WF04: Luồng Onboarding Lab End-to-End (Guest nộp đơn -> Admin duyệt -> Chủ lab tạo gói, bị chặn 422 khi thiếu máy -> Khai báo P1 & S1 + settings -> Bật nhận đơn thành công)")
  void tcWf04_endToEndLabOnboardingAndMachineReadinessWorkflow() throws Exception {
    Long activeServiceTypeId =
        serviceTypeRepository.findAll().stream()
            .filter(st -> st.getStatus() == CatalogStatus.ACTIVE)
            .findFirst()
            .orElseThrow()
            .getId();

    UserAccount admin =
        userAccountRepository
            .findByEmail("wf04.admin@filmlink.vn")
            .orElseGet(
                () -> {
                  UserAccount u = new UserAccount();
                  u.setEmail("wf04.admin@filmlink.vn");
                  u.setFullName("WF04 Admin");
                  u.setPasswordHash(passwordEncoder.encode("Password123@"));
                  u.setRole(UserAccountRole.ADMIN);
                  u.setStatus(UserAccountStatus.ACTIVE);
                  u.setTermsAcceptedAt(Instant.now());
                  u.setEmailVerifiedAt(Instant.now());
                  return userAccountRepository.save(u);
                });
    String adminToken =
        jwtTokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), admin.getRole());

    String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    String newOwnerEmail = "wf04.owner." + uniqueSuffix + "@filmlink.vn";
    String taxCode =
        "03" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));

    Long appId = null;
    Long wf04LabId = null;
    Long wf04OwnerId = null;

    try {
      // Bước 1 (UC07): Guest nộp đơn đăng ký Lab
      SubmitLabApplicationRequest submitRequest =
          new SubmitLabApplicationRequest(
              "WF04 Saigon Lab " + uniqueSuffix,
              taxCode,
              "88 Dong Khoi " + uniqueSuffix,
              "Quan 1",
              "TP Ho Chi Minh",
              "contact." + uniqueSuffix + "@filmlink.vn",
              "0909887766",
              Set.of(activeServiceTypeId),
              Set.of(activeProcessId),
              Set.of(activeFormatId));

      MockMultipartFile dataPart =
          new MockMultipartFile(
              "data",
              "",
              MediaType.APPLICATION_JSON_VALUE,
              objectMapper.writeValueAsBytes(submitRequest));
      MockMultipartFile pdfDoc =
          new MockMultipartFile(
              "documents",
              "giay_phep_kinh_doanh.pdf",
              "application/pdf",
              "%PDF-1.4 wf04 legal doc".getBytes(StandardCharsets.UTF_8));

      String submitResponse =
          mockMvc
              .perform(multipart("/api/v1/public/lab-applications").file(dataPart).file(pdfDoc))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.status", is("PENDING_REVIEW")))
              .andReturn()
              .getResponse()
              .getContentAsString(StandardCharsets.UTF_8);

      JsonNode submitNode = objectMapper.readTree(submitResponse);
      appId = submitNode.get("id").asLong();

      // Bước 2 (UC08): Admin xem chi tiết đơn để lấy version hiện tại rồi duyệt đơn -> tạo
      // FILM_LAB_OWNER ACTIVE và FilmLab (acceptingOrders=false)
      String detailResponse =
          mockMvc
              .perform(
                  get("/api/v1/admin/lab-applications/" + appId)
                      .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.status", is("PENDING_REVIEW")))
              .andReturn()
              .getResponse()
              .getContentAsString(StandardCharsets.UTF_8);

      int version = objectMapper.readTree(detailResponse).get("version").asInt();

      String approveBody =
          """
          {
            "version": %d,
            "accountEmail": "%s"
          }
          """
              .formatted(version, newOwnerEmail);

      mockMvc
          .perform(
              post("/api/v1/admin/lab-applications/" + appId + "/approve")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(approveBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status", is("APPROVED")));

      UserAccount wf04Owner = userAccountRepository.findByEmail(newOwnerEmail).orElseThrow();
      wf04OwnerId = wf04Owner.getId();
      assertThat(wf04Owner.getRole()).isEqualTo(UserAccountRole.FILM_LAB_OWNER);
      assertThat(wf04Owner.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);

      FilmLab wf04Lab = filmLabRepository.findByOwnerAccountId(wf04OwnerId).orElseThrow();
      wf04LabId = wf04Lab.getId();
      assertThat(wf04Lab.isAcceptingOrders()).isFalse();

      String wf04OwnerToken =
          jwtTokenProvider.generateAccessToken(
              wf04Owner.getId(), wf04Owner.getEmail(), wf04Owner.getRole());

      // Bước 3 (UC13 & BR-57): Tạo gói ON_SALE nhưng chưa có máy -> bật nhận đơn bị chặn 422 MSG32
      String packageBody =
          """
          {
            "packageName": "Gói Tráng C-41 Tiêu Chuẩn",
            "serviceTypeId": %d,
            "filmProcessId": %d,
            "filmFormatId": %d,
            "priceVnd": 70000,
            "turnaroundDays": 5,
            "pushPullAvailable": false,
            "scanOptions": []
          }
          """
              .formatted(activeServiceTypeId, activeProcessId, activeFormatId);

      mockMvc
          .perform(
              post("/api/v1/film-labs/me/packages")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + wf04OwnerToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(packageBody))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.status", is("ON_SALE")));

      mockMvc
          .perform(
              patch("/api/v1/film-labs/me/accepting-orders")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + wf04OwnerToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"acceptingOrders\":true}"))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.errorCode", is("LAB_MACHINES_NOT_READY")))
          .andExpect(jsonPath("$.messageCode", is("MSG32")))
          .andExpect(jsonPath("$.missing[0]", is("MACHINES")));

      // Bước 4 (UC68): Khai báo đủ PROCESSOR + SCANNER ACTIVE và tham số lập lịch
      String workingHoursJson =
          """
          [
            {"dayOfWeek": 1, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 2, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 3, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 4, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 5, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 6, "closed": false, "opensAt": "08:00", "closesAt": "16:00"},
            {"dayOfWeek": 7, "closed": true}
          ]
          """;

      String createProcessorBody =
          """
          {
            "machineCode": "P-WF04",
            "modelName": "Noritsu V30",
            "machineType": "PROCESSOR",
            "capacityPerHour": 26,
            "filmProcessIds": [%d],
            "filmFormatIds": [%d],
            "workingHours": %s
          }
          """
              .formatted(activeProcessId, activeFormatId, workingHoursJson);

      String createScannerBody =
          """
          {
            "machineCode": "S-WF04",
            "modelName": "Frontier SP-3000",
            "machineType": "SCANNER",
            "capacityPerHour": 327,
            "filmProcessIds": [],
            "filmFormatIds": [%d],
            "workingHours": %s
          }
          """
              .formatted(activeFormatId, workingHoursJson);

      mockMvc
          .perform(
              post(API_PATH)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + wf04OwnerToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(createProcessorBody))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.status", is("ACTIVE")));

      mockMvc
          .perform(
              post(API_PATH)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + wf04OwnerToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(createScannerBody))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.status", is("ACTIVE")));

      mockMvc
          .perform(
              put("/api/v1/film-labs/me/scheduling-settings")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + wf04OwnerToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"qcBufferHours\":4,\"expressDailyCapPercent\":25,"
                          + "\"expressCommitmentWorkingDays\":2}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.qcBufferHours", is(4)));

      // Bước 5 (UC12): Bật nhận đơn thành công khi đã đủ điều kiện BR-57 & BR-34
      mockMvc
          .perform(
              patch("/api/v1/film-labs/me/accepting-orders")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + wf04OwnerToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"acceptingOrders\":true}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.acceptingOrders", is(true)));
    } finally {
      cleanupWf04Lab(wf04LabId, appId, wf04OwnerId);
    }
  }

  private void cleanupWf04Lab(Long wf04LabId, Long appId, Long wf04OwnerId) {
    if (wf04LabId != null) {
      jdbcTemplate.update("DELETE FROM machines WHERE film_lab_id = ?", wf04LabId);
      jdbcTemplate.update("DELETE FROM service_packages WHERE film_lab_id = ?", wf04LabId);
      jdbcTemplate.update("DELETE FROM film_labs WHERE id = ?", wf04LabId);
    }
    if (appId != null) {
      jdbcTemplate
          .queryForList(
              "SELECT blob_key FROM lab_registration_documents"
                  + " WHERE lab_registration_application_id = ?",
              String.class,
              appId)
          .forEach(storageClient::delete);
      jdbcTemplate.update("DELETE FROM lab_registration_applications WHERE id = ?", appId);
    }
    if (wf04OwnerId != null) {
      jdbcTemplate.update(
          "DELETE FROM user_accounts WHERE id = ?"
              + " AND NOT EXISTS (SELECT 1 FROM audit_logs WHERE actor_account_id = ?)",
          wf04OwnerId,
          wf04OwnerId);
    }
  }

  private long insertInProcessingOrder(Instant priceLockedAt, Instant promisedReturnAt) {
    String code = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO service_orders
            (order_code, idempotency_key, photographer_account_id, film_lab_id,
             status, service_tier, film_handover_method, payment_method,
             estimated_total_vnd, final_total_vnd, price_locked_at, expected_return_at,
             promised_return_at, machine_assignment_confirmed_at)
        VALUES (?, ?, ?, ?, 'IN_PROCESSING', 'STANDARD', 'SELF_DROP_OFF', 'PAY_AT_LAB',
                240000, 240000, ?, ?, ?, ?)
        RETURNING id
        """,
        Long.class,
        code,
        UUID.randomUUID(),
        photographerId,
        labId,
        Timestamp.from(priceLockedAt),
        Timestamp.from(priceLockedAt.plusSeconds(5L * 86400)),
        Timestamp.from(promisedReturnAt),
        Timestamp.from(priceLockedAt));
  }

  private void saveAllocation(
      long orderId,
      LocalDate workDate,
      ServiceTier tier,
      short rollCount,
      Instant orderReceivedAt) {
    OrderCapacityAllocation allocation = new OrderCapacityAllocation();
    allocation.setServiceOrderId(orderId);
    allocation.setFilmLabId(labId);
    allocation.setWorkDate(workDate);
    allocation.setFilmProcessId(activeProcessId);
    allocation.setFilmFormatId(activeFormatId);
    allocation.setServiceTier(tier);
    allocation.setRollCount(rollCount);
    allocation.setOrderReceivedAt(orderReceivedAt);
    orderCapacityAllocationRepository.saveAndFlush(allocation);
  }

  private static Instant vnTime(LocalDate date, int hour, int minute) {
    return ZonedDateTime.of(date, LocalTime.of(hour, minute), CapacitySchedulingEngine.VIETNAM_ZONE)
        .toInstant();
  }
}
