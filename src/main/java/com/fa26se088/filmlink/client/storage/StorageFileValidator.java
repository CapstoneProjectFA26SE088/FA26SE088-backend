package com.fa26se088.filmlink.client.storage;

import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.stream.Collectors;

/** Kiểm loại file và dung lượng theo {@link StoragePurpose}, dùng chung cho mọi provider. */
final class StorageFileValidator {

  private StorageFileValidator() {}

  static void checkSize(StoragePurpose purpose, long size) {
    if (size > purpose.maxSizeBytes()) {
      throw new BusinessException(
          ErrorCode.STORAGE_FILE_TOO_LARGE,
          "File vượt quá dung lượng cho phép (" + purpose.maxSizeBytes() / (1024 * 1024) + " MB).");
    }
  }

  /** Nhận biết loại file bằng magic bytes; sau khi gọi, {@code input} vẫn ở đầu file. */
  static StorageFileType detectAllowedType(StoragePurpose purpose, BufferedInputStream input)
      throws IOException {
    input.mark(StorageFileType.HEADER_LENGTH);
    byte[] header = input.readNBytes(StorageFileType.HEADER_LENGTH);
    input.reset();
    return StorageFileType.detect(header)
        .filter(purpose.allowedTypes()::contains)
        .orElseThrow(() -> typeNotAllowed(purpose));
  }

  private static BusinessException typeNotAllowed(StoragePurpose purpose) {
    String allowed =
        purpose.allowedTypes().stream()
            .map(type -> type.extension().toUpperCase(Locale.ROOT))
            .sorted()
            .collect(Collectors.joining(", "));
    return new BusinessException(
        ErrorCode.STORAGE_FILE_TYPE_NOT_ALLOWED, "Loại file không được hỗ trợ. Chỉ nhận: " + allowed);
  }
}
