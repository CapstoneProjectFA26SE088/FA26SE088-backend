package com.fa26se088.filmlink.config;

import com.google.maps.GeoApiContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cấu hình Google Maps Platform — Geocoding API.
 *
 * <p>Bean {@link GeoApiContext} là thread-safe, singleton; mọi request geocoding đi qua một
 * instance duy nhất (giống cách {@code RestClient} được sử dụng trong GHN delivery provider).
 *
 * <p>Nếu {@code google.maps.api-key} để trống thì bean vẫn được tạo nhưng sẽ log cảnh báo —
 * tương tự cơ chế VNPay ({@code filmlink.vnpay}): app vẫn khởi động, endpoint geocoding sẽ
 * trả {@code 503} khi được gọi.
 */
@Slf4j
@Configuration
public class GoogleMapsConfig {

  @Value("${google.maps.api-key:}")
  private String apiKey;

  @Value("${google.maps.connect-timeout-ms:3000}")
  private long connectTimeoutMs;

  @Value("${google.maps.max-retries:3}")
  private int maxRetries;

  /**
   * Khởi tạo {@link GeoApiContext} singleton được chia sẻ toàn ứng dụng.
   *
   * <p>Nếu API key rỗng, chỉ ghi log WARNING — bean vẫn tồn tại để Spring context khởi động
   * bình thường; {@code GeocodingServiceImpl} sẽ kiểm tra key trước khi gọi API thực.
   *
   * @return {@link GeoApiContext} đã được cấu hình.
   */
  @Bean
  public GeoApiContext geoApiContext() {
    if (apiKey == null || apiKey.isBlank()) {
      log.warn(
          "[GoogleMaps] GOOGLE_MAPS_API_KEY chưa được cấu hình. "
              + "Mọi request đến /api/v1/delivery/geocoding sẽ trả lỗi 503.");
    }

    return new GeoApiContext.Builder()
        .apiKey(apiKey == null ? "" : apiKey)
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .retryTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .maxRetries(maxRetries)
        .build();
  }
}

