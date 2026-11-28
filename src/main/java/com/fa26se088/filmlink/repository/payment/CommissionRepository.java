package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.Commission;
import com.fa26se088.filmlink.entity.payment.enums.CommissionCollectionMode;
import com.fa26se088.filmlink.entity.payment.enums.CommissionKind;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository cho {@link Commission}. */
@Repository
public interface CommissionRepository extends JpaRepository<Commission, Long> {

  Optional<Commission> findByServiceOrderIdAndKind(Long serviceOrderId, CommissionKind kind);

  boolean existsByServiceOrderIdAndKind(Long serviceOrderId, CommissionKind kind);

  boolean existsByConsignmentSaleIdAndKind(Long consignmentSaleId, CommissionKind kind);

  Page<Commission> findByFilmLabId(Long filmLabId, Pageable pageable);

  List<Commission> findByFilmLabIdAndCollectionModeAndStatusInAndEligibleAtBefore(
      Long filmLabId,
      CommissionCollectionMode collectionMode,
      List<CommissionStatus> statuses,
      Instant threshold);

  /** Hoa hồng đơn đã khấu trừ, đủ điều kiện chi trả và chưa có dòng trong payout_items. */
  @Query(
      "SELECT c FROM Commission c WHERE c.kind = :kind AND c.collectionMode = :mode"
          + " AND c.status = :status AND c.eligibleAt <= :now AND c.serviceOrderId IS NOT NULL"
          + " AND NOT EXISTS (SELECT 1 FROM PayoutItem pi WHERE pi.serviceOrderId = c.serviceOrderId)"
          + " ORDER BY c.filmLabId, c.id")
  List<Commission> findPayable(
      @Param("kind") CommissionKind kind,
      @Param("mode") CommissionCollectionMode mode,
      @Param("status") CommissionStatus status,
      @Param("now") Instant now);

  @Query(
      "SELECT DISTINCT c.filmLabId FROM Commission c"
          + " WHERE c.collectionMode = :mode AND c.status IN :statuses AND c.eligibleAt < :threshold")
  List<Long> findDistinctLabIdsWithUnbilledCommissions(
      @Param("mode") CommissionCollectionMode mode,
      @Param("statuses") List<CommissionStatus> statuses,
      @Param("threshold") Instant threshold);
}
