package com.fa26se088.filmlink.service.machine.impl;

import com.fa26se088.filmlink.service.machine.ScheduleChangeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Stub tạm thời của {@link ScheduleChangeService} cho Task H-00 (PR Hợp đồng chia sẻ).
 *
 * <p>Sẽ được thay thế bằng triển khai nghiệp vụ hoàn chỉnh trong Task H-05.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "scheduleChangeServiceImpl")
public class StubScheduleChangeService implements ScheduleChangeService {

  @Override
  public void answer(Long scheduleChangeId, Long serviceOrderId, String choice, String note) {
    log.info(
        "[STUB ScheduleChangeService] answer changeId={}, orderId={}, choice={}, note={}",
        scheduleChangeId,
        serviceOrderId,
        choice,
        note);
  }
}

