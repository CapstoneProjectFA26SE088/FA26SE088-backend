package com.fa26se088.filmlink.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailUtilsTest {

  @Test
  @DisplayName("normalize: strips spaces and converts to lowercase")
  void testNormalize() {
    assertThat(EmailUtils.normalize("  Test.User@Example.COM  ")).isEqualTo("test.user@example.com");
    assertThat(EmailUtils.normalize(null)).isNull();
  }

  @Test
  @DisplayName("mask: masks email for safe logging")
  void testMask() {
    assertThat(EmailUtils.mask("user@example.com")).isEqualTo("u***r@example.com");
    assertThat(EmailUtils.mask("ab@example.com")).isEqualTo("a***@example.com");
    assertThat(EmailUtils.mask("a@example.com")).isEqualTo("a***@example.com");
    assertThat(EmailUtils.mask("invalid-email")).isEqualTo("invalid-email");
    assertThat(EmailUtils.mask(null)).isNull();
  }
}

