package com.fa26se088.filmlink.repository.order;

import com.fa26se088.filmlink.entity.order.FilmRoll;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilmRollRepository extends JpaRepository<FilmRoll, Long> {

  /** Các cuộn của một đơn theo thứ tự khai báo ({@code uq_film_rolls_sequence}). */
  List<FilmRoll> findByServiceOrderIdOrderByRollSequenceAsc(Long serviceOrderId);
}
