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
  /**
   * Xung đột ghi đồng thời (Optimistic Locking, {@code @Version}) mà service không tự đổi sang mã
   * riêng của domain; {@code GlobalExceptionHandler} dùng mã này.
   */
  CONCURRENT_UPDATE_CONFLICT(HttpStatus.CONFLICT, null),

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
  ACCOUNT_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, null),
  /** UC07: số lượng file giấy tờ pháp lý không nằm trong khoảng 1..5 file. */
  LAB_APPLICATION_DOCUMENT_COUNT_INVALID(HttpStatus.BAD_REQUEST, null),
  /** BR-16: dịch vụ, quy trình hoặc khổ phim trong đơn đăng ký không tồn tại hoặc không ACTIVE. */
  LAB_APPLICATION_CATALOG_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** UC08: không tìm thấy đơn đăng ký lab. */
  LAB_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  /** UC08: đơn đăng ký lab đã được duyệt hoặc từ chối trước đó. */
  LAB_APPLICATION_ALREADY_REVIEWED(HttpStatus.CONFLICT, null),
  /** UC08: xung đột phiên bản (Optimistic Locking) khi 2 Admin cùng xử lý một đơn đăng ký lab. */
  LAB_APPLICATION_CONFLICT(HttpStatus.CONFLICT, null),

  // --- lab ---
  LAB_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  LAB_ACCESS_DENIED(HttpStatus.FORBIDDEN, null),
  /** UC12: tối đa 20 ảnh mẫu mỗi lab. */
  LAB_IMAGE_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_CONTENT, "MSG08"),
  /** Ảnh không tồn tại hoặc thuộc lab khác (không lộ id ảnh của lab khác). */
  LAB_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  /** BR-57: chưa có máy PROCESSOR và máy SCANNER đang ACTIVE thì không được bật nhận đơn. */
  LAB_MACHINES_NOT_READY(HttpStatus.UNPROCESSABLE_CONTENT, "MSG32"),
  /** Gói không tồn tại hoặc thuộc lab khác (không lộ id gói của lab khác). */
  LAB_PACKAGE_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  /** UC13: tên gói trùng một gói khác của cùng lab (không phân biệt hoa thường). */
  LAB_PACKAGE_NAME_DUPLICATE(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** BR-16: Service Type / Film Process / Film Format không tồn tại hoặc không còn ACTIVE. */
  LAB_PACKAGE_MASTERDATA_INACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** UC13: mức scan bị bỏ khi sửa gói đang được {@code film_rolls} tham chiếu; nên ngừng bán gói. */
  LAB_SCAN_OPTION_IN_USE(HttpStatus.CONFLICT, null),

  // --- machine (UC68, BR-57, BR-58) ---
  /** Máy không tồn tại hoặc thuộc lab khác (không lộ id máy của lab khác). */
  MACHINE_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  /** UC68: mã máy trùng một máy khác trong cùng lab (không phân biệt hoa thường). */
  MACHINE_CODE_DUPLICATE(HttpStatus.CONFLICT, null),
  /** BR-16: Film Process hoặc Film Format của máy không tồn tại hoặc không còn ACTIVE. */
  MACHINE_MASTERDATA_INACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** UC70: không tìm thấy sự cố của máy. */
  MACHINE_INCIDENT_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  /** UC70: sự cố của máy đã được giải quyết trước đó. */
  MACHINE_INCIDENT_ALREADY_RESOLVED(HttpStatus.CONFLICT, null),

  // --- order ---
  /** UC20, BR-34, BR-57: lab chưa bật nhận đơn hoặc chưa đủ điều kiện nhận đơn. */
  ORDER_LAB_NOT_ACCEPTING(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** UC20: gói không thuộc lab, không ON_SALE, hoặc master data của gói đã ngừng dùng (BR-16). */
  ORDER_PACKAGE_NOT_ORDERABLE(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** UC20: khổ phim của cuộn khác khổ phim của gói. */
  ORDER_FILM_FORMAT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** UC20: mức scan không thuộc gói đã chọn. */
  ORDER_SCAN_OPTION_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** UC20: gói không nhận push/pull mà cuộn có số stop khác 0. */
  ORDER_PUSH_PULL_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** UC20: dòng phim không có trong danh mục hoặc đã ngừng dùng (BR-16). */
  ORDER_FILM_STOCK_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** Phương án chưa hỗ trợ ở giai đoạn này: Express, Parcel Shipping, COD. */
  ORDER_OPTION_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** Đơn không tồn tại hoặc thuộc lab khác (không lộ đơn của lab khác). */
  ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  /** UC22: thao tác không hợp lệ với trạng thái hiện tại của đơn, hoặc đơn vừa bị người khác đổi. */
  ORDER_STATUS_CONFLICT(HttpStatus.CONFLICT, null),
  /** UC69, UH-51: lab không đủ máy / công suất phù hợp để xử lý đơn. */
  ORDER_SCHEDULE_INFEASIBLE(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** BR-60: ngày hẹn trả lab chọn sớm hơn ngày đề xuất; lab chỉ được dời muộn hơn. */
  ORDER_PROMISED_DATE_TOO_EARLY(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** BR-07, UC24: công đoạn ghi không phải công đoạn kế tiếp (nhảy cóc, lùi, lặp, hoặc đã tới QC). */
  ORDER_STAGE_OUT_OF_ORDER(HttpStatus.UNPROCESSABLE_CONTENT, null),

  // --- payment ---
  PAYMENT_GATEWAY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, null),

  // --- storage (client/storage) ---
  STORAGE_FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, null),
  STORAGE_FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, null),
  /** Sai chữ ký và hết hạn dùng chung một mã, không tiết lộ lý do. */
  STORAGE_LINK_INVALID(HttpStatus.FORBIDDEN, null),
  /** UH-39: storage lỗi thì báo rõ, phần khác vẫn chạy. */
  STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, null),

  // --- masterdata (MD-02, UC54) ---
  /** Mã code đã tồn tại trong bảng danh mục (BR-16). */
  MASTERDATA_CODE_DUPLICATE(HttpStatus.CONFLICT, null),
  /** Danh mục đang được tham chiếu, không thể xóa cứng; phải chuyển sang DISCONTINUED (BR-16). */
  MASTERDATA_IN_USE(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** Tham số hệ thống không tồn tại (UC54). */
  SYSTEM_PARAMETER_NOT_FOUND(HttpStatus.NOT_FOUND, null),
  /** Giá trị tham số hệ thống không hợp lệ hoặc vi phạm ràng buộc miền giá trị / thứ tự (BR-38). */
  SYSTEM_PARAMETER_INVALID_VALUE(HttpStatus.UNPROCESSABLE_CONTENT, null),
  /** Xung đột phiên bản cập nhật tham số hệ thống do có người khác cập nhật trước (Optimistic Locking). */
  SYSTEM_PARAMETER_CONFLICT(HttpStatus.CONFLICT, null);

  private final HttpStatus httpStatus;
  private final String messageCode;

  ErrorCode(HttpStatus httpStatus, String messageCode) {
    this.httpStatus = httpStatus;
    this.messageCode = messageCode;
  }
}
