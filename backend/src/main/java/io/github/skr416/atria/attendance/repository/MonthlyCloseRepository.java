package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.MonthlyClose;
import io.github.skr416.atria.attendance.model.MonthlyCloseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyCloseRepository extends JpaRepository<MonthlyClose, Long> {

    Optional<MonthlyClose> findByCalendarCodeAndYearMonth(String calendarCode, String yearMonth);

    boolean existsByCalendarCodeAndYearMonthAndStatus(
            String calendarCode,
            String yearMonth,
            MonthlyCloseStatus status
    );

    List<MonthlyClose> findByCalendarCodeOrderByYearMonthDesc(String calendarCode);

    List<MonthlyClose> findAllByOrderByYearMonthDescCalendarCodeAsc();
}
