package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.PaymentGatewayEvent;
import com.fa26se088.filmlink.entity.payment.enums.PaymentGatewayEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link PaymentGatewayEvent}.
 */
@Repository
public interface PaymentGatewayEventRepository extends JpaRepository<PaymentGatewayEvent, Long> {

  /** Kiểm tra trùng sự kiện callback theo ràng buộc {@code uq_payment_gateway_events_dedup}. */
  @Query(
      """
      SELECT COUNT(e) > 0 FROM PaymentGatewayEvent e
      WHERE e.provider = :provider
        AND e.eventType = :eventType
        AND e.transactionCode = :transactionCode
        AND ((:gatewayTransactionNo IS NULL AND e.gatewayTransactionNo IS NULL)
             OR e.gatewayTransactionNo = :gatewayTransactionNo)
        AND ((:responseCode IS NULL AND e.responseCode IS NULL)
             OR e.responseCode = :responseCode)
      """)
  boolean existsDuplicateEvent(
      @Param("provider") String provider,
      @Param("eventType") PaymentGatewayEventType eventType,
      @Param("transactionCode") String transactionCode,
      @Param("gatewayTransactionNo") String gatewayTransactionNo,
      @Param("responseCode") String responseCode);
}

