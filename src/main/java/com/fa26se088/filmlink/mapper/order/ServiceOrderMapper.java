package com.fa26se088.filmlink.mapper.order;

import com.fa26se088.filmlink.dto.archive.response.ArchiveRollResponse;
import com.fa26se088.filmlink.dto.archive.response.ArchiveRollSummaryResponse;
import com.fa26se088.filmlink.dto.archive.response.RollScanStatusResponse;
import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverResponse;
import com.fa26se088.filmlink.dto.lab.response.FilmLabBriefResponse;
import com.fa26se088.filmlink.dto.order.response.FilmArchiveRollResponse;
import com.fa26se088.filmlink.dto.order.response.FilmArchiveRollSummaryResponse;
import com.fa26se088.filmlink.dto.order.response.FilmRollDamagePhotoResponse;
import com.fa26se088.filmlink.dto.order.response.FilmRollResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderSummaryResponse;
import com.fa26se088.filmlink.dto.order.response.OrderFilmHandoverResponse;
import com.fa26se088.filmlink.dto.order.response.OrderPhotographerResponse;
import com.fa26se088.filmlink.dto.order.response.ProcessingStageRecordResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderDetailResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderSummaryResponse;
import com.fa26se088.filmlink.entity.order.FilmRoll;
import com.fa26se088.filmlink.entity.order.FilmRollDamagePhoto;
import com.fa26se088.filmlink.entity.order.ProcessingStageRecord;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Chuyển Service Order và Film Roll sang DTO. Viết tay, không dùng MapStruct. */
@Component
public class ServiceOrderMapper {

  /**
   * @param payment link thanh toán do service dựng (hiện luôn {@code null}); mapper không gọi
   *     service
   */
  public ServiceOrderResponse toResponse(ServiceOrder order, List<FilmRoll> rolls, Object payment) {
    return new ServiceOrderResponse(
        order.getId(),
        order.getOrderCode(),
        order.getStatus(),
        order.getFilmLabId(),
        order.getServiceTier(),
        order.getFilmHandoverMethod(),
        order.getPaymentMethod(),
        order.getSpecialRequest(),
        order.getCustomerAddressLine(),
        order.getCustomerDistrict(),
        order.getCustomerProvince(),
        order.getCustomerPhone(),
        order.getPickupWindowStartAt(),
        order.getPickupWindowEndAt(),
        order.getEstimatedTotalVnd(),
        order.getExpectedReturnAt(),
        order.getLabResponseDueAt(),
        rolls.stream().map(this::toRollResponse).toList(),
        payment,
        order.getCreatedAt());
  }

  /**
   * UC22, UC23, UC69, UC24, UC26: chi tiết đơn cho chủ lab; {@code photographer} do service đọc qua
   * domain account, {@code handovers} đọc qua domain delivery, {@code stages} theo thứ tự thời
   * gian, {@code scanStatuses} ({@code filmRollId →} tình trạng scan) đọc qua domain archive,
   * {@code damagePhotosByRollId} đọc gộp từ {@code film_roll_damage_photos}.
   */
  public LabServiceOrderResponse toLabResponse(
      ServiceOrder order,
      List<FilmRoll> rolls,
      OrderPhotographerResponse photographer,
      List<FilmHandoverResponse> handovers,
      List<ProcessingStageRecord> stages,
      Map<Long, RollScanStatusResponse> scanStatuses,
      Map<Long, List<FilmRollDamagePhotoResponse>> damagePhotosByRollId) {
    return new LabServiceOrderResponse(
        order.getId(),
        order.getOrderCode(),
        order.getStatus(),
        order.getServiceTier(),
        order.getFilmHandoverMethod(),
        order.getPaymentMethod(),
        order.getSpecialRequest(),
        order.getCustomerAddressLine(),
        order.getCustomerDistrict(),
        order.getCustomerProvince(),
        order.getCustomerPhone(),
        order.getPickupWindowStartAt(),
        order.getPickupWindowEndAt(),
        order.getEstimatedTotalVnd(),
        order.getFinalTotalVnd(),
        order.getPriceLockedAt(),
        order.getExpectedReturnAt(),
        order.getPromisedReturnAt(),
        order.getMachineAssignmentConfirmedAt(),
        order.getLabResponseDueAt(),
        order.getAcceptedAt(),
        order.getDeliveredAt(),
        order.getClosedAt(),
        order.getCancelledAt(),
        order.getRejectionReason(),
        order.getCancellationReason(),
        order.isKeepNegativesAtLab(),
        photographer,
        rolls.stream()
            .map(
                roll ->
                    toLabRollResponse(
                        roll,
                        scanStatuses.get(roll.getId()),
                        damagePhotosByRollId.getOrDefault(roll.getId(), List.of())))
            .toList(),
        handovers,
        stages.stream().map(this::toStageResponse).toList(),
        order.getCreatedAt());
  }

