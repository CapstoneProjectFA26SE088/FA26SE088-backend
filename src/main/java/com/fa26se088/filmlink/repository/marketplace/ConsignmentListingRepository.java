package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.ConsignmentListing;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentListingStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ConsignmentListing}. */
@Repository
public interface ConsignmentListingRepository extends JpaRepository<ConsignmentListing, Long> {

  Optional<ConsignmentListing> findByConsignmentItemId(Long consignmentItemId);

  Page<ConsignmentListing> findByStatus(ConsignmentListingStatus status, Pageable pageable);

  /** Khoá dòng tin đăng khi người mua tạo đơn mua (UH-46: chỉ người đầu tiên khoá được ON_SALE). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT l FROM ConsignmentListing l WHERE l.id = :id")
  Optional<ConsignmentListing> findByIdForUpdate(@Param("id") Long id);
}
