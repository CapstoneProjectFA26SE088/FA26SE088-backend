package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.Payment;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import com.fa26se088.filmlink.entity.payment.enums.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Payment}.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

  Optional<Payment> findByTransactionCode(String transactionCode);

  List<Payment> findByServiceOrderIdOrderByCreatedAtDesc(Long serviceOrderId);

  List<Payment> findByServiceOrderId(Long serviceOrderId);

  /** Giao dịch PENDING còn hạn cùng đơn, mục đích và số tiền — để trả lại link cũ (UC30). */
  @Query(
      """
      SELECT p FROM Payment p
      WHERE p.serviceOrderId = :serviceOrderId
        AND p.purpose = :purpose
        AND p.status = :status
        AND p.amountVnd = :amountVnd
        AND p.paymentUrlExpiresAt > :now
      ORDER BY p.createdAt DESC
      LIMIT 1
      """)
  Optional<Payment> findReusablePending(
      @Param("serviceOrderId") Long serviceOrderId,
      @Param("purpose") PaymentPurpose purpose,
      @Param("status") PaymentStatus status,
      @Param("amountVnd") Long amountVnd,
      @Param("now") Instant now);

  Page<Payment> findByCounterpartyAccountId(Long accountId, Pageable pageable);

  List<Payment> findByStatusAndPaymentUrlExpiresAtBefore(PaymentStatus status, Instant threshold);

  boolean existsByTransactionCode(String transactionCode);
}

