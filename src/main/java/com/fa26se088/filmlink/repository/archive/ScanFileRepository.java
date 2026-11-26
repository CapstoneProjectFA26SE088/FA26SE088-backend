package com.fa26se088.filmlink.repository.archive;

import com.fa26se088.filmlink.entity.archive.ScanFile;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanFileRepository extends JpaRepository<ScanFile, Long> {

  /** SCR-23: ảnh xem trước hiện hành của một cuộn theo số khung ({@code ix_scan_files_roll}). */
  @Query(
      "select s from ScanFile s where s.filmRollId = :filmRollId and s.current = true"
          + " order by s.frameNumber asc, s.id asc")
  List<ScanFile> findCurrentByFilmRollId(@Param("filmRollId") Long filmRollId);

  /** Số ảnh hiện hành của một cuộn (giới hạn 100 ảnh mỗi cuộn). */
  @Query("select count(s) from ScanFile s where s.filmRollId = :filmRollId and s.current = true")
  long countCurrentByFilmRollId(@Param("filmRollId") Long filmRollId);

  /** Số khung lớn nhất đã dùng của cuộn (kể cả ảnh không còn hiện hành); chưa có ảnh → null. */
  @Query("select max(s.frameNumber) from ScanFile s where s.filmRollId = :filmRollId")
  Short findMaxFrameNumber(@Param("filmRollId") Long filmRollId);

  /** Số ảnh hiện hành của nhiều cuộn trong một query; cuộn không có ảnh thì không có dòng. */
  @Query(
      "select s.filmRollId as filmRollId, count(s) as previewCount from ScanFile s"
          + " where s.filmRollId in :filmRollIds and s.current = true group by s.filmRollId")
  List<PreviewCountRow> countCurrentByFilmRollIds(
      @Param("filmRollIds") Collection<Long> filmRollIds);

  /**
   * Ảnh hiện hành có số khung nhỏ nhất của mỗi cuộn (ảnh bìa trong danh sách archive), một query
   * cho cả trang.
   */
  @Query(
      "select s from ScanFile s where s.filmRollId in :filmRollIds and s.current = true"
          + " and s.frameNumber = (select min(s2.frameNumber) from ScanFile s2"
          + " where s2.filmRollId = s.filmRollId and s2.current = true)"
          + " order by s.filmRollId asc, s.id asc")
  List<ScanFile> findFirstCurrentByFilmRollIds(@Param("filmRollIds") Collection<Long> filmRollIds);

  /** Một dòng của {@link #countCurrentByFilmRollIds}; tên getter khớp alias trong JPQL. */
  interface PreviewCountRow {
    Long getFilmRollId();

    Long getPreviewCount();
  }
}
