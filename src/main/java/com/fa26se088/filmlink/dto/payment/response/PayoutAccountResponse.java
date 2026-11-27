package com.fa26se088.filmlink.dto.payment.response;

import java.time.Instant;

/** Tài khoản ngân hàng nhận tiền hiện hành của lab. */
public record PayoutAccountResponse(
    Long id,
    Long filmLabId,
    String bankName,
    String bankAccountNumber,
    String accountHolderName,
    Instant updatedAt) {}
