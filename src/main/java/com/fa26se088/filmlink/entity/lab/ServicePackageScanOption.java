package com.fa26se088.filmlink.entity.lab;

import com.fa26se088.filmlink.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Mức độ phân giải scan của một Service Package, mỗi mức có giá riêng (UC13), bảng {@code
 * service_package_scan_options}.
 *
 * <p>Nhãn duy nhất trong gói ({@code uq_service_package_scan_options_label}); giá {@code > 0}
 * ({@code ck_service_package_scan_options_price}). {@code film_rolls.service_package_scan_option_id}
 * tham chiếu tới bảng này.
 */
@Getter
@Setter
@Entity
@Table(name = "service_package_scan_options")
public class ServicePackageScanOption extends BaseEntity {

  @Column(name = "service_package_id", nullable = false)
  private Long servicePackageId;

  @Column(name = "resolution_label", nullable = false, length = 50)
  private String resolutionLabel;

  @Column(name = "price_vnd", nullable = false)
  private Long priceVnd;
}
