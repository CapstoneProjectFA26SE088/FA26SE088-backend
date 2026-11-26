package com.fa26se088.filmlink.repository.order;

import com.fa26se088.filmlink.entity.order.FilmRollDamagePhoto;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** UC24, BR-26: truy vấn ảnh bằng chứng cuộn phim hỏng ({@code film_roll_damage_photos}). */
public interface FilmRollDamagePhotoRepository extends JpaRepository<FilmRollDamagePhoto, Long> {

  /** Đọc gộp ảnh bằng chứng của nhiều cuộn trong một query (tránh N+1). */
  List<FilmRollDamagePhoto> findByFilmRollIdInOrderByFilmRollIdAscIdAsc(
      Collection<Long> filmRollIds);
}
