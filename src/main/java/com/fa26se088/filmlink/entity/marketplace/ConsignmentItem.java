package com.fa26se088.filmlink.entity.marketplace;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentClosingReason;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Thiết bị gửi bán ký gửi tại Film Lab (ERD #32, UC45–UC48), bảng {@code consignment_items}. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "consignment_items")
public class ConsignmentItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "consignment_booking_id", nullable = false)
  private ConsignmentBooking booking;

  @Enumerated(EnumType.STRING)
  @Column(name = "item_type", nullable = false, length = 20)
  private ConsignmentItemType itemType;

  @Column(name = "item_name", nullable = false, length = 150)
  private String itemName;

  @Column(name = "seller_description", nullable = false, length = 2000)
  private String sellerDescription;

  @Column(name = "desired_price_vnd")
  private Long desiredPriceVnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ConsignmentItemStatus status = ConsignmentItemStatus.AWAITING_DROP_OFF;

  @Enumerated(EnumType.STRING)
  @Column(name = "closing_reason", length = 30)
  private ConsignmentClosingReason closingReason;

  @Column(name = "agreed_price_vnd")
  private Long agreedPriceVnd;

  @Column(name = "price_agreed_at")
  private Instant priceAgreedAt;

  @Column(name = "collection_due_at")
  private Instant collectionDueAt;

  @Column(name = "collected_at")
  private Instant collectedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version = 0;
}
