package com.fa26se088.filmlink.dto.payment.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Định dạng phản hồi cho VNPay IPN webhook (UC30).
 * VNPay yêu cầu chính xác 2 key viết hoa: {@code RspCode} và {@code Message}.
 */
public record VnPayIpnResponse(
    @JsonProperty("RspCode") String rspCode,
    @JsonProperty("Message") String message) {

  public static VnPayIpnResponse success() {
    return new VnPayIpnResponse("00", "Confirm Success");
  }

  public static VnPayIpnResponse orderNotFound() {
    return new VnPayIpnResponse("01", "Order not found");
  }

  public static VnPayIpnResponse alreadyConfirmed() {
    return new VnPayIpnResponse("02", "Order already confirmed");
  }

  public static VnPayIpnResponse invalidAmount() {
    return new VnPayIpnResponse("04", "Invalid amount");
  }

  public static VnPayIpnResponse invalidSignature() {
    return new VnPayIpnResponse("97", "Invalid signature");
  }

  public static VnPayIpnResponse unknownError(String message) {
    return new VnPayIpnResponse("99", message == null ? "Unknown error" : message);
  }
}

