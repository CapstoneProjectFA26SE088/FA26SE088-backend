package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.ConsignmentBooking;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentBookingStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ConsignmentBooking}. */
@Repository
public interface ConsignmentBookingRepository extends JpaRepository<ConsignmentBooking, Long> {

  /** Kiểm tra mã lịch hẹn đã tồn tại hay chưa. */
  boolean existsByBookingCode(String bookingCode);

  /** Đếm số lịch hẹn theo trạng thái của người bán (BR-49). */
  long countBySellerAccountIdAndStatus(Long sellerAccountId, ConsignmentBookingStatus status);

  /** Lấy các lịch hẹn đang hoạt động của Lab trong khoảng thời gian. */
  List<ConsignmentBooking>
      findByFilmLabIdAndStatusInAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThan(
          Long filmLabId,
          Collection<ConsignmentBookingStatus> statuses,
          Instant rangeStart,
          Instant rangeEnd);

  /** Đếm số chỗ đã đặt trong một khung giờ cụ thể của Lab. */
  long countByFilmLabIdAndSlotStartAtAndStatusIn(
      Long filmLabId, Instant slotStartAt, Collection<ConsignmentBookingStatus> statuses);
}
