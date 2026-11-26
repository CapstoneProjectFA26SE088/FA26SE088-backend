package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.CommissionDebt;
import com.fa26se088.filmlink.entity.payment.enums.CommissionDebtStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link CommissionDebt}.
 */
@Repository
public interface CommissionDebtRepository extends JpaRepository<CommissionDebt, Long> {

  Optional<CommissionDebt> findByFilmLabIdAndBillingPeriod(Long filmLabId, LocalDate billingPeriod);

  boolean existsByFilmLabIdAndBillingPeriod(Long filmLabId, LocalDate billingPeriod);

  Page<CommissionDebt> findByFilmLabId(Long filmLabId, Pageable pageable);

  List<CommissionDebt> findByFilmLabIdAndStatusNot(Long filmLabId, CommissionDebtStatus status);

  List<CommissionDebt> findByStatusNotAndDueDateBefore(CommissionDebtStatus status, LocalDate thresholdDate);
}

