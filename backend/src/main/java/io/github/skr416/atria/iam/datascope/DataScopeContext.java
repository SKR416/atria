package io.github.skr416.atria.iam.datascope;

import io.github.skr416.atria.iam.model.DataScopeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.Set;

/**
 * ThreadLocal holder populated by {@code DataScopeAspect} around {@link DataScope}-annotated methods.
 * Service queries can call {@link #toSpecification()} or inspect {@link #current()} directly.
 */
public final class DataScopeContext {

    public record Filter(
            DataScopeType scopeType,
            Long userId,
            Long departmentId,
            Set<Long> allowedDepartmentIds,
            String deptField,
            String userField
    ) {
        public boolean isUnrestricted() {
            return scopeType == DataScopeType.ALL;
        }

        public boolean permitsUser(Long targetUserId, Long targetDeptId) {
            return switch (scopeType) {
                case ALL -> true;
                case SELF_ONLY -> userId != null && userId.equals(targetUserId);
                case DEPT_ONLY -> (departmentId != null && departmentId.equals(targetDeptId))
                        || (userId != null && userId.equals(targetUserId));
                case DEPT_AND_SUB -> (targetDeptId != null && allowedDepartmentIds != null && allowedDepartmentIds.contains(targetDeptId))
                        || (userId != null && userId.equals(targetUserId));
            };
        }
    }

    private static final ThreadLocal<Filter> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(Filter filter) {
        HOLDER.set(filter);
    }

    public static Filter current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * Builds a JPA {@link Specification} enforcing the active {@link Filter} on the given entity root.
     * If no {@code @DataScope} is active on the current thread, returns an unrestricted predicate.
     */
    public static <T> Specification<T> toSpecification() {
        Filter f = HOLDER.get();
        if (f == null || f.isUnrestricted()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (Root<T> root, jakarta.persistence.criteria.CriteriaQuery<?> query, CriteriaBuilder cb) -> switch (f.scopeType()) {
            case ALL -> cb.conjunction();
            case SELF_ONLY -> f.userId() == null
                    ? cb.disjunction()
                    : cb.equal(root.get(f.userField()), f.userId());
            case DEPT_ONLY -> {
                if (f.departmentId() == null) {
                    yield f.userId() == null ? cb.disjunction() : cb.equal(root.get(f.userField()), f.userId());
                }
                yield cb.equal(root.get(f.deptField()), f.departmentId());
            }
            case DEPT_AND_SUB -> {
                Set<Long> depts = f.allowedDepartmentIds() != null ? f.allowedDepartmentIds() : Collections.emptySet();
                if (depts.isEmpty()) {
                    yield f.userId() == null ? cb.disjunction() : cb.equal(root.get(f.userField()), f.userId());
                }
                Predicate inDepts = root.get(f.deptField()).in(depts);
                yield inDepts;
            }
        };
    }
}
