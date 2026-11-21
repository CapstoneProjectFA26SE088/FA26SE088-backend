package com.fa26se088.filmlink.service.lab.impl;

import com.fa26se088.filmlink.service.lab.MachineReadiness;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Đã được thay thế bởi {@code MachineReadinessImpl} trong domain {@code machine} (UC68, BR-57).
 */
@RequiredArgsConstructor
public class SqlMachineReadiness implements MachineReadiness {

  private static final String COUNT_ACTIVE_MACHINE_TYPES =
      """
      SELECT count(DISTINCT machine_type)
      FROM machines
      WHERE film_lab_id = ?
        AND status = 'ACTIVE'
        AND machine_type IN ('PROCESSOR', 'SCANNER')
      """;

  private final JdbcTemplate jdbcTemplate;

  /** BR-57: đủ cả hai loại máy PROCESSOR và SCANNER đang ACTIVE. */
  @Override
  public boolean hasActiveProcessorAndScanner(Long filmLabId) {
    Integer types =
        jdbcTemplate.queryForObject(COUNT_ACTIVE_MACHINE_TYPES, Integer.class, filmLabId);
    return types != null && types == 2;
  }
}
