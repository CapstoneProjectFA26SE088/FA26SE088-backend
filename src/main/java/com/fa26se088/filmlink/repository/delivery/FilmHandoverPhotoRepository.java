package com.fa26se088.filmlink.repository.delivery;

import com.fa26se088.filmlink.entity.delivery.FilmHandoverPhoto;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ insert và đọc: bảng append-only (trigger chặn UPDATE / DELETE). */
public interface FilmHandoverPhotoRepository extends JpaRepository<FilmHandoverPhoto, Long> {

  /** Ảnh của nhiều lần bàn giao trong một query (tránh N+1), theo {@code display_order}. */
  List<FilmHandoverPhoto> findByFilmHandoverIdInOrderByFilmHandoverIdAscDisplayOrderAsc(
      Collection<Long> filmHandoverIds);
}
