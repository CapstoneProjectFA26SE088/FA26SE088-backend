package com.fa26se088.filmlink.dto.payment.response;

import java.time.Instant;

/** Link thanh toán VNPay trả cho caller (contract SPEC-00 §4.5). */
public record PaymentLink(
    Long paymentId, String transactionCode, String paymentUrl, Instant expiresAt) {}
