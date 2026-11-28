package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.ConsignmentItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ConsignmentItem}. */
@Repository
public interface ConsignmentItemRepository extends JpaRepository<ConsignmentItem, Long> {

  Optional<ConsignmentItem> findByBookingId(Long bookingId);

  @Query("SELECT i FROM ConsignmentItem i JOIN FETCH i.booking WHERE i.id = :id")
  Optional<ConsignmentItem> findByIdWithBooking(@Param("id") Long id);

  @Query("SELECT i FROM ConsignmentItem i JOIN FETCH i.booking WHERE i.id IN :ids")
  List<ConsignmentItem> findByIdInWithBooking(@Param("ids") Collection<Long> ids);
}
