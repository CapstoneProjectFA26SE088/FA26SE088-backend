package com.fa26se088.filmlink.client.notification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggingEmailClientTest {

  private final LoggingEmailClient loggingEmailClient = new LoggingEmailClient();

  @Test
  @DisplayName("LoggingEmailClient executes send and sendHtml without error")
  void testLoggingClient() {
    assertDoesNotThrow(() -> loggingEmailClient.send("test@example.com", "Subject", "Body"));
    assertDoesNotThrow(() -> loggingEmailClient.sendHtml("test@example.com", "Subject", "<h1>Html</h1>"));
  }
}

