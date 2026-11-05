package com.fa26se088.filmlink.controller.account;

import com.fa26se088.filmlink.dto.account.request.CreateAdminRequest;
import com.fa26se088.filmlink.dto.account.response.UserAccountResponse;
import com.fa26se088.filmlink.service.account.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Quản trị tài khoản người dùng dành cho Admin. */
@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "User Account - Admin", description = "Quản trị tài khoản (Admin)")
public class UserAccountAdminController {

  private final UserAccountService userAccountService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "[Admin] Tạo tài khoản Admin mới",
      description = "Yêu cầu quyền ADMIN. Tạo tài khoản Admin mới với trạng thái ACTIVE và ghi nhận audit log.")
  public UserAccountResponse createAdmin(@Valid @RequestBody CreateAdminRequest request) {
    return userAccountService.createAdmin(request);
  }
}
