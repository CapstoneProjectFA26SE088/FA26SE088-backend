package com.fa26se088.filmlink.repository.lab;

import com.fa26se088.filmlink.entity.lab.ServicePackage;
import com.fa26se088.filmlink.entity.lab.enums.ServicePackageStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

  /** Bảng giá của một lab; dùng cột đầu của {@code uq_service_packages_lab_name}. */
  List<ServicePackage> findByFilmLabIdOrderByIdAsc(Long filmLabId);

  /** Gói {@code id} chỉ khi thuộc lab {@code filmLabId}; gói của lab khác coi như không tồn tại. */
  Optional<ServicePackage> findByIdAndFilmLabId(Long id, Long filmLabId);

  /** UC13: tên gói đã có trong lab chưa, không phân biệt hoa thường (khi tạo gói). */
  boolean existsByFilmLabIdAndPackageNameIgnoreCase(Long filmLabId, String packageName);

  /** UC13: như trên nhưng bỏ qua gói đang sửa {@code id}. */
  boolean existsByFilmLabIdAndPackageNameIgnoreCaseAndIdNot(
      Long filmLabId, String packageName, Long id);

  /**
   * BR-34, BR-57, BR-16: lab có ít nhất một gói {@code ON_SALE} mà Service Type, Film Process, Film
   * Format đều còn {@code ACTIVE} chưa. Cùng quy tắc với gói hiển thị trên trang lab công khai (cổng
   * bật nhận đơn và lọc lab công khai).
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT EXISTS (
              SELECT 1
              FROM service_packages sp
              JOIN service_types st ON st.id = sp.service_type_id
              JOIN film_processes fp ON fp.id = sp.film_process_id
              JOIN film_formats ff ON ff.id = sp.film_format_id
              WHERE sp.film_lab_id = :filmLabId
                AND sp.status = 'ON_SALE'
                AND st.status = 'ACTIVE'
                AND fp.status = 'ACTIVE'
                AND ff.status = 'ACTIVE')
          """)
  boolean existsDisplayablePackageOnSale(@Param("filmLabId") Long filmLabId);

  /** UC05: bảng giá công khai của lab: các gói đang bán, rẻ nhất trước. */
  List<ServicePackage> findByFilmLabIdAndStatusOrderByPriceVndAscIdAsc(
      Long filmLabId, ServicePackageStatus status);
}
