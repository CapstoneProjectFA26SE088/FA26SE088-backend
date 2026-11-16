package com.fa26se088.filmlink.entity.lab.enums;

/**
 * Trạng thái Service Package ({@code ck_service_packages_status}). UC13: gói đã được đơn tham chiếu
 * thì không xoá mà chuyển {@link #WITHDRAWN}; gói {@code WITHDRAWN} không nhận đơn mới.
 */
public enum ServicePackageStatus {
  ON_SALE,
  WITHDRAWN
}
