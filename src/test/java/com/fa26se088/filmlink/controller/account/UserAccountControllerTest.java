package com.fa26se088.filmlink.controller.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.dto.account.request.UpdateAccountRequest;
import com.fa26se088.filmlink.dto.account.response.UserAccountResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.account.enums.UserAccountStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.exception.GlobalExceptionHandler;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.account.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class UserAccountControllerTest {

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private UserAccountService userAccountService;

  private AuthenticatedAccount currentAccount;

  @BeforeEach
  void setUp() {
    currentAccount = new AuthenticatedAccount(1L, "user@example.com", UserAccountRole.PHOTOGRAPHER);

    HandlerMethodArgumentResolver authPrincipalResolver =
        new HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
          }

          @Override
          public Object resolveArgument(
              MethodParameter parameter,
              ModelAndViewContainer mavContainer,
              NativeWebRequest webRequest,
              WebDataBinderFactory binderFactory) {
            return currentAccount;
          }
        };

    UserAccountController controller = new UserAccountController(userAccountService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(authPrincipalResolver)
            .build();
  }

  private UserAccountResponse sampleResponse(Long id, String email, String fullName) {
    return new UserAccountResponse(
        id,
        email,
        fullName,
        "0912345678",
        "avatars/1/avatar.png",
        UserAccountRole.PHOTOGRAPHER,
        UserAccountStatus.ACTIVE,
        Instant.now(),
        Instant.now());
  }

  // --- Tra cứu theo ID (Read) ---

  @Test
  @DisplayName("GET /api/v1/accounts/{id} thành công trả 200 và dữ liệu chi tiết")
  void getAccountById_found_returns200() throws Exception {
    when(userAccountService.getAccountById(eq(1L), any()))
        .thenReturn(sampleResponse(1L, "user@example.com", "Nguyen Van A"));

    mockMvc
        .perform(get("/api/v1/accounts/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.fullName").value("Nguyen Van A"));
  }

  @Test
  @DisplayName("GET /api/v1/accounts/{id} không tìm thấy trả lỗi 404")
  void getAccountById_notFound_returns404() throws Exception {
    when(userAccountService.getAccountById(eq(999L), any()))
        .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài khoản"));

    mockMvc
        .perform(get("/api/v1/accounts/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
  }

  // --- Tra cứu theo Email (Read) ---

  @Test
  @DisplayName("GET /api/v1/accounts?email=... thành công trả 200")
  void getAccountByEmailParam_found_returns200() throws Exception {
    when(userAccountService.getAccountByEmail(eq("user@example.com"), any()))
        .thenReturn(sampleResponse(1L, "user@example.com", "Nguyen Van A"));

    mockMvc
        .perform(get("/api/v1/accounts").param("email", "user@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("user@example.com"));
  }

  @Test
  @DisplayName("GET /api/v1/accounts?email=... không tìm thấy trả lỗi 404")
  void getAccountByEmailParam_notFound_returns404() throws Exception {
    when(userAccountService.getAccountByEmail(eq("unknown@example.com"), any()))
        .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài khoản"));

    mockMvc
        .perform(get("/api/v1/accounts").param("email", "unknown@example.com"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  @DisplayName("GET /api/v1/accounts/search?id=... thành công trả 200")
  void searchAccount_byId_returns200() throws Exception {
    when(userAccountService.getAccountById(eq(1L), any()))
        .thenReturn(sampleResponse(1L, "user@example.com", "Nguyen Van A"));

    mockMvc
        .perform(get("/api/v1/accounts/search").param("id", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  @DisplayName("GET /api/v1/accounts/search?email=... thành công trả 200")
  void searchAccount_byEmail_returns200() throws Exception {
    when(userAccountService.getAccountByEmail(eq("user@example.com"), any()))
        .thenReturn(sampleResponse(1L, "user@example.com", "Nguyen Van A"));

    mockMvc
        .perform(get("/api/v1/accounts/search").param("email", "user@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("user@example.com"));
  }

  @Test
  @DisplayName("GET /api/v1/accounts/search thiếu cả id và email trả 400 VALIDATION_ERROR")
  void searchAccount_noParams_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/accounts/search"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
  }

  // --- Cập nhật thông tin (Update) ---

  @Test
  @DisplayName("PUT /api/v1/accounts/{id} gửi dữ liệu hợp lệ trả 200 và thông tin mới")
  void updateAccount_validData_returns200() throws Exception {
    UpdateAccountRequest request =
        new UpdateAccountRequest("Nguyen Van B", "0987654321", "avatars/new.png", true, true, false);

    when(userAccountService.updateAccount(eq(1L), any(), any()))
        .thenReturn(sampleResponse(1L, "user@example.com", "Nguyen Van B"));

    mockMvc
        .perform(
            put("/api/v1/accounts/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Nguyen Van B"));
  }

  @Test
  @DisplayName("PUT /api/v1/accounts/{id} sai định dạng (tên trống) trả 400")
  void updateAccount_invalidName_returns400() throws Exception {
    UpdateAccountRequest request =
        new UpdateAccountRequest("", "0987654321", null, null, null, null);

    mockMvc
        .perform(
            put("/api/v1/accounts/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PUT /api/v1/accounts/{id} sai định dạng số điện thoại trả 400")
  void updateAccount_invalidPhone_returns400() throws Exception {
    UpdateAccountRequest request =
        new UpdateAccountRequest("Nguyen Van A", "12345", null, null, null, null);

    mockMvc
        .perform(
            put("/api/v1/accounts/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  // --- Vô hiệu hóa (Deactivate / Soft Delete) ---

  @Test
  @DisplayName("DELETE /api/v1/accounts/{id} thành công trả 204 No Content")
  void deactivateAccount_deleteMethod_returns204() throws Exception {
    doNothing().when(userAccountService).deactivateAccount(eq(1L), any());

    mockMvc
        .perform(delete("/api/v1/accounts/1"))
        .andExpect(status().isNoContent());

    verify(userAccountService).deactivateAccount(eq(1L), any());
  }

  @Test
  @DisplayName("PATCH /api/v1/accounts/{id}/deactivate thành công trả 204 No Content")
  void deactivateAccount_patchMethod_returns204() throws Exception {
    doNothing().when(userAccountService).deactivateAccount(eq(1L), any());

    mockMvc
        .perform(patch("/api/v1/accounts/1/deactivate"))
        .andExpect(status().isNoContent());

    verify(userAccountService).deactivateAccount(eq(1L), any());
  }

  @Test
  @DisplayName("DELETE /api/v1/accounts/{id} không có quyền trả 403 Forbidden")
  void deactivateAccount_unauthorized_returns403() throws Exception {
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "Chỉ quản trị viên mới có quyền"))
        .when(userAccountService)
        .deactivateAccount(eq(1L), any());

    mockMvc
        .perform(delete("/api/v1/accounts/1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
  }
}

