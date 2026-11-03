package io.github.skr416.atria.iam.controller;

import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.iam.dto.dept.DepartmentRequest;
import io.github.skr416.atria.iam.dto.dept.DepartmentTreeNodeResponse;
import io.github.skr416.atria.iam.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller exposing department tree query and CRUD endpoints (ADR-0004).
 */
@RestController
@RequestMapping("/api/v1/iam/departments")
@RequiredArgsConstructor
@Tag(name = "IAM - Departments", description = "Department tree CRUD and materialized path management")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final MessageSource messageSource;

    private String successMessage() {
        return messageSource.getMessage(ErrorCode.SUCCESS.getMessageKey(), null, LocaleContextHolder.getLocale());
    }

    @GetMapping(value = {"", "/tree"})
    @PreAuthorize("hasAuthority('iam:dept:read')")
    @Operation(summary = "Get department tree", description = "Returns full hierarchical department tree sorted by sortOrder and id")
    public ApiResponse<List<DepartmentTreeNodeResponse>> getTree() {
        List<DepartmentTreeNodeResponse> tree = departmentService.getTree();
        return ApiResponse.ok(tree, successMessage());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:dept:read')")
    @Operation(summary = "Get department by ID", description = "Returns a single department node by its primary key")
    public ApiResponse<DepartmentTreeNodeResponse> getById(@PathVariable Long id) {
        DepartmentTreeNodeResponse dept = departmentService.getById(id);
        return ApiResponse.ok(dept, successMessage());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('iam:dept:write')")
    @Operation(summary = "Create department", description = "Creates a new department and computes its materialized treePath")
    public ApiResponse<DepartmentTreeNodeResponse> create(@Valid @RequestBody DepartmentRequest request) {
        DepartmentTreeNodeResponse created = departmentService.create(request);
        return ApiResponse.ok(created, successMessage());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:dept:write')")
    @Operation(summary = "Update department", description = "Updates department details and cascades treePath updates to descendants if parentId changed")
    public ApiResponse<DepartmentTreeNodeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request) {
        DepartmentTreeNodeResponse updated = departmentService.update(id, request);
        return ApiResponse.ok(updated, successMessage());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:dept:write')")
    @Operation(summary = "Delete department", description = "Soft-deletes department if it has no child departments and no assigned users")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ApiResponse.ok(null, successMessage());
    }
}
