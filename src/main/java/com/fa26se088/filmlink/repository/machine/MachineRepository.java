package com.fa26se088.filmlink.repository.machine;

import com.fa26se088.filmlink.entity.machine.Machine;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Truy vấn dữ liệu thiết bị máy tráng và máy scan của Film Lab (UC68, BR-57).
 */
public interface MachineRepository extends JpaRepository<Machine, Long> {

  List<Machine> findByFilmLabIdOrderByIdAsc(Long filmLabId);

  Optional<Machine> findByIdAndFilmLabId(Long id, Long filmLabId);

  boolean existsByFilmLabIdAndMachineCodeIgnoreCase(Long filmLabId, String machineCode);

  boolean existsByFilmLabIdAndMachineTypeAndStatus(
      Long filmLabId, MachineType machineType, MachineStatus status);
}
