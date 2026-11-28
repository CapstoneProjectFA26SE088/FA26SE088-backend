package com.fa26se088.filmlink.dto.marketplace.response;

import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentPhotoSource;

/** Ảnh món đồ hoặc ảnh bàn giao ký gửi kèm link tải ký hạn 15 phút. */
public record ConsignmentPhotoResponse(
    Long id,
    ConsignmentPhotoSource source,
    Short displayOrder,
    String blobKey,
    String downloadUrl) {}
