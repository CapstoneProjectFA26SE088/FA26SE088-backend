package com.fa26se088.filmlink.service.lab;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.lab.request.SearchFilmLabRequest;
import com.fa26se088.filmlink.dto.lab.response.PublicFilmLabResponse;
import com.fa26se088.filmlink.dto.lab.response.PublicFilmLabSummaryResponse;

/**
 * UC05, UC17: Guest / Photographer xem và tìm Film Lab, không cần đăng nhập.
 *
 * <p>BR-34: chỉ lab đang bật nhận đơn và đủ điều kiện nhận đơn ({@link
 * FilmLabService#isReadyToAcceptOrders}: máy BR-57 + ít nhất một gói {@code ON_SALE}) mới hiển thị.
 * Lab khác coi như không tồn tại.
 */
public interface PublicFilmLabService {

  /** UC17: tìm lab theo master data, toạ độ / bán kính; sắp theo giá hoặc khoảng cách. */
  PageResponse<PublicFilmLabSummaryResponse> searchLabs(SearchFilmLabRequest request);

  /** UC05: trang lab công khai; lab không hiển thị thì {@code LAB_NOT_FOUND} (404). */
  PublicFilmLabResponse getLab(Long filmLabId);
}
