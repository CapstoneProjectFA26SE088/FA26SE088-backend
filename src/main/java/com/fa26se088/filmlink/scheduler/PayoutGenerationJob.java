package com.fa26se088.filmlink.scheduler;

import com.fa26se088.filmlink.service.payment.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** UC35, BR-46: mỗi ngày gom hoa hồng ONLINE đã hết thời gian re-scan thành đợt chi trả cho lab. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutGenerationJob {

  private final PayoutService payoutService;

  @Scheduled(cron = "${filmlink.jobs.payout-generation.cron}", zone = "Asia/Ho_Chi_Minh")
  public void run() {
    int created = payoutService.generateDuePayouts();
    log.info("[PayoutGenerationJob] Hoàn tất: tạo {} đợt chi trả.", created);
  }
}
