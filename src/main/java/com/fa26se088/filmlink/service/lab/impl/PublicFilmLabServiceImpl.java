package com.fa26se088.filmlink.service.lab.impl;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.lab.request.SearchFilmLabRequest;
import com.fa26se088.filmlink.dto.lab.response.CatalogRefResponse;
import com.fa26se088.filmlink.dto.lab.response.FilmLabImageResponse;
import com.fa26se088.filmlink.dto.lab.response.PublicFilmLabResponse;
import com.fa26se088.filmlink.dto.lab.response.PublicFilmLabSummaryResponse;
import com.fa26se088.filmlink.dto.lab.response.PublicServicePackageResponse;
import com.fa26se088.filmlink.dto.masterdata.response.FilmFormatResponse;
import com.fa26se088.filmlink.dto.masterdata.response.FilmProcessResponse;
import com.fa26se088.filmlink.dto.masterdata.response.ServiceTypeResponse;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.lab.ServicePackage;
import com.fa26se088.filmlink.entity.lab.ServicePackageScanOption;
import com.fa26se088.filmlink.entity.lab.enums.ServicePackageStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.exception.GlobalExceptionHandler.FieldViolation;
import com.fa26se088.filmlink.mapper.lab.FilmLabMapper;
import com.fa26se088.filmlink.mapper.lab.ServicePackageMapper;
import com.fa26se088.filmlink.repository.lab.FilmLabImageRepository;
import com.fa26se088.filmlink.repository.lab.FilmLabOpeningHourRepository;
import com.fa26se088.filmlink.repository.lab.FilmLabRepository;
import com.fa26se088.filmlink.repository.lab.FilmLabRepository.PublicLabSearchRow;
import com.fa26se088.filmlink.repository.lab.ServicePackageRepository;
import com.fa26se088.filmlink.repository.lab.ServicePackageScanOptionRepository;
import com.fa26se088.filmlink.service.lab.FilmLabService;
import com.fa26se088.filmlink.service.lab.PublicFilmLabService;
import com.fa26se088.filmlink.service.masterdata.MasterdataService;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicFilmLabServiceImpl implements PublicFilmLabService {

  /** CR-01: mặc định 20 dòng mỗi trang, tối đa 100 (khớp {@code spring.data.web.pageable}). */
  static final int DEFAULT_PAGE_SIZE = 20;

  static final int MAX_PAGE_SIZE = 100;

  private static final String SORT_DISTANCE = "distance";

  private final FilmLabRepository filmLabRepository;
  private final FilmLabOpeningHourRepository filmLabOpeningHourRepository;
  private final FilmLabImageRepository filmLabImageRepository;
  private final ServicePackageRepository servicePackageRepository;
  private final ServicePackageScanOptionRepository scanOptionRepository;
  private final FilmLabService filmLabService;
  private final MasterdataService masterdataService;
  private final StorageClient storageClient;
  private final FilmLabMapper filmLabMapper;
  private final ServicePackageMapper servicePackageMapper;

  /**
   * UC17, BR-34: lab khớp nếu có ít nhất một gói {@code ON_SALE} khớp <b>tất cả</b> bộ lọc đã gửi;
   * giá của lab là giá thấp nhất trong các gói đó. Có {@code radiusKm} (1–50) thì chỉ lấy lab có
   * toạ độ và nằm trong bán kính (Haversine). {@code radiusKm} và {@code sort=distance} cần {@code
   * lat}, {@code lng}.
   *
   * <p>SQL lọc cờ nhận đơn, gói và khoảng cách; điều kiện máy (BR-57) lọc tiếp ở đây qua {@link
   * FilmLabService#isReadyToAcceptOrders} để chỉ có một chỗ định nghĩa "lab sẵn sàng nhận đơn"
   * (bảng {@code machines} thuộc domain machine). Vì vậy phân trang làm sau bước lọc, trên bộ nhớ.
   */
  @Override
  @Transactional(readOnly = true)
  public PageResponse<PublicFilmLabSummaryResponse> searchLabs(SearchFilmLabRequest request) {
    boolean sortByDistance = validateSearch(request);
    int page = request.page() == null ? 0 : request.page();
    int size =
        request.size() == null ? DEFAULT_PAGE_SIZE : Math.min(request.size(), MAX_PAGE_SIZE);

    List<PublicLabSearchRow> visible =
        filmLabRepository
            .searchPublicLabCandidates(
                request.filmProcessId(),
                request.filmFormatId(),
                request.serviceTypeId(),
                request.lat(),
                request.lng(),
                request.radiusKm(),
                sortByDistance)
            .stream()
            .filter(row -> filmLabService.isReadyToAcceptOrders(row.getId()))
            .toList();

    long total = visible.size();
    int from = (int) Math.min((long) page * size, total);
    int to = (int) Math.min((long) from + size, total);
    List<PublicFilmLabSummaryResponse> content =
        visible.subList(from, to).stream()
            .map(row -> filmLabMapper.toPublicSummary(row, signedUrlOrNull(row.getCoverBlobKey())))
            .toList();
    int totalPages = (int) ((total + size - 1) / size);
    return new PageResponse<>(content, page, size, total, totalPages, to < total);
  }

  /**
   * UC05, BR-34: lab không bật nhận đơn hoặc chưa đủ điều kiện nhận đơn thì trả {@code
   * LAB_NOT_FOUND} như lab không tồn tại. Bảng giá chỉ gồm gói {@code ON_SALE} có master data còn
   * ACTIVE (BR-16), rẻ trước; lab không còn gói nào như vậy cũng trả {@code LAB_NOT_FOUND} (khớp
   * kết quả tìm kiếm). Ảnh kèm link ký có hạn (NFR-PRI-02).
   */
  @Override
  @Transactional(readOnly = true)
  public PublicFilmLabResponse getLab(Long filmLabId) {
    FilmLab lab =
        filmLabRepository
            .findById(filmLabId)
            .filter(FilmLab::isAcceptingOrders)
            .filter(found -> filmLabService.isReadyToAcceptOrders(found.getId()))
            .orElseThrow(PublicFilmLabServiceImpl::labNotFound);
    List<PublicServicePackageResponse> packages = displayablePackages(lab.getId());
    if (packages.isEmpty()) {
      throw labNotFound();
    }

    List<FilmLabImageResponse> images =
        filmLabImageRepository.findByFilmLabIdOrderByDisplayOrder(lab.getId()).stream()
            .map(
                image ->
                    filmLabMapper.toImageResponse(image, storageClient.signedUrl(image.getBlobKey())))
            .toList();
    return filmLabMapper.toPublicResponse(
        lab,
        filmLabOpeningHourRepository.findByFilmLabIdOrderByDayOfWeek(lab.getId()),
        images,
        packages);
  }

  /**
   * Gói {@code ON_SALE} có Service Type, Film Process, Film Format đều còn ACTIVE (BR-16). Danh mục
   * ACTIVE lấy qua {@link MasterdataService} (có cache, bị xoá khi Admin đổi trạng thái), nên gói có
   * master data đã ngừng dùng không có trong các map và bị bỏ.
   */
  private List<PublicServicePackageResponse> displayablePackages(Long filmLabId) {
    List<ServicePackage> packages =
        servicePackageRepository.findByFilmLabIdAndStatusOrderByPriceVndAscIdAsc(
            filmLabId, ServicePackageStatus.ON_SALE);
    Map<Long, List<ServicePackageScanOption>> optionsByPackage =
        scanOptionRepository
            .findByServicePackageIdInOrderByPriceVndAscIdAsc(
                packages.stream().map(ServicePackage::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(ServicePackageScanOption::getServicePackageId));

    Map<Long, CatalogRefResponse> serviceTypes =
        byId(
            masterdataService.getServiceTypes(),
            ServiceTypeResponse::id,
            item -> new CatalogRefResponse(item.id(), item.code(), item.displayName()));
    Map<Long, CatalogRefResponse> filmProcesses =
        byId(
            masterdataService.getFilmProcesses(),
            FilmProcessResponse::id,
            item -> new CatalogRefResponse(item.id(), item.code(), item.displayName()));
    Map<Long, CatalogRefResponse> filmFormats =
        byId(
            masterdataService.getFilmFormats(),
            FilmFormatResponse::id,
            item -> new CatalogRefResponse(item.id(), item.code(), item.displayName()));

    return packages.stream()
        .filter(
            servicePackage ->
                serviceTypes.containsKey(servicePackage.getServiceTypeId())
                    && filmProcesses.containsKey(servicePackage.getFilmProcessId())
                    && filmFormats.containsKey(servicePackage.getFilmFormatId()))
        .map(
            servicePackage ->
                servicePackageMapper.toPublicResponse(
                    servicePackage,
                    optionsByPackage.getOrDefault(servicePackage.getId(), List.of()),
                    serviceTypes.get(servicePackage.getServiceTypeId()),
                    filmProcesses.get(servicePackage.getFilmProcessId()),
                    filmFormats.get(servicePackage.getFilmFormatId())))
        .toList();
  }

  /** Kiểm ràng buộc giữa các tham số tìm kiếm; trả {@code true} nếu sắp theo khoảng cách. */
  private static boolean validateSearch(SearchFilmLabRequest request) {
    boolean hasLat = request.lat() != null;
    boolean hasLng = request.lng() != null;
    if (hasLat != hasLng) {
      throw fieldError(hasLat ? "lng" : "lat", "lat và lng phải gửi cùng nhau.");
    }
    if (request.radiusKm() != null && !hasLat) {
      throw fieldError("radiusKm", "Tìm theo bán kính cần gửi lat và lng.");
    }
    boolean sortByDistance =
        request.sort() != null && SORT_DISTANCE.equals(request.sort().toLowerCase(Locale.ROOT));
    if (sortByDistance && !hasLat) {
      throw fieldError("sort", "Sắp xếp theo khoảng cách cần gửi lat và lng.");
    }
    return sortByDistance;
  }

  private URI signedUrlOrNull(String blobKey) {
    return blobKey == null ? null : storageClient.signedUrl(blobKey);
  }

  private static <T> Map<Long, CatalogRefResponse> byId(
      List<T> items, Function<T, Long> id, Function<T, CatalogRefResponse> toRef) {
    return items.stream().collect(Collectors.toMap(id, toRef));
  }

  private static BusinessException labNotFound() {
    return new BusinessException(ErrorCode.LAB_NOT_FOUND, "Không tìm thấy Film Lab.");
  }

  private static BusinessException fieldError(String field, String message) {
    return new BusinessException(
        ErrorCode.VALIDATION_ERROR,
        message,
        Map.of("errors", List.of(new FieldViolation(field, message))));
  }
}
