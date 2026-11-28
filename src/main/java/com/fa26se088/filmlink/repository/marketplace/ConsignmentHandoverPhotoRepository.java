package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.ConsignmentHandoverPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ConsignmentHandoverPhoto}. */
@Repository
public interface ConsignmentHandoverPhotoRepository
    extends JpaRepository<ConsignmentHandoverPhoto, Long> {

  List<ConsignmentHandoverPhoto> findByConsignmentHandoverIdOrderByDisplayOrderAsc(
      Long consignmentHandoverId);
}
