package com.fa26se088.filmlink.entity.archive;

import com.fa26se088.filmlink.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Scan File (ERD #23), bảng {@code scan_files}: một ảnh Preview Scan của một cuộn. BR-08: file độ
 * phân giải gốc <b>không</b> lưu ở đây (chỉ có link trong {@code archive_rolls}).
 *
 * <p>Ràng buộc: {@code uq_scan_files_blob}, {@code ck_scan_files_size} (1–5 242 880 byte), {@code
 * ck_scan_files_dimensions} (1–2048 px mỗi cạnh), {@code ck_scan_files_frame} (0–100). Cột {@code
 * scan_job_id} đã bị xoá ở migration M-01 (bỏ mô hình gom mẻ). {@code is_current = false} dành cho
 * ảnh bị thay bởi re-scan (BR-27, Later).
 */
@Getter
@Setter
@Entity
@Table(name = "scan_files")
public class ScanFile extends BaseEntity {

  @Column(name = "film_roll_id", nullable = false, updatable = false)
  private Long filmRollId;

  @Column(name = "frame_number")
  private Short frameNumber;

  @Column(name = "blob_key", nullable = false, length = 500, updatable = false)
  private String blobKey;

  @Column(name = "size_bytes", nullable = false)
  private Integer sizeBytes;

  @Column(name = "width_px", nullable = false)
  private Short widthPx;

  @Column(name = "height_px", nullable = false)
  private Short heightPx;

  /** NFR-CAP-03: SHA-256 dạng hex chữ thường, 64 ký tự ({@code char(64)}). */
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @Column(name = "is_current", nullable = false)
  private boolean current = true;
}
