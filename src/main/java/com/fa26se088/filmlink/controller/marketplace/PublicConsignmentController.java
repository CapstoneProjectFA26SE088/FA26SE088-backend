package com.fa26se088.filmlink.controller.marketplace;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentListingDetailResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentListingSummaryResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSlotResponse;
import com.fa26se088.filmlink.service.marketplace.ConsignmentInspectionService;
import com.fa26se088.filmlink.service.marketplace.ConsignmentListingService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API công khai không cần đăng nhập cho lịch trống ký gửi và tin đăng ký gửi (UC45, UC61). */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicConsignmentController {

  private final ConsignmentInspectionService inspectionService;
  private final ConsignmentListingService listingService;

  /** Tra cứu danh sách khung giờ nhận ký gửi trong ngày của một Film Lab. */
  @GetMapping("/film-labs/{id}/consignment-slots")
  public ResponseEntity<List<ConsignmentSlotResponse>> getConsignmentSlots(
      @PathVariable("id") Long filmLabId,
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return ResponseEntity.ok(inspectionService.getFreeSlots(filmLabId, date));
  }

  /** Duyệt danh sách tin đăng bán thiết bị ký gửi đang mở bán công khai. */
  @GetMapping("/consignment-listings")
  public ResponseEntity<PageResponse<ConsignmentListingSummaryResponse>> listListings(
      @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(PageResponse.from(listingService.listPublicListings(pageable)));
  }

  /** Xem chi tiết tin đăng bán thiết bị ký gửi kèm biên bản kiểm định công khai. */
  @GetMapping("/consignment-listings/{id}")
  public ResponseEntity<ConsignmentListingDetailResponse> getListingDetail(
      @PathVariable("id") Long id) {
    return ResponseEntity.ok(listingService.getPublicListingDetail(id));
  }
}
