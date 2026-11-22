package com.fa26se088.filmlink.service.machine.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.machine.ScheduleChange;
import com.fa26se088.filmlink.entity.machine.enums.ScheduleChangeChoice;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.repository.machine.ScheduleChangeRepository;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.machine.event.ScheduleChangeCancelledEvent;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleChangeServiceImplTest {

  @Mock private ScheduleChangeRepository scheduleChangeRepository;
  @Mock private ServiceOrderRepository serviceOrderRepository;
  @Mock private MachineRepository machineRepository;
  @Mock private AuditLogService auditLogService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private ScheduleChangeServiceImpl scheduleChangeService;

  @BeforeEach
  void setUp() {
    scheduleChangeService =
        new ScheduleChangeServiceImpl(
            scheduleChangeRepository,
            serviceOrderRepository,
            machineRepository,
            auditLogService,
            eventPublisher);
  }

  @Test
  @DisplayName(
      "UC72: Khách chọn WAIT kèm ghi chú -> cập nhật customerChoice=WAIT, customerNote, respondedAt và ghi audit")
  void answer_wait_updatesChoiceAndRecordsAudit() {
    ScheduleChange change = createPendingChange(10L, 200L, true);
    when(scheduleChangeRepository.findByIdAndServiceOrderId(10L, 200L))
        .thenReturn(Optional.of(change));
    when(scheduleChangeRepository.saveAndFlush(any(ScheduleChange.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    scheduleChangeService.answer(10L, 200L, "WAIT", "  Tôi chờ được đến Thứ 2  ");

    assertThat(change.getCustomerChoice()).isEqualTo(ScheduleChangeChoice.WAIT);
    assertThat(change.getCustomerNote()).isEqualTo("Tôi chờ được đến Thứ 2");
    assertThat(change.getRespondedAt()).isNotNull();

    verify(eventPublisher, never()).publishEvent(any(ScheduleChangeCancelledEvent.class));

    ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(auditCaptor.capture());
    assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.SCHEDULE_CHANGE_ANSWERED);
    assertThat(auditCaptor.getValue().targetType()).isEqualTo(AuditTargetType.SCHEDULE_CHANGE);
    assertThat(auditCaptor.getValue().targetId()).isEqualTo(10L);
  }

  @Test
  @DisplayName("UC72: Khách chọn CANCEL -> cập nhật customerChoice=CANCEL và phát ScheduleChangeCancelledEvent")
  void answer_cancel_updatesChoiceAndPublishesCancelledEvent() {
    ScheduleChange change = createPendingChange(11L, 201L, true);
    when(scheduleChangeRepository.findByIdAndServiceOrderId(11L, 201L))
        .thenReturn(Optional.of(change));
    when(scheduleChangeRepository.saveAndFlush(any(ScheduleChange.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    scheduleChangeService.answer(11L, 201L, "CANCEL", "Cần giao gấp nên xin hủy");

    assertThat(change.getCustomerChoice()).isEqualTo(ScheduleChangeChoice.CANCEL);
    assertThat(change.getRespondedAt()).isNotNull();

    ArgumentCaptor<ScheduleChangeCancelledEvent> eventCaptor =
        ArgumentCaptor.forClass(ScheduleChangeCancelledEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue()).isEqualTo(new ScheduleChangeCancelledEvent(201L, 11L));
  }

  @Test
  @DisplayName("Trả lời bản ghi đã được phản hồi trước đó -> ném 409 ORDER_STATUS_CONFLICT")
  void answer_alreadyAnswered_throwsConflict() {
    ScheduleChange change = createPendingChange(12L, 202L, true);
    change.setCustomerChoice(ScheduleChangeChoice.WAIT);
    change.setRespondedAt(Instant.now());
    when(scheduleChangeRepository.findByIdAndServiceOrderId(12L, 202L))
        .thenReturn(Optional.of(change));

    assertThatThrownBy(() -> scheduleChangeService.answer(12L, 202L, "WAIT", null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATUS_CONFLICT));

    verify(scheduleChangeRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Trả lời bản ghi không yêu cầu phản hồi (requiresResponse = false) -> ném 409 ORDER_STATUS_CONFLICT")
  void answer_notRequiresResponse_throwsConflict() {
    ScheduleChange change = createPendingChange(13L, 203L, false);
    when(scheduleChangeRepository.findByIdAndServiceOrderId(13L, 203L))
        .thenReturn(Optional.of(change));

    assertThatThrownBy(() -> scheduleChangeService.answer(13L, 203L, "WAIT", null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATUS_CONFLICT));

    verify(scheduleChangeRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Không tìm thấy bản ghi thay đổi lịch -> ném 404 RESOURCE_NOT_FOUND")
  void answer_notFound_throwsResourceNotFound() {
    when(scheduleChangeRepository.findByIdAndServiceOrderId(999L, 204L))
        .thenReturn(Optional.empty());
    when(serviceOrderRepository.findById(204L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> scheduleChangeService.answer(999L, 204L, "WAIT", null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

    verify(scheduleChangeRepository, never()).saveAndFlush(any());
  }

  private static ScheduleChange createPendingChange(
      Long changeId, Long orderId, boolean requiresResponse) {
    ScheduleChange change = new ScheduleChange();
    ReflectionTestUtils.setField(change, "id", changeId);
    change.setServiceOrderId(orderId);
    change.setMachineIncidentId(88L);
    change.setOldPromisedAt(Instant.parse("2026-11-19T05:00:00Z"));
    change.setNewPromisedAt(Instant.parse("2026-11-23T05:00:00Z"));
    change.setRequiresResponse(requiresResponse);
    return change;
  }
}
