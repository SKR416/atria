package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.AttendanceRequest;
import io.github.skr416.atria.attendance.model.AttendanceRequestStatus;
import io.github.skr416.atria.attendance.model.AttendanceRequestType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AttendanceRequestRepository extends JpaRepository<AttendanceRequest, Long> {

    List<AttendanceRequest> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<AttendanceRequest> findAllByOrderByCreatedAtDescIdDesc();

    List<AttendanceRequest> findByStatusOrderByCreatedAtDescIdDesc(AttendanceRequestStatus status);

    /**
     * Finds requests for a user with a given status where {@code startDate <= targetDate} and {@code endDate >= targetDate}.
     */
    List<AttendanceRequest> findByUserIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartTimeAsc(
            Long userId,
            AttendanceRequestStatus status,
            LocalDate endOfTargetDate,
            LocalDate startOfTargetDate
    );

    /**
     * Finds requests for a user of a specific type and status set within a date range (used for overtime cap checks).
     */
    List<AttendanceRequest> findByUserIdAndRequestTypeAndStatusInAndStartDateBetween(
            Long userId,
            AttendanceRequestType requestType,
            Collection<AttendanceRequestStatus> statuses,
            LocalDate fromDate,
            LocalDate toDate
    );

    /**
     * Finds requests touching a target date range for a calendar region and status (used by PendingRequestChecker).
     */
    List<AttendanceRequest> findByCalendarCodeAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByIdAsc(
            String calendarCode,
            AttendanceRequestStatus status,
            LocalDate periodEnd,
            LocalDate periodStart
    );
}
