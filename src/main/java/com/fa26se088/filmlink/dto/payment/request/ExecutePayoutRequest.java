package com.fa26se088.filmlink.dto.payment.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request Admin thực thi giải ngân Payout cho Film Lab và đính kèm biên lai (UC35, BR-31).
 */
public record ExecutePayoutRequest(
    @NotBlank(message = "Tên ngân hàng không được để trống")
    String bankName,

    @NotBlank(message = "Số tài khoản ngân hàng không được để trống")
    String bankAccountNumber,

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    String accountHolderName,

    @NotBlank(message = "URL hoặc key ảnh biên lai không được để trống")
    String receiptBlobKey
) {}

