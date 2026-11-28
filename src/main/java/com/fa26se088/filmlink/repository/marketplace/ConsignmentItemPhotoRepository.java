package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.ConsignmentItemPhoto;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentPhotoSource;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ConsignmentItemPhoto}. */
@Repository
public interface ConsignmentItemPhotoRepository extends JpaRepository<ConsignmentItemPhoto, Long> {

  List<ConsignmentItemPhoto> findByConsignmentItemIdAndSourceOrderByDisplayOrderAsc(
      Long consignmentItemId, ConsignmentPhotoSource source);

  List<ConsignmentItemPhoto> findByConsignmentItemIdInOrderByDisplayOrderAsc(
      Collection<Long> consignmentItemIds);
}
