package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    List<LeaveBalance> findByUserIdOrderByValidUntilAscIdAsc(Long userId);

    List<LeaveBalance> findByUserIdAndLeaveTypeCodeOrderByValidUntilAscIdAsc(Long userId, String leaveTypeCode);

    List<LeaveBalance> findByUserIdAndLeaveTypeCodeAndValidFromLessThanEqualAndValidUntilGreaterThanEqualOrderByValidUntilAscIdAsc(
            Long userId,
            String leaveTypeCode,
            LocalDate validFromMax,
            LocalDate validUntilMin
    );

    Optional<LeaveBalance> findBySourceCompGrantId(Long sourceCompGrantId);

    List<LeaveBalance> findByUserIdAndLeaveTypeCodeAndGrantYear(Long userId, String leaveTypeCode, int grantYear);
}
