package com.fa26se088.filmlink.service.delivery.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.google.maps.GeoApiContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceImplTest {

  @Mock private GeoApiContext geoApiContext;

  private GeocodingServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new GeocodingServiceImpl(geoApiContext);
  }

  @Test
  @DisplayName("geocodeAddress ném GEOCODING_SERVICE_UNAVAILABLE khi chưa cấu hình API key")
  void geocodeAddress_missingApiKey_throws503() {
    ReflectionTestUtils.setField(service, "apiKey", "");

    assertThatThrownBy(() -> service.geocodeAddress("170 Tran Duy Hung"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEOCODING_SERVICE_UNAVAILABLE));
  }

  @Test
  @DisplayName("geocodeAddress ném VALIDATION_ERROR khi địa chỉ rỗng")
  void geocodeAddress_blankAddress_throwsValidationError() {
    ReflectionTestUtils.setField(service, "apiKey", "test-api-key");

    assertThatThrownBy(() -> service.geocodeAddress("   "))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
  }

  @Test
  @DisplayName("reverseGeocode ném GEOCODING_SERVICE_UNAVAILABLE khi chưa cấu hình API key")
  void reverseGeocode_missingApiKey_throws503() {
    ReflectionTestUtils.setField(service, "apiKey", null);

    assertThatThrownBy(() -> service.reverseGeocode(21.0, 105.0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEOCODING_SERVICE_UNAVAILABLE));
  }

  @Test
  @DisplayName("reverseGeocode ném VALIDATION_ERROR khi vĩ độ ngoài phạm vi [-90, 90]")
  void reverseGeocode_invalidLatitude_throwsValidationError() {
    ReflectionTestUtils.setField(service, "apiKey", "test-api-key");

    assertThatThrownBy(() -> service.reverseGeocode(95.0, 105.0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
  }

  @Test
  @DisplayName("reverseGeocode ném VALIDATION_ERROR khi kinh độ ngoài phạm vi [-180, 180]")
  void reverseGeocode_invalidLongitude_throwsValidationError() {
    ReflectionTestUtils.setField(service, "apiKey", "test-api-key");

    assertThatThrownBy(() -> service.reverseGeocode(21.0, 200.0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
  }
}

