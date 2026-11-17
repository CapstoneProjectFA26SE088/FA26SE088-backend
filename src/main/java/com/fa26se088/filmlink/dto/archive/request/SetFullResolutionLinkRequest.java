package com.fa26se088.filmlink.dto.archive.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC26: link file độ phân giải gốc của một cuộn (Google Drive, Dropbox, server của lab…). Khớp
 * {@code archive_rolls.full_resolution_link varchar(1000)} và {@code ck_archive_rolls_link}.
 */
public record SetFullResolutionLinkRequest(
    @NotBlank
        @Size(max = 1000)
        @Pattern(regexp = "(?i)^https?://\\S+$", message = "phải là link http hoặc https")
        String url) {}
