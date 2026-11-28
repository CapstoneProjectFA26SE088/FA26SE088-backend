package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.ConditionReportCheck;
import com.fa26se088.filmlink.entity.marketplace.ConditionReportCheckId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ConditionReportCheck}. */
@Repository
public interface ConditionReportCheckRepository
    extends JpaRepository<ConditionReportCheck, ConditionReportCheckId> {

  List<ConditionReportCheck> findByIdConditionReportId(Long conditionReportId);

  List<ConditionReportCheck> findByIdConditionReportIdIn(Collection<Long> conditionReportIds);
}
