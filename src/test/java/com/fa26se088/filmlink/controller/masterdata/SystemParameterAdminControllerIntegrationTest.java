package com.fa26se088.filmlink.controller.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.account.enums.UserAccountStatus;
import com.fa26se088.filmlink.entity.masterdata.enums.ParameterValueType;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC54: danh sách tham số hệ thống chạy trên DB thật. Bắt lỗi enum {@link SystemParameterKey} lệch với
 * các dòng seed trong migration (Hibernate không đổi được {@code param_key} lạ sang enum → 500).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemParameterAdminControllerIntegrationTest {

  private static final String API_PATH = "/api/v1/admin/system-parameters";
  private static final String ADMIN_EMAIL = "admin.sysparam.it@filmlink.vn";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private PasswordEncoder passwordEncoder;

  private String adminToken;

  @BeforeEach
  void setUp() {
    UserAccount admin =
        userAccountRepository
            .findByEmail(ADMIN_EMAIL)
            .orElseGet(
                () -> {
                  UserAccount user = new UserAccount();
                  user.setEmail(ADMIN_EMAIL);
                  user.setFullName("Admin System Parameter");
                  user.setPasswordHash(passwordEncoder.encode("Password123@"));
                  user.setRole(UserAccountRole.ADMIN);
                  user.setStatus(UserAccountStatus.ACTIVE);
                  user.setTermsAcceptedAt(Instant.now());
                  user.setEmailVerifiedAt(Instant.now());
                  return userAccountRepository.save(user);
                });
    adminToken =
        jwtTokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), admin.getRole());
  }

  @AfterEach
  void tearDown() {
    userAccountRepository.findByEmail(ADMIN_EMAIL).ifPresent(userAccountRepository::delete);
  }

  @Test
  @DisplayName("UC54: Admin lấy danh sách tham số → 200, đủ mọi dòng trong system_parameters")
  void getAllParameters_returnsEveryRow() throws Exception {
    Integer rowCount =
        jdbcTemplate.queryForObject("SELECT count(*) FROM system_parameters", Integer.class);

    mockMvc
        .perform(get(API_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(rowCount)));
  }

  @Test
  @DisplayName("Mọi param_key trong DB đều đổi được sang SystemParameterKey, đúng value_type")
  void everySeededKey_existsInEnum() {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList("SELECT param_key, value_type FROM system_parameters");

    assertThat(rows).isNotEmpty();
    for (Map<String, Object> row : rows) {
      String paramKey = (String) row.get("param_key");
      assertThatCode(() -> SystemParameterKey.valueOf(paramKey))
          .as("param_key %s có trong DB nhưng thiếu trong SystemParameterKey", paramKey)
          .doesNotThrowAnyException();
      assertThat(SystemParameterKey.valueOf(paramKey).getExpectedType())
          .as("expectedType của %s phải khớp value_type đã seed", paramKey)
          .isEqualTo(ParameterValueType.valueOf((String) row.get("value_type")));
    }
  }

  @Test
  @DisplayName("Mọi hằng số của SystemParameterKey đều đã được seed trong DB")
  void everyEnumKey_isSeeded() {
    Set<String> dbKeys =
        Set.copyOf(
            jdbcTemplate.queryForList("SELECT param_key FROM system_parameters", String.class));
    Set<String> enumKeys =
        Arrays.stream(SystemParameterKey.values()).map(Enum::name).collect(Collectors.toSet());

    assertThat(dbKeys).as("Key có trong enum nhưng chưa có migration seed").containsAll(enumKeys);
  }
}
