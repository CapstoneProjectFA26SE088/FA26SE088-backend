package com.fa26se088.filmlink.repository.lab;

import com.fa26se088.filmlink.entity.lab.ServicePackageScanOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicePackageScanOptionRepository
    extends JpaRepository<ServicePackageScanOption, Long> {

  List<ServicePackageScanOption> findByServicePackageIdOrderByPriceVndAscIdAsc(
      Long servicePackageId);

  /** Mức scan của nhiều gói một lần (bảng giá của lab), tránh N+1. */
  List<ServicePackageScanOption> findByServicePackageIdInOrderByPriceVndAscIdAsc(
      Collection<Long> servicePackageIds);

  /**
   * UC13 (follow-up FA26-80): nhãn của các mức scan trong {@code ids} đang được {@code film_rolls}
   * tham chiếu ({@code fk_film_rolls_scan_option}); xoá chúng sẽ vi phạm khoá ngoại. Native query
   * vì bảng {@code film_rolls} thuộc domain order (tầng trên), lab không gọi lên service order.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT DISTINCT so.resolution_label
          FROM service_package_scan_options so
          WHERE so.id IN (:ids)
            AND EXISTS (SELECT 1 FROM film_rolls fr WHERE fr.service_package_scan_option_id = so.id)
          ORDER BY so.resolution_label
          """)
  List<String> findLabelsReferencedByFilmRolls(@Param("ids") Collection<Long> ids);
}
