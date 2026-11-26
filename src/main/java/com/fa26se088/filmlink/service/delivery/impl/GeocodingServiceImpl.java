package com.fa26se088.filmlink.service.delivery.impl;

import com.fa26se088.filmlink.dto.delivery.response.GeocodeResponseDTO;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.service.delivery.GeocodingService;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * DEL-GEO-01: Triển khai {@link GeocodingService} sử dụng Google Maps Geocoding API.
 *
 * <p>Quy tắc xử lý lỗi (bám kiến trúc R5 — validation / business rule tại service layer):
 * <ul>
 *   <li>API key rỗng → {@link ErrorCode#GEOCODING_SERVICE_UNAVAILABLE} (503).
 *   <li>Google trả danh sách rỗng → {@link ErrorCode#GEOCODING_NO_RESULT} (404).
 *   <li>Lỗi mạng / timeout / quota → {@link ErrorCode#GEOCODING_API_ERROR} (502).
 * </ul>
 *
 * <p>Luôn lấy phần tử đầu tiên [{@code results[0]}] — đây là kết quả khớp nhất theo tài liệu
 * Google Maps Geocoding API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingServiceImpl implements GeocodingService {

  private final GeoApiContext geoApiContext;

  @Value("${google.maps.api-key:}")
  private String apiKey;

  // -------------------------------------------------------------------------
  // Forward Geocoding: address → coordinates
  // -------------------------------------------------------------------------

  /**
   * {@inheritDoc}
   *
   * <p>Triển khai: gọi {@code GeocodingApi.geocode(context, address).await()}, lấy
   * {@code results[0]}, map sang {@link GeocodeResponseDTO}.
   */
  @Override
  public GeocodeResponseDTO geocodeAddress(String address) {
    guardApiKey();

    if (address == null || address.isBlank()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Địa chỉ không được để trống.");
    }

    log.debug("[Geocoding] Forward geocode: address='{}'", address);

    GeocodingResult[] results;
    try {
      results = GeocodingApi.geocode(geoApiContext, address.strip()).await();
    } catch (Exception ex) {
      log.error("[Geocoding] Lỗi khi gọi Google Geocoding API (forward): {}", ex.getMessage(), ex);
      throw new BusinessException(
          ErrorCode.GEOCODING_API_ERROR,
          "Không thể kết nối đến dịch vụ Geocoding. Vui lòng thử lại sau.");
    }

    if (results == null || results.length == 0) {
      log.warn("[Geocoding] Không tìm thấy kết quả cho địa chỉ: '{}'", address);
      throw new BusinessException(
          ErrorCode.GEOCODING_NO_RESULT,
          "Không tìm thấy tọa độ cho địa chỉ: " + address);
    }

    GeocodingResult best = results[0];
    log.info(
        "[Geocoding] Forward geocode OK: '{}' → ({}, {}), placeId={}",
        address,
        best.geometry.location.lat,
        best.geometry.location.lng,
        best.placeId);

    return GeocodeResponseDTO.builder()
        .formattedAddress(best.formattedAddress)
        .latitude(best.geometry.location.lat)
        .longitude(best.geometry.location.lng)
        .placeId(best.placeId)
        .build();
  }

  // -------------------------------------------------------------------------
  // Reverse Geocoding: coordinates → address
  // -------------------------------------------------------------------------

  /**
   * {@inheritDoc}
   *
   * <p>Triển khai: gọi {@code GeocodingApi.reverseGeocode(context, latlng).await()}, lấy
   * {@code results[0]}, map sang {@link GeocodeResponseDTO}.
   */
  @Override
  public GeocodeResponseDTO reverseGeocode(double lat, double lng) {
    guardApiKey();
    guardLatLng(lat, lng);

    log.debug("[Geocoding] Reverse geocode: lat={}, lng={}", lat, lng);

    GeocodingResult[] results;
    try {
      results = GeocodingApi.reverseGeocode(geoApiContext, new LatLng(lat, lng)).await();
    } catch (Exception ex) {
      log.error(
          "[Geocoding] Lỗi khi gọi Google Geocoding API (reverse) lat={} lng={}: {}",
          lat, lng, ex.getMessage(), ex);
      throw new BusinessException(
          ErrorCode.GEOCODING_API_ERROR,
          "Không thể kết nối đến dịch vụ Geocoding. Vui lòng thử lại sau.");
    }

    if (results == null || results.length == 0) {
      log.warn("[Geocoding] Không tìm thấy địa chỉ cho tọa độ ({}, {})", lat, lng);
      throw new BusinessException(
          ErrorCode.GEOCODING_NO_RESULT,
          String.format("Không tìm thấy địa chỉ cho tọa độ (%.6f, %.6f).", lat, lng));
    }

    GeocodingResult best = results[0];
    log.info(
        "[Geocoding] Reverse geocode OK: ({}, {}) → '{}', placeId={}",
        lat, lng, best.formattedAddress, best.placeId);

    return GeocodeResponseDTO.builder()
        .formattedAddress(best.formattedAddress)
        .latitude(best.geometry.location.lat)
        .longitude(best.geometry.location.lng)
        .placeId(best.placeId)
        .build();
  }

  // -------------------------------------------------------------------------
  // Guard methods (private)
  // -------------------------------------------------------------------------

  /** Kiểm tra API key đã được cấu hình; nếu chưa thì ném 503 ngay, không gọi Google. */
  private void guardApiKey() {
    if (apiKey == null || apiKey.isBlank()) {
      throw new BusinessException(
          ErrorCode.GEOCODING_SERVICE_UNAVAILABLE,
          "Dịch vụ Geocoding chưa được cấu hình. Liên hệ quản trị viên.");
    }
  }

  /** Kiểm tra phạm vi lat/lng hợp lệ theo tiêu chuẩn WGS-84. */
  private static void guardLatLng(double lat, double lng) {
    if (lat < -90 || lat > 90) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Vĩ độ (lat) phải nằm trong khoảng [-90, 90]. Giá trị nhận: " + lat);
    }
    if (lng < -180 || lng > 180) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          "Kinh độ (lng) phải nằm trong khoảng [-180, 180]. Giá trị nhận: " + lng);
    }
  }
}

