package com.fa26se088.filmlink.scheduler;

import com.fa26se088.filmlink.service.marketplace.ConsignmentSaleService;
import com.fa26se088.filmlink.service.payment.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC35, UC64, BR-46, BR-54: mỗi ngày gom hoa hồng đơn ONLINE đã hết thời gian re-scan và các giao
 * dịch ký gửi đã hết thời gian đổi trả thành các đợt chi trả {@code PENDING}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutGenerationJob {

  private final PayoutService payoutService;
  private final ConsignmentSaleService consignmentSaleService;

  /** Thực thi job định kỳ lúc 01:30 sáng theo múi giờ Việt Nam. */
  @Scheduled(cron = "${filmlink.jobs.payout-generation.cron}", zone = "Asia/Ho_Chi_Minh")
  public void run() {
    int labPayouts = payoutService.generateDuePayouts();
    int sellerPayouts = consignmentSaleService.generateDueSellerPayouts();
    log.info(
        "[PayoutGenerationJob] Hoàn tất: tạo {} đợt payout Lab và {} đợt payout người bán ký gửi.",
        labPayouts,
        sellerPayouts);
  }
}
