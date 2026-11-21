package com.fa26se088.filmlink.repository.machine;

import com.fa26se088.filmlink.entity.machine.ScheduleChange;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn và quản lý lịch sử dời ngày hẹn trả đơn hàng do sự cố máy (UC71, UC72). */
public interface ScheduleChangeRepository extends JpaRepository<ScheduleChange, Long> {

  /** Lấy danh sách thay đổi lịch phát sinh từ một sự cố máy theo thứ tự ID tăng dần. */
  List<ScheduleChange> findByMachineIncidentIdOrderByIdAsc(Long machineIncidentId);

  /** Tìm bản ghi thay đổi lịch theo ID và ID đơn hàng dịch vụ. */
  Optional<ScheduleChange> findByIdAndServiceOrderId(Long id, Long serviceOrderId);
}
