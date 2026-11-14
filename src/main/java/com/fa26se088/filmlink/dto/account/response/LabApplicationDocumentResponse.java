package com.fa26se088.filmlink.dto.account.response;

/**
 * Thông tin tài liệu đính kèm trong đơn đăng ký lab (UC07, UC08).
 *
 * @param id ID tài liệu
 * @param fileName tên file gốc
 * @param contentType loại MIME của file
 * @param sizeBytes dung lượng file (bytes)
 * @param url đường dẫn tải file (signed URL)
 */
public record LabApplicationDocumentResponse(
    Long id,
    String fileName,
    String contentType,
    Long sizeBytes,
    String url) {}

