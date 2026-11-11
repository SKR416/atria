package io.github.skr416.atria.notification.repository;

import io.github.skr416.atria.notification.model.DigestDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DigestDeliveryLog}.
 */
public interface DigestDeliveryLogRepository extends JpaRepository<DigestDeliveryLog, Long> {

    Optional<DigestDeliveryLog> findByUserIdAndPeriodYearMonth(Long userId, String periodYearMonth);

    List<DigestDeliveryLog> findByPeriodYearMonthOrderByUserIdAsc(String periodYearMonth);
}
