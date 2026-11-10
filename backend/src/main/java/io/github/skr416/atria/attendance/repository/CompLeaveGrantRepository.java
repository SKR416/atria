package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.CompLeaveGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompLeaveGrantRepository extends JpaRepository<CompLeaveGrant, Long> {

    Optional<CompLeaveGrant> findBySourceRequestId(Long sourceRequestId);

    List<CompLeaveGrant> findByUserIdOrderByExpiresAtAscIdAsc(Long userId);

    List<CompLeaveGrant> findByExpiredSettledFalseAndExpiresAtBeforeOrderByIdAsc(LocalDate asOfDate);
}
