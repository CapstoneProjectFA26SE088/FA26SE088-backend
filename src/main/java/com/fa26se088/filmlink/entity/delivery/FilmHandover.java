package com.fa26se088.filmlink.entity.delivery;

import com.fa26se088.filmlink.entity.delivery.enums.HandoverPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Film Handover (BR-25), bảng {@code film_handovers}: bằng chứng mỗi lần phim đổi tay.
 *
 * <p>Append-only: bảng không có {@code created_at}/{@code updated_at} (thời điểm là {@code
 * occurred_at}) nên <b>không</b> kế thừa {@code BaseEntity}. {@link Immutable}: Hibernate không
 * phát UPDATE; DB có trigger {@code film_handovers_append_only} chặn UPDATE / DELETE.
 *
 * <p>Ràng buộc: {@code ck_film_handovers_roll_count} (0–25), {@code ck_film_handovers_performer}
 * (có {@code performed_by_account_id} hoặc {@code performed_by_name}). Khoá ngoại giữ dạng {@code
 * Long}; domain delivery không tham chiếu entity của order. Chỉ tạo qua {@code FilmHandoverService}.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "film_handovers")
public class FilmHandover {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "service_order_id", nullable = false, updatable = false)
  private Long serviceOrderId;

  /** Có giá trị khi bàn giao gắn với một chuyến giao nhận (PICKUP, Parcel Shipping). */
  @Column(name = "shipment_id", updatable = false)
  private Long shipmentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "handover_point", nullable = false, length = 40, updatable = false)
  private HandoverPoint handoverPoint;

  @Column(name = "roll_count", nullable = false, updatable = false)
  private Short rollCount;

  @Column(name = "note", length = 500, updatable = false)
  private String note;

  @Column(name = "performed_by_account_id", updatable = false)
  private Long performedByAccountId;

  /** Tên người thực hiện không có tài khoản (ví dụ tài xế của Delivery Partner). */
  @Column(name = "performed_by_name", length = 100, updatable = false)
  private String performedByName;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;
}
