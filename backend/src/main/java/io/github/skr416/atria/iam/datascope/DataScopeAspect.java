package io.github.skr416.atria.iam.datascope;

import io.github.skr416.atria.iam.model.DataScopeType;
import io.github.skr416.atria.iam.model.Department;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.security.AtriaUserPrincipal;
import io.github.skr416.atria.iam.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AOP Aspect intercepting {@link DataScope}-annotated methods.
 * Populates {@link DataScopeContext} with the caller's effective data visibility filter (ADR-0004).
 */
@Slf4j
@Aspect
@Component
@Order(100)
@RequiredArgsConstructor
public class DataScopeAspect {

    private final DepartmentRepository departmentRepository;

    @Around("@annotation(dataScope)")
    public Object applyDataScope(ProceedingJoinPoint pjp, DataScope dataScope) throws Throwable {
        Optional<AtriaUserPrincipal> principalOpt = SecurityUtils.currentPrincipal();
        if (principalOpt.isEmpty()) {
            return pjp.proceed();
        }

        AtriaUserPrincipal principal = principalOpt.get();
        DataScopeType scope = principal.getDataScope() != null ? principal.getDataScope() : DataScopeType.SELF_ONLY;

        Set<Long> allowedDeptIds;
        if (scope == DataScopeType.DEPT_AND_SUB) {
            String treePath = principal.getDepartmentTreePath();
            if ((treePath == null || treePath.isBlank()) && principal.getDepartmentId() != null) {
                treePath = departmentRepository.findById(principal.getDepartmentId())
                        .map(Department::getTreePath)
                        .orElse(null);
            }

            if (treePath != null && !treePath.isBlank()) {
                allowedDeptIds = departmentRepository.findByTreePathStartingWith(treePath).stream()
                        .map(Department::getId)
                        .collect(Collectors.toUnmodifiableSet());
            } else if (principal.getDepartmentId() != null) {
                allowedDeptIds = Set.of(principal.getDepartmentId());
            } else {
                allowedDeptIds = Collections.emptySet();
            }
        } else if (scope == DataScopeType.DEPT_ONLY) {
            allowedDeptIds = principal.getDepartmentId() != null
                    ? Set.of(principal.getDepartmentId())
                    : Collections.emptySet();
        } else {
            allowedDeptIds = Collections.emptySet();
        }

        DataScopeContext.Filter filter = new DataScopeContext.Filter(
                scope,
                principal.getUserId(),
                principal.getDepartmentId(),
                allowedDeptIds,
                dataScope.deptField(),
                dataScope.userField()
        );

        DataScopeContext.Filter previous = DataScopeContext.current();
        try {
            DataScopeContext.set(filter);
            return pjp.proceed();
        } finally {
            if (previous != null) {
                DataScopeContext.set(previous);
            } else {
                DataScopeContext.clear();
            }
        }
    }
}
