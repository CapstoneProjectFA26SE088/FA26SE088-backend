package com.fa26se088.filmlink.dto.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.service.machine.event.PromisedDateChangedEvent;
import com.fa26se088.filmlink.service.machine.event.ScheduleChangeCancelledEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapacityDtoTest {

  @Test
  @DisplayName("RollGroup khởi tạo thành công khi dữ liệu hợp lệ")
  void rollGroup_validData_createsInstance() {
    RollGroup group = new RollGroup(1L, 2L, 5, 36);

    assertThat(group.filmProcessId()).isEqualTo(1L);
    assertThat(group.filmFormatId()).isEqualTo(2L);
    assertThat(group.rollCount()).isEqualTo(5);
    assertThat(group.framesPerRoll()).isEqualTo(36);
  }

  @Test
  @DisplayName("RollGroup ném ngoại lệ khi rollCount <= 0 hoặc processId/formatId null")
  void rollGroup_invalidData_throwsException() {
    assertThatThrownBy(() -> new RollGroup(null, 1L, 1, 36))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> new RollGroup(1L, null, 1, 36))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> new RollGroup(1L, 1L, 0, 36))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new RollGroup(1L, 1L, -2, 36))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new RollGroup(1L, 1L, 2, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("DayAllocation ném ngoại lệ khi rollCount <= 0 hoặc tham số null")
  void dayAllocation_invalidData_throwsException() {
    LocalDate now = LocalDate.now();

    assertThatThrownBy(() -> new DayAllocation(null, 1L, 1L, 5))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> new DayAllocation(now, null, 1L, 5))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> new DayAllocation(now, 1L, null, 5))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> new DayAllocation(now, 1L, 1L, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("CapacityRequest kiểm tra null và danh sách groups rỗng")
  void capacityRequest_validations() {
    Instant now = Instant.now();

    assertThatThrownBy(() -> new CapacityRequest(null, 1L, ServiceTier.STANDARD, now, List.of()))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(
            () -> new CapacityRequest(1L, 1L, ServiceTier.STANDARD, now, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("PromisedDateChangedEvent kiểm tra null parameters")
  void promisedDateChangedEvent_validations() {
    Instant now = Instant.now();

    assertThatThrownBy(
            () -> new PromisedDateChangedEvent(null, 1L, now, now, false))
        .isInstanceOf(NullPointerException.class);

    PromisedDateChangedEvent event =
        new PromisedDateChangedEvent(1L, 2L, now, now.plusSeconds(3600), true);
    assertThat(event.serviceOrderId()).isEqualTo(1L);
    assertThat(event.machineIncidentId()).isEqualTo(2L);
    assertThat(event.requiresCustomerResponse()).isTrue();
  }

  @Test
  @DisplayName("ScheduleChangeCancelledEvent kiểm tra null parameters")
  void scheduleChangeCancelledEvent_validations() {
    assertThatThrownBy(() -> new ScheduleChangeCancelledEvent(null, 1L))
        .isInstanceOf(NullPointerException.class);

    ScheduleChangeCancelledEvent event = new ScheduleChangeCancelledEvent(1L, 5L);
    assertThat(event.serviceOrderId()).isEqualTo(1L);
    assertThat(event.scheduleChangeId()).isEqualTo(5L);
  }
}

