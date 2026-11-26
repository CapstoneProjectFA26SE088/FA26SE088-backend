package com.fa26se088.filmlink.dto.delivery.response;

import java.net.URI;

/** Một ảnh bằng chứng bàn giao; {@code url} là link có chữ ký, hết hạn sau 15 phút (NFR-PRI-02). */
public record FilmHandoverPhotoResponse(Long id, Short displayOrder, URI url) {}
