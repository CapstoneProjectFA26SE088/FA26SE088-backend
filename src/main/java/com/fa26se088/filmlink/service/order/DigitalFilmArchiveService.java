package com.fa26se088.filmlink.service.order;

import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.order.request.SearchFilmArchiveRequest;
import com.fa26se088.filmlink.dto.order.response.FilmArchiveRollResponse;
import com.fa26se088.filmlink.dto.order.response.FilmArchiveRollSummaryResponse;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import java.util.Set;

/**
 * UC28: Photographer xem Digital Film Archive của mình.
 *
 * <p>Nằm ở domain order (không ở archive) vì quyền xem dựa trên dữ liệu sở hữu của đơn: chỉ cuộn
 * {@code RECEIVED} thuộc đơn của Photographer và đơn đã giao scan ({@code READY_FOR_HANDOVER},
 * {@code AWAITING_TOP_UP}, {@code DELIVERED}, {@code CLOSED}). Domain archive chỉ nhận danh sách
 * {@code film_roll_id} đã lọc.
 */
public interface DigitalFilmArchiveService {

  /** UC26, UC28: trạng thái đơn mà scan đã được giao (Photographer thấy cuộn trong archive). */
  Set<ServiceOrderStatus> SCANS_DELIVERED_STATUSES =
      Set.of(
          ServiceOrderStatus.READY_FOR_HANDOVER,
          ServiceOrderStatus.AWAITING_TOP_UP,
          ServiceOrderStatus.DELIVERED,
          ServiceOrderStatus.CLOSED);

  /** UC28: các cuộn đã giao scan của Photographer, giao mới nhất trước (CR-01, CR-02). */
  PageResponse<FilmArchiveRollSummaryResponse> getMyArchive(
      Long photographerAccountId, SearchFilmArchiveRequest request);

  /**
   * UC28: chi tiết một cuộn trong archive.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ARCHIVE_ROLL_NOT_FOUND} (404)
   *     khi cuộn không tồn tại, không thuộc Photographer, hoặc đơn chưa giao scan
   */
  FilmArchiveRollResponse getMyArchiveRoll(Long photographerAccountId, Long archiveRollId);
}
