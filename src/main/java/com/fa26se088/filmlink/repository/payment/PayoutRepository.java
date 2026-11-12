package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.Payout;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Payout}.
 */
@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {

  Optional<Payout> findByPayoutCode(String payoutCode);

  Page<Payout> findByFilmLabId(Long filmLabId, Pageable pageable);

  Page<Payout> findByStatus(PayoutStatus status, Pageable pageable);
}

