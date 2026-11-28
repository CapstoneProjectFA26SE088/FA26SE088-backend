package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.ConsignmentSale;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentSaleStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ConsignmentSale}. */
@Repository
public interface ConsignmentSaleRepository extends JpaRepository<ConsignmentSale, Long> {

  /** Kiểm tra mã giao dịch mua đã tồn tại hay chưa. */
  boolean existsBySaleCode(String saleCode);

  /** Tra cứu giao dịch theo người mua và khoá idempotency. */
  Optional<ConsignmentSale> findByBuyerAccountIdAndIdempotencyKey(
      Long buyerAccountId, UUID idempotencyKey);

  /** Khoá bi quan giao dịch mua ký gửi để cập nhật trạng thái an toàn. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM ConsignmentSale s WHERE s.id = :id")
  Optional<ConsignmentSale> findByIdForUpdate(@Param("id") Long id);

  /** Giao dịch đã bàn giao và hết thời gian yêu cầu trả hàng, chờ chi trả cho người bán. */
  List<ConsignmentSale> findByStatusAndReturnWindowEndsAtLessThanEqualOrderByIdAsc(
      ConsignmentSaleStatus status, Instant now);
}
