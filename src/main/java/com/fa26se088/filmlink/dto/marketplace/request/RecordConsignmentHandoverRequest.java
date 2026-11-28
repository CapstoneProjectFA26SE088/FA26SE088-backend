package com.fa26se088.filmlink.dto.marketplace.request;

import jakarta.validation.constraints.Size;

/** Ghi chú tuỳ chọn khi bàn giao thiết bị ký gửi tại quầy Lab (UC63). */
public record RecordConsignmentHandoverRequest(
    @Size(max = 20) String saleCode, @Size(max = 500) String note) {}
