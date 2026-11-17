package com.fa26se088.filmlink.service.delivery;

import com.fa26se088.filmlink.dto.delivery.request.RecordFilmHandoverRequest;
import com.fa26se088.filmlink.dto.delivery.response.FilmHandoverResponse;
import java.util.List;

/**
 * BR-25: bằng chứng bàn giao phim (Film Handover). Dùng chung cho mọi điểm bàn giao (lab nhận phim
 * UC23, lấy phim tận nơi, trả phim UC27). Chỉ ghi thêm, không sửa / xoá (bảng append-only).
 *
 * <p>Domain delivery không biết trạng thái đơn: domain gọi (order…) tự kiểm nghiệp vụ và quyền
 * trước khi gọi, và gọi trong transaction của mình.
 */
public interface FilmHandoverService {

  /**
   * BR-25: ghi một lần bàn giao kèm 1–10 ảnh bằng chứng. Ảnh được kiểm loại / dung lượng qua {@code
   * StorageClient} ({@code StoragePurpose.HANDOVER_PHOTO}); {@code display_order} = 1..n theo thứ
   * tự gửi lên.
   *
   * @return id của dòng {@code film_handovers} vừa tạo
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code VALIDATION_ERROR} khi số ảnh
   *     ngoài 1–10, số cuộn ngoài 0–25, thiếu người thực hiện; lỗi {@code STORAGE_*} khi ảnh sai
   *     loại, quá dung lượng hoặc storage lỗi
   */
  Long record(RecordFilmHandoverRequest request);

  /** BR-25: các lần bàn giao của một đơn theo thời gian, kèm link ảnh có chữ ký (15 phút). */
  List<FilmHandoverResponse> getByServiceOrderId(Long serviceOrderId);
}
