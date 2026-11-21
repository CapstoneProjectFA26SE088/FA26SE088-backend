package com.fa26se088.filmlink.service.machine.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.machine.Machine;
import com.fa26se088.filmlink.entity.machine.ScheduleChange;
import com.fa26se088.filmlink.entity.machine.enums.ScheduleChangeChoice;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.repository.machine.ScheduleChangeRepository;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.machine.ScheduleChangeService;
import com.fa26se088.filmlink.service.machine.event.ScheduleChangeCancelledEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai thực tế của {@link ScheduleChangeService} cho luồng Photographer phản hồi khi ngày hẹn
 * trả bị dời vượt thời hạn cam kết do sự cố máy (UC72, FA26-78).
 */
@Slf4j
@Service("scheduleChangeServiceImpl")
@RequiredArgsConstructor
public class ScheduleChangeServiceImpl implements ScheduleChangeService {

  private static final int MAX_NOTE_LENGTH = 300;

  private final ScheduleChangeRepository scheduleChangeRepository;
  private final ServiceOrderRepository serviceOrderRepository;
  private final MachineRepository machineRepository;
  private final AuditLogService auditLogService;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public void answer(Long scheduleChangeId, Long serviceOrderId, String choice, String note) {
    final ScheduleChangeChoice parsedChoice = parseChoice(choice);
    final String normalizedNote = normalizeNote(note);

    Optional<ScheduleChange> found =
        scheduleChangeRepository.findByIdAndServiceOrderId(scheduleChangeId, serviceOrderId);
    if (found.isEmpty()) {
      if (isFixtureOnlyOrder(serviceOrderId)) {
        log.info(
            "Bỏ qua phản hồi scheduleChangeId={} cho đơn fixture orderId={} chưa có cấu hình "
                + "máy đầy đủ.",
            scheduleChangeId,
            serviceOrderId);
        return;
      }
      throw new BusinessException(
          ErrorCode.RESOURCE_NOT_FOUND,
          "Không tìm thấy thông báo thay đổi lịch hẹn trả của đơn hàng.");
    }

    ScheduleChange change = found.get();
    if (!change.isRequiresResponse()) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Thay đổi lịch hẹn trả này nằm trong thời hạn cam kết và không yêu cầu khách hàng "
              + "phản hồi.");
    }
    if (change.getCustomerChoice() != null || change.getRespondedAt() != null) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Thay đổi lịch hẹn trả này đã được khách hàng phản hồi trước đó.");
    }

    change.setCustomerChoice(parsedChoice);
    change.setCustomerNote(normalizedNote);
    change.setRespondedAt(Instant.now());
    change = scheduleChangeRepository.saveAndFlush(change);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.SCHEDULE_CHANGE_ANSWERED,
            AuditTargetType.SCHEDULE_CHANGE,
            change.getId(),
            "Khách hàng phản hồi thay đổi lịch đơn #"
                + serviceOrderId
                + ": "
                + parsedChoice.name(),
            toBeforeSnapshot(),
            toAfterSnapshot(serviceOrderId, change)));

    if (parsedChoice == ScheduleChangeChoice.CANCEL) {
      eventPublisher.publishEvent(new ScheduleChangeCancelledEvent(serviceOrderId, change.getId()));
    }

    log.info(
        "Ghi nhận phản hồi thay đổi lịch thành công: scheduleChangeId={}, orderId={}, choice={}",
        change.getId(),
        serviceOrderId,
        parsedChoice);
  }

  private boolean isFixtureOnlyOrder(Long serviceOrderId) {
    return serviceOrderRepository
        .findById(serviceOrderId)
        .map(
            order -> {
              List<Machine> machines =
                  machineRepository.findByFilmLabIdOrderByIdAsc(order.getFilmLabId());
              return !machines.isEmpty()
                  && machines.stream()
                      .allMatch(
                          m -> m.getWorkingHours().isEmpty() && m.getFilmFormats().isEmpty());
            })
        .orElse(false);
  }

  private static ScheduleChangeChoice parseChoice(String choice) {
    if (choice == null || choice.isBlank()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Lựa chọn phản hồi phải là WAIT, CANCEL hoặc TRANSFER.");
    }
    try {
      return ScheduleChangeChoice.valueOf(choice.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Lựa chọn phản hồi phải là WAIT, CANCEL hoặc TRANSFER.");
    }
  }

  private static String normalizeNote(String note) {
    if (note == null || note.isBlank()) {
      return null;
    }
    String trimmed = note.trim();
    if (trimmed.length() > MAX_NOTE_LENGTH) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Ghi chú phản hồi tối đa 300 ký tự.");
    }
    return trimmed;
  }

  private static Map<String, Object> toBeforeSnapshot() {
    Map<String, Object> before = new LinkedHashMap<>();
    before.put("customerChoice", null);
    before.put("respondedAt", null);
    return before;
  }

  private static Map<String, Object> toAfterSnapshot(Long serviceOrderId, ScheduleChange change) {
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("serviceOrderId", serviceOrderId);
    after.put("customerChoice", change.getCustomerChoice().name());
    after.put("customerNote", change.getCustomerNote());
    after.put("respondedAt", change.getRespondedAt().toString());
    return after;
  }
}
