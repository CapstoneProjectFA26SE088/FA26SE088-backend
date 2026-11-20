package com.fa26se088.filmlink.repository.lab;

import com.fa26se088.filmlink.entity.lab.FilmLab;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FilmLabRepository extends JpaRepository<FilmLab, Long> {

  /** Mỗi Film Lab Owner có tối đa một lab ({@code uq_film_labs_owner}). */
  Optional<FilmLab> findByOwnerAccountId(Long ownerAccountId);

  /** Một đơn đăng ký được duyệt tạo tối đa một lab ({@code uq_film_labs_application}). */
  boolean existsByLabRegistrationApplicationId(Long labRegistrationApplicationId);

  /**
   * {@code SELECT ... FOR UPDATE} trên dòng lab: tuần tự hoá các thao tác sửa ảnh / giờ mở cửa của
   * cùng một lab (đếm giới hạn 20 ảnh, chọn {@code display_order} trống, upsert theo ngày).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select l from FilmLab l where l.id = :id")
  Optional<FilmLab> findByIdForUpdate(@Param("id") Long id);

  /**
   * UC17, BR-34: ứng viên cho tìm lab công khai. Lab đang bật nhận đơn và có ít nhất một gói {@code
   * ON_SALE} có Service Type, Film Process, Film Format đều còn {@code ACTIVE} (BR-16) và khớp
   * <b>tất cả</b> bộ lọc đã gửi (tham số {@code null} = không lọc). {@code minPriceVnd} là giá thấp
   * nhất trong các gói đó (gói WITHDRAWN hoặc có master data đã ngừng dùng không tính).
   *
   * <p>Khoảng cách theo công thức Haversine (bán kính Trái Đất 6371 km) trên {@code latitude} /
   * {@code longitude}, không dùng PostGIS; {@code null} khi không gửi toạ độ hoặc lab chưa có toạ
   * độ. Có {@code radiusKm} thì lab không có toạ độ bị loại ({@code NULL <= x} là không đúng).
   *
   * <p>Không phân trang trong SQL: service còn lọc điều kiện máy (BR-57) qua {@code
   * FilmLabService.isReadyToAcceptOrders} rồi mới cắt trang. {@code sortByDistance}: gần trước,
   * rồi rẻ trước; ngược lại rẻ trước, rồi gần trước; cuối cùng theo {@code id} cho ổn định.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT r.id AS id, r.lab_name AS labName, r.address_line AS addressLine,
                 r.district AS district, r.province AS province,
                 r.cover_blob_key AS coverBlobKey, r.min_price_vnd AS minPriceVnd,
                 r.distance_km AS distanceKm
          FROM (
              SELECT l.id, l.lab_name, l.address_line, l.district, l.province,
                     (SELECT i.blob_key FROM film_lab_images i
                       WHERE i.film_lab_id = l.id
                       ORDER BY i.display_order LIMIT 1) AS cover_blob_key,
                     p.min_price_vnd,
                     CASE
                       WHEN CAST(:lat AS double precision) IS NULL OR l.latitude IS NULL THEN NULL
                       ELSE 2 * 6371.0 * asin(sqrt(least(1.0,
                              power(sin(radians(l.latitude - CAST(:lat AS double precision)) / 2), 2)
                              + cos(radians(CAST(:lat AS double precision)))
                                * cos(radians(l.latitude))
                                * power(sin(radians(l.longitude - CAST(:lng AS double precision)) / 2), 2))))
                     END AS distance_km
              FROM film_labs l
              JOIN (
                  SELECT sp.film_lab_id, min(sp.price_vnd) AS min_price_vnd
                  FROM service_packages sp
                  JOIN service_types st ON st.id = sp.service_type_id
                  JOIN film_processes fp ON fp.id = sp.film_process_id
                  JOIN film_formats ff ON ff.id = sp.film_format_id
                  WHERE sp.status = 'ON_SALE'
                    AND st.status = 'ACTIVE'
                    AND fp.status = 'ACTIVE'
                    AND ff.status = 'ACTIVE'
                    AND (CAST(:filmProcessId AS bigint) IS NULL
                         OR sp.film_process_id = CAST(:filmProcessId AS bigint))
                    AND (CAST(:filmFormatId AS bigint) IS NULL
                         OR sp.film_format_id = CAST(:filmFormatId AS bigint))
                    AND (CAST(:serviceTypeId AS bigint) IS NULL
                         OR sp.service_type_id = CAST(:serviceTypeId AS bigint))
                  GROUP BY sp.film_lab_id
              ) p ON p.film_lab_id = l.id
              WHERE l.is_accepting_orders = true
          ) r
          WHERE CAST(:radiusKm AS double precision) IS NULL
             OR r.distance_km <= CAST(:radiusKm AS double precision)
          ORDER BY CASE WHEN :sortByDistance THEN r.distance_km END ASC NULLS LAST,
                   r.min_price_vnd ASC,
                   r.distance_km ASC NULLS LAST,
                   r.id ASC
          """)
  List<PublicLabSearchRow> searchPublicLabCandidates(
      @Param("filmProcessId") Long filmProcessId,
      @Param("filmFormatId") Long filmFormatId,
      @Param("serviceTypeId") Long serviceTypeId,
      @Param("lat") Double lat,
      @Param("lng") Double lng,
      @Param("radiusKm") Double radiusKm,
      @Param("sortByDistance") boolean sortByDistance);

  /** Một dòng kết quả của {@link #searchPublicLabCandidates}; tên getter khớp alias trong SQL. */
  interface PublicLabSearchRow {
    Long getId();

    String getLabName();

    String getAddressLine();

    String getDistrict();

    String getProvince();

    String getCoverBlobKey();

    Long getMinPriceVnd();

    Double getDistanceKm();
  }
}
