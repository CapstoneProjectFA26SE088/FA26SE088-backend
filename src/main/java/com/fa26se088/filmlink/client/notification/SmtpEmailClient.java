package com.fa26se088.filmlink.client.notification;

import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.utils.EmailUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Hiện thực {@link EmailClient} gửi email thật qua giao thức SMTP bằng {@link JavaMailSender}.
 *
 * <p>Được kích hoạt khi cấu hình {@code filmlink.mail.provider=smtp}.
 * Sử dụng mã hoá UTF-8 cho tiêu đề và nội dung để hiển thị chính xác tiếng Việt.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "filmlink.mail.provider", havingValue = "smtp")
@RequiredArgsConstructor
public class SmtpEmailClient implements EmailClient {

  private final JavaMailSender mailSender;
  private final MailProperties mailProperties;

  @Override
  public void send(String recipient, String subject, String body) {
    sendInternal(recipient, subject, body, false);
  }

  @Override
  public void sendHtml(String recipient, String subject, String htmlBody) {
    sendInternal(recipient, subject, htmlBody, true);
  }

  private void sendInternal(String recipient, String subject, String content, boolean isHtml) {
    String maskedRecipient = EmailUtils.mask(recipient);
    log.info(
        "[MAIL provider=smtp] Đang gửi email (HTML={}) tới: {}, Subject: '{}'",
        isHtml,
        maskedRecipient,
        subject);

    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());

      String fromAddress = mailProperties.from();
      String fromName = mailProperties.fromName();

      if (fromAddress != null && !fromAddress.isBlank()) {
        if (fromName != null && !fromName.isBlank()) {
          helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
        } else {
          helper.setFrom(fromAddress);
        }
      }

      helper.setTo(recipient);
      helper.setSubject(subject);
      helper.setText(content, isHtml);

      mailSender.send(mimeMessage);
      log.info("[MAIL provider=smtp] Đã gửi thành công email tới: {}", maskedRecipient);
    } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
      log.error(
          "[MAIL provider=smtp] Gửi email thất bại tới {}: {}",
          maskedRecipient,
          ex.getMessage(),
          ex);
      throw new BusinessException(
          ErrorCode.EMAIL_SEND_FAILED, "Không thể gửi email qua dịch vụ SMTP. Vui lòng thử lại sau.");
    }
  }
}

