package com.fa26se088.filmlink.dto.account.response;

import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Thông tin phản hồi chi tiết của đơn đăng ký mở Film Lab (UC07, UC08).
 *
 * @param id ID bản ghi đơn đăng ký
 * @param applicationCode mã tra cứu duy nhất của đơn (MSG14)
 * @param labName tên phòng lab
 * @param taxCode mã số thuế
 * @param addressLine địa chỉ chi tiết
 * @param district quận/huyện
 * @param province tỉnh/thành phố
 * @param latitude vĩ độ (có thể null)
 * @param longitude kinh độ (có thể null)
 * @param contactEmail email liên hệ
 * @param contactPhone số điện thoại liên hệ
 * @param status trạng thái xử lý đơn (PENDING_REVIEW, AWAITING_INFORMATION, APPROVED, REJECTED)
 * @param isDuplicateSuspected cảnh báo nghi vấn trùng mã số thuế hoặc địa chỉ (UH-02)
 * @param serviceTypeIds danh sách ID loại dịch vụ
 * @param filmProcessIds danh sách ID quy trình tráng
 * @param filmFormatIds danh sách ID khổ phim
 * @param documents danh sách tài liệu pháp lý đính kèm
 * @param createdAt thời điểm nộp đơn
 */
public record LabApplicationResponse(
    Long id,
    String applicationCode,
    String labName,
    String taxCode,
    String addressLine,
    String district,
    String province,
    BigDecimal latitude,
    BigDecimal longitude,
    String contactEmail,
    String contactPhone,
    LabApplicationStatus status,
    boolean isDuplicateSuspected,
    Set<Long> serviceTypeIds,
    Set<Long> filmProcessIds,
    Set<Long> filmFormatIds,
    List<LabApplicationDocumentResponse> documents,
    Instant createdAt) {}

