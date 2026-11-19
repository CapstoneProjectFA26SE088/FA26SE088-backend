package com.fa26se088.filmlink.service.machine.impl;

import com.fa26se088.filmlink.dto.machine.response.MachineResponse;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.mapper.machine.MachineMapper;
import com.fa26se088.filmlink.repository.machine.MachineRepository;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.machine.MachineService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai nghiệp vụ quản lý thiết bị máy tráng, máy scan và công suất ngày cho Film Lab Owner
 * (UC68, FA26-72).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MachineServiceImpl implements MachineService {

  private final MachineRepository machineRepository;
  private final LabAccessService labAccessService;
  private final MachineMapper machineMapper;

  @Override
  @Transactional(readOnly = true)
  public List<MachineResponse> getMyMachines(Long ownerAccountId) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccountId);
    return machineRepository.findByFilmLabIdOrderByIdAsc(lab.getId()).stream()
        .map(machineMapper::toResponse)
        .toList();
  }
}
