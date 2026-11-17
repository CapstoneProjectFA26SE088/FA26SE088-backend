package com.fa26se088.filmlink.mapper.order;

import com.fa26se088.filmlink.dto.order.response.FilmRollResponse;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.entity.order.FilmRoll;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import java.util.List;
import org.springframework.stereotype.Component;

/** Chuyển Service Order và Film Roll sang DTO. Viết tay, không dùng MapStruct. */
@Component
public class ServiceOrderMapper {

  /**
   * @param payment link thanh toán do service dựng (hiện luôn {@code null}); mapper không gọi
   *     service
   */
  public ServiceOrderResponse toResponse(ServiceOrder order, List<FilmRoll> rolls, Object payment) {
    return new ServiceOrderResponse(
        order.getId(),
        order.getOrderCode(),
        order.getStatus(),
        order.getFilmLabId(),
        order.getServiceTier(),
        order.getFilmHandoverMethod(),
        order.getPaymentMethod(),
        order.getSpecialRequest(),
        order.getCustomerAddressLine(),
        order.getCustomerDistrict(),
        order.getCustomerProvince(),
        order.getCustomerPhone(),
        order.getPickupWindowStartAt(),
        order.getPickupWindowEndAt(),
        order.getEstimatedTotalVnd(),
        order.getExpectedReturnAt(),
        order.getLabResponseDueAt(),
        rolls.stream().map(this::toRollResponse).toList(),
        payment,
        order.getCreatedAt());
  }

  public FilmRollResponse toRollResponse(FilmRoll roll) {
    return new FilmRollResponse(
        roll.getId(),
        roll.getRollSequence(),
        roll.getFilmStockId(),
        roll.getFilmStockNameText(),
        roll.getFilmFormatId(),
        roll.getFilmProcessId(),
        roll.getServicePackageId(),
        roll.getServicePackageScanOptionId(),
        roll.getExposureCount(),
        roll.getShootingIso(),
        roll.getPushPullStops(),
        roll.getUnitPriceVnd(),
        roll.getScanOptionPriceVnd(),
        roll.getPushPullSurchargeVnd(),
        roll.getExpressSurchargeVnd(),
        roll.getStatus());
  }
}
