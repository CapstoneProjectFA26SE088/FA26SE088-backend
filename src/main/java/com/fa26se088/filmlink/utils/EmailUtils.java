package com.fa26se088.filmlink.utils;

import java.util.Locale;

/** Hàm tiện ích cho địa chỉ email. */
public final class EmailUtils {

  private EmailUtils() {}

  /**
   * Chuẩn hoá email trước khi lưu và tra cứu: bỏ khoảng trắng hai đầu, chuyển chữ thường. Bảng
   * {@code user_accounts} có CHECK {@code email = lower(email)}.
   */
  public static String normalize(String email) {
    return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
  }

  /**
   * Che giấu một phần địa chỉ email để ghi log an toàn (NFR-PRI-04).
   * Ví dụ: an@example.com -> a***n@example.com.
   */
  public static String mask(String email) {
    if (email == null || !email.contains("@")) {
      return email;
    }
    int atIndex = email.indexOf('@');
    String local = email.substring(0, atIndex);
    String domain = email.substring(atIndex);
    if (local.length() <= 2) {
      return local.charAt(0) + "***" + domain;
    }
    return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
  }
}
