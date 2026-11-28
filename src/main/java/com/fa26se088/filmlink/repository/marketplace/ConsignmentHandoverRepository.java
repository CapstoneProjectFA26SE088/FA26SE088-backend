package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.ConsignmentHandover;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ConsignmentHandover}. */
@Repository
public interface ConsignmentHandoverRepository extends JpaRepository<ConsignmentHandover, Long> {

  Optional<ConsignmentHandover> findByConsignmentSaleId(Long consignmentSaleId);
}
