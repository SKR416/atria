package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.model.MonthlyCloseStatus;
import io.github.skr416.atria.attendance.model.PunchRule;
import io.github.skr416.atria.attendance.repository.MonthlyCloseRepository;
import io.github.skr416.atria.iam.model.Department;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scheduled and on-demand daily attendance settlement job (ADR-0008 Decision 3).
 * Evaluates finalized logical work dates across regional timezones, marking unclocked workdays
 * as ABSENT and incomplete punch pairs as MISSING_PUNCH with settled = true.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailySettlementJob {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRecordCalculator calculator;
    private final MonthlyCloseRepository monthlyCloseRepository;

    /**
     * Hourly settlement runner checking if any department timezone has crossed its daily cutoff hour.
     * Runs at minute 15 of every hour.
     */
    @Scheduled(cron = "0 15 * * * *")
    public void runHourlySettlement() {
        log.info("Starting hourly attendance settlement check...");
        List<Department> departments = departmentRepository.findAll();
        Set<String> processedTimezones = new HashSet<>();

        if (departments.isEmpty()) {
            checkAndSettleTimezone("Asia/Taipei", "TW");
        } else {
            for (Department dept : departments) {
                String tz = dept.getTimezone() != null ? dept.getTimezone() : "Asia/Taipei";
                if (processedTimezones.add(tz)) {
                    checkAndSettleTimezone(tz, dept.getCalendarCode() != null ? dept.getCalendarCode() : "TW");
                }
            }
        }
    }

    private void checkAndSettleTimezone(String timezoneStr, String calendarCode) {
        try {
            ZoneId zoneId = ZoneId.of(timezoneStr);
            ZonedDateTime nowZdt = ZonedDateTime.now(zoneId);
            PunchRule rule = calculator.resolvePunchRule(calendarCode);

            if (nowZdt.getHour() == rule.getDayCutoffHour()) {
                LocalDate yesterday = nowZdt.toLocalDate().minusDays(1);
                log.info("Timezone {} passed dayCutoffHour {}. Settling previous work date {}...",
                        timezoneStr, rule.getDayCutoffHour(), yesterday);
                settleWorkDate(yesterday);
            }
        } catch (Exception ex) {
            log.error("Failed to run hourly settlement for timezone {}", timezoneStr, ex);
        }
    }

    /**
     * Executes daily attendance settlement for all active employees on the specified logical work date.
     *
     * @param targetWorkDate the logical date to finalize
     * @return count of evaluated and settled employee daily records
     */
    @Transactional
    public int settleWorkDate(LocalDate targetWorkDate) {
        if (targetWorkDate == null) {
            return 0;
        }
        log.info("Executing daily attendance settlement for targetWorkDate {}...", targetWorkDate);
        List<User> users = userRepository.findAll();
        int settledCount = 0;

        for (User user : users) {
            if (!user.isEnabled()) {
                continue;
            }
            // Skip system administrator accounts without hire date
            if (user.getHireDate() == null && "admin".equalsIgnoreCase(user.getUsername())) {
                continue;
            }
            // If employee has a hire date, do not settle dates before hire date
            if (user.getHireDate() != null && user.getHireDate().isAfter(targetWorkDate)) {
                continue;
            }

            String calendarCode = "TW";
            if (user.getDepartmentId() != null) {
                Department dept = departmentRepository.findById(user.getDepartmentId()).orElse(null);
                if (dept != null && dept.getCalendarCode() != null) {
                    calendarCode = dept.getCalendarCode();
                }
            }

            String yearMonth = String.format("%04d-%02d", targetWorkDate.getYear(), targetWorkDate.getMonthValue());
            boolean isClosed = monthlyCloseRepository.existsByCalendarCodeAndYearMonthAndStatus(
                    calendarCode,
                    yearMonth,
                    MonthlyCloseStatus.CLOSED
            );
            if (isClosed) {
                log.warn("Monthly period {} for calendar {} is closed. Skipping settlement for user {}.",
                        yearMonth, calendarCode, user.getId());
                continue;
            }

            calculator.recomputeDailyRecord(user.getId(), targetWorkDate, true);
            settledCount++;
        }

        log.info("Daily settlement completed for workDate {}: {} records evaluated and settled.",
                targetWorkDate, settledCount);
        return settledCount;
    }
}
