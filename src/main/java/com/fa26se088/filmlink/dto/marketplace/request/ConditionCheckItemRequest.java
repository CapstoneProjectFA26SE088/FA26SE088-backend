package com.fa26se088.filmlink.dto.marketplace.request;

import com.fa26se088.filmlink.entity.marketplace.enums.ConditionCheckCode;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionCheckResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Một dòng kiểm tra kỹ thuật trong biên bản kiểm định (UC46). */
public record ConditionCheckItemRequest(
    @NotNull ConditionCheckCode checkCode,
    @NotNull ConditionCheckResult result,
    @Size(max = 200) String note) {}
