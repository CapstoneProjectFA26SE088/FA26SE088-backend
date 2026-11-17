package com.fa26se088.filmlink.repository.order;

import com.fa26se088.filmlink.entity.order.ServiceOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {

  /** NFR-REL-02: đơn đã tạo bằng cùng khoá idempotency ({@code uq_service_orders_idempotency}). */
  Optional<ServiceOrder> findByPhotographerAccountIdAndIdempotencyKey(
      Long photographerAccountId, UUID idempotencyKey);

  /** Mã đơn đã dùng chưa ({@code uq_service_orders_code}). */
  boolean existsByOrderCode(String orderCode);
}
