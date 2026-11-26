package com.fa26se088.filmlink.scheduler;

import com.fa26se088.filmlink.dto.payment.response.CommissionDebtResponse;
import com.fa26se088.filmlink.service.payment.CommissionDebtService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC32 / BR-20 / PAY-05: job định kỳ đầu tháng chốt Commission Debt cho các lab phát sinh hoa hồng
 * {@code DEBT} trong tháng trước. Mỗi lab chạy trong một transaction riêng để một lab lỗi không
 * làm hỏng cả loạt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommissionDebtGenerationJob {

  private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final CommissionDebtService commissionDebtService;
  private final Clock clock;

  /**
   * Chạy lúc 00:15 ngày 1 hàng tháng (giờ Việt Nam) để chốt kỳ nợ tháng trước.
   */
  @Scheduled(cron = "${filmlink.jobs.commission-debt-generation.cron}", zone = "Asia/Ho_Chi_Minh")
  public void run() {
    LocalDate previousMonth = LocalDate.now(clock.withZone(VIETNAM_ZONE)).minusMonths(1).withDayOfMonth(1);
    List<Long> labIds = commissionDebtService.findLabIdsWithUnbilledCommissions(previousMonth);
    int createdCount = 0;
    for (Long labId : labIds) {
      try {
        CommissionDebtResponse response = commissionDebtService.generateMonthlyDebt(labId, previousMonth);
        if (response != null) {
          createdCount++;
        }
      } catch (RuntimeException ex) {
        log.error(
            "[CommissionDebtGenerationJob] Lỗi chốt kỳ nợ tháng {} cho lab #{}: {}",
            previousMonth,
            labId,
            ex.getMessage(),
            ex);
      }
    }
    log.info(
        "[CommissionDebtGenerationJob] Hoàn tất chốt kỳ nợ tháng {}: quét {} lab, xử lý {} kỳ nợ.",
        previousMonth,
        labIds.size(),
        createdCount);
  }
}
