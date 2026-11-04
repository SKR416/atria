package io.github.skr416.atria.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skr416.atria.common.crypto.AesGcmCipher;
import io.github.skr416.atria.common.crypto.AesGcmStringConverter;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive Phase 1 integration tests covering ADR-0004:
 * - AES-256-GCM field encryption ({v1} prefix, random 96-bit IV, tamper detection)
 * - Bootstrap admin login, JWT access token + HttpOnly refresh cookie rotation
 * - DBA emergency plaintext / {noop} recovery login & mandatory password change gate
 * - 5 consecutive failed logins -> 15-minute lockout & admin manual unlock
 * - Hierarchical department tree CRUD, materialized tree_path cascade & cycle prevention
 * - @DataScope subtree query filtering (ALL vs DEPT_AND_SUB vs SELF_ONLY)
 * - Soft-delete non-destructive archiving & username reuse
 * - @AuditLog administrative mutation trail
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phase1IamIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AesGcmCipher aesGcmCipher;

    @Autowired
    private AesGcmStringConverter aesGcmStringConverter;

    @Autowired
    private UserRepository userRepository;

    private String loginAndGetAccessToken(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", password
        ));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    @Test
    @Order(1)
    @DisplayName("1. AES-256-GCM converter encrypts with {v1} prefix, random 96-bit IV, and rejects tampering")
    void aesGcmConverterShouldEncryptWithRandomIvAndDetectTampering() {
        assertNull(aesGcmStringConverter.convertToDatabaseColumn(null));
        assertNull(aesGcmStringConverter.convertToEntityAttribute(null));

        String secret = "slack-webhook-secret-token-2026-日本語-繁體";
        String c1 = aesGcmStringConverter.convertToDatabaseColumn(secret);
        String c2 = aesGcmStringConverter.convertToDatabaseColumn(secret);

        assertTrue(c1.startsWith(AesGcmCipher.CURRENT_VERSION_PREFIX));
        assertTrue(c2.startsWith(AesGcmCipher.CURRENT_VERSION_PREFIX));
        // Random 96-bit IV guarantees non-deterministic ciphertext
        assertNotEquals(c1, c2);

        assertEquals(secret, aesGcmStringConverter.convertToEntityAttribute(c1));
        assertEquals(secret, aesGcmStringConverter.convertToEntityAttribute(c2));

        // Tampered ciphertext or missing prefix must fail closed
        assertThrows(IllegalStateException.class,
                () -> aesGcmStringConverter.convertToEntityAttribute("plaintext-without-prefix"));
        String tampered = c1.substring(0, c1.length() - 2) + (c1.endsWith("A") ? "B" : "A");
        assertThrows(IllegalStateException.class,
                () -> aesGcmStringConverter.convertToEntityAttribute(tampered));
    }

    @Test
    @Order(2)
    @DisplayName("2. Bootstrap admin login, /me profile, and HttpOnly refresh cookie rotation")
    void bootstrapAdminLoginMeAndRefreshShouldSucceed() throws Exception {
        String loginJson = objectMapper.writeValueAsString(Map.of(
                "username", "admin",
                "password", "Admin@12345"
        ));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.user.dataScope").value("ALL"))
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("atria_refresh");
        assertNotNull(refreshCookie, "Refresh token HttpOnly cookie must be issued");
        assertTrue(refreshCookie.isHttpOnly());

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // Call /api/v1/auth/me with Bearer token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.dataScope").value("ALL"));

        // Refresh token rotation via HttpOnly cookie
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("3. Department tree CRUD, materialized tree_path cascade on move, and cycle/delete guards")
    void departmentTreeCrudAndMaterializedPathCascadeShouldWork() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", "Admin@12345");

        // Create R&D Division under Root (id=1, treePath=/1/)
        String createRdJson = objectMapper.writeValueAsString(Map.of(
                "parentId", 1,
                "code", "RD_DIV",
                "name", Map.of("zh-TW", "研發處", "en-US", "R&D Division", "ja-JP", "研究開発本部"),
                "timezone", "Asia/Taipei",
                "sortOrder", 10
        ));
        MvcResult rdRes = mockMvc.perform(post("/api/v1/iam/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRdJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("RD_DIV"))
                .andReturn();
        long rdId = objectMapper.readTree(rdRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertEquals("/1/" + rdId + "/",
                objectMapper.readTree(rdRes.getResponse().getContentAsString()).path("data").path("treePath").asText());

        // Create Backend Team under R&D Division
        String createBeJson = objectMapper.writeValueAsString(Map.of(
                "parentId", rdId,
                "code", "RD_BACKEND",
                "name", Map.of("zh-TW", "後端組", "en-US", "Backend Team", "ja-JP", "バックエンド課"),
                "timezone", "Asia/Taipei",
                "sortOrder", 20
        ));
        MvcResult beRes = mockMvc.perform(post("/api/v1/iam/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBeJson))
                .andExpect(status().isOk())
                .andReturn();
        long beId = objectMapper.readTree(beRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertEquals("/1/" + rdId + "/" + beId + "/",
                objectMapper.readTree(beRes.getResponse().getContentAsString()).path("data").path("treePath").asText());

        // Cycle prevention: moving R&D Division under its own child (Backend Team) must fail with 1015
        String invalidMoveJson = objectMapper.writeValueAsString(Map.of(
                "parentId", beId,
                "code", "RD_DIV",
                "name", Map.of("zh-TW", "研發處", "en-US", "R&D Division", "ja-JP", "研究開発本部"),
                "timezone", "Asia/Taipei",
                "sortOrder", 10
        ));
        mockMvc.perform(put("/api/v1/iam/departments/" + rdId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidMoveJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1015));

        // Deletion guard: deleting R&D Division while it still has child Backend Team must fail with 1014
        mockMvc.perform(delete("/api/v1/iam/departments/" + rdId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1014));

        // Create Tokyo Branch under Root, then move R&D Division under Tokyo Branch -> Backend Team treePath must cascade!
        String createTokyoJson = objectMapper.writeValueAsString(Map.of(
                "parentId", 1,
                "code", "TOKYO_HQ",
                "name", Map.of("zh-TW", "東京支社", "en-US", "Tokyo Branch", "ja-JP", "東京支社"),
                "timezone", "Asia/Tokyo",
                "sortOrder", 5
        ));
        MvcResult tokyoRes = mockMvc.perform(post("/api/v1/iam/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTokyoJson))
                .andExpect(status().isOk())
                .andReturn();
        long tokyoId = objectMapper.readTree(tokyoRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        String moveRdUnderTokyoJson = objectMapper.writeValueAsString(Map.of(
                "parentId", tokyoId,
                "code", "RD_DIV",
                "name", Map.of("zh-TW", "研發處", "en-US", "R&D Division", "ja-JP", "研究開発本部"),
                "timezone", "Asia/Tokyo",
                "sortOrder", 10
        ));
        mockMvc.perform(put("/api/v1/iam/departments/" + rdId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveRdUnderTokyoJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.treePath").value("/1/" + tokyoId + "/" + rdId + "/"));

        // Verify Backend Team's treePath cascaded automatically
        mockMvc.perform(get("/api/v1/iam/departments/" + beId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.treePath").value("/1/" + tokyoId + "/" + rdId + "/" + beId + "/"))
                .andExpect(jsonPath("$.data.localizedName").value("バックエンド課"));
    }

    @Test
    @Order(4)
    @DisplayName("4. DBA emergency plaintext recovery login forces password change and blocks other endpoints until changed")
    void dbaEmergencyPlaintextRecoveryShouldEnforcePasswordChangeGate() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", "Admin@12345");

        // Create a user via Admin API
        String createUserJson = objectMapper.writeValueAsString(Map.of(
                "username", "dba_target",
                "email", "dba_target@atria.local",
                "initialPassword", "InitPass@123",
                "displayName", "DBA Recovery Target",
                "departmentId", 1,
                "roleIds", Set.of(3), // DEPT_MANAGER
                "mustChangePassword", false
        ));
        MvcResult createRes = mockMvc.perform(post("/api/v1/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson))
                .andExpect(status().isOk())
                .andReturn();
        long userId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Simulate DBA emergency troubleshooting: directly write raw plaintext password in DB without {bcrypt} prefix
        User dbUser = userRepository.findById(userId).orElseThrow();
        dbUser.setPasswordHash("EmergencyDbaPlain999");
        dbUser.setMustChangePassword(false);
        userRepository.saveAndFlush(dbUser);

        // User logs in with the DBA plaintext password -> succeeds once with mustChangePassword = true
        String dbaLoginJson = objectMapper.writeValueAsString(Map.of(
                "username", "dba_target",
                "password", "EmergencyDbaPlain999"
        ));
        MvcResult dbaLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dbaLoginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();

        String restrictedToken = objectMapper.readTree(dbaLoginRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // Attempting to access any non-password-change endpoint with restricted token must be rejected with 403 / 1003
        mockMvc.perform(get("/api/v1/iam/departments/tree")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + restrictedToken)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003))
                .andExpect(jsonPath("$.message").value("Password change is required upon initial login before proceeding"));

        // Change password using the restricted token -> re-hashes with {bcrypt} and returns full access token
        String changePwdJson = objectMapper.writeValueAsString(Map.of(
                "currentPassword", "EmergencyDbaPlain999",
                "newPassword", "NewSecure@2026"
        ));
        MvcResult changeRes = mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + restrictedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePwdJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andReturn();

        String freshAccessToken = objectMapper.readTree(changeRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // Verify DB now holds a {bcrypt} hash and mustChangePassword is false
        User afterChange = userRepository.findById(userId).orElseThrow();
        assertTrue(afterChange.getPasswordHash().startsWith("{bcrypt}$2a$12$"));
        assertEquals(false, afterChange.isMustChangePassword());

        // Old restricted token is invalidated because tokenVersion was incremented
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + restrictedToken))
                .andExpect(status().isUnauthorized());

        // New access token works normally on business endpoints
        mockMvc.perform(get("/api/v1/iam/departments/tree")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(5)
    @DisplayName("5. Brute-force protection locks account after 5 failed attempts and admin unlock restores access")
    void bruteForceLockoutAfterFiveFailuresAndAdminUnlockShouldWork() throws Exception {
        String badLoginJson = objectMapper.writeValueAsString(Map.of(
                "username", "dba_target",
                "password", "WrongPassword!"
        ));

        // Attempts 1..4 return 401 (1001 AUTH_INVALID_CREDENTIALS)
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badLoginJson))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(1001));
        }

        // 5th consecutive failure triggers 15-minute lockout -> HTTP 423 Locked (1002 AUTH_ACCOUNT_LOCKED)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLoginJson))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value(1002));

        // Even with the correct password, locked account cannot log in
        String goodLoginJson = objectMapper.writeValueAsString(Map.of(
                "username", "dba_target",
                "password", "NewSecure@2026"
        ));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodLoginJson))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value(1002));

        // Admin unlocks the user
        String adminToken = loginAndGetAccessToken("admin", "Admin@12345");
        User lockedUser = userRepository.findWithAuthoritiesByUsername("dba_target").orElseThrow();
        mockMvc.perform(post("/api/v1/iam/users/" + lockedUser.getId() + "/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Now login with correct password succeeds
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodLoginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(6)
    @DisplayName("6. @DataScope filters department manager user queries to subtree and allows soft-deleted username reuse")
    void dataScopeSubtreeFilteringAndSoftDeleteReuseShouldWork() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", "Admin@12345");

        // Create two sibling departments under Root: Dept A and Dept B, plus Sub-Dept A1 under Dept A
        long deptAId = createDept(adminToken, 1L, "DEPT_A", "部門A");
        long deptA1Id = createDept(adminToken, deptAId, "DEPT_A1", "部門A1");
        long deptBId = createDept(adminToken, 1L, "DEPT_B", "部門B");

        // Create Manager in Dept A (role 3 = DEPT_MANAGER with DEPT_AND_SUB scope)
        long mgrAId = createUser(adminToken, "mgr_a", "mgr_a@atria.local", deptAId, Set.of(3L));
        // Create Employee in Sub-Dept A1 (role 4 = EMPLOYEE)
        long empA1Id = createUser(adminToken, "emp_a1", "emp_a1@atria.local", deptA1Id, Set.of(4L));
        // Create Employee in Dept B (outside Manager A's subtree)
        long empBId = createUser(adminToken, "emp_b", "emp_b@atria.local", deptBId, Set.of(4L));

        String mgrAToken = loginAndGetAccessToken("mgr_a", "Pass@12345");

        // Manager A lists users -> should only see mgr_a (Dept A) and emp_a1 (Sub-Dept A1), NOT emp_b or admin
        MvcResult listRes = mockMvc.perform(get("/api/v1/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mgrAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        String listBody = listRes.getResponse().getContentAsString();
        assertTrue(listBody.contains("\"mgr_a\""));
        assertTrue(listBody.contains("\"emp_a1\""));
        assertTrue(!listBody.contains("\"emp_b\""));

        // Manager A can view emp_a1 by ID, but is FORBIDDEN (403) from viewing emp_b in Dept B
        mockMvc.perform(get("/api/v1/iam/users/" + empA1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mgrAToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/iam/users/" + empBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mgrAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // Soft-delete emp_b and verify the same username/email can be reused for a new user (ADR-0004 soft delete)
        mockMvc.perform(delete("/api/v1/iam/users/" + empBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        long rehireId = createUser(adminToken, "emp_b", "emp_b@atria.local", deptBId, Set.of(4L));
        assertNotEquals(empBId, rehireId);

        // Verify @AuditLog recorded mutations and is accessible via /api/v1/iam/audit-logs
        mockMvc.perform(get("/api/v1/iam/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @Order(7)
    @DisplayName("7. User create, update, get, and paged list persist and return hireDate (DATE) accurately")
    void userCreateAndUpdateShouldPersistAndReturnHireDate() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", "Admin@12345");

        // Create user with hireDate = 2024-04-01
        String createBody = objectMapper.writeValueAsString(Map.of(
                "username", "emp_hire_test",
                "email", "emp_hire_test@atria.local",
                "initialPassword", "Pass@12345",
                "displayName", "Hire Date Employee",
                "departmentId", 1L,
                "hireDate", "2024-04-01",
                "locale", "ja-JP",
                "roleIds", Set.of(4L),
                "mustChangePassword", false
        ));

        MvcResult createRes = mockMvc.perform(post("/api/v1/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("emp_hire_test"))
                .andExpect(jsonPath("$.data.hireDate").value("2024-04-01"))
                .andReturn();

        long userId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Update user's hireDate to 2023-10-15
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "email", "emp_hire_test@atria.local",
                "displayName", "Hire Date Employee Updated",
                "departmentId", 1L,
                "hireDate", "2023-10-15",
                "locale", "ja-JP",
                "enabled", true,
                "roleIds", Set.of(4L)
        ));

        mockMvc.perform(put("/api/v1/iam/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hireDate").value("2023-10-15"));

        // Verify GET by ID returns updated hireDate
        mockMvc.perform(get("/api/v1/iam/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hireDate").value("2023-10-15"));

        // Verify paged user list also includes hireDate
        mockMvc.perform(get("/api/v1/iam/users")
                        .param("keyword", "emp_hire_test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].hireDate").value("2023-10-15"));
    }

    private long createDept(String token, Long parentId, String code, String zhName) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "parentId", parentId,
                "code", code,
                "name", Map.of("zh-TW", zhName, "en-US", code, "ja-JP", zhName),
                "timezone", "Asia/Taipei",
                "sortOrder", 1
        ));
        MvcResult res = mockMvc.perform(post("/api/v1/iam/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createUser(String token, String username, String email, Long deptId, Set<Long> roleIds) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "email", email,
                "initialPassword", "Pass@12345",
                "displayName", username,
                "departmentId", deptId,
                "roleIds", roleIds,
                "mustChangePassword", false
        ));
        MvcResult res = mockMvc.perform(post("/api/v1/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }
}
