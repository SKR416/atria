package io.github.skr416.atria.attendance.dto.close;

import io.github.skr416.atria.attendance.model.MonthlyClose;
import io.github.skr416.atria.attendance.model.MonthlyCloseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyCloseResponse {

    private Long id;
    private String calendarCode;
    private String yearMonth;
    private MonthlyCloseStatus status;
    private Instant closedAt;
    private Long closedBy;
    private String closedByUsername;
    private Instant reopenedAt;
    private Long reopenedBy;
    private String note;
    private Instant updatedAt;

    public static MonthlyCloseResponse from(MonthlyClose entity) {
        return MonthlyCloseResponse.builder()
                .id(entity.getId())
                .calendarCode(entity.getCalendarCode())
                .yearMonth(entity.getYearMonth())
                .status(entity.getStatus())
                .closedAt(entity.getClosedAt())
                .closedBy(entity.getClosedBy())
                .closedByUsername(entity.getClosedByUsername())
                .reopenedAt(entity.getReopenedAt())
                .reopenedBy(entity.getReopenedBy())
                .note(entity.getNote())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
