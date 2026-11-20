package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.Payment;
import com.fa26se088.filmlink.entity.payment.enums.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Payment}.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

  Optional<Payment> findByTransactionCode(String transactionCode);

  List<Payment> findByServiceOrderIdOrderByCreatedAtDesc(Long serviceOrderId);

  List<Payment> findByServiceOrderId(Long serviceOrderId);

  Page<Payment> findByCounterpartyAccountId(Long accountId, Pageable pageable);

  List<Payment> findByStatusAndPaymentUrlExpiresAtBefore(PaymentStatus status, Instant threshold);

  boolean existsByTransactionCode(String transactionCode);
}

