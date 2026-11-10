package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.LeaveGrantRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveGrantRuleRepository extends JpaRepository<LeaveGrantRule, Long> {

    List<LeaveGrantRule> findByCalendarCodeOrderByMinTenureMonthsAsc(String calendarCode);

    List<LeaveGrantRule> findAllByOrderByCalendarCodeAscMinTenureMonthsAsc();
}
