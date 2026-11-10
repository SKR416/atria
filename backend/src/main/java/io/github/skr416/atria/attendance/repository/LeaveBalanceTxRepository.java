package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.LeaveBalanceTx;
import io.github.skr416.atria.attendance.model.LeaveBalanceTxType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveBalanceTxRepository extends JpaRepository<LeaveBalanceTx, Long> {

    List<LeaveBalanceTx> findByRequestIdOrderByIdAsc(Long requestId);

    List<LeaveBalanceTx> findByRequestIdAndTxTypeOrderByIdAsc(Long requestId, LeaveBalanceTxType txType);

    List<LeaveBalanceTx> findByUserIdOrderByIdDesc(Long userId);
}
