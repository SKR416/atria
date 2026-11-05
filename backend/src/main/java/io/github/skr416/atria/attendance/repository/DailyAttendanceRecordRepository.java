package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.DailyAttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyAttendanceRecordRepository extends JpaRepository<DailyAttendanceRecord, Long> {

    Optional<DailyAttendanceRecord> findByUserIdAndWorkDate(Long userId, LocalDate workDate);

    List<DailyAttendanceRecord> findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<DailyAttendanceRecord> findByDepartmentIdInAndWorkDateBetweenOrderByWorkDateAscUserIdAsc(
            List<Long> departmentIds,
            LocalDate startDate,
            LocalDate endDate
    );

    List<DailyAttendanceRecord> findByWorkDateBetweenOrderByWorkDateAscUserIdAsc(
            LocalDate startDate,
            LocalDate endDate
    );
}
