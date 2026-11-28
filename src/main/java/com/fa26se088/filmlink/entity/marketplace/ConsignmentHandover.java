package com.fa26se088.filmlink.entity.marketplace;

import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentHandoverMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Bằng chứng bàn giao thiết bị ký gửi cho người mua (ERD #37, UC63, BR-25), bảng {@code
 * consignment_handovers}.
 *
 * <p>Append-only: không có {@code created_at}/{@code updated_at} nên không kế thừa {@code
 * BaseEntity}. Trigger {@code consignment_handovers_append_only} chặn UPDATE / DELETE.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "consignment_handovers")
public class ConsignmentHandover {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "consignment_sale_id", nullable = false, unique = true, updatable = false)
  private Long consignmentSaleId;

  @Enumerated(EnumType.STRING)
  @Column(name = "handover_method", nullable = false, length = 20, updatable = false)
  private ConsignmentHandoverMethod handoverMethod;

  @Column(name = "shipment_id", updatable = false)
  private Long shipmentId;

  @Column(name = "amount_collected_vnd", updatable = false)
  private Long amountCollectedVnd;

  @Column(name = "is_recipient_code_verified", nullable = false, updatable = false)
  private boolean recipientCodeVerified = false;

  @Column(name = "note", length = 500, updatable = false)
  private String note;

  @Column(name = "performed_by_account_id", nullable = false, updatable = false)
  private Long performedByAccountId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;
}
