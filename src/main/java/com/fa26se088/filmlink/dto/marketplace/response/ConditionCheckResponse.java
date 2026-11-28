package com.fa26se088.filmlink.dto.marketplace.response;

import com.fa26se088.filmlink.entity.marketplace.enums.ConditionCheckCode;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionCheckResult;

/** Kết quả một hạng mục kiểm tra trong biên bản thẩm định. */
public record ConditionCheckResponse(
    ConditionCheckCode checkCode, ConditionCheckResult result, String note) {}
