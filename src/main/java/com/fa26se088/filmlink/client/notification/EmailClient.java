package com.fa26se088.filmlink.client.notification;

/**
 * Interface trừu tượng gửi email (R9). Bản dùng được chọn bằng property {@code filmlink.mail.provider}:
 *
 * <ul>
 *   <li>{@code log}: {@link LoggingEmailClient}, chỉ ghi nội dung ra log (máy local / dev).
 *   <li>{@code smtp}: {@link SmtpEmailClient}, gửi email thật qua máy chủ SMTP (Gmail, SendGrid,...).
 * </ul>
 */
public interface EmailClient {

  /**
   * Gửi một email văn bản thuần (plain text).
   *
   * @param recipient địa chỉ người nhận
   * @param subject tiêu đề email
   * @param body nội dung văn bản thuần
   */
  void send(String recipient, String subject, String body);

  /**
   * Gửi một email định dạng HTML.
   *
   * @param recipient địa chỉ người nhận
   * @param subject tiêu đề email
   * @param htmlBody nội dung HTML
   */
  default void sendHtml(String recipient, String subject, String htmlBody) {
    send(recipient, subject, htmlBody);
  }
}
