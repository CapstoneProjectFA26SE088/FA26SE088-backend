package com.fa26se088.filmlink.dto.payment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Chủ lab khai báo tài khoản ngân hàng nhận tiền (UC35). */
public record UpdatePayoutAccountRequest(
    @NotBlank @Size(max = 100) String bankName,
    @NotBlank @Pattern(regexp = "^[0-9]{6,30}$", message = "must contain 6 to 30 digits")
        String bankAccountNumber,
    @NotBlank @Size(min = 2, max = 100) String accountHolderName) {}
