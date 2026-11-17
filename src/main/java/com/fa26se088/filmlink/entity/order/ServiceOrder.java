package com.fa26se088.filmlink.entity.order;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.machine.enums.ServiceTier;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Service Order (ERD #17, UC20), bảng {@code service_orders}. Không xoá vật lý: huỷ là một trạng
 * thái (CR-15).
 *
 * <p>Ràng buộc quan trọng trong DB: {@code uq_service_orders_code}, {@code
 * uq_service_orders_idempotency} ({@code photographer_account_id}, {@code idempotency_key}), {@code
 * ck_service_orders_cod} (BR-09), {@code ck_service_orders_customer_address} (địa chỉ và khung giờ
 * bắt buộc trừ SELF_DROP_OFF), {@code ck_service_orders_pickup_window} (end &gt; start). Khoá ngoại
 * giữ dạng {@code Long} như các entity khác. Giá trị khởi tạo khớp {@code DEFAULT} trong DB.
 */
@Getter
@Setter
@Entity
@Table(name = "service_orders")
public class ServiceOrder extends BaseEntity {

  /** MSG18: mã đơn hiển thị, duy nhất, tối đa 20 ký tự. */
  @Column(name = "order_code", nullable = false, length = 20)
  private String orderCode;

  /** NFR-REL-02: gửi lại cùng yêu cầu tạo đơn không tạo đơn thứ hai. */
  @Column(name = "idempotency_key", nullable = false)
  private UUID idempotencyKey;

  @Column(name = "photographer_account_id", nullable = false)
  private Long photographerAccountId;

  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 40)
  private ServiceOrderStatus status = ServiceOrderStatus.AWAITING_PAYMENT;

  /** Chỉ có giá trị khi {@code status = SUSPENDED} ({@code ck_service_orders_suspension}). */
  @Enumerated(EnumType.STRING)
  @Column(name = "status_before_suspension", length = 40)
  private ServiceOrderStatus statusBeforeSuspension;

  @Enumerated(EnumType.STRING)
  @Column(name = "service_tier", nullable = false, length = 20)
  private ServiceTier serviceTier;

  @Enumerated(EnumType.STRING)
  @Column(name = "film_handover_method", nullable = false, length = 20)
  private FilmHandoverMethod filmHandoverMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 20)
  private PaymentMethod paymentMethod;

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

  /** UC20: giá tạm tính lúc tạo đơn (Σ giá cuộn + phụ phí Express + phí giao phim). */
  @Column(name = "estimated_total_vnd", nullable = false)
  private Long estimatedTotalVnd;

  /** UC23: giá chốt sau khi đếm cuộn; cùng có / cùng null với {@code priceLockedAt}. */
  @Column(name = "final_total_vnd")
  private Long finalTotalVnd;

  @Column(name = "price_locked_at")
  private Instant priceLockedAt;

  /** BR-60: ngày dự kiến trả lúc tạo đơn (ước lượng). */
  @Column(name = "expected_return_at", nullable = false)
  private Instant expectedReturnAt;

  /** BR-60: ngày hẹn trả sau khi xếp máy; cùng có / cùng null với {@code machineAssignmentConfirmedAt}. */
  @Column(name = "promised_return_at")
  private Instant promisedReturnAt;

  @Column(name = "machine_assignment_confirmed_at")
  private Instant machineAssignmentConfirmedAt;

  /** Chụp {@code now + LAB_RESPONSE_LIMIT_HOURS} khi đơn tới lab; đổi tham số sau đó không dời hạn. */
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

  /** Optimistic locking: lab nhận đơn và Photographer huỷ đơn cùng lúc. */
  @Version
  @Column(name = "version", nullable = false)
  private Integer version;
}
