package com.fa26se088.filmlink.service.order;

import com.fa26se088.filmlink.dto.archive.request.SetFullResolutionLinkRequest;
import com.fa26se088.filmlink.dto.archive.response.ScanFileResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.dto.order.request.ConfirmFilmReceiptRequest;
import com.fa26se088.filmlink.dto.order.request.ConfirmScheduleRequest;
import com.fa26se088.filmlink.dto.order.request.RecordFilmReturnRequest;
import com.fa26se088.filmlink.dto.order.request.RecordFilmRollDamageRequest;
import com.fa26se088.filmlink.dto.order.request.RecordProcessingStageRequest;
import com.fa26se088.filmlink.dto.order.request.RejectServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.SearchLabServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.response.FilmRollResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderResponse;
import com.fa26se088.filmlink.dto.order.response.LabServiceOrderSummaryResponse;
import com.fa26se088.filmlink.dto.order.response.ScheduleProposalResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * UC22: Film Lab Owner xem và nhận Service Order của lab mình. Mọi method nhận {@code
 * ownerAccountId} của tài khoản đang đăng nhập (role FILM_LAB_OWNER, kiểm ở controller); lab được
 * tra theo chủ ({@code LabAccessService}), không nhận {@code labId} từ client.
 *
 * <p>Đơn không tồn tại hoặc thuộc lab khác đều trả {@code ORDER_NOT_FOUND} (404), không lộ đơn của
 * lab khác.
 */
public interface LabServiceOrderService {

  /** UC22: đơn của lab mình, mới nhất trước, lọc tuỳ chọn theo trạng thái (CR-01, CR-02). */
  PageResponse<LabServiceOrderSummaryResponse> getMyOrders(
      Long ownerAccountId, SearchLabServiceOrderRequest request);

  /** UC22: chi tiết một đơn của lab mình, kèm cuộn và Photographer. */
  LabServiceOrderResponse getMyOrder(Long ownerAccountId, Long orderId);

  /**
   * UC22: nhận đơn đang {@code AWAITING_LAB_CONFIRMATION} → {@code AWAITING_FILM}.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_STATUS_CONFLICT} (409)
   *     khi đơn không còn chờ lab xác nhận
   */
  LabServiceOrderResponse accept(Long ownerAccountId, Long orderId);

  /**
   * UC22, BR-39: lab từ chối đơn đang {@code AWAITING_LAB_CONFIRMATION} (hoặc {@code AWAITING_FILM}
   * trước khi nhận phim) → {@code CANCELLED}.
   *
   * <p>Bắt buộc có lý do 10–500 ký tự. Hoàn 100% tiền đã thu tự động qua domain payment (BR-39).
   * Nếu phim đã gửi đi (UH-09), ghi nhận để tạo chiều trả phim với phí do lab chịu.
   *
   * @param ownerAccountId tài khoản Film Lab Owner đang đăng nhập
   * @param orderId id của Service Order
   * @param request body chứa lý do từ chối
   * @return chi tiết đơn sau khi từ chối
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404), {@code
   *     ORDER_STATUS_CONFLICT} (409) khi đơn không ở trạng thái được từ chối, {@code
   *     VALIDATION_ERROR} (400) khi lý do không hợp lệ
   */
  LabServiceOrderResponse reject(
      Long ownerAccountId, Long orderId, RejectServiceOrderRequest request);

  /**
   * UC23, BR-25: lab xác nhận đã nhận phim của đơn {@code AWAITING_FILM}, đếm từng cuộn, ghi bằng
   * chứng bàn giao kèm 1–10 ảnh và chốt giá → {@code AWAITING_MACHINE_ASSIGNMENT}.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404),
   *     {@code ORDER_STATUS_CONFLICT} (409), {@code VALIDATION_ERROR} (400: danh sách cuộn sai, số
   *     ảnh ngoài 1–10), {@code ORDER_OPTION_NOT_SUPPORTED} (422: không nhận cuộn nào), lỗi {@code
   *     STORAGE_*} khi ảnh sai loại / quá dung lượng
   */
  LabServiceOrderResponse confirmFilmReceipt(
      Long ownerAccountId,
      Long orderId,
      ConfirmFilmReceiptRequest request,
      List<MultipartFile> photos);

  /**
   * UC69, BR-58–BR-61: ngày hẹn trả đề xuất cho đơn {@code AWAITING_MACHINE_ASSIGNMENT}. Không ghi
   * DB.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404),
   *     {@code ORDER_STATUS_CONFLICT} (409)
   */
  ScheduleProposalResponse getScheduleProposal(Long ownerAccountId, Long orderId);

