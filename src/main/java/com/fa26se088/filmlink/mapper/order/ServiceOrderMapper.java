package com.fa26se088.filmlink.mapper.order;

import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverResponse;
import com.fa26se088.filmlink.dto.order.response.FilmRollResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderSummaryResponse;
import com.fa26se088.filmlink.dto.order.response.OrderPhotographerResponse;
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

  /**
   * UC22, UC23: chi tiết đơn cho chủ lab; {@code photographer} do service đọc qua domain account,
   * {@code handovers} đọc qua domain delivery.
   */
  public LabServiceOrderResponse toLabResponse(
      ServiceOrder order,
      List<FilmRoll> rolls,
      OrderPhotographerResponse photographer,
      List<FilmHandoverResponse> handovers) {
    return new LabServiceOrderResponse(
        order.getId(),
        order.getOrderCode(),
        order.getStatus(),
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
        order.getFinalTotalVnd(),
        order.getPriceLockedAt(),
        order.getExpectedReturnAt(),
        order.getLabResponseDueAt(),
        order.getAcceptedAt(),
        photographer,
        rolls.stream().map(this::toRollResponse).toList(),
        handovers,
        order.getCreatedAt());
  }

  /** UC22: một dòng danh sách đơn của lab; {@code rollCount} do service đếm gộp cả trang. */
  public LabServiceOrderSummaryResponse toLabSummaryResponse(ServiceOrder order, long rollCount) {
    return new LabServiceOrderSummaryResponse(
        order.getId(),
        order.getOrderCode(),
        order.getStatus(),
        order.getServiceTier(),
        order.getFilmHandoverMethod(),
        order.getPaymentMethod(),
        rollCount,
        order.getEstimatedTotalVnd(),
        order.getLabResponseDueAt(),
        order.getExpectedReturnAt(),
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
