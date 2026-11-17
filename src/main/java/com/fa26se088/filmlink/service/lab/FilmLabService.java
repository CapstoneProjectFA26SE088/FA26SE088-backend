package com.fa26se088.filmlink.service.lab;

import com.fa26se088.filmlink.dto.lab.request.UpdateFilmLabAcceptingOrdersRequest;
import com.fa26se088.filmlink.dto.lab.request.UpdateFilmLabOpeningHoursRequest;
import com.fa26se088.filmlink.dto.lab.request.UpdateFilmLabProfileRequest;
import com.fa26se088.filmlink.dto.lab.response.FilmLabImageResponse;
import com.fa26se088.filmlink.dto.lab.response.FilmLabProfileResponse;
import com.fa26se088.filmlink.dto.lab.response.OrderingFilmLabResponse;
import com.fa26se088.filmlink.dto.lab.response.ServicePackageResponse;
import java.util.Collection;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * UC12: Film Lab Owner quản lý hồ sơ lab của chính mình. Mọi method nhận {@code ownerAccountId}
 * của tài khoản đang đăng nhập; lab được tra theo chủ ({@link LabAccessService}), không nhận
 * {@code labId} từ client.
 *
 * <p>Lab được tạo khi đơn đăng ký được duyệt: {@code FilmLabServiceImpl} lắng nghe {@code
 * LabRegistrationApprovedEvent} (BR-03).
 */
public interface FilmLabService {

  /** Hồ sơ đầy đủ: thông tin lab, giờ mở cửa, ảnh mẫu (kèm link ký có hạn), trạng thái nhận đơn. */
  FilmLabProfileResponse getMyProfile(Long ownerAccountId);

  /** Sửa thông tin hồ sơ (không gồm email / số điện thoại liên hệ). */
  FilmLabProfileResponse updateMyProfile(Long ownerAccountId, UpdateFilmLabProfileRequest request);

  /** Thay toàn bộ giờ mở cửa 7 ngày. */
  FilmLabProfileResponse updateMyOpeningHours(
      Long ownerAccountId, UpdateFilmLabOpeningHoursRequest request);

  /** Thêm một ảnh mẫu (tối đa 20 ảnh mỗi lab). */
  FilmLabImageResponse uploadMyImage(Long ownerAccountId, MultipartFile file);

  /** Xoá một ảnh mẫu của lab mình. */
  void deleteMyImage(Long ownerAccountId, Long imageId);

  /** Bật/tắt nhận đơn (BR-57). */
  FilmLabProfileResponse updateMyAcceptingOrders(
      Long ownerAccountId, UpdateFilmLabAcceptingOrdersRequest request);

  /**
   * BR-57, BR-34: lab đủ điều kiện nhận đơn, tức có máy PROCESSOR và SCANNER đang ACTIVE ({@link
   * MachineReadiness}) <b>và</b> ít nhất một gói {@code ON_SALE} có Service Type, Film Process,
   * Film Format đều còn ACTIVE (BR-16). Không xét cờ {@code is_accepting_orders}. Chỗ dùng chung
   * cho cổng bật nhận đơn và cho việc lọc lab công khai.
   */
  boolean isReadyToAcceptOrders(Long filmLabId);

  /**
   * UC20: lab cho domain order khi báo giá / tạo đơn (domain order không inject repository của lab,
   * R4).
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code LAB_NOT_FOUND} (404) khi lab
   *     không tồn tại
   */
  OrderingFilmLabResponse getLabForOrdering(Long filmLabId);

  /**
   * UC20: các gói trong {@code packageIds} <b>thuộc lab</b> {@code filmLabId} (mọi trạng thái), kèm
   * mức scan. Id không tồn tại hoặc thuộc lab khác thì không có trong kết quả; bên gọi tự kiểm
   * trạng thái và master data.
   */
  List<ServicePackageResponse> getPackagesForOrdering(Long filmLabId, Collection<Long> packageIds);
}
