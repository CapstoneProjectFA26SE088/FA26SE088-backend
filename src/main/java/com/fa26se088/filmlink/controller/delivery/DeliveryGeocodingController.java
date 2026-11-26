package com.fa26se088.filmlink.controller.delivery;

import com.fa26se088.filmlink.dto.delivery.request.GeocodeRequestDTO;
import com.fa26se088.filmlink.dto.delivery.response.GeocodeResponseDTO;
import com.fa26se088.filmlink.service.delivery.GeocodingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEL-GEO-01: API Geocoding (Google Maps Platform) phục vụ giao nhận và định vị địa chỉ.
 *
 * <p>Cung cấp hai chức năng:
 * <ul>
 *   <li><b>Forward Geocoding</b>: Chuyển địa chỉ văn bản thành tọa độ (vĩ độ, kinh độ).
 *   <li><b>Reverse Geocoding</b>: Chuyển tọa độ thành địa chỉ chuẩn hóa và Google Place ID.
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/delivery/geocoding")
@RequiredArgsConstructor
@Tag(
    name = "Delivery Geocoding",
    description = "Tích hợp Google Maps Geocoding API phục vụ xác thực và chuyển đổi tọa độ địa chỉ giao nhận")
public class DeliveryGeocodingController {

  private final GeocodingService geocodingService;

  /**
   * Forward Geocoding qua POST request body.
   *
   * @param request DTO chứa địa chỉ cần chuyển đổi
   * @return {@link ResponseEntity} chứa thông tin tọa độ và địa chỉ chuẩn hóa
   */
  @PostMapping("/forward")
  @Operation(
      summary = "Chuyển địa chỉ thành tọa độ (POST)",
      description =
          "Nhận chuỗi địa chỉ văn bản trong request body, gọi Google Maps Geocoding API để lấy vĩ độ, kinh độ và Place ID.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tìm thấy tọa độ thành công"),
    @ApiResponse(responseCode = "400", description = "Địa chỉ trống hoặc không hợp lệ"),
    @ApiResponse(responseCode = "404", description = "Không tìm thấy kết quả cho địa chỉ này"),
    @ApiResponse(responseCode = "502", description = "Lỗi kết nối đến Google Maps Platform"),
    @ApiResponse(responseCode = "503", description = "Google Maps API Key chưa được cấu hình")
  })
  public ResponseEntity<GeocodeResponseDTO> geocodeAddressPost(
      @Valid @RequestBody GeocodeRequestDTO request) {
    log.info("[DeliveryGeocodingController] POST /forward address='{}'", request.getAddress());
    GeocodeResponseDTO response = geocodingService.geocodeAddress(request.getAddress());
    return ResponseEntity.ok(response);
  }

  /**
   * Forward Geocoding qua GET query parameter (tiện lợi khi gọi trực tiếp từ thanh tìm kiếm).
   *
   * @param address Chuỗi địa chỉ cần tra cứu
   * @return {@link ResponseEntity} chứa thông tin tọa độ và địa chỉ chuẩn hóa
   */
  @GetMapping("/forward")
  @Operation(
      summary = "Chuyển địa chỉ thành tọa độ (GET query)",
      description =
          "Nhận chuỗi địa chỉ qua query parameter 'address', trả về tọa độ và địa chỉ chuẩn hóa.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tìm thấy tọa độ thành công"),
    @ApiResponse(responseCode = "400", description = "Tham số address trống"),
    @ApiResponse(responseCode = "404", description = "Không tìm thấy kết quả"),
    @ApiResponse(responseCode = "502", description = "Lỗi kết nối đến Google Maps Platform"),
    @ApiResponse(responseCode = "503", description = "Google Maps API Key chưa được cấu hình")
  })
  public ResponseEntity<GeocodeResponseDTO> geocodeAddressGet(
      @Parameter(description = "Địa chỉ cần tìm kiếm", example = "170 Trần Duy Hưng, Cầu Giấy, Hà Nội")
          @RequestParam
          String address) {
    log.info("[DeliveryGeocodingController] GET /forward address='{}'", address);
    GeocodeResponseDTO response = geocodingService.geocodeAddress(address);
    return ResponseEntity.ok(response);
  }

  /**
   * Reverse Geocoding qua GET query parameters.
   *
   * @param lat Vĩ độ (latitude) [-90, 90]
   * @param lng Kinh độ (longitude) [-180, 180]
   * @return {@link ResponseEntity} chứa địa chỉ chuẩn hóa tương ứng với tọa độ
   */
  @GetMapping("/reverse")
  @Operation(
      summary = "Chuyển tọa độ thành địa chỉ chuẩn hóa (Reverse Geocoding)",
      description =
          "Nhận cặp tọa độ lat/lng, gọi Google Maps Reverse Geocoding API để tìm địa chỉ thực tế và Place ID.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tìm thấy địa chỉ thành công"),
    @ApiResponse(responseCode = "400", description = "Tọa độ ngoài phạm vi hợp lệ"),
    @ApiResponse(responseCode = "404", description = "Không tìm thấy địa chỉ cho tọa độ này"),
    @ApiResponse(responseCode = "502", description = "Lỗi kết nối đến Google Maps Platform"),
    @ApiResponse(responseCode = "503", description = "Google Maps API Key chưa được cấu hình")
  })
  public ResponseEntity<GeocodeResponseDTO> reverseGeocode(
      @Parameter(description = "Vĩ độ (latitude)", example = "21.0083") @RequestParam double lat,
      @Parameter(description = "Kinh độ (longitude)", example = "105.8411") @RequestParam double lng) {
    log.info("[DeliveryGeocodingController] GET /reverse lat={}, lng={}", lat, lng);
    GeocodeResponseDTO response = geocodingService.reverseGeocode(lat, lng);
    return ResponseEntity.ok(response);
  }
}

