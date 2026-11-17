package com.fa26se088.filmlink.entity.archive;

import com.fa26se088.filmlink.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Mục Digital Film Archive của một cuộn (UC26, UC28), bảng {@code archive_rolls}: link file độ phân
 * giải gốc (lưu theo từng cuộn, quyết định 14/10/2026) và metadata Photographer ghi thêm.
 *
 * <p>Domain archive chỉ biết {@code film_roll_id}; quyền sở hữu và trạng thái đơn do domain order
 * kiểm trước khi gọi. Ràng buộc: {@code uq_archive_rolls_roll} (một dòng mỗi cuộn), {@code
 * ck_archive_rolls_link} ({@code ^https?://}). {@code full_resolution_link}, {@code
 * link_verified_at} NOT NULL nên dòng chỉ được tạo khi lab gửi link đã kiểm; {@code delivered_at}
 * gán lúc tạo rồi gán lại lúc giao scan.
 */
@Getter
@Setter
@Entity
@Table(name = "archive_rolls")
public class ArchiveRoll extends BaseEntity {

  @Column(name = "film_roll_id", nullable = false, updatable = false)
  private Long filmRollId;

  @Column(name = "full_resolution_link", nullable = false, length = 1000)
  private String fullResolutionLink;

  @Column(name = "link_verified_at", nullable = false)
  private Instant linkVerifiedAt;

  @Column(name = "frame_count_discrepancy_note", length = 500)
  private String frameCountDiscrepancyNote;

  @Column(name = "camera", length = 100)
  private String camera;

  @Column(name = "lens", length = 100)
  private String lens;

  @Column(name = "shot_on")
  private LocalDate shotOn;

  @Column(name = "location", length = 200)
  private String location;

  @Column(name = "note", length = 1000)
  private String note;

  @Column(name = "delivered_at", nullable = false)
  private Instant deliveredAt;
}
