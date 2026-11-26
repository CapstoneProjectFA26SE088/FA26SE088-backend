package com.fa26se088.filmlink.repository.archive;

import com.fa26se088.filmlink.entity.archive.ArchiveRoll;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveRollRepository extends JpaRepository<ArchiveRoll, Long> {

  /** Dòng archive của một cuộn ({@code uq_archive_rolls_roll}). */
  Optional<ArchiveRoll> findByFilmRollId(Long filmRollId);

  /** Dòng archive của nhiều cuộn trong một query. */
  List<ArchiveRoll> findByFilmRollIdIn(Collection<Long> filmRollIds);

  /** UC28: một trang archive trong tập cuộn domain order đã lọc theo quyền. */
  Page<ArchiveRoll> findByFilmRollIdIn(Collection<Long> filmRollIds, Pageable pageable);
}
