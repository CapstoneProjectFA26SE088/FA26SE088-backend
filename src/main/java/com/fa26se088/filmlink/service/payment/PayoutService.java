package com.fa26se088.filmlink.service.payment;

import com.fa26se088.filmlink.dto.payment.request.UpdatePayoutAccountRequest;
import com.fa26se088.filmlink.dto.payment.response.PayoutAccountResponse;
import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/** Chi trả cho Film Lab (UC35, UC36, BR-31, BR-45, BR-46). */
public interface PayoutService {

  /**
   * BR-46: gom hoa hồng ONLINE đã qua thời gian re-scan thành đợt chi trả {@code PENDING} theo từng
   * lab, mỗi đơn một dòng. Idempotent: đơn đã có dòng chi trả không bị tính lại.
   *
   * @return số đợt chi trả mới được tạo
   */
  int generateDuePayouts();

  /**
   * UC64, BR-54: tạo đợt chi trả {@code PENDING} cho người bán ký gửi sau khi hết hạn đổi trả.
   * Idempotent theo {@code consignmentSaleId}.
   */
  boolean createSellerPayoutIfAbsent(
      Long sellerAccountId, Long consignmentSaleId, long amountVnd);

  /** Tài khoản ngân hàng nhận tiền cá nhân của người bán ký gửi (UC64). */
  PayoutAccountResponse getMyPersonalPayoutAccount(AuthenticatedAccount account);

  /** Khai báo hoặc cập nhật tài khoản ngân hàng nhận tiền cá nhân (UC64). */
  PayoutAccountResponse updateMyPersonalPayoutAccount(
      UpdatePayoutAccountRequest request, AuthenticatedAccount account);

  /** UC35, BR-31: Admin ghi nhận đã chuyển khoản kèm biên lai, đợt chi trả sang {@code PAID}. */
  PayoutResponse markPaid(Long payoutId, MultipartFile receipt, AuthenticatedAccount adminAccount);

  /** Tài khoản nhận tiền của lab mà chủ lab đang đăng nhập sở hữu. */
  PayoutAccountResponse getMyPayoutAccount(AuthenticatedAccount ownerAccount);

  /** Tạo mới hoặc cập nhật tài khoản nhận tiền của lab. */
  PayoutAccountResponse updateMyPayoutAccount(
      UpdatePayoutAccountRequest request, AuthenticatedAccount ownerAccount);

  /** Lịch sử chi trả của lab mà chủ lab đang đăng nhập sở hữu. */
  Page<PayoutResponse> getMyLabPayouts(Pageable pageable, AuthenticatedAccount ownerAccount);

  /** UC36: Admin giám sát toàn bộ đợt chi trả, lọc theo trạng thái. */
  Page<PayoutResponse> getAllPayouts(
      PayoutStatus status, Pageable pageable, AuthenticatedAccount adminAccount);

  /** Chi tiết một đợt chi trả: Admin hoặc chủ lab nhận tiền. */
  PayoutResponse getPayoutById(Long payoutId, AuthenticatedAccount account);
}
