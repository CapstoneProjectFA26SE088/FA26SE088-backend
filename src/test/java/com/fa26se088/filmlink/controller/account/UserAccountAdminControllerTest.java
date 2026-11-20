package com.fa26se088.filmlink.controller.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.dto.account.request.CreateAdminRequest;
import com.fa26se088.filmlink.dto.account.response.UserAccountResponse;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.account.enums.UserAccountStatus;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.fa26se088.filmlink.service.account.UserAccountService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAccountAdminControllerTest {

  private static final String BASE_PATH = "/api/v1/admin/accounts";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private UserAccountRepository userAccountRepository;

  @MockitoBean private UserAccountService userAccountService;

  private final List<UserAccount> createdAccounts = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    userAccountRepository.deleteAll(createdAccounts);
  }

  @Test
  @DisplayName("POST /api/v1/admin/accounts không có token trả 401")
  void createAdmin_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "newadmin@example.com",
                      "password": "Password123",
                      "fullName": "New Admin"
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /api/v1/admin/accounts với role PHOTOGRAPHER trả 403 Forbidden")
  void createAdmin_forbiddenRole_returns403() throws Exception {
    mockMvc
        .perform(
            post(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(UserAccountRole.PHOTOGRAPHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "newadmin@example.com",
                      "password": "Password123",
                      "fullName": "New Admin"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /api/v1/admin/accounts với ADMIN trả 201 Created")
  void createAdmin_admin_returns201() throws Exception {
    when(userAccountService.createAdmin(any(CreateAdminRequest.class)))
        .thenReturn(
            new UserAccountResponse(
                10L,
                "newadmin@example.com",
                "New Admin",
                "0901234567",
                UserAccountRole.ADMIN,
                UserAccountStatus.ACTIVE,
                Instant.now(),
                Instant.now()));

    mockMvc
        .perform(
            post(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(UserAccountRole.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "newadmin@example.com",
                      "password": "Password123",
                      "fullName": "New Admin",
                      "phoneNumber": "0901234567"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.email").value("newadmin@example.com"))
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(userAccountService).createAdmin(any(CreateAdminRequest.class));
  }

  @Test
  @DisplayName("POST /api/v1/admin/accounts với body không hợp lệ trả 400 Bad Request")
  void createAdmin_invalidBody_returns400() throws Exception {
    mockMvc
        .perform(
            post(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(UserAccountRole.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "invalid-email",
                      "password": "short",
                      "fullName": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
  }

  private String bearer(UserAccountRole role) {
    UserAccount account = new UserAccount();
    account.setEmail("admin-test-" + UUID.randomUUID() + "@example.com");
    account.setPasswordHash("hash");
    account.setFullName("Admin Test");
    account.setRole(role);
    account.setStatus(UserAccountStatus.ACTIVE);
    account.setTermsAcceptedAt(Instant.now());
    createdAccounts.add(userAccountRepository.save(account));
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(account.getId(), account.getEmail(), role);
  }
}
