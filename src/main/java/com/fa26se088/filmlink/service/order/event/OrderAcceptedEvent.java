package com.fa26se088.filmlink.service.order.event;

import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import java.time.Instant;
import java.util.Objects;

/**
 * UC22: Film Lab Owner nhận Service Order (AWAITING_LAB_CONFIRMATION → AWAITING_FILM). Domain
 * delivery (DLV-01) lắng nghe để kích hoạt tạo chuyến lấy phim khi phương thức là PICKUP hoặc
 * PARCEL_SHIPPING.
 *
 * @param serviceOrderId id của Service Order
 * @param orderCode mã hiển thị của đơn
 * @param filmLabId id của Film Lab nhận đơn
 * @param filmHandoverMethod phương thức bàn giao phim
 * @param acceptedAt thời điểm nhận đơn
 */
public record OrderAcceptedEvent(
    Long serviceOrderId,
    String orderCode,
    Long filmLabId,
    FilmHandoverMethod filmHandoverMethod,
    Instant acceptedAt) {

  public OrderAcceptedEvent {
    Objects.requireNonNull(serviceOrderId, "serviceOrderId");
    Objects.requireNonNull(orderCode, "orderCode");
    Objects.requireNonNull(filmLabId, "filmLabId");
    Objects.requireNonNull(filmHandoverMethod, "filmHandoverMethod");
    Objects.requireNonNull(acceptedAt, "acceptedAt");
  }
}
