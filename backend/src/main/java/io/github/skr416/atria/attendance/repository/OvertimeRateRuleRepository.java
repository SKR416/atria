package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.OvertimeRateRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OvertimeRateRuleRepository extends JpaRepository<OvertimeRateRule, Long> {

    List<OvertimeRateRule> findByCalendarCodeAndDayTypeOrderByFromMinuteAsc(
            String calendarCode,
            CalendarDayType dayType
    );

    List<OvertimeRateRule> findByCalendarCodeOrderByDayTypeAscFromMinuteAsc(String calendarCode);

    List<OvertimeRateRule> findAllByOrderByCalendarCodeAscDayTypeAscFromMinuteAsc();
}
