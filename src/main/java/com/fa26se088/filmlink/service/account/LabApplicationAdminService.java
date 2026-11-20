package com.fa26se088.filmlink.service.account;

import com.fa26se088.filmlink.dto.account.request.ApproveLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.request.RejectLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.response.AdminLabApplicationSummaryResponse;
import com.fa26se088.filmlink.dto.account.response.LabApplicationResponse;
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

  /**
   * Xem chi tiết một đơn đăng ký mở lab theo ID kèm signed URL tài liệu (UC08, NFR-PRI-02).
   *
   * @param id ID của đơn đăng ký
   * @return thông tin chi tiết đơn đăng ký kèm link tải tài liệu pháp lý
   */
  LabApplicationResponse getApplicationDetail(Long id);

  /**
   * Duyệt đơn đăng ký mở Film Lab: tạo tài khoản FILM_LAB_OWNER, phát sự kiện tạo FilmLab, ghi
   * audit log và gửi email mật khẩu tạm sau khi commit (UC08, BR-03).
   *
   * @param id ID của đơn đăng ký
   * @param request thông tin version và email chủ lab (tuỳ chọn)
   * @param adminAccountId ID tài khoản Admin thực hiện xét duyệt
   * @return thông tin đơn đăng ký sau khi chuyển sang APPROVED
   */
  LabApplicationResponse approveApplication(
      Long id, ApproveLabApplicationRequest request, Long adminAccountId);

  /**
   * Từ chối đơn đăng ký mở Film Lab: cập nhật trạng thái REJECTED kèm lý do, ghi audit log và gửi
   * email thông báo cho người nộp đơn sau khi commit (UC08).
   *
   * @param id ID của đơn đăng ký
   * @param request thông tin version và lý do từ chối (10-500 ký tự)
   * @param adminAccountId ID tài khoản Admin thực hiện từ chối
   * @return thông tin đơn đăng ký sau khi chuyển sang REJECTED
   */
  LabApplicationResponse rejectApplication(
      Long id, RejectLabApplicationRequest request, Long adminAccountId);
}
