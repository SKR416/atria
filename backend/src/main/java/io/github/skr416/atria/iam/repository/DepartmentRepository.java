package io.github.skr416.atria.iam.repository;

import io.github.skr416.atria.iam.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByOrderBySortOrderAscIdAsc();

    /** Subtree including the node itself (materialized path prefix, e.g. "/1/4/%"). */
    List<Department> findByTreePathStartingWith(String treePathPrefix);

    boolean existsByCode(String code);

    boolean existsByParentId(Long parentId);
}
