package io.github.skr416.atria.iam.service;

import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.audit.AuditLog;
import io.github.skr416.atria.iam.dto.dept.DepartmentRequest;
import io.github.skr416.atria.iam.dto.dept.DepartmentTreeNodeResponse;
import io.github.skr416.atria.iam.model.Department;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Service managing department tree structure, materialized path updates, and lifecycle operations (ADR-0004).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    /**
     * Builds and returns the hierarchical department tree.
     */
    @Transactional(readOnly = true)
    public List<DepartmentTreeNodeResponse> getTree() {
        Locale locale = LocaleContextHolder.getLocale();
        List<Department> departments = departmentRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, DepartmentTreeNodeResponse> nodeMap = new LinkedHashMap<>();

        for (Department dept : departments) {
            nodeMap.put(dept.getId(), DepartmentTreeNodeResponse.from(dept, locale));
        }

        List<DepartmentTreeNodeResponse> roots = new ArrayList<>();
        for (Department dept : departments) {
            DepartmentTreeNodeResponse node = nodeMap.get(dept.getId());
            Long parentId = dept.getParentId();
            if (parentId == null || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                nodeMap.get(parentId).getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * Finds a single department by its primary key.
     */
    @Transactional(readOnly = true)
    public DepartmentTreeNodeResponse getById(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
        return DepartmentTreeNodeResponse.from(dept, LocaleContextHolder.getLocale());
    }

    /**
     * Creates a new department node and initializes its materialized treePath.
     */
    @Transactional
    @AuditLog(action = "DEPT_CREATE", targetType = "DEPARTMENT")
    public DepartmentTreeNodeResponse create(DepartmentRequest req) {
        if (departmentRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.DEPT_CODE_ALREADY_EXISTS);
        }

        Department parent = null;
        if (req.parentId() != null) {
            parent = departmentRepository.findById(req.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
        }

        if (req.leaderId() != null && !userRepository.existsById(req.leaderId())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Department dept = new Department();
        dept.setCode(req.code());
        dept.setName(req.name());
        dept.setParentId(req.parentId());
        dept.setLeaderId(req.leaderId());
        dept.setTimezone(req.timezone() != null && !req.timezone().isBlank()
                ? req.timezone()
                : (parent != null ? parent.getTimezone() : "Asia/Taipei"));
        dept.setCalendarCode(req.calendarCode() != null && !req.calendarCode().isBlank()
                ? req.calendarCode().trim().toUpperCase()
                : (parent != null && parent.getCalendarCode() != null ? parent.getCalendarCode() : "TW"));
        dept.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        dept.setTreePath("/");

        dept = departmentRepository.save(dept);

        String computedTreePath = parent == null
                ? "/" + dept.getId() + "/"
                : parent.getTreePath() + dept.getId() + "/";
        dept.setTreePath(computedTreePath);
        dept = departmentRepository.save(dept);

        log.info("Created department [{}] with treePath [{}]", dept.getCode(), dept.getTreePath());
        return DepartmentTreeNodeResponse.from(dept, LocaleContextHolder.getLocale());
    }

    /**
     * Updates department metadata and cascades treePath updates to descendants if parentId changed.
     */
    @Transactional
    @AuditLog(action = "DEPT_UPDATE", targetType = "DEPARTMENT")
    public DepartmentTreeNodeResponse update(Long id, DepartmentRequest req) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));

        if (!dept.getCode().equals(req.code()) && departmentRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.DEPT_CODE_ALREADY_EXISTS);
        }

        if (req.leaderId() != null && !userRepository.existsById(req.leaderId())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        boolean parentChanged = !Objects.equals(dept.getParentId(), req.parentId());
        if (parentChanged) {
            String oldTreePath = dept.getTreePath();
            String newTreePath;
            if (req.parentId() != null) {
                if (req.parentId().equals(id)) {
                    throw new BusinessException(ErrorCode.DEPT_INVALID_PARENT);
                }
                Department newParent = departmentRepository.findById(req.parentId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));

                if (newParent.getTreePath().startsWith(oldTreePath)) {
                    throw new BusinessException(ErrorCode.DEPT_INVALID_PARENT);
                }
                newTreePath = newParent.getTreePath() + id + "/";
            } else {
                newTreePath = "/" + id + "/";
            }

            List<Department> descendants = departmentRepository.findByTreePathStartingWith(oldTreePath);
            for (Department desc : descendants) {
                if (desc.getId().equals(id)) {
                    continue;
                }
                String updatedPath = newTreePath + desc.getTreePath().substring(oldTreePath.length());
                desc.setTreePath(updatedPath);
                departmentRepository.save(desc);
            }

            dept.setParentId(req.parentId());
            dept.setTreePath(newTreePath);
        }

        dept.setCode(req.code());
        dept.setName(req.name());
        dept.setLeaderId(req.leaderId());
        if (req.timezone() != null && !req.timezone().isBlank()) {
            dept.setTimezone(req.timezone());
        }
        if (req.calendarCode() != null && !req.calendarCode().isBlank()) {
            dept.setCalendarCode(req.calendarCode().trim().toUpperCase());
        }
        if (req.sortOrder() != null) {
            dept.setSortOrder(req.sortOrder());
        }

        dept = departmentRepository.save(dept);
        log.info("Updated department [{}] (id={}) with treePath [{}]", dept.getCode(), dept.getId(), dept.getTreePath());
        return DepartmentTreeNodeResponse.from(dept, LocaleContextHolder.getLocale());
    }

    /**
     * Soft-deletes a department if it contains no child departments and has no assigned users.
     */
    @Transactional
    @AuditLog(action = "DEPT_DELETE", targetType = "DEPARTMENT")
    public void delete(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));

        if (departmentRepository.existsByParentId(id) || userRepository.existsByDepartmentId(id)) {
            throw new BusinessException(ErrorCode.DEPT_HAS_CHILDREN_OR_USERS);
        }

        dept.setDeleted(true);
        departmentRepository.save(dept);
        log.info("Soft-deleted department [{}] (id={})", dept.getCode(), id);
    }
}
