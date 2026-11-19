package com.fa26se088.filmlink.service.machine;

import com.fa26se088.filmlink.dto.machine.request.CreateMachineRequest;
import com.fa26se088.filmlink.dto.machine.response.MachineResponse;
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
}
