package com.fa26se088.filmlink.repository.marketplace;

import com.fa26se088.filmlink.entity.marketplace.ConsignmentLabSettings;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ConsignmentLabSettings}. */
@Repository
public interface ConsignmentLabSettingsRepository
    extends JpaRepository<ConsignmentLabSettings, Long> {

  /** Khoá dòng cấu hình của Lab khi kiểm tra và đặt chỗ slot đồng thời (UC45). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM ConsignmentLabSettings s WHERE s.filmLabId = :filmLabId")
  Optional<ConsignmentLabSettings> findByFilmLabIdForUpdate(@Param("filmLabId") Long filmLabId);
}
