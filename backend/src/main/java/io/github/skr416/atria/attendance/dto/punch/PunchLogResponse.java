package io.github.skr416.atria.attendance.dto.punch;

import io.github.skr416.atria.attendance.model.PunchLog;
import io.github.skr416.atria.attendance.model.PunchSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PunchLogResponse {

    private Long id;
    private Long userId;
    private Long departmentId;
    private LocalDate workDate;
    private Instant rawPunchTime;
    private String clientIp;
    private PunchSource source;
    private String botClientId;
    private Long correctionRequestId;
    private String note;
    private Instant createdAt;

    public static PunchLogResponse from(PunchLog entity) {
        return PunchLogResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .departmentId(entity.getDepartmentId())
                .workDate(entity.getWorkDate())
                .rawPunchTime(entity.getRawPunchTime())
                .clientIp(entity.getClientIp())
                .source(entity.getSource())
                .botClientId(entity.getBotClientId())
                .correctionRequestId(entity.getCorrectionRequestId())
                .note(entity.getNote())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
