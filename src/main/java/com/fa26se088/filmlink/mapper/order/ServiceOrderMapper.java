package com.fa26se088.filmlink.mapper.order;

import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import org.springframework.stereotype.Component;

/**
 * Mapper chuyển đổi giữa ServiceOrder entity và ServiceOrderResponse DTO.
 */
@Component
public class ServiceOrderMapper {

  public ServiceOrderResponse toResponse(ServiceOrder entity) {
    if (entity == null) {
      return null;
    }
    return new ServiceOrderResponse(
        entity.getId(),
        entity.getOrderCode(),
        entity.getIdempotencyKey(),
        entity.getPhotographerAccountId(),
        entity.getFilmLabId(),
        entity.getStatus(),
        entity.getStatusBeforeSuspension(),
        entity.getServiceTier(),
        entity.getFilmHandoverMethod(),
        entity.getPaymentMethod(),
        entity.getSpecialRequest(),
        entity.getCustomerAddressLine(),
        entity.getCustomerDistrict(),
        entity.getCustomerProvince(),
        entity.getCustomerPhone(),
        entity.getPickupWindowStartAt(),
        entity.getPickupWindowEndAt(),
        entity.getEstimatedTotalVnd(),
        entity.getFinalTotalVnd(),
        entity.getPriceLockedAt(),
        entity.getExpectedReturnAt(),
        entity.getPromisedReturnAt(),
        entity.getMachineAssignmentConfirmedAt(),
        entity.getLabResponseDueAt(),
        entity.getAcceptedAt(),
        entity.getRejectionReason(),
        entity.getDeliveredAt(),
        entity.getClosedAt(),
        entity.isKeepNegativesAtLab(),
        entity.getCancelledAt(),
        entity.getCancelledByAccountId(),
        entity.getCancellationReason(),
        entity.getCancellationFeeVnd(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }
}

