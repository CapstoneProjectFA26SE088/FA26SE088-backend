package com.fa26se088.filmlink.repository.order;

import com.fa26se088.filmlink.entity.order.FilmRoll;
import com.fa26se088.filmlink.entity.order.enums.FilmRollStatus;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FilmRollRepository extends JpaRepository<FilmRoll, Long> {

  /** Các cuộn của một đơn theo thứ tự khai báo ({@code uq_film_rolls_sequence}). */
  List<FilmRoll> findByServiceOrderIdOrderByRollSequenceAsc(Long serviceOrderId);

  /** UC26: cuộn {@code id} chỉ khi thuộc đơn {@code serviceOrderId}. */
  Optional<FilmRoll> findByIdAndServiceOrderId(Long id, Long serviceOrderId);

  /**
   * UC28: id các cuộn có trạng thái {@code rollStatus} thuộc đơn của Photographer đang ở một trong
   * {@code orderStatuses} (đơn đã giao scan). Domain archive chỉ nhận danh sách id này.
   */
  @Query(
      "select r.id from FilmRoll r join ServiceOrder o on o.id = r.serviceOrderId"
          + " where o.photographerAccountId = :photographerAccountId"
          + " and o.status in :orderStatuses and r.status = :rollStatus")
  List<Long> findIdsByPhotographerAndOrderStatuses(
      @Param("photographerAccountId") Long photographerAccountId,
      @Param("orderStatuses") Collection<ServiceOrderStatus> orderStatuses,
      @Param("rollStatus") FilmRollStatus rollStatus);

  /**
   * UC28: cuộn {@code id} chỉ khi có trạng thái {@code rollStatus} và thuộc đơn của Photographer ở
   * một trong {@code orderStatuses}.
   */
  @Query(
      "select r from FilmRoll r join ServiceOrder o on o.id = r.serviceOrderId"
          + " where r.id = :id and o.photographerAccountId = :photographerAccountId"
          + " and o.status in :orderStatuses and r.status = :rollStatus")
  Optional<FilmRoll> findByIdAndPhotographerAndOrderStatuses(
      @Param("id") Long id,
      @Param("photographerAccountId") Long photographerAccountId,
      @Param("orderStatuses") Collection<ServiceOrderStatus> orderStatuses,
      @Param("rollStatus") FilmRollStatus rollStatus);

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
