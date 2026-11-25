/**
 * Client hệ thống bên ngoài — lưu trữ file (ảnh, giấy tờ, scan).
 *
 * <p>Code nghiệp vụ chỉ dùng interface {@link com.fa26se088.filmlink.client.storage.StorageClient};
 * bản dùng chọn bằng {@code filmlink.storage.provider}:
 *
 * <ul>
 *   <li>{@code local} (mặc định, máy dev): {@code LocalStorageClient} lưu trong {@code
 *       STORAGE_LOCAL_ROOT}, link tải {@code GET /api/v1/files} ký HMAC-SHA256.
 *   <li>{@code azure} (khi deploy): {@code AzureBlobStorageClient} lưu trong container private, link tải
 *       là SAS chỉ đọc.
 * </ul>
 */
package com.fa26se088.filmlink.client.storage;
