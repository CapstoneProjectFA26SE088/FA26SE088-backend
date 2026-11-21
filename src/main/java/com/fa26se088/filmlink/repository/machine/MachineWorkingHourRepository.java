package com.fa26se088.filmlink.repository.machine;

import com.fa26se088.filmlink.entity.machine.MachineWorkingHour;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Truy vấn cấu hình giờ hoạt động của máy theo ngày trong tuần (UC68).
 */
public interface MachineWorkingHourRepository extends JpaRepository<MachineWorkingHour, Long> {}
