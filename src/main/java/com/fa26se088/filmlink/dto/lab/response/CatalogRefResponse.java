package com.fa26se088.filmlink.dto.lab.response;

/**
 * Tham chiếu tới một mục master data (Service Type, Film Process, Film Format) kèm tên, để trang
 * công khai không phải tra thêm API danh mục. Trang công khai chỉ hiện gói có cả ba mục còn ACTIVE
 * (BR-16).
 */
public record CatalogRefResponse(Long id, String code, String displayName) {}
