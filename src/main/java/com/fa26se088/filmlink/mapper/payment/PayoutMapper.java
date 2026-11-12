package com.fa26se088.filmlink.mapper.payment;

import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.entity.payment.Payout;
import org.springframework.stereotype.Component;

/**
 * Mapper cho Payout.
 */
@Component
public class PayoutMapper {

  public PayoutResponse toResponse(Payout entity) {
    if (entity == null) {
      return null;
    }
    return new PayoutResponse(
        entity.getId(),
        entity.getPayoutCode(),
        entity.getRecipientType(),
        entity.getFilmLabId(),
        entity.getRecipientAccountId(),
        entity.getAmountVnd(),
        entity.getStatus(),
        entity.getBankName(),
        entity.getBankAccountNumber(),
        entity.getAccountHolderName(),
        entity.getReceiptBlobKey(),
        entity.getExecutedByAccountId(),
        entity.getPaidAt(),
        entity.getFailureReason(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }
}

