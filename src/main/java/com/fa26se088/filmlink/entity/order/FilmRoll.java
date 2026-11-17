package com.fa26se088.filmlink.entity.order;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.order.enums.FilmRollStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Film Roll (ERD #18, UC20), bảng {@code film_rolls}. Đơn vị xếp lịch và đơn vị lưu trữ.
 *
 * <p>Giá được <b>chụp</b> vào cuộn lúc tạo đơn ({@code unit_price_vnd}, {@code
 * scan_option_price_vnd}, {@code push_pull_surcharge_vnd}, {@code express_surcharge_vnd}): lab sửa
 * bảng giá sau đó không đổi đơn (UC13). Ràng buộc: {@code uq_film_rolls_sequence}, {@code
 * ck_film_rolls_stock} (đúng một trong {@code film_stock_id} / {@code film_stock_name_text}),
 * {@code ck_film_rolls_exposures} (1–40), {@code ck_film_rolls_push_pull} (-3..3), {@code
 * ck_film_rolls_prices} ({@code unit_price_vnd > 0}, các phụ phí ≥ 0). Kiểu {@code smallint} map
 * sang {@code Short}.
 */
@Getter
@Setter
@Entity
@Table(name = "film_rolls")
public class FilmRoll extends BaseEntity {

  @Column(name = "service_order_id", nullable = false)
  private Long serviceOrderId;

  /** 1–20 khi khai báo (UC20); UC23 có thể thêm tới 25. */
  @Column(name = "roll_sequence", nullable = false)
  private Short rollSequence;

  @Column(name = "film_stock_id")
  private Long filmStockId;

  @Column(name = "film_stock_name_text", length = 100)
  private String filmStockNameText;

  @Column(name = "film_format_id", nullable = false)
  private Long filmFormatId;

  /** Chép từ gói đã chọn; xếp lịch (BR-58, BR-59) nhóm cuộn theo cột này. */
  @Column(name = "film_process_id", nullable = false)
  private Long filmProcessId;

  @Column(name = "service_package_id", nullable = false)
  private Long servicePackageId;

  @Column(name = "service_package_scan_option_id")
  private Long servicePackageScanOptionId;

  @Column(name = "exposure_count", nullable = false)
  private Short exposureCount;

  @Column(name = "shooting_iso")
  private Short shootingIso;

  @Column(name = "push_pull_stops", nullable = false)
  private Short pushPullStops = 0;

  @Column(name = "unit_price_vnd", nullable = false)
  private Long unitPriceVnd;

  @Column(name = "scan_option_price_vnd", nullable = false)
  private Long scanOptionPriceVnd = 0L;

  @Column(name = "push_pull_surcharge_vnd", nullable = false)
  private Long pushPullSurchargeVnd = 0L;

  @Column(name = "express_surcharge_vnd", nullable = false)
  private Long expressSurchargeVnd = 0L;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private FilmRollStatus status = FilmRollStatus.DECLARED;

  @Column(name = "is_added_at_receipt", nullable = false)
  private boolean addedAtReceipt = false;

  /** Bắt buộc khi và chỉ khi {@code status = DAMAGED} ({@code ck_film_rolls_damage}). */
  @Column(name = "damage_note", length = 500)
  private String damageNote;
}
