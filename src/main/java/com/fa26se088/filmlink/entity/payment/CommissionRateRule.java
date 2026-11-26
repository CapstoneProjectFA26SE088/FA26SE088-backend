package com.fa26se088.filmlink.entity.payment;

import com.fa26se088.filmlink.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quy tắc tỷ lệ hoa hồng riêng theo Film Lab hoặc Service Package (BR-10, UC54), bảng
 * {@code commission_rate_rules}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commission_rate_rules")
public class CommissionRateRule extends BaseEntity {

  @Column(name = "film_lab_id")
  private Long filmLabId;

  @Column(name = "service_package_id")
  private Long servicePackageId;

  @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal ratePercent;

  @Column(name = "note", length = 300)
  private String note;

  @Column(name = "created_by_account_id", nullable = false)
  private Long createdByAccountId;
}
