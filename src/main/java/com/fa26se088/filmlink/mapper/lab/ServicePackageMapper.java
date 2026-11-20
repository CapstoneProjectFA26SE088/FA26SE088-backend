package com.fa26se088.filmlink.mapper.lab;

import com.fa26se088.filmlink.dto.lab.response.CatalogRefResponse;
import com.fa26se088.filmlink.dto.lab.response.PublicServicePackageResponse;
import com.fa26se088.filmlink.dto.lab.response.ServicePackageResponse;
import com.fa26se088.filmlink.dto.lab.response.ServicePackageScanOptionResponse;
import com.fa26se088.filmlink.entity.lab.ServicePackage;
import com.fa26se088.filmlink.entity.lab.ServicePackageScanOption;
import java.util.List;
import org.springframework.stereotype.Component;

/** Chuyển Service Package và mức scan sang DTO. Viết tay, không dùng MapStruct. */
@Component
public class ServicePackageMapper {

  public ServicePackageResponse toResponse(
      ServicePackage servicePackage, List<ServicePackageScanOption> scanOptions) {
    return new ServicePackageResponse(
        servicePackage.getId(),
        servicePackage.getPackageName(),
        servicePackage.getServiceTypeId(),
        servicePackage.getFilmProcessId(),
        servicePackage.getFilmFormatId(),
        servicePackage.getPriceVnd(),
        servicePackage.getTurnaroundDays(),
        servicePackage.isPushPullAvailable(),
        servicePackage.getPushPullSurchargePerStopVnd(),
        servicePackage.getStatus(),
        scanOptions.stream().map(this::toScanOptionResponse).toList(),
        servicePackage.getCreatedAt(),
        servicePackage.getUpdatedAt());
  }

  public ServicePackageScanOptionResponse toScanOptionResponse(ServicePackageScanOption option) {
    return new ServicePackageScanOptionResponse(
        option.getId(), option.getResolutionLabel(), option.getPriceVnd());
  }

  /**
   * UC05: gói trên trang lab công khai. Tham chiếu master data do service dựng sẵn (tra tên qua
   * {@code MasterdataService}); mapper không gọi service.
   */
  public PublicServicePackageResponse toPublicResponse(
      ServicePackage servicePackage,
      List<ServicePackageScanOption> scanOptions,
      CatalogRefResponse serviceType,
      CatalogRefResponse filmProcess,
      CatalogRefResponse filmFormat) {
    return new PublicServicePackageResponse(
        servicePackage.getId(),
        servicePackage.getPackageName(),
        serviceType,
        filmProcess,
        filmFormat,
        servicePackage.getPriceVnd(),
        servicePackage.getTurnaroundDays(),
        servicePackage.isPushPullAvailable(),
        servicePackage.getPushPullSurchargePerStopVnd(),
        scanOptions.stream().map(this::toScanOptionResponse).toList());
  }
}
