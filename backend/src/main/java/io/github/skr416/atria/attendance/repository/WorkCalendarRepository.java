package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.WorkCalendarDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkCalendarRepository extends JpaRepository<WorkCalendarDay, Long> {

    Optional<WorkCalendarDay> findByCalendarCodeAndWorkDate(String calendarCode, LocalDate workDate);

    List<WorkCalendarDay> findByCalendarCodeAndWorkDateBetweenOrderByWorkDateAsc(
            String calendarCode,
            LocalDate startDate,
            LocalDate endDate
    );

    boolean existsByCalendarCodeAndWorkDate(String calendarCode, LocalDate workDate);
}
