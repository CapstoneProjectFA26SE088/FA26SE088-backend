package com.fa26se088.filmlink.mapper.marketplace;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.lab.response.FilmLabBriefResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConditionCheckResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConditionReportResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentBookingResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentHandoverResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentItemResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentListingDetailResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentListingSummaryResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentPhotoResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSaleResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSettingsResponse;
import com.fa26se088.filmlink.dto.marketplace.response.PriceOfferResponse;
import com.fa26se088.filmlink.dto.payment.response.PaymentLink;
import com.fa26se088.filmlink.entity.marketplace.ConditionReport;
import com.fa26se088.filmlink.entity.marketplace.ConditionReportCheck;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentBooking;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentHandover;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentHandoverPhoto;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentItem;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentItemPhoto;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentLabSettings;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentListing;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentSale;
import com.fa26se088.filmlink.entity.marketplace.PriceOffer;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Mapper viết tay chuyển đổi các entity {@code marketplace} sang DTO phản hồi. */
@Component
@RequiredArgsConstructor
public class ConsignmentMapper {

  private final StorageClient storageClient;

  /** Ánh xạ cấu hình ký gửi của Lab. */
  public ConsignmentSettingsResponse toSettingsResponse(ConsignmentLabSettings entity) {
    return new ConsignmentSettingsResponse(
        entity.getFilmLabId(),
        entity.isAcceptingConsignments(),
        entity.getConsignmentFeeRate(),
        entity.getSlotMinutes(),
        entity.getAppointmentsPerSlot(),
        entity.getUpdatedAt());
  }

  /** Ánh xạ thiết bị ký gửi. */
  public ConsignmentItemResponse toItemResponse(ConsignmentItem item) {
    return new ConsignmentItemResponse(
        item.getId(),
        item.getBooking().getId(),
        item.getItemType(),
        item.getItemName(),
        item.getSellerDescription(),
        item.getDesiredPriceVnd(),
        item.getStatus(),
        item.getClosingReason(),
        item.getAgreedPriceVnd(),
        item.getPriceAgreedAt(),
        item.getCollectionDueAt(),
        item.getCreatedAt());
  }

  /** Ánh xạ lịch hẹn kèm thiết bị ký gửi. */
  public ConsignmentBookingResponse toBookingResponse(
      ConsignmentBooking booking, ConsignmentItem item) {
    return new ConsignmentBookingResponse(
        booking.getId(),
        booking.getBookingCode(),
        booking.getSellerAccountId(),
        booking.getFilmLabId(),
        booking.getSlotStartAt(),
        booking.getSlotEndAt(),
        booking.getStatus(),
        booking.getCheckedInAt(),
        item != null ? toItemResponse(item) : null,
        booking.getCreatedAt());
  }

  /** Ánh xạ ảnh món đồ ký gửi kèm link tải có chữ ký. */
  public ConsignmentPhotoResponse toItemPhotoResponse(ConsignmentItemPhoto photo) {
    return new ConsignmentPhotoResponse(
        photo.getId(),
        photo.getSource(),
        photo.getDisplayOrder(),
        photo.getBlobKey(),
        signedUrl(photo.getBlobKey()));
  }

  /** Ánh xạ ảnh bàn giao kèm link tải có chữ ký. */
  public ConsignmentPhotoResponse toHandoverPhotoResponse(ConsignmentHandoverPhoto photo) {
    return new ConsignmentPhotoResponse(
        photo.getId(),
        null,
        photo.getDisplayOrder(),
        photo.getBlobKey(),
        signedUrl(photo.getBlobKey()));
  }

  /** Ánh xạ biên bản kiểm định đầy đủ. */
  public ConditionReportResponse toReportResponse(
      ConditionReport report,
      ConsignmentItem item,
      List<ConditionReportCheck> checks,
      List<ConsignmentItemPhoto> photos) {
    List<ConditionCheckResponse> mappedChecks =
        checks == null
            ? List.of()
            : checks.stream()
                .map(
                    check ->
                        new ConditionCheckResponse(
                            check.getId().getCheckCode(), check.getResult(), check.getNote()))
                .toList();
    List<ConsignmentPhotoResponse> mappedPhotos =
        photos == null ? List.of() : photos.stream().map(this::toItemPhotoResponse).toList();
    return new ConditionReportResponse(
        report.getId(),
        report.getConsignmentItemId(),
        item != null ? item.getStatus() : null,
        report.getOverallGrade(),
        report.getConclusion(),
        report.getDeclineReason(),
        report.getCorrectedItemName(),
        report.getDiscrepancyNote(),
        report.getFilmExpiryDate(),
        report.getInspectorAccountId(),
        report.getInspectedAt(),
        mappedChecks,
        mappedPhotos);
  }

