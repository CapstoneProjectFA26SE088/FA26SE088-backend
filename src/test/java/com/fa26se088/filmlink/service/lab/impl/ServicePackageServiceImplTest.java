package com.fa26se088.filmlink.service.lab.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.fa26se088.filmlink.entity.lab.enums.ServicePackageStatus;
import com.fa26se088.filmlink.entity.masterdata.enums.CatalogStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.lab.ServicePackageMapper;
import com.fa26se088.filmlink.repository.lab.FilmLabRepository;
import com.fa26se088.filmlink.repository.lab.ServicePackageRepository;
import com.fa26se088.filmlink.repository.lab.ServicePackageScanOptionRepository;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.masterdata.MasterdataService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ServicePackageServiceImplTest {

  private static final Long OWNER_ID = 7L;
  private static final Long LAB_ID = 100L;
  private static final Long PACKAGE_ID = 500L;
  private static final Long SERVICE_TYPE_ID = 1L;
  private static final Long PROCESS_ID = 2L;
  private static final Long FORMAT_ID = 3L;

  @Mock private ServicePackageRepository servicePackageRepository;
  @Mock private ServicePackageScanOptionRepository scanOptionRepository;
  @Mock private FilmLabRepository filmLabRepository;
  @Mock private LabAccessService labAccessService;
  @Mock private MasterdataService masterdataService;
  @Mock private AuditLogService auditLogService;

  private ServicePackageServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ServicePackageServiceImpl(
            servicePackageRepository,
            scanOptionRepository,
            filmLabRepository,
            labAccessService,
            masterdataService,
            auditLogService,
            new ServicePackageMapper());
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", LAB_ID);
    lab.setOwnerAccountId(OWNER_ID);
    lenient().when(labAccessService.requireOwnedLab(OWNER_ID)).thenReturn(lab);
    lenient().when(filmLabRepository.findByIdForUpdate(LAB_ID)).thenReturn(Optional.of(lab));
    lenient()
        .when(masterdataService.getServiceTypes())
        .thenReturn(
            List.of(
                new ServiceTypeResponse(
                    SERVICE_TYPE_ID, "DEVELOPING", "Tráng phim", null, CatalogStatus.ACTIVE)));
    lenient()
        .when(masterdataService.getFilmProcesses())
        .thenReturn(
            List.of(
                new FilmProcessResponse(PROCESS_ID, "C41", "C-41", null, CatalogStatus.ACTIVE)));
    lenient()
        .when(masterdataService.getFilmFormats())
        .thenReturn(
            List.of(new FilmFormatResponse(FORMAT_ID, "135", "35mm", null, CatalogStatus.ACTIVE)));
  }

  // --- Tạo gói (UC13, BR-16) ---

  @Test
  void create_valid_startsOnSaleAndWritesAudit() {
    when(servicePackageRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              ServicePackage saved = inv.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", PACKAGE_ID);
              return saved;
            });
    when(scanOptionRepository.findByServicePackageIdOrderByPriceVndAscIdAsc(PACKAGE_ID))
        .thenReturn(List.of());

    ServicePackageResponse response =
        service.createMyPackage(OWNER_ID, request("  C-41 35mm  ", false, null, List.of()));

    assertThat(response.packageName()).isEqualTo("C-41 35mm");
    assertThat(response.status()).isEqualTo(ServicePackageStatus.ON_SALE);
    ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(audit.capture());
    assertThat(audit.getValue().action()).isEqualTo(AuditAction.LAB_SERVICE_PACKAGE_CREATED);
    assertThat(audit.getValue().targetType()).isEqualTo(AuditTargetType.SERVICE_PACKAGE);
    assertThat(audit.getValue().targetId()).isEqualTo(PACKAGE_ID);
  }

  @Test
  void create_duplicateNameIgnoringCase_throwsDuplicate() {
    when(servicePackageRepository.existsByFilmLabIdAndPackageNameIgnoreCase(LAB_ID, "c-41 35MM"))
        .thenReturn(true);

    assertThatThrownBy(
            () -> service.createMyPackage(OWNER_ID, request("c-41 35MM", false, null, List.of())))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LAB_PACKAGE_NAME_DUPLICATE);
    verify(servicePackageRepository, never()).saveAndFlush(any());
  }

  /** UC13: tên chỉ có 1 ký tự sau khi bỏ khoảng trắng (Bean Validation đếm cả khoảng trắng). */
  @Test
  void create_nameTooShortAfterStrip_throwsValidation() {
    assertValidationError(request("  A  ", false, null, List.of()), "packageName");
  }

  @Test
  void create_pushPullWithoutSurcharge_throwsValidation() {
    assertValidationError(
        request("Gói Push", true, null, List.of()), "pushPullSurchargePerStopVnd");
  }

  @Test
  void create_surchargeWithoutPushPull_throwsValidation() {
    assertValidationError(
        request("Gói Push", false, 10_000L, List.of()), "pushPullSurchargePerStopVnd");
  }

  @Test
  void create_duplicateScanLabelIgnoringCase_throwsValidation() {
    assertValidationError(
        request(
            "Gói Scan",
            false,
            null,
            List.of(
                new ServicePackageScanOptionRequest("S", 20_000L),
                new ServicePackageScanOptionRequest(" s ", 30_000L))),
        "scanOptions");
  }

  /** BR-16: id không nằm trong danh sách ACTIVE (DISCONTINUED hoặc không tồn tại). */
  @Test
  void create_inactiveFilmFormat_throwsMasterdataInactive() {
    SaveServicePackageRequest request =
        new SaveServicePackageRequest(
            "Gói 127", SERVICE_TYPE_ID, PROCESS_ID, 99L, 60_000L, (short) 5, false, null, null);

    assertThatThrownBy(() -> service.createMyPackage(OWNER_ID, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LAB_PACKAGE_MASTERDATA_INACTIVE);
  }

  // --- Sửa / đổi trạng thái ---

  /** CR-14: gói của lab khác coi như không tồn tại (theo mẫu ảnh của lab). */
  @Test
  void update_packageOfAnotherLab_throwsNotFound() {
    when(servicePackageRepository.findByIdAndFilmLabId(PACKAGE_ID, LAB_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.updateMyPackage(
                    OWNER_ID, PACKAGE_ID, request("Gói A", false, null, List.of())))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LAB_PACKAGE_NOT_FOUND);
  }

  @Test
  void update_nameOfAnotherPackage_throwsDuplicate() {
    when(servicePackageRepository.findByIdAndFilmLabId(PACKAGE_ID, LAB_ID))
        .thenReturn(Optional.of(existingPackage(ServicePackageStatus.ON_SALE)));
    when(servicePackageRepository.existsByFilmLabIdAndPackageNameIgnoreCaseAndIdNot(
            LAB_ID, "Gói B", PACKAGE_ID))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                service.updateMyPackage(
                    OWNER_ID, PACKAGE_ID, request("Gói B", false, null, List.of())))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LAB_PACKAGE_NAME_DUPLICATE);
  }

  @Test
  void updateStatus_withdraw_writesAuditBeforeAfter() {
    ServicePackage servicePackage = existingPackage(ServicePackageStatus.ON_SALE);
    when(servicePackageRepository.findByIdAndFilmLabId(PACKAGE_ID, LAB_ID))
        .thenReturn(Optional.of(servicePackage));

    ServicePackageResponse response =
        service.updateMyPackageStatus(
            OWNER_ID,
            PACKAGE_ID,
            new UpdateServicePackageStatusRequest(ServicePackageStatus.WITHDRAWN));

    assertThat(response.status()).isEqualTo(ServicePackageStatus.WITHDRAWN);
    ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLogService).recordSuccess(audit.capture());
    assertThat(audit.getValue().action())
        .isEqualTo(AuditAction.LAB_SERVICE_PACKAGE_STATUS_CHANGED);
    assertThat(audit.getValue().before()).isEqualTo(Map.of("status", "ON_SALE"));
    assertThat(audit.getValue().after()).isEqualTo(Map.of("status", "WITHDRAWN"));
  }

  @Test
  void updateStatus_sameStatus_noAudit() {
    when(servicePackageRepository.findByIdAndFilmLabId(PACKAGE_ID, LAB_ID))
        .thenReturn(Optional.of(existingPackage(ServicePackageStatus.ON_SALE)));

    service.updateMyPackageStatus(
        OWNER_ID, PACKAGE_ID, new UpdateServicePackageStatusRequest(ServicePackageStatus.ON_SALE));

    verify(auditLogService, never()).recordSuccess(any());
  }

  /** BR-16: mở bán lại gói có master data đã ngừng sử dụng thì bị chặn. */
  @Test
  void updateStatus_republishWithDiscontinuedProcess_throwsMasterdataInactive() {
    ServicePackage servicePackage = existingPackage(ServicePackageStatus.WITHDRAWN);
    servicePackage.setFilmProcessId(42L);
    when(servicePackageRepository.findByIdAndFilmLabId(PACKAGE_ID, LAB_ID))
        .thenReturn(Optional.of(servicePackage));

    assertThatThrownBy(
            () ->
                service.updateMyPackageStatus(
                    OWNER_ID,
                    PACKAGE_ID,
                    new UpdateServicePackageStatusRequest(ServicePackageStatus.ON_SALE)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LAB_PACKAGE_MASTERDATA_INACTIVE);
    assertThat(servicePackage.getStatus()).isEqualTo(ServicePackageStatus.WITHDRAWN);
  }

  // --- helpers ---

  private void assertValidationError(SaveServicePackageRequest request, String field) {
    assertThatThrownBy(() -> service.createMyPackage(OWNER_ID, request))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException business = (BusinessException) ex;
              assertThat(business.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(business.getProperties().get("errors").toString()).contains(field);
            });
    verify(servicePackageRepository, never()).saveAndFlush(any());
    verify(servicePackageRepository, never())
        .existsByFilmLabIdAndPackageNameIgnoreCase(anyLong(), anyString());
  }

  private static SaveServicePackageRequest request(
      String name,
      boolean pushPull,
      Long surcharge,
      List<ServicePackageScanOptionRequest> scanOptions) {
    return new SaveServicePackageRequest(
        name,
        SERVICE_TYPE_ID,
        PROCESS_ID,
        FORMAT_ID,
        60_000L,
        (short) 5,
        pushPull,
        surcharge,
        scanOptions);
  }

  private static ServicePackage existingPackage(ServicePackageStatus status) {
    ServicePackage servicePackage = new ServicePackage();
    ReflectionTestUtils.setField(servicePackage, "id", PACKAGE_ID);
    servicePackage.setFilmLabId(LAB_ID);
    servicePackage.setPackageName("Gói A");
    servicePackage.setServiceTypeId(SERVICE_TYPE_ID);
    servicePackage.setFilmProcessId(PROCESS_ID);
    servicePackage.setFilmFormatId(FORMAT_ID);
    servicePackage.setPriceVnd(60_000L);
    servicePackage.setTurnaroundDays((short) 5);
    servicePackage.setStatus(status);
    return servicePackage;
  }
}