  /** UC22: một dòng danh sách đơn của lab; {@code rollCount} do service đếm gộp cả trang. */
  public LabServiceOrderSummaryResponse toLabSummaryResponse(ServiceOrder order, long rollCount) {
    return new LabServiceOrderSummaryResponse(
        order.getId(),
        order.getOrderCode(),
        order.getStatus(),
        order.getServiceTier(),
        order.getFilmHandoverMethod(),
        order.getPaymentMethod(),
        rollCount,
        order.getEstimatedTotalVnd(),
        order.getLabResponseDueAt(),
        order.getExpectedReturnAt(),
        order.getCreatedAt());
  }

  /** UC25: một dòng danh sách đơn của Photographer; tên lab và số cuộn do service đọc gộp. */
  public ServiceOrderSummaryResponse toSummaryResponse(
      ServiceOrder order, String labName, long rollCount) {
    return new ServiceOrderSummaryResponse(
        order.getId(),
        order.getOrderCode(),
        order.getStatus(),
        order.getFilmLabId(),
        labName,
        rollCount,
        order.getEstimatedTotalVnd(),
        order.getFinalTotalVnd(),
        order.getExpectedReturnAt(),
        order.getPromisedReturnAt(),
        order.getCreatedAt());
  }

  /**
   * UC25: chi tiết đơn cho Photographer. {@code lab} đọc qua domain lab, {@code handovers} qua
   * domain delivery (bỏ người thực hiện phía lab), {@code stages} theo thứ tự thời gian (bỏ người
   * ghi).
   *
   * @param damagePhotosByRollId {@code filmRollId →} danh sách ảnh bằng chứng cuộn hỏng (BR-26)
   * @param archiveRollIds {@code filmRollId → archiveRollId} của các cuộn đã giao scan (UC28);
   *     rỗng khi đơn chưa giao scan
   * @param payment thông tin thanh toán do service dựng (hiện luôn {@code null})
   */
  public ServiceOrderDetailResponse toDetailResponse(
      ServiceOrder order,
      FilmLabBriefResponse lab,
      List<FilmRoll> rolls,
      List<ProcessingStageRecord> stages,
      List<FilmHandoverResponse> handovers,
      Map<Long, List<FilmRollDamagePhotoResponse>> damagePhotosByRollId,
      Map<Long, Long> archiveRollIds,
      Object payment) {
    return new ServiceOrderDetailResponse(
        order.getId(),
        order.getOrderCode(),
        order.getStatus(),
        lab,
        order.getServiceTier(),
        order.getFilmHandoverMethod(),
        order.getPaymentMethod(),
        order.getSpecialRequest(),
        order.getCustomerAddressLine(),
        order.getCustomerDistrict(),
        order.getCustomerProvince(),
        order.getCustomerPhone(),
        order.getPickupWindowStartAt(),
        order.getPickupWindowEndAt(),
        order.getEstimatedTotalVnd(),
        order.getFinalTotalVnd(),
        order.getPriceLockedAt(),
        order.getExpectedReturnAt(),
        order.getPromisedReturnAt(),
        order.getAcceptedAt(),
        order.getDeliveredAt(),
        order.getClosedAt(),
        order.getCancelledAt(),
        order.getCancellationReason(),
        order.getCancellationFeeVnd(),
        order.isKeepNegativesAtLab(),
        rolls.stream()
            .map(
                roll ->
                    toRollResponse(
                        roll,
                        damagePhotosByRollId.getOrDefault(roll.getId(), List.of()),
                        archiveRollIds.get(roll.getId()),
                        null,
                        null))
            .toList(),
        stages.stream().map(this::toStageResponse).toList(),
        handovers.stream().map(this::toOrderHandoverResponse).toList(),
        payment,
        order.getCreatedAt());
  }

  /** BR-25: bàn giao cho Photographer, không có {@code performedByAccountId} / {@code Name}. */
  public OrderFilmHandoverResponse toOrderHandoverResponse(FilmHandoverResponse handover) {
    return new OrderFilmHandoverResponse(
        handover.id(),
        handover.handoverPoint(),
        handover.rollCount(),
        handover.note(),
        handover.occurredAt(),
        handover.photos());
  }

