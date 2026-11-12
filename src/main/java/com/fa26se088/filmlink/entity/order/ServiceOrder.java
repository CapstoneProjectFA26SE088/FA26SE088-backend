package com.fa26se088.filmlink.entity.order;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.OrderPaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.entity.order.enums.ServiceTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Đơn dịch vụ tráng / scan (ERD #17), bảng {@code service_orders}.
 *
 * <p>Quy tắc bất biến:
 * <ul>
 *   <li>Không bao giờ xóa cứng (CR-15): hủy là chuyển trạng thái sang {@code CANCELLED}.
 *   <li>{@code version} dùng optimistic locking (ERD 3.8, R9.1) để chống race condition.
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "service_orders")
public class ServiceOrder extends BaseEntity {

  @Column(name = "order_code", nullable = false, unique = true, length = 20)
  private String orderCode;

  @Column(name = "idempotency_key", nullable = false)
  private UUID idempotencyKey;

  @Column(name = "photographer_account_id", nullable = false)
  private Long photographerAccountId;

  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 40)
  private ServiceOrderStatus status = ServiceOrderStatus.AWAITING_PAYMENT;

  @Column(name = "status_before_suspension", length = 40)
  private String statusBeforeSuspension;

  @Enumerated(EnumType.STRING)
  @Column(name = "service_tier", nullable = false, length = 20)
  private ServiceTier serviceTier;

  @Enumerated(EnumType.STRING)
  @Column(name = "film_handover_method", nullable = false, length = 20)
  private FilmHandoverMethod filmHandoverMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 20)
  private OrderPaymentMethod paymentMethod;

  @Column(name = "special_request", length = 500)
  private String specialRequest;

  @Column(name = "customer_address_line", length = 255)
  private String customerAddressLine;

  @Column(name = "customer_district", length = 100)
  private String customerDistrict;

  @Column(name = "customer_province", length = 100)
  private String customerProvince;

  @Column(name = "customer_phone", length = 15)
  private String customerPhone;

  @Column(name = "pickup_window_start_at")
  private Instant pickupWindowStartAt;

  @Column(name = "pickup_window_end_at")
  private Instant pickupWindowEndAt;

  @Column(name = "estimated_total_vnd", nullable = false)
  private Long estimatedTotalVnd;

  @Column(name = "final_total_vnd")
  private Long finalTotalVnd;

  @Column(name = "price_locked_at")
  private Instant priceLockedAt;

  @Column(name = "expected_return_at", nullable = false)
  private Instant expectedReturnAt;

  @Column(name = "promised_return_at")
  private Instant promisedReturnAt;

  @Column(name = "machine_assignment_confirmed_at")
  private Instant machineAssignmentConfirmedAt;

  @Column(name = "lab_response_due_at")
  private Instant labResponseDueAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "keep_negatives_at_lab", nullable = false)
  private boolean keepNegativesAtLab = false;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_by_account_id")
  private Long cancelledByAccountId;

  @Column(name = "cancellation_reason", length = 500)
  private String cancellationReason;

  @Column(name = "cancellation_fee_vnd")
  private Long cancellationFeeVnd;

  @Version
  @Column(name = "version", nullable = false)
  private int version = 0;
}

