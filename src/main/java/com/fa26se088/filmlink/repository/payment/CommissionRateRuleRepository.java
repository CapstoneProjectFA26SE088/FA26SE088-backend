package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.CommissionRateRule;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link CommissionRateRule}.
 */
@Repository
public interface CommissionRateRuleRepository extends JpaRepository<CommissionRateRule, Long> {

  List<CommissionRateRule> findByServicePackageIdIn(Collection<Long> servicePackageIds);

  Optional<CommissionRateRule> findByFilmLabId(Long filmLabId);
}
