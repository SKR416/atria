package io.github.skr416.atria.iam.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an administrative mutation method so {@code AuditLogAspect} records an {@code iam_audit_log} entry
 * after successful execution.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** Action code, e.g. {@code "USER_CREATE"}, {@code "DEPT_UPDATE"}, {@code "ROLE_DELETE"}. */
    String action();

    /** Target resource category, e.g. {@code "USER"}, {@code "DEPARTMENT"}, {@code "ROLE"}. */
    String targetType() default "";
}
