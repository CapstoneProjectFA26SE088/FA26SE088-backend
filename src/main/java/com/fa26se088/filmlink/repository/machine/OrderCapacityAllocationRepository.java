package com.fa26se088.filmlink.repository.machine;

import com.fa26se088.filmlink.entity.machine.OrderCapacityAllocation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn và quản lý phân bổ công suất theo ngày của đơn hàng (UC69, UC71). */
public interface OrderCapacityAllocationRepository
    extends JpaRepository<OrderCapacityAllocation, Long> {

  List<OrderCapacityAllocation> findByFilmLabIdAndWorkDateGreaterThanEqual(
      Long filmLabId, LocalDate fromDate);

  List<OrderCapacityAllocation> findByServiceOrderIdOrderByWorkDateAsc(Long serviceOrderId);

  void deleteByServiceOrderId(Long serviceOrderId);
}
