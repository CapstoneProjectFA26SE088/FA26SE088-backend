package com.fa26se088.filmlink.repository.lab;

import com.fa26se088.filmlink.entity.lab.ServicePackage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
