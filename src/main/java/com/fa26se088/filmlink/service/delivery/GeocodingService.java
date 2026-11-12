package com.fa26se088.filmlink.service.delivery;

import com.fa26se088.filmlink.dto.delivery.response.GeocodeResponseDTO;

/**
 * DEL-GEO-01: Dịch vụ Geocoding — tích hợp Google Maps Geocoding API.
 *
 * <p>Hai chiều chuyển đổi:
 * <ul>
 *   <li><b>Forward geocoding</b>: địa chỉ văn bản → tọa độ ({@link #geocodeAddress}).
 *   <li><b>Reverse geocoding</b>: tọa độ → địa chỉ văn bản ({@link #reverseGeocode}).
 * </ul>
 *
 * <p>Dùng trong domain Delivery để xác định tọa độ giao / nhận hàng; sau này có thể mở rộng
 * sang các domain khác (Film Lab location, v.v.).
 */
public interface GeocodingService {

  /**
   * Chuyển địa chỉ văn bản thành tọa độ địa lý (Forward Geocoding).
   *
   * @param address địa chỉ tự do, nên có tỉnh / thành phố để tăng độ chính xác.
   * @return {@link GeocodeResponseDTO} chứa tọa độ và địa chỉ chuẩn hóa của kết quả đầu tiên.
   * @throws com.fa26se088.filmlink.exception.BusinessException khi API key chưa cấu hình,
   *     địa chỉ không tìm thấy kết quả, hoặc lỗi kết nối đến Google.
   */
  GeocodeResponseDTO geocodeAddress(String address);

  /**
   * Chuyển tọa độ địa lý thành địa chỉ văn bản (Reverse Geocoding).
   *
   * @param lat  vĩ độ, phạm vi hợp lệ [-90, 90].
   * @param lng  kinh độ, phạm vi hợp lệ [-180, 180].
   * @return {@link GeocodeResponseDTO} chứa địa chỉ chuẩn hóa và Place ID của kết quả đầu tiên.
   * @throws com.fa26se088.filmlink.exception.BusinessException khi tọa độ không có địa chỉ
   *     tương ứng hoặc lỗi kết nối đến Google.
   */
  GeocodeResponseDTO reverseGeocode(double lat, double lng);
}

