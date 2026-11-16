package com.fa26se088.filmlink.dto.account.response;

import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import java.time.Instant;

/**
 * Tóm tắt đơn đăng ký Film Lab phục vụ danh sách cho Admin (UC08).
 *
 * @param id ID đơn đăng ký
 * @param applicationCode mã hồ sơ tra cứu (MSG14)
 * @param labName tên phòng lab
 * @param taxCode mã số thuế
 * @param province tỉnh / thành phố
 * @param district quận / huyện
 * @param contactEmail email liên hệ
 * @param contactPhone số điện thoại liên hệ
 * @param status trạng thái đơn (PENDING_REVIEW, AWAITING_INFORMATION, APPROVED, REJECTED)
 * @param isDuplicateSuspected cờ cảnh báo nghi vấn trùng mã số thuế hoặc địa chỉ (UH-02)
 * @param createdAt thời điểm nộp đơn
 */
public record AdminLabApplicationSummaryResponse(
    Long id,
    String applicationCode,
    String labName,
    String taxCode,
    String province,
    String district,
    String contactEmail,
    String contactPhone,
    LabApplicationStatus status,
    boolean isDuplicateSuspected,
    Instant createdAt) {}
