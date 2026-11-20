package com.fa26se088.filmlink.service.payment;

import com.fa26se088.filmlink.dto.payment.request.ReportDebtPaymentRequest;
import com.fa26se088.filmlink.dto.payment.response.CommissionDebtResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service quản lý kỳ nợ hoa hồng của Film Lab (UC32, UC33, UC34, BR-11, BR-20-22).
 */
public interface CommissionDebtService {

  /**
   * Tạo kỳ nợ cho một lab trong tháng định kỳ (BR-20, UC32).
   * Gom các khoản hoa hồng ngoài nền tảng (DEBT) ở trạng thái OPEN hoặc DEFERRED.
   *
   * @param labId ID của Film Lab
   * @param period Ngày đầu tháng của kỳ nợ (VD: 2026-10-01)
   * @return CommissionDebtResponse nếu có nợ, hoặc null nếu không phát sinh nợ
   */
  CommissionDebtResponse generateMonthlyDebt(Long labId, LocalDate period);

  /**
   * Film Lab báo cáo đã nộp nợ hoa hồng kèm ảnh biên lai (UC33).
   */
  CommissionDebtResponse reportDebtPayment(
      Long debtId, ReportDebtPaymentRequest request, AuthenticatedAccount account);

  /**
   * Quản trị viên (Admin) đối soát và xác nhận đã nhận đủ tiền nợ (UC33, BR-21).
   * Chuyển trạng thái kỳ nợ sang PAID, gỡ bỏ chế tài (Sanction) nếu có.
   */
  CommissionDebtResponse confirmDebtPayment(Long debtId, AuthenticatedAccount adminAccount);

  /**
   * Danh sách kỳ nợ của Film Lab.
   */
  Page<CommissionDebtResponse> getLabDebts(Long labId, Pageable pageable, AuthenticatedAccount account);

  /**
   * Chi tiết kỳ nợ theo ID.
   */
  CommissionDebtResponse getDebtById(Long debtId, AuthenticatedAccount account);
}

