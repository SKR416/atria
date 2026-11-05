package io.github.skr416.atria.attendance.dto.close;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Summary of an unresolved {@code PENDING} attendance request blocking monthly closing
 * (ADR-0008 Decision 7).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockingPendingRequestDto {

    private Long requestId;
    private Long userId;
    private String username;
    private String displayName;
    private String requestType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
}
