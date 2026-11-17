package com.fa26se088.filmlink.repository.delivery;

import com.fa26se088.filmlink.entity.delivery.FilmHandover;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ insert và đọc: bảng append-only (trigger chặn UPDATE / DELETE). */
public interface FilmHandoverRepository extends JpaRepository<FilmHandover, Long> {

  /** Mọi lần bàn giao của một đơn theo thời gian ({@code ix_film_handovers_order}). */
  List<FilmHandover> findByServiceOrderIdOrderByOccurredAtAscIdAsc(Long serviceOrderId);
}
