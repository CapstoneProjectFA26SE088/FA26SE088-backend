package com.fa26se088.filmlink.entity.marketplace;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentHandoverMethod;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentPaymentMethod;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentSaleCancellationReason;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentSaleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Giao dịch mua thiết bị ký gửi (ERD #36, UC62–UC64), bảng {@code consignment_sales}. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "consignment_sales")
public class ConsignmentSale extends BaseEntity {

  @Column(name = "sale_code", nullable = false, unique = true, length = 20)
  private String saleCode;

  @Column(name = "idempotency_key", nullable = false)
  private UUID idempotencyKey;

  @Column(name = "consignment_listing_id", nullable = false)
  private Long consignmentListingId;

  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  @Column(name = "buyer_account_id", nullable = false)
  private Long buyerAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 20)
  private ConsignmentPaymentMethod paymentMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "handover_method", nullable = false, length = 20)
  private ConsignmentHandoverMethod handoverMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 25)
  private ConsignmentSaleStatus status = ConsignmentSaleStatus.AWAITING_PAYMENT;

  @Column(name = "item_price_vnd", nullable = false)
  private Long itemPriceVnd;

  @Column(name = "shipping_fee_vnd", nullable = false)
  private Long shippingFeeVnd = 0L;

  @Column(name = "total_vnd", nullable = false)
  private Long totalVnd;

  @Column(name = "recipient_name", length = 100)
  private String recipientName;

  @Column(name = "recipient_address_line", length = 255)
  private String recipientAddressLine;

  @Column(name = "recipient_district", length = 100)
  private String recipientDistrict;

  @Column(name = "recipient_province", length = 100)
  private String recipientProvince;

  @Column(name = "recipient_phone", length = 15)
  private String recipientPhone;

  @Column(name = "payment_due_at")
  private Instant paymentDueAt;

  @Column(name = "handed_over_at")
  private Instant handedOverAt;

  @Column(name = "return_window_ends_at")
  private Instant returnWindowEndsAt;

  @Column(name = "collection_reminder_count", nullable = false)
  private Short collectionReminderCount = 0;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "cancellation_reason", length = 30)
  private ConsignmentSaleCancellationReason cancellationReason;

  @Column(name = "consignment_fee_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal consignmentFeeRate;

  @Column(name = "consignment_fee_vnd", nullable = false)
  private Long consignmentFeeVnd;

  @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal commissionRate;

  @Column(name = "commission_vnd", nullable = false)
  private Long commissionVnd;

  @Column(name = "seller_payout_vnd", nullable = false)
  private Long sellerPayoutVnd;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version = 0;
}
