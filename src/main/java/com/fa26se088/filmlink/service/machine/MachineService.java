package com.fa26se088.filmlink.service.machine;

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
}
