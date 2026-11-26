package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.PayoutItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link PayoutItem}. */
@Repository
public interface PayoutItemRepository extends JpaRepository<PayoutItem, Long> {

  List<PayoutItem> findByPayoutId(Long payoutId);

  List<PayoutItem> findByPayoutIdIn(Collection<Long> payoutIds);

  boolean existsByServiceOrderId(Long serviceOrderId);
}
