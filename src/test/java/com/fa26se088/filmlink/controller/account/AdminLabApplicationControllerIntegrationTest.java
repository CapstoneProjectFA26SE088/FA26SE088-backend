package com.fa26se088.filmlink.controller.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.notification.EmailClient;
import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.account.enums.UserAccountStatus;
import com.fa26se088.filmlink.entity.audit.AuditLog;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.repository.audit.AuditLogRepository;
import com.fa26se088.filmlink.repository.lab.FilmLabRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLabApplicationControllerIntegrationTest {

  private static final String API_PATH = "/api/v1/admin/lab-applications";
  private static final String OWNER_EMAIL = "owner.saigon@filmlink.vn";

  @Autowired private MockMvc mockMvc;
  @Autowired private LabRegistrationApplicationRepository applicationRepository;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private FilmLabRepository filmLabRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private EmailClient emailClient;

  private final List<Long> createdApplicationIds = new ArrayList<>();
  private final List<Long> createdAccountIds = new ArrayList<>();
  private String adminToken;
  private String photographerToken;
  private Long savedApp1Id;
  private Long savedApp2Id;

  @BeforeEach
  void setUp() {
    applicationRepository
        .findByApplicationCode("LA261114000001")
        .ifPresent(existing -> createdApplicationIds.add(existing.getId()));
    applicationRepository
        .findByApplicationCode("LA261114000002")
        .ifPresent(existing -> createdApplicationIds.add(existing.getId()));
    cleanupTestData();

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
                  UserAccount saved = userAccountRepository.save(user);
                  createdAccountIds.add(saved.getId());
                  return saved;
                });
    adminToken =
        jwtTokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), admin.getRole());

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
                  UserAccount saved = userAccountRepository.save(user);
                  createdAccountIds.add(saved.getId());
                  return saved;
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
    savedApp2Id = applicationRepository.save(app2).getId();
    createdApplicationIds.add(savedApp1Id);
    createdApplicationIds.add(savedApp2Id);
  }

  @AfterEach
  void tearDown() {
    cleanupTestData();
  }

  private void cleanupTestData() {
    userAccountRepository
        .findByEmail(OWNER_EMAIL)
        .ifPresent(
            owner -> {
              filmLabRepository
                  .findByOwnerAccountId(owner.getId())
                  .ifPresent(filmLabRepository::delete);
              createdAccountIds.add(owner.getId());
            });

    if (!createdApplicationIds.isEmpty()) {
      for (Long appId : createdApplicationIds) {
        jdbcTemplate.update(
            "DELETE FROM film_labs WHERE lab_registration_application_id = ?", appId);
      }
      applicationRepository.deleteAllById(createdApplicationIds);
      createdApplicationIds.clear();
    }

    if (!createdAccountIds.isEmpty()) {
      for (Long accountId : createdAccountIds) {
        jdbcTemplate.update(
            "DELETE FROM user_accounts WHERE id = ?"
                + " AND NOT EXISTS (SELECT 1 FROM audit_logs WHERE actor_account_id = ?)",
            accountId,
            accountId);
      }
      createdAccountIds.clear();
    }
  }

  @Test
  @DisplayName(
      "Admin xem danh sách đơn lọc theo PENDING_REVIEW -> 200 OK, chứa đơn vừa tạo có isDuplicateSuspected = true")
  void getApplications_asAdmin_filteredPendingReview_returns200() throws Exception {
    mockMvc
        .perform(
            get(API_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .param("status", "PENDING_REVIEW"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].applicationCode", hasItem("LA261114000001")))
        .andExpect(
            jsonPath(
                "$.content[?(@.applicationCode == 'LA261114000001')].labName",
                hasItem("Saigon Film Lab")))
        .andExpect(
            jsonPath(
                "$.content[?(@.applicationCode == 'LA261114000001')].isDuplicateSuspected",
                hasItem(true)))
        .andExpect(
            jsonPath(
                "$.content[?(@.applicationCode == 'LA261114000001')].status",
                hasItem("PENDING_REVIEW")));
  }

  @Test
  @DisplayName("Admin xem danh sách tất cả các đơn -> 200 OK, chứa đủ 2 đơn test vừa tạo")
  void getApplications_asAdmin_all_returns200() throws Exception {
    mockMvc
        .perform(get(API_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.content[*].applicationCode", hasItems("LA261114000001", "LA261114000002")));
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
        .andExpect(jsonPath("$.version", is(0)))
        .andExpect(jsonPath("$.isDuplicateSuspected", is(true)));
  }

  @Test
  @DisplayName("Admin xem chi tiết ID không tồn tại -> 404 Not Found")
  void getApplicationDetail_notFound_returns404() throws Exception {
    mockMvc
        .perform(
            get(API_PATH + "/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
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

  @Test
  @DisplayName(
      "TC-F03-01 & TC-SM02-02: Admin duyệt đơn PENDING_REVIEW -> 200 OK, APPROVED, tạo FILM_LAB_OWNER ACTIVE, tạo film_labs (acceptingOrders=false), gửi mail, ghi audit")
  void approveApplication_asAdmin_success_createsOwnerAndFilmLab() throws Exception {
    String requestBody =
        """
        {
          "version": 0,
          "accountEmail": "%s"
        }
        """
            .formatted(OWNER_EMAIL);

    mockMvc
        .perform(
            post(API_PATH + "/" + savedApp1Id + "/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(savedApp1Id.intValue())))
        .andExpect(jsonPath("$.status", is("APPROVED")))
        .andExpect(jsonPath("$.reviewedByAccountId").isNumber())
        .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

    // 1. Kiểm tra tài khoản FILM_LAB_OWNER đã được tạo với status ACTIVE
    UserAccount ownerAccount = userAccountRepository.findByEmail(OWNER_EMAIL).orElseThrow();
    assertThat(ownerAccount.getRole()).isEqualTo(UserAccountRole.FILM_LAB_OWNER);
    assertThat(ownerAccount.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);

    // 2. Kiểm tra bản ghi film_labs được tạo tự động với is_accepting_orders = false
    FilmLab createdLab =
        filmLabRepository.findByOwnerAccountId(ownerAccount.getId()).orElseThrow();
    assertThat(createdLab.getLabRegistrationApplicationId()).isEqualTo(savedApp1Id);
    assertThat(createdLab.getLabName()).isEqualTo("Saigon Film Lab");
    assertThat(createdLab.isAcceptingOrders()).isFalse();

    // 3. Kiểm tra Audit Log được ghi
    List<AuditLog> logs =
        auditLogRepository
            .findAll(
                AuditLogRepository.byTarget(
                    AuditTargetType.LAB_REGISTRATION_APPLICATION, savedApp1Id),
                PageRequest.of(0, 10))
            .getContent();
    assertThat(logs).isNotEmpty();
    assertThat(logs.getFirst().getAction()).isEqualTo(AuditAction.LAB_APPLICATION_APPROVED);

    // 4. Kiểm tra gửi email mật khẩu tạm sau khi commit
    verify(emailClient).send(eq(OWNER_EMAIL), anyString(), anyString());
  }

  @Test
  @DisplayName("TC-F03-03: Admin duyệt đơn với version cũ -> 409 Conflict (LAB_APPLICATION_CONFLICT)")
  void approveApplication_versionConflict_returns409() throws Exception {
    String requestBody =
        """
        {
          "version": 99
        }
        """;

    mockMvc
        .perform(
            post(API_PATH + "/" + savedApp1Id + "/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode", is("LAB_APPLICATION_CONFLICT")));
  }

  @Test
  @DisplayName(
      "TC-SM02-03: Admin từ chối đơn PENDING_REVIEW hợp lệ -> 200 OK, REJECTED, lưu lý do, ghi audit, gửi email")
  void rejectApplication_asAdmin_success_updatesStatusAndSendsEmail() throws Exception {
    String requestBody =
        """
        {
          "version": 0,
          "reason": "Giấy phép đăng ký kinh doanh bị mờ và không khớp mã số thuế"
        }
        """;

    mockMvc
        .perform(
            post(API_PATH + "/" + savedApp1Id + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(savedApp1Id.intValue())))
        .andExpect(jsonPath("$.status", is("REJECTED")))
        .andExpect(
            jsonPath(
                "$.rejectionReason",
                is("Giấy phép đăng ký kinh doanh bị mờ và không khớp mã số thuế")))
        .andExpect(jsonPath("$.reviewedByAccountId").isNumber())
        .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

    List<AuditLog> logs =
        auditLogRepository
            .findAll(
                AuditLogRepository.byTarget(
                    AuditTargetType.LAB_REGISTRATION_APPLICATION, savedApp1Id),
                PageRequest.of(0, 10))
            .getContent();
    assertThat(logs).isNotEmpty();
    assertThat(logs.getFirst().getAction()).isEqualTo(AuditAction.LAB_APPLICATION_REJECTED);

    verify(emailClient).send(eq("saigon@lab.vn"), anyString(), anyString());
  }

  @Test
  @DisplayName("TC-F03-02: Admin từ chối đơn với lý do dưới 10 ký tự -> 400 Bad Request")
  void rejectApplication_shortReason_returns400() throws Exception {
    String requestBody =
        """
        {
          "version": 0,
          "reason": "Quá ngắn"
        }
        """;

    mockMvc
        .perform(
            post(API_PATH + "/" + savedApp1Id + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
  }
}
