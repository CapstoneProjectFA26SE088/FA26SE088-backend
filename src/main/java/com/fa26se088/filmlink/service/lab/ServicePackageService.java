package com.fa26se088.filmlink.service.lab;

import com.fa26se088.filmlink.dto.lab.request.SaveServicePackageRequest;
import com.fa26se088.filmlink.dto.lab.request.UpdateServicePackageStatusRequest;
import com.fa26se088.filmlink.dto.lab.response.ServicePackageResponse;
import java.util.List;

/**
 * UC13: Film Lab Owner quản lý bảng giá (danh sách Service Package) của lab mình. Mọi method nhận
 * {@code ownerAccountId} của tài khoản đang đăng nhập; lab tra theo chủ ({@link LabAccessService}).
 *
 * <p>Không có xoá gói: gói đã được đơn tham chiếu thì ngừng bán ({@code WITHDRAWN}). Sửa giá không
 * đổi đơn đã tạo vì đơn chụp lại giá vào {@code film_rolls}.
 */
public interface ServicePackageService {

  /** Toàn bộ gói của lab mình (mọi trạng thái), kèm mức scan. */
  List<ServicePackageResponse> getMyPackages(Long ownerAccountId);

  /** UC13, BR-16: tạo gói, bắt đầu ở {@code ON_SALE}. */
  ServicePackageResponse createMyPackage(Long ownerAccountId, SaveServicePackageRequest request);

  /** UC13, BR-16: sửa gói và thay toàn bộ danh sách mức scan. */
  ServicePackageResponse updateMyPackage(
      Long ownerAccountId, Long packageId, SaveServicePackageRequest request);

  /** UC13: mở bán / ngừng bán gói. */
  ServicePackageResponse updateMyPackageStatus(
      Long ownerAccountId, Long packageId, UpdateServicePackageStatusRequest request);
}
