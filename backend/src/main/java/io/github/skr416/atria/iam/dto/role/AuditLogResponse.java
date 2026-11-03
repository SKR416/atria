package io.github.skr416.atria.iam.dto.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response payload describing an administrative audit log entry (ADR-0004).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private Long actorId;
    private String actorUsername;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String clientIp;
    private Instant createdAt;
}
