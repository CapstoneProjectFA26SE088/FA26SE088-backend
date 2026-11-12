package com.fa26se088.filmlink.mapper.payment;

import com.fa26se088.filmlink.dto.payment.response.PaymentResponse;
import com.fa26se088.filmlink.entity.payment.Payment;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi {@link Payment} sang {@link PaymentResponse}. Viết tay, không dùng MapStruct.
 */
@Component
public class PaymentMapper {

  public PaymentResponse toResponse(Payment payment) {
    return toResponse(payment, null);
  }

  public PaymentResponse toResponse(Payment payment, String paymentUrl) {
    if (payment == null) {
      return null;
    }
    return PaymentResponse.builder()
        .id(payment.getId())
        .transactionCode(payment.getTransactionCode())
        .purpose(payment.getPurpose())
        .direction(payment.getDirection())
        .method(payment.getMethod())
        .status(payment.getStatus())
        .amountVnd(payment.getAmountVnd())
        .serviceOrderId(payment.getServiceOrderId())
        .counterpartyAccountId(payment.getCounterpartyAccountId())
        .paymentUrl(paymentUrl)
        .paymentUrlExpiresAt(payment.getPaymentUrlExpiresAt())
        .gatewayTransactionNo(payment.getGatewayTransactionNo())
        .gatewayResponseCode(payment.getGatewayResponseCode())
        .gatewayBankCode(payment.getGatewayBankCode())
        .confirmationStatus(payment.getConfirmationStatus())
        .confirmedAt(payment.getConfirmedAt())
        .succeededAt(payment.getSucceededAt())
        .failureReason(payment.getFailureReason())
        .createdAt(payment.getCreatedAt())
        .build();
  }
}

