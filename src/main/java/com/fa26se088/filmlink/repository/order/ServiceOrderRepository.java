package com.fa26se088.filmlink.repository.order;

import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {

  /** NFR-REL-02: đơn đã tạo bằng cùng khoá idempotency ({@code uq_service_orders_idempotency}). */
  Optional<ServiceOrder> findByPhotographerAccountIdAndIdempotencyKey(
      Long photographerAccountId, UUID idempotencyKey);

  /** Mã đơn đã dùng chưa ({@code uq_service_orders_code}). */
  boolean existsByOrderCode(String orderCode);

  /** UC22: đơn {@code id} chỉ khi thuộc lab {@code filmLabId}. */
  Optional<ServiceOrder> findByIdAndFilmLabId(Long id, Long filmLabId);

  /**
   * UC22: {@code SELECT ... FOR UPDATE} trên dòng đơn của lab. Tuần tự hoá các thao tác đổi trạng
   * thái của cùng một đơn (ví dụ hai request nhận đơn cùng lúc): request sau chờ khoá rồi đọc trạng
   * thái mới.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from ServiceOrder o where o.id = :id and o.filmLabId = :filmLabId")
  Optional<ServiceOrder> findByIdAndFilmLabIdForUpdate(
      @Param("id") Long id, @Param("filmLabId") Long filmLabId);

  /** UC72: đơn {@code id} chỉ khi thuộc Photographer {@code photographerAccountId}. */
  Optional<ServiceOrder> findByIdAndPhotographerAccountId(Long id, Long photographerAccountId);

  /** UC25: đơn của Photographer, mọi trạng thái. */
  Page<ServiceOrder> findByPhotographerAccountId(Long photographerAccountId, Pageable pageable);

  /** UC25: đơn của Photographer theo một trạng thái. */
  Page<ServiceOrder> findByPhotographerAccountIdAndStatus(
      Long photographerAccountId, ServiceOrderStatus status, Pageable pageable);

  /** UC22: đơn của lab, mọi trạng thái. */
  Page<ServiceOrder> findByFilmLabId(Long filmLabId, Pageable pageable);

  /** UC22: đơn của lab theo một trạng thái. */
  Page<ServiceOrder> findByFilmLabIdAndStatus(
      Long filmLabId, ServiceOrderStatus status, Pageable pageable);
}
