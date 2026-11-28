package com.fa26se088.filmlink.service.marketplace;

import com.fa26se088.filmlink.dto.marketplace.request.CreateConsignmentBookingRequest;
import com.fa26se088.filmlink.dto.marketplace.request.RecordConditionReportRequest;
import com.fa26se088.filmlink.dto.marketplace.request.UpdateConsignmentSettingsRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConditionReportResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentBookingResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSettingsResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSlotResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Cấu hình nhận ký gửi, đặt lịch hẹn, check-in và kiểm định thiết bị (F-20, UC45, UC46). */
public interface ConsignmentInspectionService {

  /** Chủ Lab xem cấu hình nhận ký gửi hiện hành của Lab mình. */
  ConsignmentSettingsResponse getMySettings(AuthenticatedAccount ownerAccount);

  /** Chủ Lab bật/tắt nhận ký gửi và cấu hình phí, độ dài slot, sức chứa slot (UC45, BR-54). */
  ConsignmentSettingsResponse updateMySettings(
      UpdateConsignmentSettingsRequest request, AuthenticatedAccount ownerAccount);

  /** Danh sách khung giờ hẹn ký gửi trong ngày của một Lab kèm trạng thái còn chỗ (UC45). */
  List<ConsignmentSlotResponse> getFreeSlots(Long filmLabId, LocalDate date);

  /** Người bán đặt lịch hẹn mang thiết bị tới Lab kiểm định (UC45). */
  ConsignmentBookingResponse createBooking(
      CreateConsignmentBookingRequest request, AuthenticatedAccount sellerAccount);

  /** Chủ Lab xác nhận nhận thiết bị người bán mang tới (UC46). */
  ConsignmentBookingResponse checkInBooking(Long bookingId, AuthenticatedAccount ownerAccount);

  /** Chủ Lab lập biên bản kiểm định thiết bị kèm tối thiểu 4 ảnh bằng chứng (UC46, BR-51). */
  ConditionReportResponse recordConditionReport(
      Long itemId,
      RecordConditionReportRequest request,
      List<MultipartFile> photos,
      AuthenticatedAccount ownerAccount);
}
