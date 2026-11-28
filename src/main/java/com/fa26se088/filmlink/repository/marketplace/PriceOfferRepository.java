package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.PriceOffer;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferParty;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferPurpose;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link PriceOffer}. */
@Repository
public interface PriceOfferRepository extends JpaRepository<PriceOffer, Long> {

  List<PriceOffer> findByConsignmentItemIdOrderByCreatedAtAsc(Long consignmentItemId);

  boolean existsByConsignmentItemIdAndPurposeAndRoundNumberAndOfferedByParty(
      Long consignmentItemId,
      PriceOfferPurpose purpose,
      Short roundNumber,
      PriceOfferParty offeredByParty);
}
