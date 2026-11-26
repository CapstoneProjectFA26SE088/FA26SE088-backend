package com.fa26se088.filmlink.controller.lab;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.lab.request.SearchFilmLabRequest;
import com.fa26se088.filmlink.dto.lab.response.PublicFilmLabResponse;
import com.fa26se088.filmlink.dto.lab.response.PublicFilmLabSummaryResponse;
import com.fa26se088.filmlink.service.lab.PublicFilmLabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC05, UC17: xem và tìm Film Lab công khai, không cần đăng nhập ({@code permitAll} trong {@code
 * SecurityConfig}). Chỉ lab hiển thị theo BR-34 mới có kết quả.
 */
@RestController
@RequestMapping("/api/v1/public/film-labs")
@RequiredArgsConstructor
@Tag(
    name = "Public Film Lab",
    description = "Xem trang lab và tìm lab, không cần đăng nhập (UC05, UC17)")
public class PublicFilmLabController {

  private final PublicFilmLabService publicFilmLabService;

  @GetMapping
  @Operation(
      summary =
          "Tìm lab theo quy trình / khổ phim / loại dịch vụ, bán kính 1–50 km quanh lat/lng;"
              + " sort=price|distance; 20 dòng/trang, tối đa 100")
  public PageResponse<PublicFilmLabSummaryResponse> searchLabs(
      @Valid @ParameterObject @ModelAttribute SearchFilmLabRequest request) {
    return publicFilmLabService.searchLabs(request);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Trang lab: hồ sơ, giờ mở cửa, ảnh (link 15 phút), bảng giá đang bán")
  public PublicFilmLabResponse getLab(@PathVariable Long id) {
    return publicFilmLabService.getLab(id);
  }
}
