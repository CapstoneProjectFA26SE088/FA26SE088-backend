package com.fa26se088.filmlink.service.machine.impl;

import com.fa26se088.filmlink.dto.machine.CapacityProposal;
import com.fa26se088.filmlink.dto.machine.CapacityRequest;
import com.fa26se088.filmlink.service.machine.CapacitySchedulingService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Stub tạm thời của {@link CapacitySchedulingService} cho Task H-00 (PR Hợp đồng chia sẻ).
 *
 * <p>Mục đích: Cung cấp bean để thành viên phụ trách domain {@code order} có thể tích hợp và kiểm
 * thử luồng tạo đơn hàng (ORD-01) song song mà không bị phụ thuộc vào thuật toán tính công suất ở
 * H-04.
 *
 * <p>Mặc định trả về ngày hẹn trả: {@code receivedAt + 5 ngày}. Sẽ được thay thế hoàn toàn khi hoàn
 * thành Task H-04.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "capacitySchedulingServiceImpl")
public class StubCapacitySchedulingService implements CapacitySchedulingService {

  private static final Duration DEFAULT_TURNAROUND = Duration.ofDays(5);

  @Override
  public CapacityProposal propose(CapacityRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Instant promisedReturnAt = request.receivedAt().plus(DEFAULT_TURNAROUND);
    log.info(
        "[STUB CapacityScheduling] propose for orderId={}, labId={}, tier={}, promisedAt={}",
        request.serviceOrderId(),
        request.filmLabId(),
        request.tier(),
        promisedReturnAt);
    return new CapacityProposal(
        true,
        null,
        List.of(),
        promisedReturnAt,
        promisedReturnAt);
  }

  @Override
  public CapacityProposal confirm(CapacityRequest request, Instant promisedReturnAt) {
    Objects.requireNonNull(request, "request must not be null");
    Instant finalPromisedAt =
        (promisedReturnAt != null) ? promisedReturnAt : request.receivedAt().plus(DEFAULT_TURNAROUND);
    log.info(
        "[STUB CapacityScheduling] confirm for orderId={}, labId={}, confirmedAt={}",
        request.serviceOrderId(),
        request.filmLabId(),
        finalPromisedAt);
    return new CapacityProposal(
        true,
        null,
        List.of(),
        finalPromisedAt,
        finalPromisedAt);
  }
}

