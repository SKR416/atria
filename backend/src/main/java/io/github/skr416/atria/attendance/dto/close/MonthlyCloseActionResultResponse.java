package io.github.skr416.atria.attendance.dto.close;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Result of a monthly close check or execution, carrying any blocking {@code PENDING} requests
 * when closing is blocked (ADR-0008 Decision 7).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyCloseActionResultResponse {

    private boolean closed;
    private MonthlyCloseResponse record;
    @Builder.Default
    private List<BlockingPendingRequestDto> blockingRequests = Collections.emptyList();
}
