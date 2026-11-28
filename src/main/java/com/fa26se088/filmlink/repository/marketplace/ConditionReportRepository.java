package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.ConditionReport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ConditionReport}. */
@Repository
public interface ConditionReportRepository extends JpaRepository<ConditionReport, Long> {

  Optional<ConditionReport> findByConsignmentItemId(Long consignmentItemId);

  List<ConditionReport> findByConsignmentItemIdIn(Collection<Long> consignmentItemIds);
}
