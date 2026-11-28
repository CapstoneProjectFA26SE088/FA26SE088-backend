package com.fa26se088.filmlink.controller.marketplace;

import com.fa26se088.filmlink.dto.marketplace.request.CreatePriceOfferRequest;
import com.fa26se088.filmlink.dto.marketplace.request.PublishConsignmentListingRequest;
import com.fa26se088.filmlink.dto.marketplace.request.RecordConditionReportRequest;
import com.fa26se088.filmlink.dto.marketplace.request.RecordConsignmentHandoverRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConditionReportResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentBookingResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentListingDetailResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSaleResponse;
import com.fa26se088.filmlink.dto.marketplace.response.PriceOfferResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.marketplace.ConsignmentInspectionService;
import com.fa26se088.filmlink.service.marketplace.ConsignmentListingService;
import com.fa26se088.filmlink.service.marketplace.ConsignmentSaleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Hàng đợi tác vụ ký gửi tại quầy dành cho Chủ Film Lab (UC46, UC47, UC48, UC63). */
@RestController
@RequestMapping("/api/v1/lab/consignments")
@RequiredArgsConstructor
public class LabConsignmentController {

  private final ConsignmentInspectionService inspectionService;
  private final ConsignmentListingService listingService;
  private final ConsignmentSaleService saleService;

  /** Check-in lịch hẹn khi người bán mang thiết bị tới quầy Lab. */
  @PostMapping("/bookings/{id}/check-in")
  @PreAuthorize("hasRole('FILM_LAB_OWNER')")
  public ResponseEntity<ConsignmentBookingResponse> checkInBooking(
      @PathVariable("id") Long bookingId, @AuthenticationPrincipal AuthenticatedAccount owner) {
    return ResponseEntity.ok(inspectionService.checkInBooking(bookingId, owner));
  }

  /** Lập biên bản kiểm định thiết bị kèm tối thiểu 4 ảnh bằng chứng thực tế. */
  @PostMapping(
      path = "/items/{id}/condition-report",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('FILM_LAB_OWNER')")
  public ResponseEntity<ConditionReportResponse> recordConditionReport(
      @PathVariable("id") Long itemId,
      @Valid @RequestPart("data") RecordConditionReportRequest data,
      @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
      @AuthenticationPrincipal AuthenticatedAccount owner) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(inspectionService.recordConditionReport(itemId, data, photos, owner));
  }

  /** Gửi đề xuất giá bán lần đầu cho thiết bị sau khi kiểm định ACCEPTED. */
  @PostMapping("/items/{id}/offers")
  @PreAuthorize("hasRole('FILM_LAB_OWNER')")
  public ResponseEntity<PriceOfferResponse> createInitialOffer(
      @PathVariable("id") Long itemId,
      @Valid @RequestBody CreatePriceOfferRequest request,
      @AuthenticationPrincipal AuthenticatedAccount owner) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(listingService.createInitialOffer(itemId, request, owner));
  }

  /** Xuất bản tin đăng bán công khai cho thiết bị đã thống nhất giá với người bán. */
  @PostMapping("/items/{id}/listing")
  @PreAuthorize("hasRole('FILM_LAB_OWNER')")
  public ResponseEntity<ConsignmentListingDetailResponse> publishListing(
      @PathVariable("id") Long itemId,
      @Valid @RequestBody PublishConsignmentListingRequest request,
      @AuthenticationPrincipal AuthenticatedAccount owner) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(listingService.publishListing(itemId, request, owner));
  }

  /** Bàn giao thiết bị cho người mua tại quầy Lab kèm ảnh bằng chứng tình trạng. */
  @PostMapping(path = "/sales/{id}/handover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('FILM_LAB_OWNER')")
  public ResponseEntity<ConsignmentSaleResponse> recordHandover(
      @PathVariable("id") Long saleId,
      @Valid @RequestPart(value = "data", required = false) RecordConsignmentHandoverRequest data,
      @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
      @AuthenticationPrincipal AuthenticatedAccount owner) {
    return ResponseEntity.ok(saleService.recordHandover(saleId, data, photos, owner));
  }
}
