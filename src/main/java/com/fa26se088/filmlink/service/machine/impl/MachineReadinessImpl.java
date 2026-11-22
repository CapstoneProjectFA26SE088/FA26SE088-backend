package com.fa26se088.filmlink.service.machine.impl;

import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.service.lab.MachineReadiness;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai {@link MachineReadiness} trong domain {@code machine}: kiểm tra phòng lab có đủ máy
 * tráng (PROCESSOR) và máy scan (SCANNER) đang ở trạng thái ACTIVE để bật nhận đơn hàng (BR-57,
 * UC68).
 */
@Component
@Primary
@RequiredArgsConstructor
public class MachineReadinessImpl implements MachineReadiness {

  private final MachineRepository machineRepository;

  @Override
  @Transactional(readOnly = true)
  public boolean hasActiveProcessorAndScanner(Long filmLabId) {
    return machineRepository.existsByFilmLabIdAndMachineTypeAndStatus(
            filmLabId, MachineType.PROCESSOR, MachineStatus.ACTIVE)
        && machineRepository.existsByFilmLabIdAndMachineTypeAndStatus(
            filmLabId, MachineType.SCANNER, MachineStatus.ACTIVE);
  }
}
