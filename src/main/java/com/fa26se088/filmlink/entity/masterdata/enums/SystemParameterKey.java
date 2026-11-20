package com.fa26se088.filmlink.entity.masterdata.enums;

import lombok.Getter;

/**
 * Danh sách định danh các tham số hệ thống (ERD #52, UC54, BR-38).
 * Quản lý có kiểu, không dùng chuỗi rải rác.
 */
@Getter
public enum SystemParameterKey {

  /** Tỉ lệ hoa hồng mặc định của sàn, 0-30% (BR-10). */
  DEFAULT_COMMISSION_RATE(ParameterValueType.PERCENT),

  /** Phí phạt khi Photographer hủy sau khi lab nhận phim, % trên số tiền đã thanh toán (BR-38). */
  ORDER_CANCELLATION_FEE_RATE(ParameterValueType.PERCENT),

  /** Ngày đến hạn thanh toán nợ hoa hồng trong tháng tiếp theo, 1-28 (BR-20). */
  BILLING_DUE_DAY_OF_MONTH(ParameterValueType.INTEGER),

  /** Số ngày quá hạn: Lab bị loại khỏi gợi ý (BR-21). */
  SANCTION_LEVEL_1_DAYS(ParameterValueType.INTEGER),

  /** Số ngày quá hạn: Lab bị ẩn khỏi kết quả tìm kiếm (BR-21). */
  SANCTION_LEVEL_2_DAYS(ParameterValueType.INTEGER),

  /** Số ngày quá hạn: Lab bị chặn nhận đơn hàng mới (BR-21). */
  SANCTION_LEVEL_3_DAYS(ParameterValueType.INTEGER),

  /** Số ngày tự động hoàn tất đơn sau khi trả hàng nếu không khiếu nại (BR-24). */
  ORDER_AUTO_CLOSE_DAYS(ParameterValueType.INTEGER),

  /** Số ngày tự động hủy đơn Self Drop-off nếu lab chưa nhận phim (BR-23). */
  SELF_DROPOFF_AUTO_CANCEL_DAYS(ParameterValueType.INTEGER),

  /** Thời hạn (giờ) để lab phản hồi chấp nhận hoặc từ chối đơn hàng mới. */
  LAB_RESPONSE_LIMIT_HOURS(ParameterValueType.INTEGER),

  /** Thời hạn (ngày) cho phép yêu cầu quét lại (re-scan) (BR-27). */
  RESCAN_WINDOW_DAYS(ParameterValueType.INTEGER),

  /** Hạn mức dung lượng lưu trữ số miễn phí (GB) cho mỗi Photographer. */
  FREE_STORAGE_QUOTA_GB(ParameterValueType.INTEGER),

  /** Hạn mức số câu hỏi tư vấn Chatbot AI cho mỗi user mỗi ngày. */
  CHATBOT_DAILY_QUESTION_QUOTA(ParameterValueType.INTEGER),

  // ---- Order / lab (M-02) ----

  /** Số ngày làm việc cam kết Express mặc định; lab được ghi đè 1 hoặc 2 (UC20, UC69). */
  EXPRESS_COMMITMENT_WORKING_DAYS(ParameterValueType.INTEGER),

  // ---- Payment (cụm 3) ----

  /** Thời hạn hiệu lực link thanh toán VNPay, phút (UC30, UC62). */
  PAYMENT_LINK_TTL_MINUTES(ParameterValueType.INTEGER),

  /** Số giờ đơn ONLINE chưa thanh toán thì bị hủy (UH-18, non-screen #5). */
  UNPAID_ORDER_AUTO_CANCEL_HOURS(ParameterValueType.INTEGER),

  /** Chu kỳ job đối soát giao dịch chưa nhận IPN, phút (UH-19, non-screen #4). */
  PAYMENT_RECONCILE_MINUTES(ParameterValueType.INTEGER),

  /** Số ngày để Photographer xác nhận bản ghi Pay-at-Lab (UC31). */
  PAY_AT_LAB_CONFIRM_DAYS(ParameterValueType.INTEGER),

  /** Số ngày làm việc để hoàn tiền giao dịch trùng (UH-20). */
  DUPLICATE_PAYMENT_REFUND_DAYS(ParameterValueType.INTEGER),

  // ---- Consignment / marketplace (cụm 4) ----

  /** Phí ký gửi mặc định của lab, % giá bán; lab được ghi đè (BR-54). */
  CONSIGNMENT_FEE_RATE(ParameterValueType.PERCENT),

  /** Hoa hồng nền tảng trên một giao dịch ký gửi, % giá bán (BR-54). */
  CONSIGNMENT_COMMISSION_RATE(ParameterValueType.PERCENT),

  /** Thời hạn ký gửi tính từ lúc chấp nhận, ngày (BR-53). */
  CONSIGNMENT_PERIOD_DAYS(ParameterValueType.INTEGER),

