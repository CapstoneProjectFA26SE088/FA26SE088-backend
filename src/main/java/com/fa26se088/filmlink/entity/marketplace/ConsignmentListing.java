package com.fa26se088.filmlink.entity.marketplace;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentExpiryChoice;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentListingStatus;
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
 * Tin đăng bán thiết bị ký gửi công khai (ERD #35, UC48, UC61), bảng {@code consignment_listings}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "consignment_listings")
public class ConsignmentListing extends BaseEntity {

  @Column(name = "consignment_item_id", nullable = false, unique = true)
  private Long consignmentItemId;

  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  @Column(name = "title", nullable = false, length = 150)
  private String title;

  @Column(name = "description", nullable = false, length = 3000)
  private String description;

  @Column(name = "price_vnd", nullable = false)
  private Long priceVnd;

  @Column(name = "is_pickup_at_lab", nullable = false)
  private boolean pickupAtLab = true;

  @Column(name = "is_delivery_available", nullable = false)
  private boolean deliveryAvailable = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ConsignmentListingStatus status = ConsignmentListingStatus.DRAFT;

  @Column(name = "consignment_started_at", nullable = false)
  private Instant consignmentStartedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "extension_count", nullable = false)
  private Short extensionCount = 0;

  @Column(name = "reminder_sent_at")
  private Instant reminderSentAt;

  @Column(name = "expiry_response_due_at")
  private Instant expiryResponseDueAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "expiry_choice", length = 20)
  private ConsignmentExpiryChoice expiryChoice;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "hidden_reason", length = 500)
  private String hiddenReason;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version = 0;
}
