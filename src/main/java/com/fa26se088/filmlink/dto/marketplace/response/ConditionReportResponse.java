package com.fa26se088.filmlink.dto.marketplace.response;

import com.fa26se088.filmlink.entity.marketplace.enums.ConditionConclusion;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionGrade;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Biên bản kiểm định thiết bị ký gửi công khai và nội bộ (UC46, UC48). */
public record ConditionReportResponse(
    Long id,
    Long consignmentItemId,
    ConsignmentItemStatus itemStatus,
    ConditionGrade overallGrade,
    ConditionConclusion conclusion,
    String declineReason,
    String correctedItemName,
    String discrepancyNote,
    LocalDate filmExpiryDate,
    Long inspectorAccountId,
    Instant inspectedAt,
    List<ConditionCheckResponse> checks,
    List<ConsignmentPhotoResponse> photos) {}
