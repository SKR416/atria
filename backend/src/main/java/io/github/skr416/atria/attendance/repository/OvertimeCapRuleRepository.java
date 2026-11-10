package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.OvertimeCapRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OvertimeCapRuleRepository extends JpaRepository<OvertimeCapRule, Long> {

    List<OvertimeCapRule> findByCalendarCodeAndEnabledTrueOrderByPeriodMonthsAscMaxMinutesAsc(String calendarCode);

    List<OvertimeCapRule> findByCalendarCodeOrderByPeriodMonthsAscMaxMinutesAsc(String calendarCode);

    List<OvertimeCapRule> findAllByOrderByCalendarCodeAscPeriodMonthsAscMaxMinutesAsc();

    Optional<OvertimeCapRule> findByRuleCode(String ruleCode);
}
