package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.PunchRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PunchRuleRepository extends JpaRepository<PunchRule, Long> {

    Optional<PunchRule> findByCalendarCode(String calendarCode);

    List<PunchRule> findAllByOrderByCalendarCodeAsc();
}
