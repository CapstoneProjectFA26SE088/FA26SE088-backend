package com.fa26se088.filmlink.entity.marketplace;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferParty;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferPurpose;
import com.fa26se088.filmlink.entity.marketplace.enums.PriceOfferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một lượt đề xuất / thương lượng giá bán ký gửi (ERD #34, UC47), bảng {@code price_offers}.
 *
 * <p>Trigger {@code trg_price_offers_immutable} cấm xoá dòng và chỉ cho phép cập nhật {@code
 * status}, {@code reminder_sent_at}, {@code responded_at}, {@code updated_at}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "price_offers")
public class PriceOffer extends BaseEntity {

  @Column(name = "consignment_item_id", nullable = false, updatable = false)
  private Long consignmentItemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, length = 20, updatable = false)
  private PriceOfferPurpose purpose = PriceOfferPurpose.INITIAL;

  @Column(name = "round_number", nullable = false, updatable = false)
  private Short roundNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "offered_by_party", nullable = false, length = 10, updatable = false)
  private PriceOfferParty offeredByParty;

  @Column(name = "offered_by_account_id", nullable = false, updatable = false)
  private Long offeredByAccountId;

  @Column(name = "price_vnd", nullable = false, updatable = false)
  private Long priceVnd;

  @Column(name = "justification", nullable = false, length = 1000, updatable = false)
  private String justification;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PriceOfferStatus status = PriceOfferStatus.PENDING;

  @Column(name = "response_due_at", nullable = false, updatable = false)
  private Instant responseDueAt;

  @Column(name = "reminder_sent_at")
  private Instant reminderSentAt;

  @Column(name = "responded_at")
  private Instant respondedAt;
}
