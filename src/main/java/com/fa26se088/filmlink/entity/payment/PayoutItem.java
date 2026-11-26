package com.fa26se088.filmlink.entity.payment;

import com.fa26se088.filmlink.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một dòng của đợt chi trả: tiền của đúng một đơn (hoặc một giao dịch ký gửi), bảng {@code
 * payout_items}. Chỉ mục duy nhất theo đơn chặn một đơn bị chi trả hai lần.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payout_items")
public class PayoutItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payout_id", nullable = false)
  private Payout payout;

  @Column(name = "service_order_id")
  private Long serviceOrderId;

  @Column(name = "consignment_sale_id")
  private Long consignmentSaleId;

  @Column(name = "amount_vnd", nullable = false)
  private Long amountVnd;
}
