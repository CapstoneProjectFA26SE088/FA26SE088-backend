package com.fa26se088.filmlink.dto.delivery.request;

import com.fa26se088.filmlink.entity.delivery.enums.HandoverPoint;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lệnh ghi một lần bàn giao phim (BR-25), truyền vào {@code FilmHandoverService} (không phải body
 * HTTP). Domain gọi tự kiểm nghiệp vụ của mình (trạng thái đơn, quyền) trước khi gọi.
 *
 * @param serviceOrderId đơn có phim được bàn giao, bắt buộc
 * @param shipmentId chuyến giao nhận liên quan, có thể {@code null} (ví dụ Self Drop-off)
 * @param handoverPoint điểm bàn giao, bắt buộc
 * @param rollCount số cuộn thực tế đổi tay, 0–25 ({@code ck_film_handovers_roll_count})
 * @param note ghi chú ≤ 500 ký tự, có thể {@code null}
 * @param performedByAccountId tài khoản thực hiện; {@code null} thì phải có {@code performedByName}
 * @param performedByName tên người thực hiện không có tài khoản (tài xế), có thể {@code null}
 * @param photos ảnh bằng chứng JPG/PNG ≤ 10 MB, 1–10 file
 */
public record RecordFilmHandoverRequest(
    Long serviceOrderId,
    Long shipmentId,
    HandoverPoint handoverPoint,
    int rollCount,
    String note,
    Long performedByAccountId,
    String performedByName,
    List<MultipartFile> photos) {}
