package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.close.BlockingPendingRequestDto;
import io.github.skr416.atria.attendance.model.AttendanceRequest;
import io.github.skr416.atria.attendance.model.AttendanceRequestStatus;
import io.github.skr416.atria.attendance.repository.AttendanceRequestRepository;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Database-backed implementation of {@link PendingRequestChecker} that queries {@code att_request}
 * to prevent locking monthly close when unresolved PENDING requests exist (ADR-0008 Decisions 7 & 9).
 */
@Component
@RequiredArgsConstructor
public class DatabasePendingRequestChecker implements PendingRequestChecker {

    private final AttendanceRequestRepository attendanceRequestRepository;
    private final UserRepository userRepository;

    @Override
    public List<BlockingPendingRequestDto> findPendingRequestsForMonth(String calendarCode, YearMonth yearMonth) {
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        List<AttendanceRequest> requests = attendanceRequestRepository
                .findByCalendarCodeAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByIdAsc(
                        calendarCode,
                        AttendanceRequestStatus.PENDING,
                        monthEnd,
                        monthStart
                );

        return requests.stream().map(req -> {
            User user = userRepository.findById(req.getUserId()).orElse(null);
            String username = user != null ? user.getUsername() : "user#" + req.getUserId();
            String displayName = user != null ? user.getDisplayName() : username;
            return BlockingPendingRequestDto.builder()
                    .requestId(req.getId())
                    .userId(req.getUserId())
                    .username(username)
                    .displayName(displayName)
                    .requestType(req.getRequestType().name())
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .reason(req.getReason())
                    .build();
        }).toList();
    }
}
