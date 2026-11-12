package com.fa26se088.filmlink.mapper.payment;

import com.fa26se088.filmlink.dto.payment.response.CommissionResponse;
import com.fa26se088.filmlink.entity.payment.Commission;
import org.springframework.stereotype.Component;

/**
 * Mapper cho Commission entity sang CommissionResponse DTO.
 */
@Component
public class CommissionMapper {

  public CommissionResponse toResponse(Commission entity) {
    if (entity == null) {
      return null;
    }
    return new CommissionResponse(
        entity.getId(),
        entity.getKind(),
        entity.getFilmLabId(),
        entity.getServiceOrderId(),
        entity.getConsignmentSaleId(),
        entity.getComplaintId(),
        entity.getBaseAmountVnd(),
        entity.getRatePercent(),
        entity.getAmountVnd(),
        entity.getCollectionMode(),
        entity.getStatus(),
        entity.getCommissionDebtId(),
        entity.getEligibleAt(),
        entity.getWaiverReason(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }
}

