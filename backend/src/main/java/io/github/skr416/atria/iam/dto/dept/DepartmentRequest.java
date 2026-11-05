package io.github.skr416.atria.iam.dto.dept;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request payload for creating or updating a department.
 */
public record DepartmentRequest(
        Long parentId,

        @NotBlank(message = "Department code must not be blank")
        @Size(max = 64, message = "Department code must not exceed 64 characters")
        String code,

        @NotEmpty(message = "Department name map must not be empty")
        Map<String, String> name,

        Long leaderId,

        @Size(max = 64, message = "Timezone must not exceed 64 characters")
        String timezone,

        @Size(max = 16, message = "Calendar code must not exceed 16 characters")
        String calendarCode,

        Integer sortOrder
) {
    public DepartmentRequest(
            Long parentId,
            String code,
            Map<String, String> name,
            Long leaderId,
            String timezone,
            Integer sortOrder
    ) {
        this(parentId, code, name, leaderId, timezone, null, sortOrder);
    }
}
