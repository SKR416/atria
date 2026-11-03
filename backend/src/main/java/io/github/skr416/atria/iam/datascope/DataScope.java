package io.github.skr416.atria.iam.datascope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a service or controller method must have its query filtered by the caller's
 * effective {@link io.github.skr416.atria.iam.model.DataScopeType} (ADR-0004).
 * The resolved filter is exposed via {@link DataScopeContext#current()} for the duration of the call.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** Entity attribute name holding the department ID (default {@code "departmentId"}). */
    String deptField() default "departmentId";

    /** Entity attribute name holding the owner/user ID (default {@code "id"}). */
    String userField() default "id";
}
