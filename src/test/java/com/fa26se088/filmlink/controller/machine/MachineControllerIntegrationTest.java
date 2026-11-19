package com.fa26se088.filmlink.controller.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.entity.masterdata.enums.CatalogStatus;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.repository.audit.AuditLogRepository;
import com.fa26se088.filmlink.repository.lab.FilmLabRepository;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmFormatRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmProcessRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
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
  @Autowired private FilmProcessRepository filmProcessRepository;
  @Autowired private FilmFormatRepository filmFormatRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private PasswordEncoder passwordEncoder;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  private String ownerToken;
  private String photographerToken;
  private Long labId;
  private Long savedP1Id;
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
    machineRepository.save(s1);
  }

  @AfterEach
  void tearDown() {
    cleanupTestData();
  }

  private void cleanupTestData() {
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
}