  /** Số ngày gia hạn khi chọn gia hạn lúc hết hạn ký gửi (BR-53). */
  CONSIGNMENT_EXTENSION_DAYS(ParameterValueType.INTEGER),

  /** Ngày thứ N của kỳ ký gửi thì nhắc người bán (BR-53). */
  CONSIGNMENT_REMINDER_DAY(ParameterValueType.INTEGER),

  /** Số ngày để trả lời khi hết hạn ký gửi trước khi listing bị ẩn (BR-53). */
  CONSIGNMENT_EXPIRY_RESPONSE_DAYS(ParameterValueType.INTEGER),

  /** Khung giờ gửi hàng được mở trong N ngày tới (UC45). */
  CONSIGNMENT_BOOKING_HORIZON_DAYS(ParameterValueType.INTEGER),

  /** Số lịch hẹn đang chờ tối đa của một người bán cùng lúc (UC45). */
  CONSIGNMENT_MAX_PENDING_BOOKINGS(ParameterValueType.INTEGER),

  /** Số giờ sau khung hẹn thì lịch hẹn bị đánh dấu vắng mặt (UH-43). */
  CONSIGNMENT_NO_SHOW_HOURS(ParameterValueType.INTEGER),

  /** Số lần vắng mặt liên tiếp thì bị khóa đặt lịch (UH-43). */
  CONSIGNMENT_NO_SHOW_LIMIT(ParameterValueType.INTEGER),

  /** Số ngày khóa đặt lịch sau khi vắng mặt quá nhiều (UH-43). */
  CONSIGNMENT_BOOKING_LOCK_DAYS(ParameterValueType.INTEGER),

  /** Hạn trả lời một vòng thương lượng giá, giờ (UC47). */
  PRICE_OFFER_RESPONSE_HOURS(ParameterValueType.INTEGER),

  /** Số vòng thương lượng tối đa trước khi đóng yêu cầu (UC47, UH-45). */
  PRICE_OFFER_MAX_ROUNDS(ParameterValueType.INTEGER),

  /** Số ngày để người bán nhận lại món bị từ chối / không bán được (UH-45). */
  CONSIGNMENT_COLLECTION_DAYS(ParameterValueType.INTEGER),

  /** Thời hạn yêu cầu trả hàng sau khi nhận món, ngày (BR-56). */
  CONSIGNMENT_RETURN_WINDOW_DAYS(ParameterValueType.INTEGER),

  /** Đã thanh toán nhưng không đến nhận: Admin xử lý sau N ngày, nhắc ngày 7 và 12 (UH-47). */
  CONSIGNMENT_UNCOLLECTED_ADMIN_DAYS(ParameterValueType.INTEGER),

  // ---- Community / moderation (cụm 5) ----

  /** Số ngày được sửa Lab Review sau khi đăng (UC37). */
  LAB_REVIEW_EDIT_DAYS(ParameterValueType.INTEGER),

  /** Số Content Report tối đa mỗi user mỗi ngày (UC49, UH-29). */
  CONTENT_REPORT_DAILY_LIMIT(ParameterValueType.INTEGER),

  /** Được khiếu nại tới N ngày sau khi đơn đóng (UC51). */
  COMPLAINT_WINDOW_DAYS(ParameterValueType.INTEGER),

  /** Số ngày để một bên gửi bằng chứng được yêu cầu (UC52). */
  COMPLAINT_INFO_RESPONSE_DAYS(ParameterValueType.INTEGER),

  // ---- AI (cụm 5, M-05, M-06) ----

  /** Số lượt hội thoại gửi kèm làm ngữ cảnh cho Chatbot (UC19). */
  CHATBOT_CONTEXT_TURNS(ParameterValueType.INTEGER),

  /** Số trích dẫn tối đa trong một câu trả lời của Chatbot (UC19). */
  CHATBOT_MAX_CITATIONS(ParameterValueType.INTEGER),

  /** Số lab tối đa trong một lần gợi ý (UC18). */
  RECOMMENDATION_MAX_RESULTS(ParameterValueType.INTEGER),

  /** Thời gian cache kết quả gợi ý theo user, phút (UC18). */
  RECOMMENDATION_CACHE_MINUTES(ParameterValueType.INTEGER),

  /** Độ tin cậy tối thiểu để film stock được hiện làm gợi ý; user luôn xác nhận (UC40). */
  IMAGE_ANALYSIS_CONFIDENCE_THRESHOLD(ParameterValueType.DECIMAL),

  /** Model embedding hiện hành; đọc header cụm 5 trước khi đổi (M-05). */
  EMBEDDING_MODEL(ParameterValueType.STRING),

  // ---- Archive (M-04) ----

  /** Số ngày sau khi giao trả để lab xác nhận xóa bản sao scan của mình (BR-44). */
  LAB_SCAN_RETENTION_DAYS(ParameterValueType.INTEGER);

  private final ParameterValueType expectedType;

  SystemParameterKey(ParameterValueType expectedType) {
    this.expectedType = expectedType;
  }
}
