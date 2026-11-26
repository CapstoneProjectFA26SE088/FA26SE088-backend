package com.fa26se088.filmlink.service.order.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.lab.response.OrderingFilmLabResponse;
import com.fa26se088.filmlink.dto.order.request.RevertProcessingStageRequest;
import com.fa26se088.filmlink.dto.order.response.AdminOrderStageRevertResponse;
import com.fa26se088.filmlink.dto.order.response.ProcessingStageRecordResponse;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.entity.order.ProcessingStageRecord;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.ProcessingStage;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.order.ServiceOrderMapper;
import com.fa26se088.filmlink.repository.order.ProcessingStageRecordRepository;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.FilmLabService;
import com.fa26se088.filmlink.service.notification.event.NotificationEvent;
import com.fa26se088.filmlink.service.order.AdminServiceOrderService;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC24, BR-07, BR-32: triển khai thao tác quản trị Service Order dành cho Admin.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceOrderServiceImpl implements AdminServiceOrderService {

  private static final int MAX_STAGE_NOTE_LENGTH = 300;

  private final ServiceOrderRepository serviceOrderRepository;
  private final ProcessingStageRecordRepository processingStageRecordRepository;
  private final FilmLabService filmLabService;
  private final AuditLogService auditLogService;
  private final ApplicationEventPublisher eventPublisher;
  private final ServiceOrderMapper serviceOrderMapper;
  private final Clock clock;

  /**
   * UC24, BR-07, BR-32: chỉ Admin được lùi công đoạn xử lý; lùi là insert dòng mới với {@code
   * is_admin_reversion = true} (bảng append-only), không sửa hay xoá lịch sử cũ.
   */
  @Override
  @Transactional
  public AdminOrderStageRevertResponse revertStage(
      Long adminAccountId, Long orderId, RevertProcessingStageRequest request) {
    if (adminAccountId == null) {
      throw new BusinessException(
          ErrorCode.AUTHENTICATION_REQUIRED, "Cần đăng nhập tài khoản Admin để lùi công đoạn.");
    }
    ServiceOrder order =
        serviceOrderRepository
            .findByIdForUpdate(orderId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn dịch vụ."));
    if (order.getStatus() != ServiceOrderStatus.IN_PROCESSING) {
      throw new BusinessException(
          ErrorCode.ORDER_STATUS_CONFLICT,
          "Chỉ lùi công đoạn được với đơn đang xử lý; đơn "
              + order.getOrderCode()
              + " đang ở trạng thái "
              + order.getStatus().name()
              + ".");
    }

    String note = request.note() == null ? "" : request.note().strip();
    if (note.isEmpty() || note.length() > MAX_STAGE_NOTE_LENGTH) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Lý do lùi công đoạn bắt buộc và tối đa " + MAX_STAGE_NOTE_LENGTH + " ký tự.",
          Map.of("field", "note"));
    }

    ProcessingStage current =
        processingStageRecordRepository
            .findFirstByServiceOrderIdOrderByRecordedAtDescIdDesc(order.getId())
            .map(ProcessingStageRecord::getStage)
            .orElse(ProcessingStage.RECEIVED);
    ProcessingStage target = request.stage();
    if (target.ordinal() >= current.ordinal()) {
      String message =
          current == ProcessingStage.RECEIVED
              ? "Đơn "
                  + order.getOrderCode()
                  + " đang ở công đoạn đầu tiên RECEIVED; không còn công đoạn trước để lùi."
              : "Admin chỉ được lùi về công đoạn đứng trước "
                  + current.name()
                  + "; không thể chuyển sang "
                  + target.name()
                  + ".";
      throw new BusinessException(
          ErrorCode.ORDER_STAGE_OUT_OF_ORDER,
          message,
          Map.of("field", "stage", "currentStage", current.name()));
    }

    ProcessingStageRecord record = new ProcessingStageRecord();
    record.setServiceOrderId(order.getId());
    record.setStage(target);
    record.setNote(note);
    record.setAdminReversion(true);
    record.setRecordedByAccountId(adminAccountId);
    record.setRecordedAt(clock.instant());
    processingStageRecordRepository.save(record);

    Map<String, Object> auditAfter = new LinkedHashMap<>();
    auditAfter.put("stage", target.name());
    auditAfter.put("adminReversion", true);
    auditAfter.put("note", note);
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.ORDER_STAGE_REVERTED,
            AuditTargetType.SERVICE_ORDER,
            order.getId(),
            null,
            Map.of("stage", current.name()),
            auditAfter));

    OrderingFilmLabResponse lab = filmLabService.getLabForOrdering(order.getFilmLabId());
    notifyPartiesStageReverted(lab, order, current, target, note);

    List<ProcessingStageRecordResponse> stages =
        processingStageRecordRepository
            .findByServiceOrderIdOrderByRecordedAtAscIdAsc(order.getId())
            .stream()
            .map(serviceOrderMapper::toStageResponse)
            .toList();
    return new AdminOrderStageRevertResponse(
        order.getId(), order.getOrderCode(), current, target, stages);
  }

  private void notifyPartiesStageReverted(
      OrderingFilmLabResponse lab,
      ServiceOrder order,
      ProcessingStage previous,
      ProcessingStage target,
      String note) {
    String title =
        "Đơn " + order.getOrderCode() + " được chuyển lại công đoạn " + target.getVietnameseName();
    String body =
        "Quản trị viên đã chuyển đơn "
            + order.getOrderCode()
            + " tại Film Lab "
            + lab.labName()
            + " từ công đoạn "
            + previous.getVietnameseName()
            + " về "
            + target.getVietnameseName()
            + ". Lý do: "
            + note;
    eventPublisher.publishEvent(
        new NotificationEvent(
            order.getPhotographerAccountId(), NotificationType.ORDER_STATUS, title, body, null));
    eventPublisher.publishEvent(
        new NotificationEvent(
            lab.ownerAccountId(), NotificationType.ORDER_STATUS, title, body, null));
  }
}