  /**
   * UC69, BR-07, BR-60: lab xác nhận ngày hẹn trả → {@code IN_PROCESSING}, ghi công đoạn đầu tiên
   * {@code RECEIVED}.
   *
   * @param request {@code null} hoặc {@code promisedReturnAt = null} = dùng ngày đề xuất
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404),
   *     {@code ORDER_STATUS_CONFLICT} (409), {@code ORDER_SCHEDULE_INFEASIBLE} (422), {@code
   *     ORDER_PROMISED_DATE_TOO_EARLY} (422)
   */
  LabServiceOrderResponse confirmSchedule(
      Long ownerAccountId, Long orderId, ConfirmScheduleRequest request);

  /**
   * UC24, BR-07: lab ghi công đoạn kế tiếp của đơn {@code IN_PROCESSING}. Không đổi trạng thái
   * đơn.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404),
   *     {@code ORDER_STATUS_CONFLICT} (409), {@code ORDER_STAGE_OUT_OF_ORDER} (422)
   */
  LabServiceOrderResponse recordStage(
      Long ownerAccountId, Long orderId, RecordProcessingStageRequest request);

  /**
   * UC24, BR-26: lab ghi nhận một cuộn phim {@code RECEIVED} của đơn {@code IN_PROCESSING} bị hỏng
   * (cháy sáng, loá toàn bộ, không lên hình) kèm 1–10 ảnh bằng chứng → cuộn chuyển {@code DAMAGED},
   * miễn phí cuộn hỏng, tính lại {@code final_total_vnd}, hoàn tiền chênh lệch nếu khách đã thanh
   * toán online dư, và báo Photographer.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404),
   *     {@code ORDER_STATUS_CONFLICT} (409: đơn không IN_PROCESSING hoặc cuộn đã DAMAGED), {@code
   *     ORDER_ROLL_NOT_FOUND} (404), {@code VALIDATION_ERROR} (400: số ảnh ngoài 1–10 hoặc ghi chú
   *     rỗng), lỗi {@code STORAGE_*} khi ảnh sai loại / quá dung lượng
   */
  LabServiceOrderResponse recordRollDamage(
      Long ownerAccountId,
      Long orderId,
      Long rollId,
      RecordFilmRollDamageRequest request,
      List<MultipartFile> photos);

  /**
   * UC26, BR-08: lab thêm ảnh xem trước (1–40 JPG mỗi lần) cho một cuộn {@code RECEIVED} của đơn
   * {@code IN_PROCESSING}.
   *
   * @return mọi ảnh xem trước hiện hành của cuộn
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404),
   *     {@code ORDER_STATUS_CONFLICT} (409), {@code ORDER_ROLL_NOT_FOUND} (404), lỗi của {@code
   *     ArchiveService#addPreviewScans}
   */
  List<ScanFileResponse> addPreviewScans(
      Long ownerAccountId, Long orderId, Long rollId, List<MultipartFile> files);

  /**
   * UC26: lab gửi link file độ phân giải gốc của một cuộn {@code RECEIVED} của đơn {@code
   * IN_PROCESSING}; link phải truy cập được.
   *
   * @return cuộn kèm số ảnh xem trước và link đã kiểm
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404),
   *     {@code ORDER_STATUS_CONFLICT} (409), {@code ORDER_ROLL_NOT_FOUND} (404), {@code
   *     ARCHIVE_LINK_UNREACHABLE} (422)
   */
  FilmRollResponse setFullResolutionLink(
      Long ownerAccountId, Long orderId, Long rollId, SetFullResolutionLinkRequest request);

  /**
   * UC26: lab giao scan sau công đoạn QC → {@code READY_FOR_HANDOVER}.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404),
   *     {@code ORDER_STATUS_CONFLICT} (409), {@code ORDER_STAGE_OUT_OF_ORDER} (422: chưa tới QC),
   *     {@code ARCHIVE_DELIVERY_INCOMPLETE} (422: cuộn thiếu ảnh hoặc link)
   */
  LabServiceOrderResponse deliverScans(Long ownerAccountId, Long orderId);

  /**
   * UC27, BR-25: lab trả phim tại lab (1–10 ảnh bằng chứng) hoặc giữ âm bản theo yêu cầu (ảnh
   * không bắt buộc) cho đơn {@code READY_FOR_HANDOVER} → {@code DELIVERED}.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code ORDER_NOT_FOUND} (404),
   *     {@code ORDER_STATUS_CONFLICT} (409), {@code VALIDATION_ERROR} (400: số ảnh ngoài 1–10 khi
   *     trả phim), lỗi {@code STORAGE_*} khi ảnh sai loại / quá dung lượng
   */
  LabServiceOrderResponse returnFilm(
      Long ownerAccountId,
      Long orderId,
      RecordFilmReturnRequest request,
      List<MultipartFile> photos);
}
