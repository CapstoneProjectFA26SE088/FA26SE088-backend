package com.fa26se088.filmlink.client.notification;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình gửi email, prefix {@code filmlink.mail} trong {@code application.yml}.
 *
 * @param provider bản {@link EmailClient} được dùng: {@code log} hoặc {@code smtp}
 * @param from địa chỉ email người gửi mặc định (From header)
 * @param fromName tên hiển thị người gửi mặc định
 */
@Validated
@ConfigurationProperties(prefix = "filmlink.mail")
public record MailProperties(
    @NotBlank String provider,
    String from,
    String fromName) {

  public MailProperties {
    if (from == null || from.isBlank()) {
      from = "noreply@filmlink.vn";
    }
    if (fromName == null || fromName.isBlank()) {
      fromName = "FilmLink";
    }
  }
}

