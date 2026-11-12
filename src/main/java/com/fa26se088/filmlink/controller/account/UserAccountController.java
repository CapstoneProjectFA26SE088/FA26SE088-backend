package com.fa26se088.filmlink.controller.account;

import com.fa26se088.filmlink.dto.account.request.ChangePasswordRequest;
import com.fa26se088.filmlink.dto.account.request.UpdateAccountRequest;
import com.fa26se088.filmlink.dto.account.response.UserAccountResponse;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.account.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý tài khoản người dùng (UC01, UC02, ACC-09).
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "User Account", description = "Quản lý thông tin tài khoản (RUD: Tìm kiếm, Cập nhật, Vô hiệu hóa) và đổi mật khẩu")
public class UserAccountController {

  private final UserAccountService userAccountService;

  @GetMapping("/me")
  @Operation(summary = "Thông tin tài khoản đang đăng nhập")
  public UserAccountResponse getCurrentAccount(
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return userAccountService.getCurrentAccount(account.id());
  }

  @PutMapping("/me")
  @Operation(
      summary = "Cập nhật thông tin tài khoản của chính mình (ACC-09)",
      description = "Chính chủ cập nhật thông tin cá nhân. Không cho phép sửa Email, Role, Password.")
  public UserAccountResponse updateCurrentAccount(
      @Valid @RequestBody UpdateAccountRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return userAccountService.updateAccount(account.id(), request, account);
  }

  @PutMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Đổi mật khẩu tài khoản (UC02)",
      description =
          "Kiểm tra mật khẩu cũ, mật khẩu mới khác cũ và đủ quy tắc. Thu hồi mọi refresh token đang hoạt động.")
  public void changePassword(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody ChangePasswordRequest request) {
    userAccountService.changePassword(account.id(), request);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Tra cứu tài khoản theo ID (ACC-09)",
      description = "Admin có thể xem bất kỳ ai; người dùng thông thường chỉ được xem tài khoản của chính mình.")
  public UserAccountResponse getAccountById(
      @PathVariable Long id,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return userAccountService.getAccountById(id, account);
  }

  @GetMapping(params = "email")
  @Operation(
      summary = "Tra cứu tài khoản theo Email qua query parameter (ACC-09)",
      description = "Admin có thể tìm bất kỳ email nào; người dùng thông thường chỉ được tìm email của chính mình.")
  public UserAccountResponse getAccountByEmailParam(
      @RequestParam String email,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return userAccountService.getAccountByEmail(email, account);
  }

  @GetMapping("/by-email")
  @Operation(
      summary = "Tra cứu tài khoản theo Email qua endpoint /by-email (ACC-09)",
      description = "Admin có thể tìm bất kỳ email nào; người dùng thông thường chỉ được tìm email của chính mình.")
  public UserAccountResponse getAccountByEmailPath(
      @RequestParam String email,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return userAccountService.getAccountByEmail(email, account);
  }

  @GetMapping("/search")
  @Operation(
      summary = "Tìm kiếm tài khoản theo ID hoặc Email (ACC-09)",
      description = "Hỗ trợ truyền id hoặc email để tìm kiếm tài khoản.")
  public UserAccountResponse searchAccount(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) String email,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    if (id != null) {
      return userAccountService.getAccountById(id, account);
    }
    if (email != null && !email.isBlank()) {
      return userAccountService.getAccountByEmail(email, account);
    }
    throw new BusinessException(
        ErrorCode.VALIDATION_ERROR, "Vui lòng cung cấp tham số 'id' hoặc 'email' để tìm kiếm.");
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Cập nhật thông tin cá nhân theo ID (ACC-09)",
      description = "Admin hoặc chính chủ có thể cập nhật. Không cho phép sửa Email, Role, Password.")
  public UserAccountResponse updateAccount(
      @PathVariable Long id,
      @Valid @RequestBody UpdateAccountRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return userAccountService.updateAccount(id, request, account);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Vô hiệu hoá tài khoản / Soft Delete (ACC-09)",
      description = "Chỉ Admin có quyền vô hiệu hoá. Chuyển trạng thái sang INACTIVE, đăng xuất và thu hồi mọi token.")
  public void deactivateAccount(
      @PathVariable Long id,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    userAccountService.deactivateAccount(id, account);
  }

  @PatchMapping("/{id}/deactivate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Vô hiệu hoá tài khoản qua endpoint /deactivate (ACC-09)",
      description = "Chỉ Admin có quyền vô hiệu hoá.")
  public void deactivateAccountPatch(
      @PathVariable Long id,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    userAccountService.deactivateAccount(id, account);
  }
}
