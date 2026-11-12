package com.fa26se088.filmlink.dto.delivery.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DEL-GEO-01: Yêu cầu geocoding — chuyển địa chỉ văn bản thành tọa độ địa lý.
 *
 * <p>Trường {@code address} nên được truyền ở định dạng đầy đủ, ví dụ:
 * {@code "170 Trần Duy Hưng, Cầu Giấy, Hà Nội, Việt Nam"}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeRequestDTO {

  /**
   * Địa chỉ văn bản (free-text) cần geocode.
   * Khuyến nghị kèm tỉnh / thành phố để tăng độ chính xác (theo khuyến nghị Google).
   */
  @NotBlank(message = "Địa chỉ không được để trống")
  @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
  private String address;
}

