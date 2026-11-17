package com.fa26se088.filmlink.entity.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Ảnh bằng chứng của một Film Handover (BR-25; UC23 1–10 ảnh), bảng {@code film_handover_photos}.
 *
 * <p>Append-only: bảng chỉ có {@code created_at}, không có {@code updated_at}, nên <b>không</b> kế
 * thừa {@code BaseEntity} (BaseEntity map cả {@code updated_at}). {@code created_at} map trực tiếp
 * ở đây, service gán từ {@code Clock}. DB có trigger {@code film_handover_photos_append_only}.
 *
 * <p>Ràng buộc: {@code uq_film_handover_photos_order} ({@code film_handover_id}, {@code
 * display_order}), {@code ck_film_handover_photos_order} (1–10). DB chỉ lưu blobKey, không lưu URL.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "film_handover_photos")
public class FilmHandoverPhoto {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "film_handover_id", nullable = false, updatable = false)
  private Long filmHandoverId;

  @Column(name = "blob_key", nullable = false, length = 500, updatable = false)
  private String blobKey;

  @Column(name = "display_order", nullable = false, updatable = false)
  private Short displayOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
