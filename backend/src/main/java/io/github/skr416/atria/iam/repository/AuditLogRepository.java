package io.github.skr416.atria.iam.repository;

import io.github.skr416.atria.iam.model.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
}
