package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.PunchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PunchLogRepository extends JpaRepository<PunchLog, Long> {

    List<PunchLog> findByUserIdAndWorkDateOrderByRawPunchTimeAsc(Long userId, LocalDate workDate);

    List<PunchLog> findByUserIdAndWorkDateBetweenOrderByRawPunchTimeAsc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    Optional<PunchLog> findFirstByUserIdOrderByRawPunchTimeDesc(Long userId);
}
