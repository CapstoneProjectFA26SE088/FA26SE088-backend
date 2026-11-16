package com.fa26se088.filmlink.controller.account;

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
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import java.time.Instant;
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
class AdminLabApplicationControllerIntegrationTest {

  private static final String API_PATH = "/api/v1/admin/lab-applications";

  @Autowired private MockMvc mockMvc;
  @Autowired private LabRegistrationApplicationRepository applicationRepository;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private PasswordEncoder passwordEncoder;

  private String adminToken;
  private String photographerToken;
  private Long savedApp1Id;

  @BeforeEach
  void setUp() {
    applicationRepository.deleteAll();

    UserAccount admin =
        userAccountRepository
            .findByEmail("admin.test@filmlink.vn")
            .orElseGet(
                () -> {
                  UserAccount user = new UserAccount();
                  user.setEmail("admin.test@filmlink.vn");
                  user.setFullName("Admin Test");
                  user.setPasswordHash(passwordEncoder.encode("Password123@"));
                  user.setRole(UserAccountRole.ADMIN);
                  user.setStatus(UserAccountStatus.ACTIVE);
                  user.setTermsAcceptedAt(Instant.now());
                  user.setEmailVerifiedAt(Instant.now());
                  return userAccountRepository.save(user);
                });
    adminToken = jwtTokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), admin.getRole());

    UserAccount photographer =
        userAccountRepository
            .findByEmail("photographer.test@filmlink.vn")
            .orElseGet(
                () -> {
                  UserAccount user = new UserAccount();
                  user.setEmail("photographer.test@filmlink.vn");
                  user.setFullName("Photographer Test");
                  user.setPasswordHash(passwordEncoder.encode("Password123@"));
                  user.setRole(UserAccountRole.PHOTOGRAPHER);
                  user.setStatus(UserAccountStatus.ACTIVE);
                  user.setTermsAcceptedAt(Instant.now());
                  user.setEmailVerifiedAt(Instant.now());
                  return userAccountRepository.save(user);
                });
    photographerToken =
        jwtTokenProvider.generateAccessToken(
            photographer.getId(), photographer.getEmail(), photographer.getRole());

    LabRegistrationApplication app1 = new LabRegistrationApplication();
    app1.setApplicationCode("LA261114000001");
    app1.setLabName("Saigon Film Lab");
    app1.setTaxCode("0123456789");
    app1.setAddressLine("123 Nguyen Hue");
    app1.setDistrict("Quan 1");
    app1.setProvince("TP Ho Chi Minh");
    app1.setContactEmail("saigon@lab.vn");
    app1.setContactPhone("0901234567");
    app1.setStatus(LabApplicationStatus.PENDING_REVIEW);
    app1.setDuplicateSuspected(true);

    LabRegistrationApplication app2 = new LabRegistrationApplication();
    app2.setApplicationCode("LA261114000002");
    app2.setLabName("Hanoi Film Lab");
    app2.setTaxCode("0987654321");
    app2.setAddressLine("456 Trang Tien");
    app2.setDistrict("Hoan Kiem");
    app2.setProvince("Ha Noi");
    app2.setContactEmail("hanoi@lab.vn");
    app2.setContactPhone("0912345678");
    app2.setStatus(LabApplicationStatus.APPROVED);
    app2.setReviewedAt(Instant.now());
    app2.setDuplicateSuspected(false);

    savedApp1Id = applicationRepository.save(app1).getId();
    applicationRepository.save(app2);
  }

  @AfterEach
  void tearDown() {
    applicationRepository.deleteAll();
  }

  @Test
  @DisplayName("Admin xem danh sách đơn lọc theo PENDING_REVIEW -> 200 OK, trả về 1 đơn có isDuplicateSuspected = true")
  void getApplications_asAdmin_filteredPendingReview_returns200() throws Exception {
    mockMvc
        .perform(
            get(API_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .param("status", "PENDING_REVIEW"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements", is(1)))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].applicationCode", is("LA261114000001")))
        .andExpect(jsonPath("$.content[0].labName", is("Saigon Film Lab")))
        .andExpect(jsonPath("$.content[0].isDuplicateSuspected", is(true)))
        .andExpect(jsonPath("$.content[0].status", is("PENDING_REVIEW")));
  }

  @Test
  @DisplayName("Admin xem danh sách tất cả các đơn -> 200 OK, trả về 2 đơn")
  void getApplications_asAdmin_all_returns200() throws Exception {
    mockMvc
        .perform(get(API_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements", is(2)))
        .andExpect(jsonPath("$.content", hasSize(2)));
  }

  @Test
  @DisplayName("Chưa đăng nhập gọi API admin -> 401 Unauthorized")
  void getApplications_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get(API_PATH)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("User không phải ADMIN gọi API admin -> 403 Forbidden")
  void getApplications_forbiddenRole_returns403() throws Exception {
    mockMvc
        .perform(get(API_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + photographerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Admin xem chi tiết đơn đăng ký theo ID thành công -> 200 OK")
  void getApplicationDetail_asAdmin_success_returns200() throws Exception {
    mockMvc
        .perform(
            get(API_PATH + "/" + savedApp1Id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(savedApp1Id.intValue())))
        .andExpect(jsonPath("$.applicationCode", is("LA261114000001")))
        .andExpect(jsonPath("$.labName", is("Saigon Film Lab")))
        .andExpect(jsonPath("$.status", is("PENDING_REVIEW")))
        .andExpect(jsonPath("$.isDuplicateSuspected", is(true)));
  }

  @Test
  @DisplayName("Admin xem chi tiết ID không tồn tại -> 404 Not Found")
  void getApplicationDetail_notFound_returns404() throws Exception {
    mockMvc
        .perform(
            get(API_PATH + "/999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode", is("LAB_APPLICATION_NOT_FOUND")));
  }

  @Test
  @DisplayName("User thường gọi API xem chi tiết -> 403 Forbidden")
  void getApplicationDetail_forbiddenRole_returns403() throws Exception {
    mockMvc
        .perform(
            get(API_PATH + "/" + savedApp1Id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + photographerToken))
        .andExpect(status().isForbidden());
  }
}
