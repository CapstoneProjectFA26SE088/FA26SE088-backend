package com.fa26se088.filmlink.controller.marketplace;

import com.fa26se088.filmlink.dto.marketplace.request.CreateConsignmentBookingRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentBookingResponse;
import com.fa26se088.filmlink.dto.marketplace.response.PriceOfferResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.marketplace.ConsignmentInspectionService;
import com.fa26se088.filmlink.service.marketplace.ConsignmentListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Người bán đặt lịch mang thiết bị tới Lab và xác nhận đề xuất giá ký gửi (UC45, UC47). */
@RestController
@RequestMapping("/api/v1/consignments")
@RequiredArgsConstructor
public class SellerConsignmentController {

  private final ConsignmentInspectionService inspectionService;
  private final ConsignmentListingService listingService;

  /** Đặt lịch hẹn mang thiết bị cũ tới Film Lab để kiểm định ký gửi. */
  @PostMapping("/bookings")
  @PreAuthorize("hasRole('PHOTOGRAPHER')")
  public ResponseEntity<ConsignmentBookingResponse> createBooking(
      @Valid @RequestBody CreateConsignmentBookingRequest request,
      @AuthenticationPrincipal AuthenticatedAccount seller) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(inspectionService.createBooking(request, seller));
  }

  /** Người bán chấp nhận mức giá đề xuất của Film Lab. */
  @PostMapping("/offers/{id}/accept")
  @PreAuthorize("hasRole('PHOTOGRAPHER')")
  public ResponseEntity<PriceOfferResponse> acceptOffer(
      @PathVariable("id") Long offerId, @AuthenticationPrincipal AuthenticatedAccount seller) {
    return ResponseEntity.ok(listingService.acceptOffer(offerId, seller));
  }
}
