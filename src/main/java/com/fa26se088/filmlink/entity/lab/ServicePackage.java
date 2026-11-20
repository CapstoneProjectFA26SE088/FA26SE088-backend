package com.fa26se088.filmlink.entity.lab;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.lab.enums.ServicePackageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Service Package (ERD, UC13), bảng {@code service_packages}. Bảng giá của lab là danh sách các gói.
 *
 * <p>Tên gói duy nhất trong lab, không phân biệt hoa thường ({@code uq_service_packages_lab_name}).
 * Phụ phí Express không nằm ở gói mà ở {@code film_labs.express_surcharge_percent} (M-02). Khoá ngoại
 * tới master data giữ dạng {@code Long}, như {@link FilmLab}.
 */
@Getter
@Setter
@Entity
@Table(name = "service_packages")
public class ServicePackage extends BaseEntity {

  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  @Column(name = "package_name", nullable = false, length = 150)
  private String packageName;

  @Column(name = "service_type_id", nullable = false)
  private Long serviceTypeId;

  @Column(name = "film_process_id", nullable = false)
  private Long filmProcessId;

  @Column(name = "film_format_id", nullable = false)
  private Long filmFormatId;

  /** UC13: 1–50.000.000 VND mỗi cuộn ({@code ck_service_packages_price}). */
  @Column(name = "price_vnd", nullable = false)
  private Long priceVnd;

  /** UC13: 1–60 ngày ({@code ck_service_packages_turnaround}). */
  @Column(name = "turnaround_days", nullable = false)
  private Short turnaroundDays;

  /** Có nhận push/pull thì phải có phụ phí mỗi stop ({@code ck_service_packages_push_pull}). */
  @Column(name = "is_push_pull_available", nullable = false)
  private boolean pushPullAvailable = false;

  @Column(name = "push_pull_surcharge_per_stop_vnd")
  private Long pushPullSurchargePerStopVnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ServicePackageStatus status = ServicePackageStatus.ON_SALE;
}
