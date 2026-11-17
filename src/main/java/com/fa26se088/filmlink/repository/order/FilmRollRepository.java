package com.fa26se088.filmlink.repository.order;

import com.fa26se088.filmlink.entity.order.FilmRoll;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FilmRollRepository extends JpaRepository<FilmRoll, Long> {

  /** Các cuộn của một đơn theo thứ tự khai báo ({@code uq_film_rolls_sequence}). */
  List<FilmRoll> findByServiceOrderIdOrderByRollSequenceAsc(Long serviceOrderId);

  /**
   * Số cuộn của nhiều đơn trong một query (danh sách đơn, tránh N+1). Đơn không có cuộn thì không
   * có dòng trong kết quả.
   */
  @Query(
      "select r.serviceOrderId as serviceOrderId, count(r) as rollCount from FilmRoll r"
          + " where r.serviceOrderId in :serviceOrderIds group by r.serviceOrderId")
  List<RollCountRow> countByServiceOrderIds(
      @Param("serviceOrderIds") Collection<Long> serviceOrderIds);

  /** Một dòng của {@link #countByServiceOrderIds}; tên getter khớp alias trong JPQL. */
  interface RollCountRow {
    Long getServiceOrderId();

    Long getRollCount();
  }
}
