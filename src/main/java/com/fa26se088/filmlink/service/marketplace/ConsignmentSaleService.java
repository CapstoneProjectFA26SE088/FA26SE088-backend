package com.fa26se088.filmlink.service.marketplace;

import com.fa26se088.filmlink.dto.marketplace.request.CreateConsignmentPurchaseRequest;
import com.fa26se088.filmlink.dto.marketplace.request.RecordConsignmentHandoverRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSaleResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Mua thiết bị ký gửi, bàn giao, chia doanh thu và tạo Payout người bán (F-22, UC62–UC64). */
public interface ConsignmentSaleService {

  /** Người mua đặt mua thiết bị ONLINE, khoá tin đăng sang LOCKED và trả link VNPay (UC62). */
  ConsignmentSaleResponse createPurchase(
      Long listingId,
      CreateConsignmentPurchaseRequest request,
      String clientIp,
      AuthenticatedAccount buyerAccount);

  /** Chủ Lab bàn giao thiết bị kèm ảnh bằng chứng, chia tiền và ghi hoa hồng (UC63, UC64). */
  ConsignmentSaleResponse recordHandover(
      Long saleId,
      RecordConsignmentHandoverRequest request,
      List<MultipartFile> photos,
      AuthenticatedAccount ownerAccount);

  /**
   * UC64, BR-54: tạo đợt chi trả {@code PENDING} cho người bán đối với các giao dịch đã qua thời
   * hạn yêu cầu trả hàng. Idempotent theo giao dịch.
   */
  int generateDueSellerPayouts();
}
