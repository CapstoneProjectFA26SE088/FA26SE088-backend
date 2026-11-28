package com.fa26se088.filmlink.entity.marketplace;

import com.fa26se088.filmlink.entity.marketplace.enums.ConditionCheckResult;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một dòng hạng mục kiểm tra của biên bản thẩm định ({@code condition_report_checks}). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "condition_report_checks")
public class ConditionReportCheck {

  @EmbeddedId private ConditionReportCheckId id;

  @Enumerated(EnumType.STRING)
  @Column(name = "result", nullable = false, length = 20)
  private ConditionCheckResult result;

  @Column(name = "note", length = 200)
  private String note;
}
