package com.fa26se088.filmlink.service.machine;

/**
 * Dịch vụ xử lý phản hồi của khách hàng khi đơn hàng bị thay đổi ngày hẹn trả (UC72).
 */
public interface ScheduleChangeService {

  /**
   * Tiếp nhận câu trả lời của khách hàng cho thông báo dời lịch do sự cố máy.
   *
   * @param scheduleChangeId ID bản ghi thay đổi lịch hẹn trả
   * @param serviceOrderId ID đơn hàng dịch vụ
   * @param choice lựa chọn của khách hàng: WAIT (chờ), CANCEL (hủy hoàn tiền), TRANSFER (chuyển lab)
   * @param note ghi chú hoặc yêu cầu bổ sung từ khách hàng (có thể null)
   */
  void answer(Long scheduleChangeId, Long serviceOrderId, String choice, String note);
}