  /** BR-07, UC24: một mốc công đoạn (kèm cờ {@code adminReversion}). */
  public ProcessingStageRecordResponse toStageResponse(ProcessingStageRecord record) {
    return new ProcessingStageRecordResponse(
        record.getStage(), record.getRecordedAt(), record.getNote(), record.isAdminReversion());
  }

  /** BR-26: một ảnh bằng chứng cuộn phim hỏng kèm URL ký có hạn. */
  public FilmRollDamagePhotoResponse toDamagePhotoResponse(
      FilmRollDamagePhoto photo, URI signedUrl) {
    return new FilmRollDamagePhotoResponse(photo.getId(), signedUrl, photo.getCreatedAt());
  }

  /** UC26: cuộn kèm số ảnh xem trước và link file gốc cho chủ lab ({@code scan} có thể null). */
  public FilmRollResponse toLabRollResponse(FilmRoll roll, RollScanStatusResponse scan) {
    return toLabRollResponse(roll, scan, List.of());
  }

  /** UC24, UC26: cuộn cho chủ lab kèm trạng thái scan và ảnh bằng chứng cuộn hỏng (nếu có). */
  public FilmRollResponse toLabRollResponse(
      FilmRoll roll,
      RollScanStatusResponse scan,
      List<FilmRollDamagePhotoResponse> damagePhotos) {
    return scan == null
        ? toRollResponse(roll, damagePhotos, null, 0L, null)
        : toRollResponse(roll, damagePhotos, null, scan.previewCount(), scan.fullResolutionLink());
  }

  /**
   * UC28: một dòng archive của Photographer; {@code labName}, {@code filmStockName}, {@code
   * filmFormatName} do service đọc gộp qua domain lab / masterdata.
   */
  public FilmArchiveRollSummaryResponse toArchiveSummaryResponse(
      ArchiveRollSummaryResponse archive,
      FilmRoll roll,
      ServiceOrder order,
      String labName,
      String filmStockName,
      String filmFormatName) {
    return new FilmArchiveRollSummaryResponse(
        archive.id(),
        roll.getId(),
        roll.getRollSequence(),
        order.getId(),
        order.getOrderCode(),
        order.getFilmLabId(),
        labName,
        roll.getFilmStockId(),
        filmStockName,
        roll.getFilmFormatId(),
        filmFormatName,
        archive.deliveredAt(),
        archive.previewCount(),
        archive.coverPreviewUrl());
  }

  /** UC28: chi tiết một cuộn trong archive của Photographer. */
  public FilmArchiveRollResponse toArchiveResponse(
      ArchiveRollResponse archive,
      FilmRoll roll,
      ServiceOrder order,
      String labName,
      String filmStockName,
      String filmFormatName) {
    return new FilmArchiveRollResponse(
        archive.id(),
        roll.getId(),
        roll.getRollSequence(),
        order.getId(),
        order.getOrderCode(),
        order.getFilmLabId(),
        labName,
        roll.getFilmStockId(),
        filmStockName,
        roll.getFilmFormatId(),
        filmFormatName,
        roll.getExposureCount(),
        archive.fullResolutionLink(),
        archive.linkVerifiedAt(),
        archive.deliveredAt(),
        archive.camera(),
        archive.lens(),
        archive.shotOn(),
        archive.location(),
        archive.note(),
        archive.frameCountDiscrepancyNote(),
        archive.previews());
  }

  public FilmRollResponse toRollResponse(FilmRoll roll) {
    return toRollResponse(roll, List.of(), null, null, null);
  }

  private FilmRollResponse toRollResponse(
      FilmRoll roll,
      List<FilmRollDamagePhotoResponse> damagePhotos,
      Long archiveRollId,
      Long previewScanCount,
      String fullResolutionLink) {
    return new FilmRollResponse(
        roll.getId(),
        roll.getRollSequence(),
        roll.getFilmStockId(),
        roll.getFilmStockNameText(),
        roll.getFilmFormatId(),
        roll.getFilmProcessId(),
        roll.getServicePackageId(),
        roll.getServicePackageScanOptionId(),
        roll.getExposureCount(),
        roll.getShootingIso(),
        roll.getPushPullStops(),
        roll.getUnitPriceVnd(),
        roll.getScanOptionPriceVnd(),
        roll.getPushPullSurchargeVnd(),
        roll.getExpressSurchargeVnd(),
        roll.getStatus(),
        roll.getDamageNote(),
        damagePhotos == null ? List.of() : damagePhotos,
        archiveRollId,
        previewScanCount,
        fullResolutionLink);
  }
}
