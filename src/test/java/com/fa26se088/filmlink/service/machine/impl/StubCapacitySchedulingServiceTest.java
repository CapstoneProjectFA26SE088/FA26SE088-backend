package com.fa26se088.filmlink.service.machine.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fa26se088.filmlink.dto.machine.CapacityProposal;
import com.fa26se088.filmlink.dto.machine.CapacityRequest;
import com.fa26se088.filmlink.dto.machine.RollGroup;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StubCapacitySchedulingServiceTest {

  private StubCapacitySchedulingService service;

  @BeforeEach
  void setUp() {
    service = new StubCapacitySchedulingService();
  }

  @Test
  @DisplayName("propose trả về ngày hẹn trả mặc định = receivedAt + 5 days")
  void propose_returnsDefaultFiveDaysTurnaround() {
    Instant receivedAt = Instant.parse("2026-11-06T10:00:00Z");
    CapacityRequest request =
        new CapacityRequest(
            1L,
            10L,
            ServiceTier.STANDARD,
            receivedAt,
            List.of(new RollGroup(1L, 1L, 2, 36)));

    CapacityProposal proposal = service.propose(request);

    assertThat(proposal.feasible()).isTrue();
    assertThat(proposal.infeasibleReason()).isNull();
    assertThat(proposal.allocations()).isEmpty();
    assertThat(proposal.promisedReturnAt()).isEqualTo(receivedAt.plus(Duration.ofDays(5)));
    assertThat(proposal.tierCommitmentAt()).isEqualTo(receivedAt.plus(Duration.ofDays(5)));
  }

  @Test
  @DisplayName("confirm với promisedReturnAt cụ thể trả về đúng promisedReturnAt đó")
  void confirm_withCustomPromisedDate_returnsConfirmedDate() {
    Instant receivedAt = Instant.parse("2026-11-06T10:00:00Z");
    Instant confirmedAt = Instant.parse("2026-11-09T17:00:00Z");
    CapacityRequest request =
        new CapacityRequest(
            2L,
            10L,
            ServiceTier.EXPRESS,
            receivedAt,
            List.of(new RollGroup(1L, 1L, 1, 36)));

    CapacityProposal proposal = service.confirm(request, confirmedAt);

    assertThat(proposal.feasible()).isTrue();
    assertThat(proposal.promisedReturnAt()).isEqualTo(confirmedAt);
    assertThat(proposal.tierCommitmentAt()).isEqualTo(confirmedAt);
  }

  @Test
  @DisplayName("confirm với promisedReturnAt null trả về mặc định 5 ngày")
  void confirm_withNullPromisedDate_returnsDefaultFiveDays() {
    Instant receivedAt = Instant.parse("2026-11-06T10:00:00Z");
    CapacityRequest request =
        new CapacityRequest(
            3L,
            10L,
            ServiceTier.STANDARD,
            receivedAt,
            List.of(new RollGroup(1L, 1L, 3, 36)));

    CapacityProposal proposal = service.confirm(request, null);

    assertThat(proposal.feasible()).isTrue();
    assertThat(proposal.promisedReturnAt()).isEqualTo(receivedAt.plus(Duration.ofDays(5)));
  }

  @Test
  @DisplayName("propose và confirm ném NullPointerException khi request null")
  void methods_throwOnNullRequest() {
    assertThatThrownBy(() -> service.propose(null))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> service.confirm(null, Instant.now()))
        .isInstanceOf(NullPointerException.class);
  }
}

