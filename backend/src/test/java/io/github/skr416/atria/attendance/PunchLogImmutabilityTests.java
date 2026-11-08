package io.github.skr416.atria.attendance;

import io.github.skr416.atria.attendance.model.PunchLog;
import io.github.skr416.atria.attendance.model.PunchSource;
import io.github.skr416.atria.attendance.repository.PunchLogRepository;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests verifying strict append-only immutability of {@code att_punch_log} (ADR-0008 Decision 1):
 * - JPA entity-level defense: {@code @Immutable}, {@code @PreRemove}, and {@code @PreUpdate} reject updates and deletes.
 * - Database-level defense: On PostgreSQL, trigger {@code trg_att_punch_log_immutable} blocks direct SQL UPDATE and DELETE.
 */
@SpringBootTest
class PunchLogImmutabilityTests {

    @Autowired
    private PunchLogRepository punchLogRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Test 1: JPA entity-level immutability enforcement.
     * Inserts a PunchLog via repository, then verifies that calling EntityManager.remove() or
     * updating fields triggers @PreRemove / @PreUpdate and is rejected with an exception,
     * leaving the row and note intact in att_punch_log.
     */
    @Test
    @DisplayName("1. JPA entity-level immutability: @PreRemove & @PreUpdate reject mutation and preserve row")
    void jpaLevelImmutabilityShouldRejectUpdatesAndDeletes() {
        Long userId = userRepository.findWithAuthoritiesByUsername("admin")
                .map(User::getId)
                .orElse(1L);

        PunchLog log = new PunchLog();
        log.setUserId(userId);
        log.setWorkDate(LocalDate.of(2026, 6, 15));
        log.setRawPunchTime(Instant.parse("2026-06-15T01:00:00Z"));
        log.setClientIp("127.0.0.1");
        log.setSource(PunchSource.WEB);
        log.setNote("immutable-check");

        PunchLog saved = punchLogRepository.saveAndFlush(log);
        Long savedId = saved.getId();
        assertNotNull(savedId, "Saved punch log must have a generated ID");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // Verify remove is rejected by @PreRemove
        Exception removeEx = assertThrows(Exception.class, () -> {
            tx.execute(status -> {
                PunchLog managed = entityManager.find(PunchLog.class, savedId);
                assertNotNull(managed);
                entityManager.remove(managed);
                entityManager.flush();
                return null;
            });
        });

        Throwable rootRemove = getRootCause(removeEx);
        assertTrue(rootRemove instanceof UnsupportedOperationException || rootRemove instanceof PersistenceException,
                "Expected UnsupportedOperationException or PersistenceException from @PreRemove, got: " + rootRemove);
        assertTrue(rootRemove.getMessage().contains("att_punch_log is strictly append-only and immutable"),
                "Exception message must state immutability: " + rootRemove.getMessage());

        // Verify row still exists with original note
        PunchLog afterRemoveAttempt = punchLogRepository.findById(savedId).orElseThrow();
        assertEquals("immutable-check", afterRemoveAttempt.getNote());

        // Verify in-memory field changes on @Immutable entity are ignored by Hibernate dirty-checking upon flush
        tx.execute(status -> {
            PunchLog managed = entityManager.find(PunchLog.class, savedId);
            assertNotNull(managed);
            managed.setNote("tampered-note");
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        // Verify database row still holds original note (no UPDATE SQL was issued)
        PunchLog afterUpdateAttempt = punchLogRepository.findById(savedId).orElseThrow();
        assertEquals("immutable-check", afterUpdateAttempt.getNote());
    }

    /**
     * Test 2: Database-level immutability enforcement.
     * When running against PostgreSQL, verifies that the trigger trg_att_punch_log_immutable rejects
     * raw SQL UPDATE and DELETE statements with DataAccessException containing the statutory audit ledger message.
     * On H2 (or other engines without the PL/pgSQL trigger), verifies row remains intact.
     */
    @Test
    @DisplayName("2. Database-level immutability: PostgreSQL trigger rejects raw SQL UPDATE and DELETE")
    void databaseLevelImmutabilityShouldRejectRawSqlMutationsOnPostgres() throws SQLException {
        Long userId = userRepository.findWithAuthoritiesByUsername("admin")
                .map(User::getId)
                .orElse(1L);

        PunchLog log = new PunchLog();
        log.setUserId(userId);
        log.setWorkDate(LocalDate.of(2026, 6, 16));
        log.setRawPunchTime(Instant.parse("2026-06-16T01:00:00Z"));
        log.setClientIp("127.0.0.1");
        log.setSource(PunchSource.WEB);
        log.setNote("immutable-check");

        PunchLog saved = punchLogRepository.saveAndFlush(log);
        Long savedId = saved.getId();
        assertNotNull(savedId);

        String databaseProductName;
        try (Connection conn = dataSource.getConnection()) {
            databaseProductName = conn.getMetaData().getDatabaseProductName();
        }

        if ("PostgreSQL".equalsIgnoreCase(databaseProductName)) {
            // Raw SQL UPDATE must trigger fn_prevent_att_punch_log_mutation()
            DataAccessException updateEx = assertThrows(DataAccessException.class, () -> {
                jdbcTemplate.update("UPDATE att_punch_log SET note = 'tampered' WHERE id = ?", savedId);
            });
            assertTrue(updateEx.getMessage().contains("att_punch_log is strictly append-only and immutable"),
                    "PostgreSQL trigger must reject UPDATE on att_punch_log: " + updateEx.getMessage());

            // Raw SQL DELETE must trigger fn_prevent_att_punch_log_mutation()
            DataAccessException deleteEx = assertThrows(DataAccessException.class, () -> {
                jdbcTemplate.update("DELETE FROM att_punch_log WHERE id = ?", savedId);
            });
            assertTrue(deleteEx.getMessage().contains("att_punch_log is strictly append-only and immutable"),
                    "PostgreSQL trigger must reject DELETE on att_punch_log: " + deleteEx.getMessage());
        }

        // On both PostgreSQL and H2, verify the row remains completely intact
        PunchLog intact = punchLogRepository.findById(savedId).orElseThrow();
        assertEquals("immutable-check", intact.getNote());
        assertEquals(userId, intact.getUserId());
        assertEquals(LocalDate.of(2026, 6, 16), intact.getWorkDate());
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
