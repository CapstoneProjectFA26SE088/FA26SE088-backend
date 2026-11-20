package com.fa26se088.filmlink.dto.order.response;

/**
 * Kết quả tạo đơn (UC20, NFR-REL-02). {@code created = false} khi request lặp lại cùng {@code
 * idempotencyKey}: {@code order} là đơn đã có, controller trả 200 thay vì 201.
 */
public record ServiceOrderCreationResult(ServiceOrderResponse order, boolean created) {}
