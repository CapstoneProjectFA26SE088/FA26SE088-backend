package com.fa26se088.filmlink.entity.order;

import com.fa26se088.filmlink.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Ảnh bằng chứng cuộn phim hỏng khi tráng / xử lý (UC24, BR-26), bảng {@code
 * film_roll_damage_photos}.
 *
 * <p>Bảng có {@code id}, {@code created_at}, {@code updated_at} nên kế thừa {@link BaseEntity}.
 * Chỉ lưu {@code blob_key}; URL tải file được ký tạm thời qua {@code StorageClient.signedUrl}.
 */
@Getter
@Setter
@Entity
@Table(name = "film_roll_damage_photos")
public class FilmRollDamagePhoto extends BaseEntity {

  @Column(name = "film_roll_id", nullable = false)
  private Long filmRollId;

  @Column(name = "blob_key", nullable = false, length = 500)
  private String blobKey;
}
