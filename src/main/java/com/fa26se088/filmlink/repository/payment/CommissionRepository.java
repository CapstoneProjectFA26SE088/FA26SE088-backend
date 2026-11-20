package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.Commission;
import com.fa26se088.filmlink.entity.payment.enums.CommissionKind;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Commission}.
 */
@Repository
public interface CommissionRepository extends JpaRepository<Commission, Long> {

  Optional<Commission> findByServiceOrderIdAndKind(Long serviceOrderId, CommissionKind kind);

  boolean existsByServiceOrderIdAndKind(Long serviceOrderId, CommissionKind kind);

  Page<Commission> findByFilmLabId(Long filmLabId, Pageable pageable);

  List<Commission> findByFilmLabIdAndStatusInAndEligibleAtBefore(
      Long filmLabId, List<CommissionStatus> statuses, Instant threshold);
}

