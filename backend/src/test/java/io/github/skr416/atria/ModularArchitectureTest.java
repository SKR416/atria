package io.github.skr416.atria;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import io.github.skr416.atria.attendance.service.AttendanceRecordCalculator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural fitness tests enforcing ADR-0001 modular monolith boundaries,
 * clean dependency flow, pure domain calculation isolation, and cycle-free module graph.
 */
@AnalyzeClasses(packages = "io.github.skr416.atria", importOptions = ImportOption.DoNotIncludeTests.class)
class ModularArchitectureTest {

    /**
     * Predicate excluding AuditLogController: In Phase 1 IAM, AuditLogController exposes
     * administrative audit log queries directly via AuditLogRepository without dedicated domain logic.
     */
    private static final DescribedPredicate<JavaClass> NOT_AUDIT_LOG_CONTROLLER =
            new DescribedPredicate<>("not AuditLogController (administrative query endpoint)") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return !"AuditLogController".equals(javaClass.getSimpleName());
                }
            };

    /**
     * Rule 1: Web presentation controllers must not directly access repositories.
     * All database access should flow through domain application services.
     */
    @ArchTest
    static final ArchRule controllersShouldNotAccessRepositoriesDirectly =
            noClasses().that().resideInAPackage("..controller..")
                    .and(NOT_AUDIT_LOG_CONTROLLER)
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("Controllers should interact via application services rather than accessing repositories directly");

    /**
     * Rule 2: Shared common infrastructure must remain independent of all business domain modules.
     */
    @ArchTest
    static final ArchRule commonModuleShouldNotDependOnBusinessModules =
            noClasses().that().resideInAPackage("io.github.skr416.atria.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.github.skr416.atria.iam..",
                            "io.github.skr416.atria.attendance..",
                            "io.github.skr416.atria.workflow..",
                            "io.github.skr416.atria.notification..",
                            "io.github.skr416.atria.announcement..",
                            "io.github.skr416.atria.ai.."
                    )
                    .because("The common module provides foundational cross-cutting utilities and must not depend on business domains");

    /**
     * Rule 3: IAM foundation module must not depend on upper-layer business modules.
     */
    @ArchTest
    static final ArchRule iamFoundationShouldNotDependOnUpperBusinessModules =
            noClasses().that().resideInAPackage("io.github.skr416.atria.iam..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.github.skr416.atria.attendance..",
                            "io.github.skr416.atria.workflow..",
                            "io.github.skr416.atria.notification..",
                            "io.github.skr416.atria.announcement..",
                            "io.github.skr416.atria.ai.."
                    )
                    .because("IAM provides identity, authentication, and RBAC foundation and must not depend on higher-level business modules");

    /**
     * Rule 4: Attendance calculation engine isolation.
     * AttendanceRecordCalculator encapsulates complex shift rounding, break deduction,
     * and status evaluations and must never depend on UI web controllers.
     */
    @ArchTest
    static final ArchRule attendanceCoreCalculatorShouldBePureDomainLogic =
            noClasses().that().areAssignableTo(AttendanceRecordCalculator.class)
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .because("AttendanceRecordCalculator domain engine must not depend on web/controller classes");

    /**
     * Rule 5: Top-level domain modules must form a directed acyclic graph (DAG) free of cycles.
     */
    @ArchTest
    static final ArchRule topLevelModulesShouldBeFreeOfCycles =
            SlicesRuleDefinition.slices().matching("io.github.skr416.atria.(*)..")
                    .should().beFreeOfCycles()
                    .because("Top-level modules (common, iam, attendance, workflow, etc.) must not have cyclic dependencies");
}
