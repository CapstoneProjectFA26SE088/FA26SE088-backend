package com.fa26se088.filmlink.mapper.delivery;

import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverPhotoResponse;
import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverResponse;
import com.fa26se088.filmlink.entity.delivery.FilmHandover;
import com.fa26se088.filmlink.entity.delivery.FilmHandoverPhoto;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

/** Chuyển Film Handover sang DTO. Viết tay; link ảnh có chữ ký do service tạo và truyền vào. */
@Component
public class FilmHandoverMapper {

  public FilmHandoverResponse toResponse(
      FilmHandover handover, List<FilmHandoverPhotoResponse> photos) {
    return new FilmHandoverResponse(
        handover.getId(),
        handover.getHandoverPoint(),
        handover.getRollCount(),
        handover.getNote(),
        handover.getPerformedByAccountId(),
        handover.getPerformedByName(),
        handover.getOccurredAt(),
        photos);
  }

  public FilmHandoverPhotoResponse toPhotoResponse(FilmHandoverPhoto photo, URI signedUrl) {
    return new FilmHandoverPhotoResponse(photo.getId(), photo.getDisplayOrder(), signedUrl);
  }
}
