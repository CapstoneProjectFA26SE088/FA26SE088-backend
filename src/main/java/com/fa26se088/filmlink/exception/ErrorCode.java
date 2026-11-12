package com.fa26se088.filmlink.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi trả cho client trong property {@code errorCode} của {@code ProblemDetail}.
 *
 * <p>Đặt tên theo mẫu {@code <DOMAIN>_<LÝ_DO>}. {@code messageCode} là mã thông báo hiển thị của
 * SRS (Appendix 5.3, MSG01–MSG37): FE dùng mã này để chọn câu tiếng Việt (CR-13). Lỗi không có
 * thông báo tương ứng trong SRS thì {@code messageCode = null}.
 */
@Getter
public enum ErrorCode {
  // --- Chung ---
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, null),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  REQUEST_NOT_SUPPORTED(HttpStatus.METHOD_NOT_ALLOWED, null),
  BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, null),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, null),

  // --- Xác thực / phân quyền ---
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, null),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, null),

  // --- account ---
  ACCOUNT_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "MSG09"),
  ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, null),
  ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, null),
  /** NFR-SEC-07: khoá đăng nhập tạm thời 15 phút sau 5 lần sai liên tiếp (MSG10). */
  ACCOUNT_TEMPORARILY_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "MSG10"),
  ACCOUNT_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, null),
  ACCOUNT_OTP_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "MSG11"),
  /** UH-36: gửi lại OTP quá nhanh (chưa đủ 60 giây giữa 2 lần gửi liên tiếp) (MSG12). */
  ACCOUNT_OTP_RESEND_TOO_FAST(HttpStatus.TOO_MANY_REQUESTS, "MSG12"),
  /** UH-36: gửi lại OTP quá số lần cho phép (tối đa 3 lần trong cửa sổ 15 phút) (MSG12). */
  ACCOUNT_OTP_RESEND_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "MSG12"),
  ACCOUNT_REGISTRATION_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** UC02: mật khẩu cũ không đúng khi đổi mật khẩu. */
  ACCOUNT_OLD_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, null),
  /** UC02: mật khẩu mới trùng với mật khẩu hiện tại. */
  ACCOUNT_NEW_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, null),

  // --- lab ---
  LAB_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  LAB_ACCESS_DENIED(HttpStatus.FORBIDDEN, null),
  LAB_NOT_ACCEPTING_ORDERS(HttpStatus.BAD_REQUEST, null),

  // --- order ---
  ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  ORDER_INVALID_STATE(HttpStatus.CONFLICT, null),
  ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, null),
  ORDER_ALREADY_PAID(HttpStatus.CONFLICT, null),
  ORDER_CREATION_FAILED(HttpStatus.BAD_REQUEST, null),

  // --- payment ---
  PAYMENT_GATEWAY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, null),
  PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  PAYMENT_INVALID_STATE(HttpStatus.CONFLICT, null),
  PAYMENT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, null),
  PAYMENT_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, null),
  PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, null),

  // --- storage (client/storage) ---
  STORAGE_FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, null),
  STORAGE_FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, null),
  /** Sai chữ ký và hết hạn dùng chung một mã, không tiết lộ lý do. */
  STORAGE_LINK_INVALID(HttpStatus.FORBIDDEN, null),
  /** UH-39: storage lỗi thì báo rõ, phần khác vẫn chạy. */
  STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, null),

  // --- notification / email (client/notification) ---
  /** UH-37: Lỗi gửi email qua SMTP hoặc dịch vụ mail không khả dụng. */
  EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, null),

  // --- delivery / geocoding (client/maps) ---
  /** DEL-GEO-01: API key Google Maps chưa được cấu hình. */
  GEOCODING_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, null),
  /** DEL-GEO-01: Không tìm thấy kết quả tọa độ hoặc địa chỉ tương ứng. */
  GEOCODING_NO_RESULT(HttpStatus.NOT_FOUND, null),
  /** DEL-GEO-01: Lỗi khi kết nối hoặc gọi API bên ngoài (Google Maps Geocoding). */
  GEOCODING_API_ERROR(HttpStatus.BAD_GATEWAY, null);

  private final HttpStatus httpStatus;
  private final String messageCode;

  ErrorCode(HttpStatus httpStatus, String messageCode) {
    this.httpStatus = httpStatus;
    this.messageCode = messageCode;
  }
}
