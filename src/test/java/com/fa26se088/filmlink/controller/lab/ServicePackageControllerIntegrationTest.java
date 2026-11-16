package com.fa26se088.filmlink.controller.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.fa26se088.filmlink.service.lab.impl.FilmLabFixture;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code /api/v1/film-labs/me/packages} trên DB thật (schema test): Film Lab Owner quản lý bảng giá
 * (UC13, BR-16). Master data lấy từ seed của migration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServicePackageControllerIntegrationTest {

  private static final String PATH = "/api/v1/film-labs/me/packages";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageClient storageClient;

  private FilmLabFixture fixture;
  private UserAccount owner;
  private long developingId;
  private long c41Id;
  private long format135Id;

  @BeforeEach
  void setUp() {
    fixture =
        new FilmLabFixture(
            jdbcTemplate, userAccountRepository, eventPublisher, transactionTemplate, storageClient);
    owner = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    fixture.createLab(owner, "Lab Bảng Giá");
    developingId = fixture.catalogId("service_types", "DEVELOPING");
    c41Id = fixture.catalogId("film_processes", "C41");
    format135Id = fixture.catalogId("film_formats", "135");
  }

  @AfterEach
  void cleanUp() {
    fixture.cleanUp();
  }

  /**
   * TC-F05-01 (Report 5): lab owner tạo gói C-41 35 mm 60.000 VND/cuộn, 5 ngày, scan S 20.000 / L
   * 50.000, rồi mở bán → gói {@code ON_SALE}.
   *
   * <p>Phần "visible on the public lab page" của Expected sẽ kiểm ở T-03 (trang lab công khai chưa
   * có); ở đây kiểm qua {@code GET /api/v1/film-labs/me/packages}.
   */
  @Test
  @DisplayName("TC-F05-01 Lab owner creates C-41 35mm package with scan options; package ON_SALE")
  void tcF0501_createAndPublishPackage() throws Exception {
    String body =
        mockMvc
            .perform(
                post(PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        packageJson(
                            "C-41 35mm",
                            60_000,
                            5,
                            "[{\"resolutionLabel\":\"S\",\"priceVnd\":20000},"
                                + "{\"resolutionLabel\":\"L\",\"priceVnd\":50000}]")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.packageName").value("C-41 35mm"))
            .andExpect(jsonPath("$.serviceTypeId").value(developingId))
            .andExpect(jsonPath("$.filmProcessId").value(c41Id))
            .andExpect(jsonPath("$.filmFormatId").value(format135Id))
            .andExpect(jsonPath("$.priceVnd").value(60_000))
            .andExpect(jsonPath("$.turnaroundDays").value(5))
            .andExpect(jsonPath("$.status").value("ON_SALE"))
            .andExpect(jsonPath("$.scanOptions", hasSize(2)))
            .andExpect(jsonPath("$.scanOptions[0].resolutionLabel").value("S"))
            .andExpect(jsonPath("$.scanOptions[0].priceVnd").value(20_000))
            .andExpect(jsonPath("$.scanOptions[1].resolutionLabel").value("L"))
            .andExpect(jsonPath("$.scanOptions[1].priceVnd").value(50_000))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    long packageId = ((Number) JsonPath.read(body, "$.id")).longValue();

    // Publish: gói đã ON_SALE từ lúc tạo; gửi ON_SALE lại là idempotent.
    mockMvc
        .perform(statusChange(packageId, "ON_SALE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ON_SALE"));

    mockMvc
        .perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(packageId))
        .andExpect(jsonPath("$[0].status").value("ON_SALE"))
        .andExpect(jsonPath("$[0].scanOptions", hasSize(2)));

    assertThat(auditCount("LAB_SERVICE_PACKAGE_CREATED", packageId)).isEqualTo(1);
  }

  @Test
  void withdrawThenPublish_writesAuditEachChange() throws Exception {
    long packageId = createPackage("Gói B&W", "[]");

    mockMvc
        .perform(statusChange(packageId, "WITHDRAWN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    mockMvc
        .perform(statusChange(packageId, "ON_SALE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ON_SALE"));

    assertThat(auditCount("LAB_SERVICE_PACKAGE_STATUS_CHANGED", packageId)).isEqualTo(2);
  }

  @Test
  void update_replacesScanOptionsAndKeepsIdOfKeptLabel() throws Exception {
    long packageId =
        createPackage(
            "Gói Scan",
            "[{\"resolutionLabel\":\"S\",\"priceVnd\":20000},"
                + "{\"resolutionLabel\":\"M\",\"priceVnd\":30000}]");
    Long idOfS =
        jdbcTemplate.queryForObject(
            "SELECT id FROM service_package_scan_options"
                + " WHERE service_package_id = ? AND resolution_label = 'S'",
            Long.class,
            packageId);

    mockMvc
        .perform(
            put(PATH + "/" + packageId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    packageJson(
                        "Gói Scan Mới",
                        70_000,
                        7,
                        "[{\"resolutionLabel\":\"S\",\"priceVnd\":25000},"
                            + "{\"resolutionLabel\":\"XL\",\"priceVnd\":80000}]")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.packageName").value("Gói Scan Mới"))
        .andExpect(jsonPath("$.priceVnd").value(70_000))
        .andExpect(jsonPath("$.scanOptions", hasSize(2)))
        .andExpect(jsonPath("$.scanOptions[0].id").value(idOfS))
        .andExpect(jsonPath("$.scanOptions[0].priceVnd").value(25_000))
        .andExpect(jsonPath("$.scanOptions[1].resolutionLabel").value("XL"));
  }

  // --- Rule ---

  @Test
  void duplicateNameIgnoringCase_returns422OnNameField() throws Exception {
    createPackage("C-41 35mm", "[]");

    mockMvc
        .perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(packageJson("  c-41 35MM ", 60_000, 5, "[]")))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("LAB_PACKAGE_NAME_DUPLICATE"))
        .andExpect(jsonPath("$.errors[0].field").value("packageName"));
  }

  @Test
  void priceOutOfRange_returns400() throws Exception {
    mockMvc
        .perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(packageJson("Gói Đắt", 50_000_001, 5, "[]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("priceVnd"));
  }

  @Test
  void pushPullWithoutSurcharge_returns400() throws Exception {
    String json =
        """
        {"packageName":"Gói Push","serviceTypeId":%d,"filmProcessId":%d,"filmFormatId":%d,
         "priceVnd":60000,"turnaroundDays":5,"pushPullAvailable":true}
        """
            .formatted(developingId, c41Id, format135Id);

    mockMvc
        .perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("pushPullSurchargePerStopVnd"));
  }

  /** BR-16: quy trình B&W Reversal được seed DISCONTINUED. */
  @Test
  void discontinuedFilmProcess_returns422() throws Exception {
    long discontinued = fixture.catalogId("film_processes", "BW_REVERSAL");
    String json =
        """
        {"packageName":"Gói Cũ","serviceTypeId":%d,"filmProcessId":%d,"filmFormatId":%d,
         "priceVnd":60000,"turnaroundDays":5,"pushPullAvailable":false}
        """
            .formatted(developingId, discontinued, format135Id);

    mockMvc
        .perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("LAB_PACKAGE_MASTERDATA_INACTIVE"))
        .andExpect(jsonPath("$.errors[0].field").value("filmProcessId"));
  }

  // --- Quyền ---

  @Test
  void photographerGets403() throws Exception {
    UserAccount photographer = fixture.createAccount(UserAccountRole.PHOTOGRAPHER);

    mockMvc
        .perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
  }

  /** CR-14: owner B không sửa / đổi trạng thái được gói của lab A; theo mẫu ảnh của lab → 404. */
  @Test
  void ownerCannotTouchPackageOfAnotherLab() throws Exception {
    long packageOfA = createPackage("Gói Lab A", "[]");
    UserAccount ownerB = fixture.createAccount(UserAccountRole.FILM_LAB_OWNER);
    fixture.createLab(ownerB, "Lab Chủ B");

    mockMvc
        .perform(
            put(PATH + "/" + packageOfA)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(packageJson("Gói Bị Sửa", 1_000, 1, "[]")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("LAB_PACKAGE_NOT_FOUND"));
    mockMvc
        .perform(
            patch(PATH + "/" + packageOfA + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"WITHDRAWN\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("LAB_PACKAGE_NOT_FOUND"));
    mockMvc
        .perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  // --- helpers ---

  private long createPackage(String name, String scanOptionsJson) throws Exception {
    String body =
        mockMvc
            .perform(
                post(PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(packageJson(name, 60_000, 5, scanOptionsJson)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private String packageJson(String name, long priceVnd, int turnaroundDays, String scanOptions) {
    return """
        {"packageName":"%s","serviceTypeId":%d,"filmProcessId":%d,"filmFormatId":%d,
         "priceVnd":%d,"turnaroundDays":%d,"pushPullAvailable":false,"scanOptions":%s}
        """
        .formatted(
            name, developingId, c41Id, format135Id, priceVnd, turnaroundDays, scanOptions);
  }

  private RequestBuilder statusChange(long packageId, String status) {
    return patch(PATH + "/" + packageId + "/status")
        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"" + status + "\"}");
  }

  private Integer auditCount(String action, long packageId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM audit_logs WHERE action = ? AND target_type = 'SERVICE_PACKAGE'"
            + " AND target_id = ? AND actor_account_id = ?",
        Integer.class,
        action,
        packageId,
        owner.getId());
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(
            account.getId(), account.getEmail(), account.getRole());
  }
}
