package com.fa26se088.filmlink.client.notification;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailPropertiesTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("Default values for from and fromName are applied when null or blank")
  void defaultValuesApplied() {
    MailProperties properties = new MailProperties("smtp", null, "   ");

    assertThat(properties.provider()).isEqualTo("smtp");
    assertThat(properties.from()).isEqualTo("noreply@filmlink.vn");
    assertThat(properties.fromName()).isEqualTo("FilmLink");
    assertThat(validator.validate(properties)).isEmpty();
  }

  @Test
  @DisplayName("Custom values for from and fromName are preserved")
  void customValuesPreserved() {
    MailProperties properties =
        new MailProperties("smtp", "custom-sender@filmlink.vn", "FilmLink Support");

    assertThat(properties.provider()).isEqualTo("smtp");
    assertThat(properties.from()).isEqualTo("custom-sender@filmlink.vn");
    assertThat(properties.fromName()).isEqualTo("FilmLink Support");
    assertThat(validator.validate(properties)).isEmpty();
  }

  @Test
  @DisplayName("Blank provider violates validation")
  void blankProviderFailsValidation() {
    MailProperties properties = new MailProperties("  ", null, null);

    assertThat(validator.validate(properties)).isNotEmpty();
  }
}

