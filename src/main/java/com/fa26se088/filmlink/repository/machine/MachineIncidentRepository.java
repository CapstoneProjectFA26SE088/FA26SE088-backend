package com.fa26se088.filmlink.repository.machine;

import com.fa26se088.filmlink.entity.machine.MachineIncident;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn và quản lý sự cố thiết bị máy tráng/scan của Film Lab (UC70). */
public interface MachineIncidentRepository extends JpaRepository<MachineIncident, Long> {

  /** Tìm bản ghi sự cố theo ID sự cố và ID máy. */
  Optional<MachineIncident> findByIdAndMachineId(Long id, Long machineId);

  /** Lấy danh sách các sự cố đang mở (chưa giải quyết) của một máy. */
  List<MachineIncident> findByMachineIdAndResolvedAtIsNull(Long machineId);
}
