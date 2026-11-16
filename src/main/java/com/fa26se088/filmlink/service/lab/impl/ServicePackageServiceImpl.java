package com.fa26se088.filmlink.service.lab.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.lab.request.SaveServicePackageRequest;
import com.fa26se088.filmlink.dto.lab.request.ServicePackageScanOptionRequest;
import com.fa26se088.filmlink.dto.lab.request.UpdateServicePackageStatusRequest;
import com.fa26se088.filmlink.dto.lab.response.ServicePackageResponse;
import com.fa26se088.filmlink.dto.masterdata.response.FilmFormatResponse;
import com.fa26se088.filmlink.dto.masterdata.response.FilmProcessResponse;
import com.fa26se088.filmlink.dto.masterdata.response.ServiceTypeResponse;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.lab.ServicePackage;
import com.fa26se088.filmlink.entity.lab.ServicePackageScanOption;
import com.fa26se088.filmlink.entity.lab.enums.ServicePackageStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.exception.GlobalExceptionHandler.FieldViolation;
import com.fa26se088.filmlink.mapper.lab.ServicePackageMapper;
import com.fa26se088.filmlink.repository.lab.FilmLabRepository;
import com.fa26se088.filmlink.repository.lab.ServicePackageRepository;
import com.fa26se088.filmlink.repository.lab.ServicePackageScanOptionRepository;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.lab.ServicePackageService;
import com.fa26se088.filmlink.service.masterdata.MasterdataService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServicePackageServiceImpl implements ServicePackageService {

  /** UC13: tên gói 2–150 ký tự ({@code ck_service_packages_name}). */
  static final int NAME_MIN = 2;

  static final int NAME_MAX = 150;

  private static final String STATUS = "status";
  private static final String ERRORS = "errors";
  private static final String PACKAGE_NAME = "packageName";

  private final ServicePackageRepository servicePackageRepository;
  private final ServicePackageScanOptionRepository scanOptionRepository;
  private final FilmLabRepository filmLabRepository;
  private final LabAccessService labAccessService;
  private final MasterdataService masterdataService;
  private final AuditLogService auditLogService;
  private final ServicePackageMapper servicePackageMapper;

  @Override
  @Transactional(readOnly = true)
  public List<ServicePackageResponse> getMyPackages(Long ownerAccountId) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    List<ServicePackage> packages =
        servicePackageRepository.findByFilmLabIdOrderByIdAsc(lab.getId());
    if (packages.isEmpty()) {
      return List.of();
    }
    Map<Long, List<ServicePackageScanOption>> optionsByPackage =
        scanOptionRepository
            .findByServicePackageIdInOrderByPriceVndAscIdAsc(
                packages.stream().map(ServicePackage::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(ServicePackageScanOption::getServicePackageId));
    return packages.stream()
        .map(
            servicePackage ->
                servicePackageMapper.toResponse(
                    servicePackage,
                    optionsByPackage.getOrDefault(servicePackage.getId(), List.of())))
        .toList();
  }

  /**
   * UC13: tên 2–150 ký tự (sau khi bỏ khoảng trắng hai đầu), duy nhất trong lab không phân biệt hoa
   * thường; giá 1–50.000.000 VND, thời gian trả 1–60 ngày (Bean Validation); push/pull bật thì phụ
   * phí mỗi stop ≥ 1, tắt thì không có phụ phí; mức scan 0..n, nhãn không trùng. BR-16: Service
   * Type, Film Process, Film Format phải là master data ACTIVE. Gói đủ thông tin nên bắt đầu ở
   * {@code ON_SALE}. BR-32: ghi audit log.
   */
  @Override
  @Transactional
  public ServicePackageResponse createMyPackage(
      Long ownerAccountId, SaveServicePackageRequest request) {
    String packageName = validateRequest(request);
    FilmLab lab = lockOwnedLab(ownerAccountId);
    if (servicePackageRepository.existsByFilmLabIdAndPackageNameIgnoreCase(
        lab.getId(), packageName)) {
      throw duplicateName();
    }

    ServicePackage servicePackage = new ServicePackage();
    servicePackage.setFilmLabId(lab.getId());
    servicePackage.setStatus(ServicePackageStatus.ON_SALE);
    applyFields(servicePackage, packageName, request);
    servicePackageRepository.saveAndFlush(servicePackage);
    List<ServicePackageScanOption> options =
        replaceScanOptions(servicePackage.getId(), request.scanOptions());

    Map<String, Object> after = new LinkedHashMap<>();
    after.put(PACKAGE_NAME, servicePackage.getPackageName());
    after.put("priceVnd", servicePackage.getPriceVnd());
    after.put(STATUS, servicePackage.getStatus().name());
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.LAB_SERVICE_PACKAGE_CREATED,
            AuditTargetType.SERVICE_PACKAGE,
            servicePackage.getId(),
            null,
            null,
            after));
    return servicePackageMapper.toResponse(servicePackage, options);
  }

  /**
   * UC13, BR-16: cùng các rule như khi tạo; tên không được trùng gói khác của lab. Danh sách mức
   * scan được thay toàn bộ, đối chiếu theo nhãn: nhãn còn giữ thì cập nhật giá (giữ nguyên {@code
   * id} mà {@code film_rolls} có thể đang tham chiếu), nhãn mới thì thêm, nhãn bị bỏ thì xoá. Sửa
   * giá không đổi đơn đã tạo (đơn chụp lại giá vào {@code film_rolls}). Trạng thái không đổi ở đây.
   */
  @Override
  @Transactional
  public ServicePackageResponse updateMyPackage(
      Long ownerAccountId, Long packageId, SaveServicePackageRequest request) {
    String packageName = validateRequest(request);
    FilmLab lab = lockOwnedLab(ownerAccountId);
    ServicePackage servicePackage = requireOwnedPackage(lab.getId(), packageId);
    if (servicePackageRepository.existsByFilmLabIdAndPackageNameIgnoreCaseAndIdNot(
        lab.getId(), packageName, servicePackage.getId())) {
      throw duplicateName();
    }

    applyFields(servicePackage, packageName, request);
    servicePackageRepository.flush();
    List<ServicePackageScanOption> options =
        replaceScanOptions(servicePackage.getId(), request.scanOptions());
    return servicePackageMapper.toResponse(servicePackage, options);
  }

  /**
   * UC13: {@code WITHDRAWN} thì gói không nhận đơn mới (thay cho xoá); mở bán lại thì master data
   * của gói phải còn ACTIVE (BR-16). BR-32: ghi audit log khi trạng thái thay đổi; gửi lại đúng
   * trạng thái hiện tại thì không làm gì.
   */
  @Override
  @Transactional
  public ServicePackageResponse updateMyPackageStatus(
      Long ownerAccountId, Long packageId, UpdateServicePackageStatusRequest request) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    ServicePackage servicePackage = requireOwnedPackage(lab.getId(), packageId);
    ServicePackageStatus before = servicePackage.getStatus();
    ServicePackageStatus after = request.status();
    if (before != after) {
      if (after == ServicePackageStatus.ON_SALE) {
        requireActiveMasterdata(
            servicePackage.getServiceTypeId(),
            servicePackage.getFilmProcessId(),
            servicePackage.getFilmFormatId());
      }
      servicePackage.setStatus(after);
      servicePackageRepository.flush();
      auditLogService.recordSuccess(
          new AuditEntry(
              AuditAction.LAB_SERVICE_PACKAGE_STATUS_CHANGED,
              AuditTargetType.SERVICE_PACKAGE,
              servicePackage.getId(),
              null,
              Map.of(STATUS, before.name()),
              Map.of(STATUS, after.name())));
    }
    return servicePackageMapper.toResponse(
        servicePackage,
        scanOptionRepository.findByServicePackageIdOrderByPriceVndAscIdAsc(servicePackage.getId()));
  }

  /** Kiểm các rule Bean Validation không diễn đạt được; trả tên gói đã bỏ khoảng trắng hai đầu. */
  private String validateRequest(SaveServicePackageRequest request) {
    String packageName = request.packageName().strip();
    int length = packageName.codePointCount(0, packageName.length());
    if (length < NAME_MIN || length > NAME_MAX) {
      throw fieldError(
          PACKAGE_NAME, "Tên gói phải có " + NAME_MIN + "–" + NAME_MAX + " ký tự.");
    }
    boolean pushPull = request.pushPullAvailable();
    if (pushPull && request.pushPullSurchargePerStopVnd() == null) {
      throw fieldError(
          "pushPullSurchargePerStopVnd", "Gói nhận push/pull phải có phụ phí mỗi stop.");
    }
    if (!pushPull && request.pushPullSurchargePerStopVnd() != null) {
      throw fieldError(
          "pushPullSurchargePerStopVnd",
          "Gói không nhận push/pull thì không có phụ phí push/pull.");
    }
    Set<String> labels = new HashSet<>();
    for (ServicePackageScanOptionRequest option : nullToEmpty(request.scanOptions())) {
      String label = option.resolutionLabel().strip();
      if (!labels.add(label.toLowerCase(Locale.ROOT))) {
        throw fieldError("scanOptions", "Mức scan \"" + label + "\" bị khai báo trùng.");
      }
    }
    requireActiveMasterdata(
        request.serviceTypeId(), request.filmProcessId(), request.filmFormatId());
    return packageName;
  }

  /**
   * BR-16: Film Lab Owner chỉ chọn master data đang ACTIVE, không tự thêm. Đọc qua {@link
   * MasterdataService} (danh sách ACTIVE, có cache) thay vì inject repository của masterdata (R4).
   */
  private void requireActiveMasterdata(Long serviceTypeId, Long filmProcessId, Long filmFormatId) {
    if (masterdataService.getServiceTypes().stream()
        .map(ServiceTypeResponse::id)
        .noneMatch(serviceTypeId::equals)) {
      throw masterdataInactive("serviceTypeId", "Loại dịch vụ");
    }
    if (masterdataService.getFilmProcesses().stream()
        .map(FilmProcessResponse::id)
        .noneMatch(filmProcessId::equals)) {
      throw masterdataInactive("filmProcessId", "Quy trình tráng");
    }
    if (masterdataService.getFilmFormats().stream()
        .map(FilmFormatResponse::id)
        .noneMatch(filmFormatId::equals)) {
      throw masterdataInactive("filmFormatId", "Khổ phim");
    }
  }

  private static void applyFields(
      ServicePackage servicePackage, String packageName, SaveServicePackageRequest request) {
    servicePackage.setPackageName(packageName);
    servicePackage.setServiceTypeId(request.serviceTypeId());
    servicePackage.setFilmProcessId(request.filmProcessId());
    servicePackage.setFilmFormatId(request.filmFormatId());
    servicePackage.setPriceVnd(request.priceVnd());
    servicePackage.setTurnaroundDays(request.turnaroundDays());
    servicePackage.setPushPullAvailable(request.pushPullAvailable());
    servicePackage.setPushPullSurchargePerStopVnd(request.pushPullSurchargePerStopVnd());
  }

  /**
   * Thay danh sách mức scan của gói, đối chiếu theo nhãn (đã bỏ khoảng trắng hai đầu). Xoá nhãn bị
   * bỏ và flush trước khi thêm, để không vướng {@code uq_service_package_scan_options_label}.
   */
  private List<ServicePackageScanOption> replaceScanOptions(
      Long servicePackageId, List<ServicePackageScanOptionRequest> requested) {
    Map<String, ServicePackageScanOption> existing =
        scanOptionRepository.findByServicePackageIdOrderByPriceVndAscIdAsc(servicePackageId).stream()
            .collect(
                Collectors.toMap(
                    ServicePackageScanOption::getResolutionLabel, Function.identity()));

    List<ServicePackageScanOption> kept = new ArrayList<>();
    for (ServicePackageScanOptionRequest item : nullToEmpty(requested)) {
      String label = item.resolutionLabel().strip();
      ServicePackageScanOption option = existing.remove(label);
      if (option == null) {
        option = new ServicePackageScanOption();
        option.setServicePackageId(servicePackageId);
        option.setResolutionLabel(label);
      }
      option.setPriceVnd(item.priceVnd());
      kept.add(option);
    }
    if (!existing.isEmpty()) {
      scanOptionRepository.deleteAll(existing.values());
      scanOptionRepository.flush();
    }
    scanOptionRepository.saveAllAndFlush(kept);
    return scanOptionRepository.findByServicePackageIdOrderByPriceVndAscIdAsc(servicePackageId);
  }

  private ServicePackage requireOwnedPackage(Long labId, Long packageId) {
    return servicePackageRepository
        .findByIdAndFilmLabId(packageId, labId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.LAB_PACKAGE_NOT_FOUND, "Không tìm thấy gói dịch vụ."));
  }

  /**
   * Khoá dòng lab để tuần tự hoá việc tạo / sửa gói của cùng một lab: kiểm trùng tên rồi ghi không
   * bị hai request chen nhau (nếu không, {@code uq_service_packages_lab_name} sẽ thành lỗi 500).
   */
  private FilmLab lockOwnedLab(Long ownerAccountId) {
    Long labId = labAccessService.requireOwnedLab(ownerAccountId).getId();
    return filmLabRepository
        .findByIdForUpdate(labId)
        .orElseThrow(
            () -> new BusinessException(ErrorCode.LAB_NOT_FOUND, "Không tìm thấy Film Lab."));
  }

  private static List<ServicePackageScanOptionRequest> nullToEmpty(
      List<ServicePackageScanOptionRequest> scanOptions) {
    return scanOptions == null ? List.of() : scanOptions;
  }

  private static BusinessException duplicateName() {
    String message = "Tên gói đã tồn tại trong bảng giá của lab.";
    return new BusinessException(
        ErrorCode.LAB_PACKAGE_NAME_DUPLICATE,
        message,
        Map.of(ERRORS, List.of(new FieldViolation(PACKAGE_NAME, message))));
  }

  private static BusinessException masterdataInactive(String field, String label) {
    String message = label + " không tồn tại hoặc đã ngừng sử dụng.";
    return new BusinessException(
        ErrorCode.LAB_PACKAGE_MASTERDATA_INACTIVE,
        message,
        Map.of(ERRORS, List.of(new FieldViolation(field, message))));
  }

  private static BusinessException fieldError(String field, String message) {
    return new BusinessException(
        ErrorCode.VALIDATION_ERROR,
        message,
        Map.of(ERRORS, List.of(new FieldViolation(field, message))));
  }
}
