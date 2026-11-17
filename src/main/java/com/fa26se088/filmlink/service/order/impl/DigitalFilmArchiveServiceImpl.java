package com.fa26se088.filmlink.service.order.impl;

import com.fa26se088.filmlink.dto.archive.response.ArchiveRollResponse;
import com.fa26se088.filmlink.dto.archive.response.ArchiveRollSummaryResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.lab.response.FilmLabBriefResponse;
import com.fa26se088.filmlink.dto.masterdata.response.FilmFormatResponse;
import com.fa26se088.filmlink.dto.masterdata.response.FilmStockResponse;
import com.fa26se088.filmlink.dto.order.request.SearchFilmArchiveRequest;
import com.fa26se088.filmlink.dto.order.response.FilmArchiveRollResponse;
import com.fa26se088.filmlink.dto.order.response.FilmArchiveRollSummaryResponse;
import com.fa26se088.filmlink.entity.order.FilmRoll;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.FilmRollStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.order.ServiceOrderMapper;
import com.fa26se088.filmlink.repository.order.FilmRollRepository;
import com.fa26se088.filmlink.repository.order.ServiceOrderRepository;
import com.fa26se088.filmlink.service.archive.ArchiveService;
import com.fa26se088.filmlink.service.lab.FilmLabService;
import com.fa26se088.filmlink.service.masterdata.MasterdataService;
import com.fa26se088.filmlink.service.order.DigitalFilmArchiveService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DigitalFilmArchiveServiceImpl implements DigitalFilmArchiveService {

  /** CR-01: 20 dòng mặc định, tối đa 100. */
  static final int DEFAULT_PAGE_SIZE = 20;

  static final int MAX_PAGE_SIZE = 100;

  private final FilmRollRepository filmRollRepository;
  private final ServiceOrderRepository serviceOrderRepository;
  private final ArchiveService archiveService;
  private final FilmLabService filmLabService;
  private final MasterdataService masterdataService;
  private final ServiceOrderMapper serviceOrderMapper;

  /**
   * UC28, CR-01, CR-02, CR-14: chỉ cuộn {@code RECEIVED} của đơn thuộc Photographer và đơn đã giao
   * scan ({@link #SCANS_DELIVERED_STATUSES}). Lọc quyền bằng một query ở domain order ({@code
   * FilmRollRepository#findIdsByPhotographerAndOrderStatuses}), rồi domain archive phân trang trong
   * tập id đó (giao mới nhất trước). Cuộn, đơn, tên lab, danh mục mỗi thứ đọc gộp một lần cho cả
   * trang (không N+1).
   */
  @Override
  @Transactional(readOnly = true)
  public PageResponse<FilmArchiveRollSummaryResponse> getMyArchive(
      Long photographerAccountId, SearchFilmArchiveRequest request) {
    int page = request.page() == null ? 0 : request.page();
    int size = request.size() == null ? DEFAULT_PAGE_SIZE : Math.min(request.size(), MAX_PAGE_SIZE);
    List<Long> visibleRollIds =
        filmRollRepository.findIdsByPhotographerAndOrderStatuses(
            photographerAccountId, SCANS_DELIVERED_STATUSES, FilmRollStatus.RECEIVED);
    Page<ArchiveRollSummaryResponse> archives =
        archiveService.getArchiveRolls(visibleRollIds, page, size);
    if (archives.isEmpty()) {
      return PageResponse.from(archives, archive -> (FilmArchiveRollSummaryResponse) null);
    }

    Map<Long, FilmRoll> rolls =
        byId(
            filmRollRepository.findAllById(
                archives.map(ArchiveRollSummaryResponse::filmRollId).getContent()),
            FilmRoll::getId);
    Map<Long, ServiceOrder> orders =
        byId(
            serviceOrderRepository.findAllById(
                rolls.values().stream().map(FilmRoll::getServiceOrderId).distinct().toList()),
            ServiceOrder::getId);
    Map<Long, String> labNames = labNames(orders.values());
    Map<Long, String> stockNames = filmStockNames();
    Map<Long, String> formatNames = filmFormatNames();
    return PageResponse.from(
        archives,
        archive -> {
          FilmRoll roll = rolls.get(archive.filmRollId());
          ServiceOrder order = orders.get(roll.getServiceOrderId());
          return serviceOrderMapper.toArchiveSummaryResponse(
              archive,
              roll,
              order,
              labNames.get(order.getFilmLabId()),
              stockName(roll, stockNames),
              formatNames.get(roll.getFilmFormatId()));
        });
  }

  /**
   * UC28, CR-14: archive roll không tồn tại, cuộn không thuộc đơn của Photographer, cuộn không
   * {@code RECEIVED}, hoặc đơn chưa giao scan → cùng một 404 {@code ARCHIVE_ROLL_NOT_FOUND} (không
   * lộ cuộn của người khác).
   */
  @Override
  @Transactional(readOnly = true)
  public FilmArchiveRollResponse getMyArchiveRoll(Long photographerAccountId, Long archiveRollId) {
    FilmRoll roll =
        archiveService
            .findFilmRollId(archiveRollId)
            .flatMap(
                filmRollId ->
                    filmRollRepository.findByIdAndPhotographerAndOrderStatuses(
                        filmRollId,
                        photographerAccountId,
                        SCANS_DELIVERED_STATUSES,
                        FilmRollStatus.RECEIVED))
            .orElseThrow(DigitalFilmArchiveServiceImpl::archiveRollNotFound);
    ServiceOrder order =
        serviceOrderRepository
            .findById(roll.getServiceOrderId())
            .orElseThrow(DigitalFilmArchiveServiceImpl::archiveRollNotFound);
    ArchiveRollResponse archive = archiveService.getArchiveRoll(archiveRollId);
    return serviceOrderMapper.toArchiveResponse(
        archive,
        roll,
        order,
        labNames(List.of(order)).get(order.getFilmLabId()),
        stockName(roll, filmStockNames()),
        filmFormatNames().get(roll.getFilmFormatId()));
  }

  private Map<Long, String> labNames(Collection<ServiceOrder> orders) {
    return filmLabService
        .getLabBriefs(orders.stream().map(ServiceOrder::getFilmLabId).distinct().toList())
        .stream()
        .collect(Collectors.toMap(FilmLabBriefResponse::id, FilmLabBriefResponse::labName));
  }

  /**
   * Tên dòng phim trong danh mục (hãng + tên). {@link MasterdataService} chỉ trả dòng phim ACTIVE,
   * nên dòng phim đã ngừng dùng không có tên (trả {@code null}, FE hiện theo {@code filmStockId}).
   */
  private Map<Long, String> filmStockNames() {
    return masterdataService.getFilmStocks(null).stream()
        .collect(
            Collectors.toMap(
                FilmStockResponse::id,
                stock ->
                    stock.manufacturer() == null
                        ? stock.stockName()
                        : stock.manufacturer() + " " + stock.stockName()));
  }

  /** Tên khổ phim ACTIVE (xem {@link #filmStockNames()} về danh mục đã ngừng dùng). */
  private Map<Long, String> filmFormatNames() {
    return masterdataService.getFilmFormats().stream()
        .collect(Collectors.toMap(FilmFormatResponse::id, FilmFormatResponse::displayName));
  }

  /** UC20: cuộn có đúng một trong {@code film_stock_id} / {@code film_stock_name_text}. */
  private static String stockName(FilmRoll roll, Map<Long, String> stockNames) {
    return roll.getFilmStockId() == null
        ? roll.getFilmStockNameText()
        : stockNames.get(roll.getFilmStockId());
  }

  private static <T> Map<Long, T> byId(List<T> items, Function<T, Long> id) {
    return items.stream().collect(Collectors.toMap(id, Function.identity()));
  }

  private static BusinessException archiveRollNotFound() {
    return new BusinessException(
        ErrorCode.ARCHIVE_ROLL_NOT_FOUND, "Không tìm thấy cuộn trong kho phim số.");
  }
}
