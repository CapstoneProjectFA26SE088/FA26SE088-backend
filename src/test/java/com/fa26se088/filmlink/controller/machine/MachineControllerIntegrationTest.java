package com.fa26se088.filmlink.controller.machine;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.account.enums.UserAccountStatus;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.machine.Machine;
import com.fa26se088.filmlink.entity.machine.MachineWorkingHour;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.repository.lab.FilmLabRepository;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private PasswordEncoder passwordEncoder;

  private String ownerToken;
  private String photographerToken;
  private Long labId;

  @BeforeEach
  void setUp() {
    cleanupTestData();

    UserAccount owner = new UserAccount();
    owner.setEmail(OWNER_EMAIL);
    owner.setFullName("Machine Lab Owner");
    owner.setPasswordHash(passwordEncoder.encode("Password123@"));
    owner.setRole(UserAccountRole.FILM_LAB_OWNER);
    owner.setStatus(UserAccountStatus.ACTIVE);
    owner.setTermsAcceptedAt(Instant.now());
    owner.setEmailVerifiedAt(Instant.now());
    owner = userAccountRepository.save(owner);
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

    LabRegistrationApplication app = new LabRegistrationApplication();
    app.setApplicationCode("LA261119MCH001");
    app.setLabName("Machine Test Film Lab");
    app.setTaxCode("0399887766");
    app.setAddressLine("100 Pasteur");
    app.setDistrict("Quan 1");
    app.setProvince("TP Ho Chi Minh");
    app.setContactEmail(OWNER_EMAIL);
    app.setContactPhone("0909112233");
    app.setStatus(LabApplicationStatus.APPROVED);
    app.setReviewedAt(Instant.now());
    app = applicationRepository.save(app);

    FilmLab lab = new FilmLab();
    lab.setOwnerAccountId(owner.getId());
    lab.setLabRegistrationApplicationId(app.getId());
    lab.setLabName("Machine Test Film Lab");
    lab.setAddressLine("100 Pasteur");
    lab.setDistrict("Quan 1");
    lab.setProvince("TP Ho Chi Minh");
    lab.setContactEmail(OWNER_EMAIL);
    lab.setContactPhone("0909112233");
    lab = filmLabRepository.save(lab);
    labId = lab.getId();

    Machine p1 = new Machine();
    p1.setFilmLabId(labId);
    p1.setMachineCode("P1");
    p1.setModelName("Noritsu V30");
    p1.setMachineType(MachineType.PROCESSOR);
    p1.setCapacityPerHour(26);
    p1.setStatus(MachineStatus.ACTIVE);
    addEightHourWorkDays(p1);
    machineRepository.save(p1);

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
    userAccountRepository
        .findByEmail(OWNER_EMAIL)
        .ifPresent(
            owner -> {
              filmLabRepository
                  .findByOwnerAccountId(owner.getId())
                  .ifPresent(filmLabRepository::delete);
              userAccountRepository.delete(owner);
            });
    applicationRepository
        .findByApplicationCode("LA261119MCH001")
        .ifPresent(applicationRepository::delete);
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
