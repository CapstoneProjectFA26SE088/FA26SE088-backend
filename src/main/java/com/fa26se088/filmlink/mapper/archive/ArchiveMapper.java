package com.fa26se088.filmlink.mapper.archive;

import com.fa26se088.filmlink.dto.archive.response.ArchiveRollResponse;
import com.fa26se088.filmlink.dto.archive.response.ArchiveRollSummaryResponse;
import com.fa26se088.filmlink.dto.archive.response.ScanFileResponse;
import com.fa26se088.filmlink.entity.archive.ArchiveRoll;
import com.fa26se088.filmlink.entity.archive.ScanFile;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Chuyển Archive Roll và Scan File sang DTO. Viết tay; link có chữ ký do service tạo và truyền
 * vào.
 */
@Component
public class ArchiveMapper {

  public ScanFileResponse toScanFileResponse(ScanFile scan, URI signedUrl) {
    return new ScanFileResponse(
        scan.getId(),
        scan.getFrameNumber(),
        scan.getWidthPx(),
        scan.getHeightPx(),
        scan.getSizeBytes(),
        scan.getChecksumSha256(),
        signedUrl,
        scan.getCreatedAt());
  }

  public ArchiveRollSummaryResponse toSummaryResponse(
      ArchiveRoll roll, long previewCount, URI coverPreviewUrl) {
    return new ArchiveRollSummaryResponse(
        roll.getId(), roll.getFilmRollId(), roll.getDeliveredAt(), previewCount, coverPreviewUrl);
  }

  public ArchiveRollResponse toResponse(ArchiveRoll roll, List<ScanFileResponse> previews) {
    return new ArchiveRollResponse(
        roll.getId(),
        roll.getFilmRollId(),
        roll.getFullResolutionLink(),
        roll.getLinkVerifiedAt(),
        roll.getDeliveredAt(),
        roll.getCamera(),
        roll.getLens(),
        roll.getShotOn(),
        roll.getLocation(),
        roll.getNote(),
        roll.getFrameCountDiscrepancyNote(),
        previews);
  }
}
