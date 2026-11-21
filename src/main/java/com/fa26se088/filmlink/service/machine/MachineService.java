package com.fa26se088.filmlink.service.machine;

import com.fa26se088.filmlink.dto.machine.request.CreateMachineRequest;
import com.fa26se088.filmlink.dto.machine.request.ReportMachineIncidentRequest;
import com.fa26se088.filmlink.dto.machine.request.UpdateMachineRequest;
import com.fa26se088.filmlink.dto.machine.request.UpdateSchedulingSettingsRequest;
import com.fa26se088.filmlink.dto.machine.response.MachineIncidentResponse;
import com.fa26se088.filmlink.dto.machine.response.MachineResponse;
import com.fa26se088.filmlink.dto.machine.response.SchedulingSettingsResponse;
import java.util.List;

/**
 * Dịch vụ quản lý máy tráng (PROCESSOR), máy scan (SCANNER) và công suất tự động theo ngày cho
 * Film Lab Owner (UC68, FA26-72).
 */
public interface MachineService {

  /**
   * Lấy danh sách toàn bộ máy của lab thuộc chủ tài khoản đang đăng nhập kèm công suất tính toán
   * theo ngày (UC68).
   *
   * @param ownerAccountId ID tài khoản FILM_LAB_OWNER đang đăng nhập
   * @return danh sách máy của lab sắp xếp theo ID tăng dần
   */
  List<MachineResponse> getMyMachines(Long ownerAccountId);

  /**
   * Khai báo máy tráng (PROCESSOR) hoặc máy scan (SCANNER) mới cho lab của chủ tài khoản đang đăng
   * nhập (UC68, BR-57, BR-58).
   *
   * @param ownerAccountId ID tài khoản FILM_LAB_OWNER đang đăng nhập
   * @param request thông tin cấu hình máy, quy trình, khổ phim và giờ hoạt động
   * @return thông tin máy vừa tạo ở trạng thái ACTIVE kèm công suất tính toán theo ngày
   */
  MachineResponse createMachine(Long ownerAccountId, CreateMachineRequest request);

  /**
   * Cập nhật thông tin cấu hình máy (modelName, capacityPerHour, quy trình, khổ phim, giờ hoạt
   * động) của lab thuộc chủ tài khoản đang đăng nhập (UC68).
   *
   * @param ownerAccountId ID tài khoản FILM_LAB_OWNER đang đăng nhập
   * @param machineId ID của máy cần cập nhật
   * @param request thông tin cấu hình mới (không sửa được machineCode và machineType)
   * @return thông tin máy sau khi cập nhật kèm công suất tính toán lại theo ngày
   */
  MachineResponse updateMachine(
      Long ownerAccountId, Long machineId, UpdateMachineRequest request);

  /**
   * Cập nhật tham số lập lịch tự động (qcBufferHours, expressDailyCapPercent,
   * expressCommitmentWorkingDays) của lab thuộc chủ tài khoản đang đăng nhập (UC68, BR-60, BR-61).
   *
   * @param ownerAccountId ID tài khoản FILM_LAB_OWNER đang đăng nhập
   * @param request thông tin cấu hình tham số lập lịch mới
   * @return thông tin tham số lập lịch sau khi cập nhật
   */
  SchedulingSettingsResponse updateSchedulingSettings(
      Long ownerAccountId, UpdateSchedulingSettingsRequest request);

  /**
   * Khai báo sự cố cho máy của lab và tự động tái xếp lịch các đơn đang xử lý bị ảnh hưởng
   * (UC70, UC71, BR-61).
   *
   * @param ownerAccountId ID tài khoản FILM_LAB_OWNER đang đăng nhập
   * @param machineId ID máy gặp sự cố
   * @param request thông tin sự cố gồm nguyên nhân, mô tả và khoảng thời gian gián đoạn
   * @return thông tin sự cố đã ghi nhận kèm danh sách các thay đổi ngày hẹn trả
   */
  MachineIncidentResponse reportIncident(
      Long ownerAccountId, Long machineId, ReportMachineIncidentRequest request);

  /**
   * Xác nhận đã khắc phục xong sự cố máy và khôi phục trạng thái máy về ACTIVE (UC70, TC-SM04-06).
   *
   * @param ownerAccountId ID tài khoản FILM_LAB_OWNER đang đăng nhập
   * @param machineId ID máy gặp sự cố
   * @param incidentId ID bản ghi sự cố cần giải quyết
   * @return thông tin sự cố sau khi đã đánh dấu resolvedAt và khôi phục trạng thái máy
   */
  MachineIncidentResponse resolveIncident(Long ownerAccountId, Long machineId, Long incidentId);
}
