package com.fa26se088.filmlink.dto.marketplace.request;

import com.fa26se088.filmlink.entity.marketplace.enums.ConditionConclusion;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionGrade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Dữ liệu biên bản kiểm định thiết bị ký gửi tại Lab (UC46, BR-51). */
public record RecordConditionReportRequest(
    ConditionGrade overallGrade,
    @NotNull ConditionConclusion conclusion,
    @Size(max = 500) String declineReason,
    @Size(max = 150) String correctedItemName,
    @Size(max = 1000) String discrepancyNote,
    LocalDate filmExpiryDate,
    @Valid List<ConditionCheckItemRequest> checks) {}
