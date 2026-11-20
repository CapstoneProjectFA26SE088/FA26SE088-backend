package com.fa26se088.filmlink.mapper.payment;

import com.fa26se088.filmlink.dto.payment.response.CommissionDebtResponse;
import com.fa26se088.filmlink.entity.payment.CommissionDebt;
import org.springframework.stereotype.Component;

/**
 * Mapper cho CommissionDebt.
 */
@Component
public class CommissionDebtMapper {

  public CommissionDebtResponse toResponse(CommissionDebt entity) {
    if (entity == null) {
      return null;
    }
    return new CommissionDebtResponse(
        entity.getId(),
        entity.getFilmLabId(),
        entity.getBillingPeriod(),
        entity.getDueDate(),
        entity.getTotalAmountVnd(),
        entity.getPaidAmountVnd(),
        entity.getStatus(),
        entity.getSettledAt(),
        entity.getSettledByAccountId(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }
}

