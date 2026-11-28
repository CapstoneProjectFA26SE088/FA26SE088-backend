package com.fa26se088.filmlink.entity.marketplace;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionConclusion;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Biên bản kiểm định tình trạng thiết bị ký gửi (ERD #33, UC46, BR-51), bảng
 * {@code condition_reports}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "condition_reports")
public class ConditionReport extends BaseEntity {

  @Column(name = "consignment_item_id", nullable = false, unique = true)
  private Long consignmentItemId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "overall_grade", length = 1)
  private ConditionGrade overallGrade;

  @Enumerated(EnumType.STRING)
  @Column(name = "conclusion", nullable = false, length = 20)
  private ConditionConclusion conclusion;

  @Column(name = "decline_reason", length = 500)
  private String declineReason;

  @Column(name = "corrected_item_name", length = 150)
  private String correctedItemName;

  @Column(name = "discrepancy_note", length = 1000)
  private String discrepancyNote;

  @Column(name = "seller_confirmed_at")
  private Instant sellerConfirmedAt;

  @Column(name = "film_expiry_date")
  private LocalDate filmExpiryDate;

  @Column(name = "inspector_account_id", nullable = false)
  private Long inspectorAccountId;

  @Column(name = "inspected_at", nullable = false)
  private Instant inspectedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version = 0;
}
