package io.github.skr416.atria.attendance.repository;

import io.github.skr416.atria.attendance.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    Optional<LeaveType> findByCode(String code);

    boolean existsByCode(String code);

    List<LeaveType> findAllByOrderBySortOrderAscIdAsc();

    List<LeaveType> findByEnabledTrueOrderBySortOrderAscIdAsc();
}
