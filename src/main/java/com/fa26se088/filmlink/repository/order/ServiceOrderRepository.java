package com.fa26se088.filmlink.repository.order;

import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link ServiceOrder}.
 */
@Repository
public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {

  Optional<ServiceOrder> findByOrderCode(String orderCode);

  boolean existsByPhotographerAccountIdAndIdempotencyKey(Long photographerAccountId, UUID idempotencyKey);

  Optional<ServiceOrder> findByPhotographerAccountIdAndIdempotencyKey(Long photographerAccountId, UUID idempotencyKey);

  Page<ServiceOrder> findByPhotographerAccountId(Long photographerAccountId, Pageable pageable);

  Page<ServiceOrder> findByPhotographerAccountIdAndStatus(Long photographerAccountId, ServiceOrderStatus status, Pageable pageable);

  Page<ServiceOrder> findByFilmLabId(Long filmLabId, Pageable pageable);

  Page<ServiceOrder> findByFilmLabIdAndStatus(Long filmLabId, ServiceOrderStatus status, Pageable pageable);

  List<ServiceOrder> findByStatusAndCreatedAtBefore(ServiceOrderStatus status, Instant threshold);
}

