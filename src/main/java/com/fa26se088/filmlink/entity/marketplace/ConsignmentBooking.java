package com.fa26se088.filmlink.entity.marketplace;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentBookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lịch hẹn mang thiết bị tới Lab kiểm định ký gửi (ERD #31, UC45), bảng
 * {@code consignment_bookings}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "consignment_bookings")
public class ConsignmentBooking extends BaseEntity {

  @Column(name = "booking_code", nullable = false, unique = true, length = 20)
  private String bookingCode;

  @Column(name = "seller_account_id", nullable = false)
  private Long sellerAccountId;

  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  @Column(name = "slot_start_at", nullable = false)
  private Instant slotStartAt;

  @Column(name = "slot_end_at", nullable = false)
  private Instant slotEndAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ConsignmentBookingStatus status = ConsignmentBookingStatus.BOOKED;

  @Column(name = "checked_in_at")
  private Instant checkedInAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason", length = 300)
  private String cancellationReason;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version = 0;
}
