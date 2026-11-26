package com.fa26se088.filmlink.client.storage;

import java.util.Set;

/**
 * Mục đích của file upload: quyết định loại file cho phép, dung lượng tối đa và tiền tố blobKey.
 *
 * <p>Chỉ gồm các mục SRS đã ghi rõ giới hạn. Mục đích khác (preview scan, bằng chứng bàn giao…) do
 * task của domain tương ứng thêm vào.
 */
public enum StoragePurpose {
  /** Ảnh đại diện tài khoản: JPG/PNG, tối đa 5 MB (SCR-05). */
  AVATAR("avatar", 5 * StoragePurpose.MB, Set.of(StorageFileType.JPEG, StorageFileType.PNG)),

  /** Giấy tờ pháp lý trong hồ sơ đăng ký lab: PDF/JPG/PNG, tối đa 10 MB mỗi file (UC07). */
  LAB_DOCUMENT(
      "lab-document",
      10 * StoragePurpose.MB,
      Set.of(StorageFileType.PDF, StorageFileType.JPEG, StorageFileType.PNG)),

  /** Ảnh mẫu của Film Lab: JPG/PNG, tối đa 10 MB. */
  LAB_IMAGE("lab-image", 10 * StoragePurpose.MB, Set.of(StorageFileType.JPEG, StorageFileType.PNG)),

  /** Ảnh bằng chứng bàn giao phim (BR-25; UC23 1–10 ảnh): JPG/PNG, tối đa 10 MB mỗi file. */
  HANDOVER_PHOTO(
      "handover-photo", 10 * StoragePurpose.MB, Set.of(StorageFileType.JPEG, StorageFileType.PNG)),

  /**
   * Ảnh xem trước (Preview Scan) của một cuộn (UC26, BR-08): chỉ JPG, tối đa 5 MB ({@code
   * ck_scan_files_size}); cạnh dài ≤ 2048 px do domain archive kiểm.
   */
  PREVIEW_SCAN("preview-scan", 5 * StoragePurpose.MB, Set.of(StorageFileType.JPEG)),

  /** Biên lai Admin chuyển khoản chi trả cho lab (UC35, BR-31): PDF/JPG/PNG, tối đa 10 MB. */
  PAYOUT_RECEIPT(
      "payout-receipt",
      10 * StoragePurpose.MB,
      Set.of(StorageFileType.PDF, StorageFileType.JPEG, StorageFileType.PNG));
  /** Ảnh bằng chứng cuộn phim hỏng (UC24, BR-26): JPG/PNG, tối đa 10 MB mỗi file. */
  DAMAGE_PHOTO(
      "damage-photo", 10 * StoragePurpose.MB, Set.of(StorageFileType.JPEG, StorageFileType.PNG));

  private static final long MB = 1024L * 1024L;

  private final String keyPrefix;
  private final long maxSizeBytes;
  private final Set<StorageFileType> allowedTypes;

  StoragePurpose(String keyPrefix, long maxSizeBytes, Set<StorageFileType> allowedTypes) {
    this.keyPrefix = keyPrefix;
    this.maxSizeBytes = maxSizeBytes;
    this.allowedTypes = allowedTypes;
  }

  /** Đoạn đầu của blobKey, ví dụ {@code lab-document}. */
  public String keyPrefix() {
    return keyPrefix;
  }

  public long maxSizeBytes() {
    return maxSizeBytes;
  }

  public Set<StorageFileType> allowedTypes() {
    return allowedTypes;
  }
}
