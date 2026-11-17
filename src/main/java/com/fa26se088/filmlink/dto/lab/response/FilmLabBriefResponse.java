package com.fa26se088.filmlink.dto.lab.response;

/**
 * Tên và địa chỉ của một Film Lab cho domain khác hiển thị kèm dữ liệu của mình (ví dụ đơn của
 * Photographer, UC25). Không có thông tin chủ lab hay dữ liệu nội bộ.
 */
public record FilmLabBriefResponse(
    Long id, String labName, String addressLine, String district, String province) {}
