package com.fa26se088.filmlink.repository.lab;

import com.fa26se088.filmlink.entity.lab.ServicePackageScanOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicePackageScanOptionRepository
    extends JpaRepository<ServicePackageScanOption, Long> {

  List<ServicePackageScanOption> findByServicePackageIdOrderByPriceVndAscIdAsc(
      Long servicePackageId);

  /** Mức scan của nhiều gói một lần (bảng giá của lab), tránh N+1. */
  List<ServicePackageScanOption> findByServicePackageIdInOrderByPriceVndAscIdAsc(
      Collection<Long> servicePackageIds);
}
