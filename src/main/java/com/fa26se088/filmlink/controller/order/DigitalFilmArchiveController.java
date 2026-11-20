package com.fa26se088.filmlink.controller.order;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.order.request.SearchFilmArchiveRequest;
import com.fa26se088.filmlink.dto.order.response.FilmArchiveRollResponse;
import com.fa26se088.filmlink.dto.order.response.FilmArchiveRollSummaryResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.order.DigitalFilmArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC28: Photographer xem Digital Film Archive của mình ({@code /api/v1/me/...}, SPEC-00 §9). Đặt ở
 * domain order vì quyền xem dựa trên đơn của Photographer. {@code hasRole('PHOTOGRAPHER')} gồm cả
 * PHOTOGRAPHY_EXPERT (BR-05, {@code RoleHierarchy}).
 */
@RestController
@RequestMapping("/api/v1/me/archive")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHOTOGRAPHER')")
@Tag(name = "Digital Film Archive", description = "Photographer xem ảnh scan đã nhận (UC28)")
public class DigitalFilmArchiveController {

  private final DigitalFilmArchiveService digitalFilmArchiveService;

  @GetMapping
  @Operation(
      summary = "Các cuộn đã giao scan của tôi, giao mới nhất trước",
      description =
          "Chỉ cuộn của đơn đã giao scan (READY_FOR_HANDOVER, AWAITING_TOP_UP, DELIVERED, CLOSED)."
              + " Phân trang 20 dòng mặc định, tối đa 100. coverPreviewUrl là link có chữ ký 15"
              + " phút.")
  public PageResponse<FilmArchiveRollSummaryResponse> getMyArchive(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @ParameterObject @ModelAttribute SearchFilmArchiveRequest request) {
    return digitalFilmArchiveService.getMyArchive(account.id(), request);
  }

  @GetMapping("/rolls/{archiveRollId}")
  @Operation(
      summary = "Chi tiết một cuộn: link file gốc, metadata, ảnh xem trước (link ký 15 phút)",
      description =
          "Cuộn không tồn tại, không thuộc tôi hoặc đơn chưa giao scan → 404"
              + " ARCHIVE_ROLL_NOT_FOUND.")
  public FilmArchiveRollResponse getMyArchiveRoll(
      @AuthenticationPrincipal AuthenticatedAccount account, @PathVariable Long archiveRollId) {
    return digitalFilmArchiveService.getMyArchiveRoll(account.id(), archiveRollId);
  }
}
