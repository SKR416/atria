package io.github.skr416.atria.attendance.dto.leave;

import java.time.LocalDate;
import java.util.List;

public record AnnualLeaveGrantBatchResultDto(
        LocalDate asOfDate,
        int grantedCount,
        int skippedAlreadyGrantedCount,
        List<String> skippedMissingHireDateUsernames,
        List<LeaveBalanceBucketDto> grantedBuckets
) {
}
