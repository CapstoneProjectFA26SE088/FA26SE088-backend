package com.fa26se088.filmlink.service.account;

import com.fa26se088.filmlink.dto.account.response.AdminLabApplicationSummaryResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import org.springframework.data.domain.Pageable;

/**
 * Quản trị đơn đăng ký mở Film Lab dành cho Admin (UC08, FA26-66).
 */
public interface LabApplicationAdminService {

  /**
   * Xem danh sách các đơn đăng ký mở lab có phân trang và lọc theo trạng thái (UC08).
   *
   * @param status trạng thái cần lọc (tuỳ chọn, null = tất cả)
   * @param pageable thông tin phân trang (mặc định size 20, sort theo createdAt DESC)
   * @return trang danh sách đơn kèm cờ nghi vấn trùng lặp (isDuplicateSuspected)
   */
  PageResponse<AdminLabApplicationSummaryResponse> getApplications(
      LabApplicationStatus status, Pageable pageable);
}
