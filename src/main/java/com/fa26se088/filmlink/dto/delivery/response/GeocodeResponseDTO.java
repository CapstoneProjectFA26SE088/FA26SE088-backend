package com.fa26se088.filmlink.dto.delivery.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DEL-GEO-01: Kết quả geocoding / reverse-geocoding từ Google Maps Geocoding API.
 *
 * <p>Ánh xạ từ {@code GeocodingResult[0]} trả về bởi API:
 * <ul>
 *   <li>{@code formattedAddress} — địa chỉ chuẩn hóa do Google cung cấp.
 *   <li>{@code latitude} / {@code longitude} — tọa độ trung tâm (geometry.location).
 *   <li>{@code placeId} — Google Place ID, dùng để tra cứu chi tiết địa điểm sau này.
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeResponseDTO {

  /** Địa chỉ đã được Google chuẩn hóa, ví dụ: "170 Trần Duy Hưng, Cầu Giấy, Hà Nội". */
  private String formattedAddress;

  /** Vĩ độ (latitude), ví dụ: 21.0083. */
  private double latitude;

  /** Kinh độ (longitude), ví dụ: 105.8411. */
  private double longitude;

  /**
   * Google Place ID — mã định danh duy nhất của địa điểm trong Google Maps.
   * Dùng để gọi Places API hoặc lấy đường link Google Maps.
   */
  private String placeId;
}

