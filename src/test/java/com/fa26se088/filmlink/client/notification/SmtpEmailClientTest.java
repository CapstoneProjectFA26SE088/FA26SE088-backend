package com.fa26se088.filmlink.client.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailClientTest {

  private JavaMailSenderImpl mailSender;
  private MailProperties mailProperties;
  private SmtpEmailClient smtpEmailClient;

  @BeforeEach
  void setUp() {
    mailSender = spy(new JavaMailSenderImpl());
    mailProperties = new MailProperties("smtp", "noreply@filmlink.vn", "FilmLink");
    smtpEmailClient = new SmtpEmailClient(mailSender, mailProperties);
  }

  @Test
  @DisplayName("send: plain text email sets UTF-8 subject, recipient, from and body correctly")
  void sendPlainTextEmailSuccess() throws Exception {
    doNothing().when(mailSender).send(any(MimeMessage.class));

    String recipient = "photographer@example.com";
    String subject = "FilmLink - Mã xác nhận OTP: 123456";
    String body = "Xin chào bạn, mã xác thực của bạn là 123456.";

    smtpEmailClient.send(recipient, subject, body);

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());

    MimeMessage sentMessage = captor.getValue();
    assertThat(sentMessage.getAllRecipients()[0].toString()).isEqualTo(recipient);
    assertThat(sentMessage.getSubject()).isEqualTo(subject);
    assertThat(sentMessage.getFrom()[0].toString()).contains("noreply@filmlink.vn");
    assertThat(sentMessage.getFrom()[0].toString()).contains("FilmLink");
    assertThat(sentMessage.getContent().toString().trim()).isEqualTo(body);
  }

  @Test
  @DisplayName("sendHtml: HTML email sets HTML content type and body correctly")
  void sendHtmlEmailSuccess() throws Exception {
    doNothing().when(mailSender).send(any(MimeMessage.class));

    String recipient = "photographer@example.com";
    String subject = "FilmLink - Thông báo đơn hàng mới";
    String htmlBody = "<h1>Đơn hàng đã được nhận</h1><p>Vui lòng kiểm tra ứng dụng.</p>";

    smtpEmailClient.sendHtml(recipient, subject, htmlBody);

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());

    MimeMessage sentMessage = captor.getValue();
    sentMessage.saveChanges();
    assertThat(sentMessage.getAllRecipients()[0].toString()).isEqualTo(recipient);
    assertThat(sentMessage.getSubject()).isEqualTo(subject);
    assertThat(sentMessage.getContentType()).contains("text/html");
    assertThat(sentMessage.getContent().toString().trim()).isEqualTo(htmlBody);
  }

  @Test
  @DisplayName("send: throws BusinessException with EMAIL_SEND_FAILED when MailSender fails")
  void sendThrowsBusinessExceptionOnMailError() {
    doThrow(new MailSendException("SMTP server connection timeout"))
        .when(mailSender)
        .send(any(MimeMessage.class));

    assertThatThrownBy(() -> smtpEmailClient.send("test@example.com", "Test Subject", "Test Body"))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException be = (BusinessException) ex;
          assertThat(be.getErrorCode()).isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
          assertThat(be.getMessage()).contains("Không thể gửi email");
        });
  }
}
