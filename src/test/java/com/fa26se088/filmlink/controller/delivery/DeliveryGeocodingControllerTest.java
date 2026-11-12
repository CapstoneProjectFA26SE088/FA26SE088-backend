package com.fa26se088.filmlink.controller.delivery;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.dto.delivery.request.GeocodeRequestDTO;
import com.fa26se088.filmlink.dto.delivery.response.GeocodeResponseDTO;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.exception.GlobalExceptionHandler;
import com.fa26se088.filmlink.service.delivery.GeocodingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DeliveryGeocodingControllerTest {

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private GeocodingService geocodingService;

  @BeforeEach
  void setUp() {
    DeliveryGeocodingController controller = new DeliveryGeocodingController(geocodingService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private GeocodeResponseDTO sampleResponse() {
    return GeocodeResponseDTO.builder()
        .formattedAddress("170 Trần Duy Hưng, Trung Hoà, Cầu Giấy, Hà Nội, Việt Nam")
        .latitude(21.0083)
        .longitude(105.8411)
        .placeId("ChIJ123456789")
        .build();
  }

  @Test
  @DisplayName("POST /api/v1/delivery/geocoding/forward thành công trả 200 và tọa độ")
  void geocodeAddressPost_success_returns200() throws Exception {
    when(geocodingService.geocodeAddress("170 Trần Duy Hưng")).thenReturn(sampleResponse());

    GeocodeRequestDTO request = new GeocodeRequestDTO("170 Trần Duy Hưng");

    mockMvc
        .perform(
            post("/api/v1/delivery/geocoding/forward")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.latitude").value(21.0083))
        .andExpect(jsonPath("$.longitude").value(105.8411))
        .andExpect(jsonPath("$.placeId").value("ChIJ123456789"))
        .andExpect(jsonPath("$.formattedAddress").value("170 Trần Duy Hưng, Trung Hoà, Cầu Giấy, Hà Nội, Việt Nam"));
  }

  @Test
  @DisplayName("POST /api/v1/delivery/geocoding/forward địa chỉ rỗng trả 400 Bad Request")
  void geocodeAddressPost_blankAddress_returns400() throws Exception {
    GeocodeRequestDTO request = new GeocodeRequestDTO("");

    mockMvc
        .perform(
            post("/api/v1/delivery/geocoding/forward")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v1/delivery/geocoding/forward thành công trả 200")
  void geocodeAddressGet_success_returns200() throws Exception {
    when(geocodingService.geocodeAddress("170 Trần Duy Hưng")).thenReturn(sampleResponse());

    mockMvc
        .perform(get("/api/v1/delivery/geocoding/forward").param("address", "170 Trần Duy Hưng"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.latitude").value(21.0083))
        .andExpect(jsonPath("$.placeId").value("ChIJ123456789"));
  }

  @Test
  @DisplayName("GET /api/v1/delivery/geocoding/reverse thành công trả 200 và địa chỉ")
  void reverseGeocode_success_returns200() throws Exception {
    when(geocodingService.reverseGeocode(21.0083, 105.8411)).thenReturn(sampleResponse());

    mockMvc
        .perform(
            get("/api/v1/delivery/geocoding/reverse")
                .param("lat", "21.0083")
                .param("lng", "105.8411"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.formattedAddress").value("170 Trần Duy Hưng, Trung Hoà, Cầu Giấy, Hà Nội, Việt Nam"))
        .andExpect(jsonPath("$.placeId").value("ChIJ123456789"));
  }

  @Test
  @DisplayName("Không tìm thấy kết quả trả 404 GEOCODING_NO_RESULT")
  void geocodeAddress_noResult_returns404() throws Exception {
    when(geocodingService.geocodeAddress(anyString()))
        .thenThrow(new BusinessException(ErrorCode.GEOCODING_NO_RESULT, "Không tìm thấy kết quả"));

    mockMvc
        .perform(get("/api/v1/delivery/geocoding/forward").param("address", "DiaChiKhongTonTai9999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("GEOCODING_NO_RESULT"));
  }

  @Test
  @DisplayName("API key chưa cấu hình trả 503 GEOCODING_SERVICE_UNAVAILABLE")
  void geocodeAddress_missingApiKey_returns503() throws Exception {
    when(geocodingService.geocodeAddress(anyString()))
        .thenThrow(
            new BusinessException(
                ErrorCode.GEOCODING_SERVICE_UNAVAILABLE, "Dịch vụ Geocoding chưa được cấu hình"));

    mockMvc
        .perform(get("/api/v1/delivery/geocoding/forward").param("address", "Hanoi"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("GEOCODING_SERVICE_UNAVAILABLE"));
  }
}

