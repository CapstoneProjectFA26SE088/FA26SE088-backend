package com.fa26se088.filmlink.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.payment.response.CommissionDebtResponse;
import com.fa26se088.filmlink.entity.payment.enums.CommissionDebtStatus;
import com.fa26se088.filmlink.service.payment.CommissionDebtService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommissionDebtGenerationJobTest {

  @Mock private CommissionDebtService commissionDebtService;

  @Test
  @DisplayName("UC32 / PAY-05: job đầu tháng chốt kỳ nợ tháng trước cho từng lab độc lập")
  void run_processesEachLabForPreviousMonth() {
    // 2026-11-01 00:15 GMT+7 = 2026-10-31 17:15 UTC
    Clock clock = Clock.fixed(Instant.parse("2026-10-31T17:15:00Z"), ZoneOffset.UTC);
    CommissionDebtGenerationJob job = new CommissionDebtGenerationJob(commissionDebtService, clock);
    LocalDate expectedPeriod = LocalDate.of(2026, 10, 1);

    when(commissionDebtService.findLabIdsWithUnbilledCommissions(expectedPeriod))
        .thenReturn(List.of(10L, 20L));
    when(commissionDebtService.generateMonthlyDebt(10L, expectedPeriod))
        .thenThrow(new IllegalStateException("simulated lab failure"));
    when(commissionDebtService.generateMonthlyDebt(20L, expectedPeriod))
        .thenReturn(
            new CommissionDebtResponse(
                1L,
                20L,
                expectedPeriod,
                LocalDate.of(2026, 11, 10),
                100_000L,
                0L,
                CommissionDebtStatus.UNPAID,
                null,
                null,
                null,
                null));

    job.run();

    verify(commissionDebtService).generateMonthlyDebt(eq(10L), eq(expectedPeriod));
    verify(commissionDebtService).generateMonthlyDebt(eq(20L), eq(expectedPeriod));
  }
}
