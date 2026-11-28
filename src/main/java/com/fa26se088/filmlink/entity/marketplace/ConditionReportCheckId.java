package com.fa26se088.filmlink.entity.marketplace;

import com.fa26se088.filmlink.entity.marketplace.enums.ConditionCheckCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Khoá chính ghép của dòng kiểm tra thẩm định ({@code pk_condition_report_checks}). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ConditionReportCheckId implements Serializable {

  @Column(name = "condition_report_id", nullable = false)
  private Long conditionReportId;

  @Enumerated(EnumType.STRING)
  @Column(name = "check_code", nullable = false, length = 30)
  private ConditionCheckCode checkCode;

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ConditionReportCheckId that)) {
      return false;
    }
    return Objects.equals(conditionReportId, that.conditionReportId)
        && checkCode == that.checkCode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(conditionReportId, checkCode);
  }
}
