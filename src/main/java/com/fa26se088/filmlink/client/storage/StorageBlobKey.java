package com.fa26se088.filmlink.client.storage;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Sinh và kiểm blobKey {@code <purpose>/<yyyy>/<MM>/<uuid>.<ext>}, dùng chung cho mọi provider. */
final class StorageBlobKey {

  private static final DateTimeFormatter KEY_MONTH =
      DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

  /** Chặn mọi key không do server sinh. */
  private static final Pattern PATTERN =
      Pattern.compile(
          "("
              + alternatives(Arrays.stream(StoragePurpose.values()).map(StoragePurpose::keyPrefix))
              + ")/\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.("
              + alternatives(Arrays.stream(StorageFileType.values()).map(StorageFileType::extension))
              + ")");

  private StorageBlobKey() {}

  static String generate(StoragePurpose purpose, StorageFileType type, Instant now) {
    return purpose.keyPrefix()
        + "/"
        + KEY_MONTH.format(now)
        + "/"
        + UUID.randomUUID()
        + "."
        + type.extension();
  }

  static boolean isValid(String blobKey) {
    return blobKey != null && PATTERN.matcher(blobKey).matches();
  }

  /** blobKey lấy từ DB mà sai mẫu là lỗi lập trình, không phải lỗi người dùng. */
  static void requireValid(String blobKey) {
    if (!isValid(blobKey)) {
      throw new IllegalArgumentException("blobKey không đúng mẫu <purpose>/<yyyy>/<MM>/<uuid>.<ext>");
    }
  }

  private static String alternatives(Stream<String> values) {
    return values.map(Pattern::quote).collect(Collectors.joining("|"));
  }
}
