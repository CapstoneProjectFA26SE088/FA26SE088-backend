package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.PaymentGatewayEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link PaymentGatewayEvent}.
 */
@Repository
public interface PaymentGatewayEventRepository extends JpaRepository<PaymentGatewayEvent, Long> {
}