  /** Ánh xạ đề xuất giá bán ký gửi. */
  public PriceOfferResponse toPriceOfferResponse(PriceOffer offer, ConsignmentItem item) {
    return new PriceOfferResponse(
        offer.getId(),
        offer.getConsignmentItemId(),
        item != null ? item.getStatus() : null,
        item != null ? item.getAgreedPriceVnd() : null,
        offer.getPurpose(),
        offer.getRoundNumber(),
        offer.getOfferedByParty(),
        offer.getOfferedByAccountId(),
        offer.getPriceVnd(),
        offer.getJustification(),
        offer.getStatus(),
        offer.getResponseDueAt(),
        offer.getRespondedAt(),
        offer.getCreatedAt());
  }

  /** Ánh xạ bản tóm tắt tin đăng công khai. */
  public ConsignmentListingSummaryResponse toListingSummary(
      ConsignmentListing listing,
      ConsignmentItem item,
      ConditionReport report,
      FilmLabBriefResponse labBrief,
      String firstPhotoBlobKey) {
    return new ConsignmentListingSummaryResponse(
        listing.getId(),
        listing.getConsignmentItemId(),
        listing.getFilmLabId(),
        labBrief != null ? labBrief.labName() : null,
        item != null ? item.getItemType() : null,
        report != null ? report.getOverallGrade() : null,
        listing.getTitle(),
        listing.getPriceVnd(),
        listing.isPickupAtLab(),
        listing.isDeliveryAvailable(),
        listing.getStatus(),
        signedUrl(firstPhotoBlobKey),
        listing.getPublishedAt(),
        listing.getExpiresAt());
  }

  /** Ánh xạ chi tiết tin đăng kèm biên bản kiểm định công khai. */
  public ConsignmentListingDetailResponse toListingDetail(
      ConsignmentListing listing,
      ConsignmentItem item,
      FilmLabBriefResponse labBrief,
      ConditionReportResponse conditionReport) {
    String address =
        labBrief == null
            ? null
            : String.join(
                ", ",
                List.of(labBrief.addressLine(), labBrief.district(), labBrief.province()));
    return new ConsignmentListingDetailResponse(
        listing.getId(),
        listing.getConsignmentItemId(),
        item.getStatus(),
        listing.getFilmLabId(),
        labBrief != null ? labBrief.labName() : null,
        address,
        item.getItemType(),
        item.getItemName(),
        listing.getTitle(),
        listing.getDescription(),
        listing.getPriceVnd(),
        listing.isPickupAtLab(),
        listing.isDeliveryAvailable(),
        listing.getStatus(),
        listing.getConsignmentStartedAt(),
        listing.getPublishedAt(),
        listing.getExpiresAt(),
        conditionReport);
  }

  /** Ánh xạ bằng chứng bàn giao. */
  public ConsignmentHandoverResponse toHandoverResponse(
      ConsignmentHandover handover, List<ConsignmentHandoverPhoto> photos) {
    List<ConsignmentPhotoResponse> mappedPhotos =
        photos == null ? List.of() : photos.stream().map(this::toHandoverPhotoResponse).toList();
    return new ConsignmentHandoverResponse(
        handover.getId(),
        handover.getHandoverMethod(),
        handover.isRecipientCodeVerified(),
        handover.getNote(),
        handover.getPerformedByAccountId(),
        handover.getOccurredAt(),
        mappedPhotos);
  }

  /** Ánh xạ giao dịch mua ký gửi. */
  public ConsignmentSaleResponse toSaleResponse(
      ConsignmentSale sale,
      ConsignmentListing listing,
      ConsignmentItem item,
      PaymentLink paymentLink,
      ConsignmentHandoverResponse handover) {
    return new ConsignmentSaleResponse(
        sale.getId(),
        sale.getSaleCode(),
        sale.getConsignmentListingId(),
        listing.getStatus(),
        item.getId(),
        item.getStatus(),
        sale.getFilmLabId(),
        sale.getBuyerAccountId(),
        sale.getPaymentMethod(),
        sale.getHandoverMethod(),
        sale.getStatus(),
        sale.getItemPriceVnd(),
        sale.getShippingFeeVnd(),
        sale.getTotalVnd(),
        sale.getConsignmentFeeRate(),
        sale.getConsignmentFeeVnd(),
        sale.getCommissionRate(),
        sale.getCommissionVnd(),
        sale.getSellerPayoutVnd(),
        sale.getPaymentDueAt(),
        sale.getHandedOverAt(),
        sale.getReturnWindowEndsAt(),
        paymentLink,
        handover,
        sale.getCreatedAt());
  }

  private String signedUrl(String blobKey) {
    if (blobKey == null || blobKey.isBlank()) {
      return null;
    }
    URI signed = storageClient.signedUrl(blobKey);
    return signed != null ? signed.toString() : null;
  }
}
