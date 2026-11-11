package io.github.skr416.atria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skr416.atria.attendance.model.AttendanceRequest;
import io.github.skr416.atria.attendance.model.AttendanceRequestStatus;
import io.github.skr416.atria.attendance.model.AttendanceStatus;
import io.github.skr416.atria.attendance.model.DailyAttendanceRecord;
import io.github.skr416.atria.attendance.model.LeaveBalance;
import io.github.skr416.atria.attendance.repository.AttendanceRequestRepository;
import io.github.skr416.atria.attendance.repository.DailyAttendanceRecordRepository;
import io.github.skr416.atria.attendance.repository.LeaveBalanceRepository;
import io.github.skr416.atria.notification.repository.DigestDeliveryLogRepository;
import io.github.skr416.atria.notification.repository.NotificationRecordRepository;
import io.github.skr416.atria.notification.service.EmailNotificationChannel;
import io.github.skr416.atria.workflow.model.WorkflowInstance;
import io.github.skr416.atria.workflow.model.WorkflowInstanceStatus;
import io.github.skr416.atria.workflow.model.WorkflowTargetType;
import io.github.skr416.atria.workflow.model.WorkflowTaskAction;
import io.github.skr416.atria.workflow.model.WorkflowTaskLog;
import io.github.skr416.atria.workflow.repository.FormTemplateRepository;
import io.github.skr416.atria.workflow.repository.WorkflowDeputyRepository;
import io.github.skr416.atria.workflow.repository.WorkflowInstanceRepository;
import io.github.skr416.atria.workflow.repository.WorkflowTaskLogRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test suite for Phase 4a:
 * Attendance -> Workflow -> Attendance Event Loop & Multi-Step Structured Conditions,
 * Dynamic Form Versioning, Return-to-Draft (差戻し), Resubmission Diff,
 * Deputy Delegation (wf_deputy) & HR Force Reassign, and
 * In-App Notifications & Idempotent Monthly Leave Balance Digest (ntf_digest_log).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase4aWorkflowNotificationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowInstanceRepository workflowInstanceRepository;

    @Autowired
    private WorkflowTaskLogRepository workflowTaskLogRepository;

    @Autowired
    private WorkflowDeputyRepository workflowDeputyRepository;

    @Autowired
    private FormTemplateRepository formTemplateRepository;

    @Autowired
    private AttendanceRequestRepository attendanceRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private DailyAttendanceRecordRepository dailyAttendanceRecordRepository;

    @Autowired
    private NotificationRecordRepository notificationRecordRepository;

    @Autowired
    private DigestDeliveryLogRepository digestDeliveryLogRepository;

    @Autowired
    private EmailNotificationChannel emailNotificationChannel;

    // Shared IDs across ordered tests
    private static String adminToken;
    private static String parentLeaderToken;
    private static String directLeaderToken;
    private static String empToken;
    private static String deputyToken;
    private static String reassignTargetToken;

    private static Long parentDeptId;
    private static Long directDeptId;

    private static Long parentLeaderId;
    private static Long directLeaderId;
    private static Long empUserId;
    private static Long deputyUserId;
    private static Long reassignTargetUserId;

    private static Long empAnnualBucketId;
    private static Long leaveRequestId;
    private static Long leaveWfInstanceId;

    private static Long dynamicFormInstanceId;
    private static Long generalExpenseTemplateId;
    private static Long createdDeputyId;

    private String login(String username, String password) throws Exception {
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

    private LocalDate findRecentWorkday(int offsetIndex) {
        LocalDate base = LocalDate.now().minusDays(10);
        int found = 0;
        for (int i = 0; i < 30; i++) {
            LocalDate candidate = base.plusDays(i);
            int dow = candidate.getDayOfWeek().getValue();
            if (dow >= 1 && dow <= 5 && candidate.getMonth() == LocalDate.now().getMonth()) {
                if (found == offsetIndex) {
                    return candidate;
                }
                found++;
            }
        }
        return LocalDate.now();
    }

    private Long createDepartment(String code, String nameZh, Long parentId, Long leaderId) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("name", Map.of("zh-TW", nameZh, "en-US", code, "ja-JP", code));
        map.put("parentId", parentId);
        map.put("timezone", "Asia/Taipei");
        map.put("calendarCode", "TW");
        map.put("sortOrder", 35);
        if (leaderId != null) {
            map.put("leaderId", leaderId);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/iam/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(map)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void updateDepartmentLeader(Long deptId, String code, String nameZh, Long parentId, Long leaderId) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("name", Map.of("zh-TW", nameZh, "en-US", code, "ja-JP", code));
        map.put("parentId", parentId);
        map.put("timezone", "Asia/Taipei");
        map.put("calendarCode", "TW");
        map.put("sortOrder", 35);
        map.put("leaderId", leaderId);
        mockMvc.perform(put("/api/v1/iam/departments/" + deptId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(map)))
                .andExpect(status().isOk());
    }

    private Long createUser(String username, String displayName, String email, Long deptId, int roleId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "displayName", displayName,
                "email", email,
                "initialPassword", "Emp@12345",
                "departmentId", deptId,
                "hireDate", LocalDate.now().minusMonths(12).toString(),
                "locale", "zh-TW",
                "enabled", true,
                "mustChangePassword", false,
                "roleIds", List.of(roleId)
        ));
        MvcResult res = mockMvc.perform(post("/api/v1/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    @Test
    @Order(1)
    @DisplayName("0. Setup Phase 4a organization hierarchy, test users, and initial leave balance")
    void setupPhase4aTestEnvironment() throws Exception {
        adminToken = login("admin", "Admin@12345");

        // Ensure 2026 TW calendar exists
        mockMvc.perform(post("/api/v1/attendance/calendars/generate-year")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "calendarCode", "TW",
                        "year", 2026,
                        "overwriteExisting", false
                ))));

        // 1. Create parent & direct departments
        parentDeptId = createDepartment("P4A_PAR", "Phase4a 上級處室", 1L, null);
        directDeptId = createDepartment("P4A_DIR", "Phase4a 直屬團隊", parentDeptId, null);

        // 2. Create users
        parentLeaderId = createUser("p4a_par_ldr", "Phase4a Parent Leader", "p4a_par_ldr@atria.local", parentDeptId, 3);
        directLeaderId = createUser("p4a_dir_ldr", "Phase4a Direct Leader", "p4a_dir_ldr@atria.local", directDeptId, 3);
        empUserId = createUser("p4a_employee", "Phase4a Applicant Emp", "p4a_emp@atria.local", directDeptId, 4);
        deputyUserId = createUser("p4a_deputy_user", "Phase4a Deputy User", "p4a_deputy@atria.local", directDeptId, 4);
        reassignTargetUserId = createUser("p4a_reassign_user", "Phase4a Reassign Target", "p4a_reassign@atria.local", directDeptId, 4);

        // 3. Assign leaders to departments
        updateDepartmentLeader(parentDeptId, "P4A_PAR", "Phase4a 上級處室", 1L, parentLeaderId);
        updateDepartmentLeader(directDeptId, "P4A_DIR", "Phase4a 直屬團隊", parentDeptId, directLeaderId);

        // 4. Acquire login tokens for all test users
        parentLeaderToken = login("p4a_par_ldr", "Emp@12345");
        directLeaderToken = login("p4a_dir_ldr", "Emp@12345");
        empToken = login("p4a_employee", "Emp@12345");
        deputyToken = login("p4a_deputy_user", "Emp@12345");
        reassignTargetToken = login("p4a_reassign_user", "Emp@12345");

        // 5. Grant manual leave balance to employee (4800 mins ANNUAL = 10 days)
        String grantBody = objectMapper.writeValueAsString(Map.of(
                "userId", empUserId,
                "leaveTypeCode", "ANNUAL",
                "grantMinutes", 4800,
                "validFrom", LocalDate.now().minusMonths(1).toString(),
                "validUntil", LocalDate.now().plusMonths(11).toString(),
                "note", "Phase 4a manual annual leave bucket"
        ));
        MvcResult grantRes = mockMvc.perform(post("/api/v1/attendance/leave-balances/manual-grant")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody))
                .andExpect(status().isOk())
                .andReturn();
        empAnnualBucketId = objectMapper.readTree(grantRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertTrue(empAnnualBucketId > 0);
    }

    @Test
    @Order(2)
    @DisplayName("1. Attendance -> Workflow -> Attendance Event Loop & Multi-Step Structured Conditions")
    void attendanceWorkflowEventLoopAndStructuredConditions() throws Exception {
        LocalDate workday = findRecentWorkday(0);
        ZoneId zone = ZoneId.of("Asia/Taipei");
        Instant startInstant = workday.atTime(9, 0).atZone(zone).toInstant();
        Instant endInstant = workday.atTime(18, 0).atZone(zone).toInstant();

        // 1a. Employee submits 1-day (480 min) leave request
        String leaveBody = objectMapper.writeValueAsString(Map.of(
                "leaveTypeCode", "ANNUAL",
                "startTime", startInstant.toString(),
                "endTime", endInstant.toString(),
                "reason", "1-day annual leave (480 mins net)"
        ));
        MvcResult leaveRes = mockMvc.perform(post("/api/v1/attendance/requests/leave")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leaveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requestedMinutes").value(480))
                .andReturn();
        leaveRequestId = objectMapper.readTree(leaveRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 1b. Verify FIFO balance freeze: pendingMinutes == 480, availableMinutes == 4320
        LeaveBalance bucketAfterSubmit = leaveBalanceRepository.findById(empAnnualBucketId).orElseThrow();
        assertEquals(480, bucketAfterSubmit.getPendingMinutes());
        assertEquals(4320, bucketAfterSubmit.getAvailableMinutes());

        // 1c. Verify WorkflowInstance automatically created via AttRequestSubmittedEvent
        WorkflowInstance instance = workflowInstanceRepository.findByAttRequestId(leaveRequestId).orElseThrow();
        leaveWfInstanceId = instance.getId();
        assertEquals(WorkflowInstanceStatus.PENDING, instance.getStatus());
        assertEquals(WorkflowTargetType.ATT_LEAVE, instance.getTargetType());
        assertEquals(1, instance.getCurrentStepOrder());
        assertEquals(directLeaderId, instance.getCurrentApproverId());
        assertEquals(1, instance.getSubmissionCount());

        // 1d. Attempting POST /return-to-draft on attendance-linked instance is rejected with 400 and ErrorCode 3009
        mockMvc.perform(post("/api/v1/workflow/instances/" + leaveWfInstanceId + "/return-to-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + directLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Cannot return attendance to draft"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3009));

        // 1e. Direct leader approves the 1-day leave (<= 1440 min, so Steps 2 & 3 conditions false -> full approval)
        mockMvc.perform(post("/api/v1/workflow/instances/" + leaveWfInstanceId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + directLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Approved by direct leader"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 1f. Verify AttRequestApprovedEvent completed: att_request status APPROVED, FIFO commit to used
        AttendanceRequest approvedReq = attendanceRequestRepository.findById(leaveRequestId).orElseThrow();
        assertEquals(AttendanceRequestStatus.APPROVED, approvedReq.getStatus());

        LeaveBalance bucketAfterApprove = leaveBalanceRepository.findById(empAnnualBucketId).orElseThrow();
        assertEquals(0, bucketAfterApprove.getPendingMinutes());
        assertEquals(480, bucketAfterApprove.getUsedMinutes());
        assertEquals(4320, bucketAfterApprove.getAvailableMinutes());

        // 1g. Verify att_daily_record for the workday reflects ON_LEAVE
        Optional<DailyAttendanceRecord> dailyOpt = dailyAttendanceRecordRepository.findByUserIdAndWorkDate(empUserId, workday);
        assertTrue(dailyOpt.isPresent(), "Daily attendance record must be created/updated upon leave approval");
        assertEquals(AttendanceStatus.ON_LEAVE, dailyOpt.get().getStatus());
        assertEquals(480, dailyOpt.get().getLeaveMinutes());

        // 1h. Post-approval revocation: Employee revokes approved instance
        mockMvc.perform(post("/api/v1/workflow/instances/" + leaveWfInstanceId + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Emergency work, need to cancel leave"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        // 1i. Verify AttRequestRevokedEvent: wf_instance & att_request both REVOKED, FIFO balance restored
        WorkflowInstance revokedInstance = workflowInstanceRepository.findById(leaveWfInstanceId).orElseThrow();
        assertEquals(WorkflowInstanceStatus.REVOKED, revokedInstance.getStatus());

        AttendanceRequest revokedReq = attendanceRequestRepository.findById(leaveRequestId).orElseThrow();
        assertEquals(AttendanceRequestStatus.REVOKED, revokedReq.getStatus());

        LeaveBalance bucketAfterRevoke = leaveBalanceRepository.findById(empAnnualBucketId).orElseThrow();
        assertEquals(0, bucketAfterRevoke.getUsedMinutes());
        assertEquals(4800, bucketAfterRevoke.getAvailableMinutes());

        // Daily record leave minutes reset
        DailyAttendanceRecord dailyAfterRevoke = dailyAttendanceRecordRepository.findByUserIdAndWorkDate(empUserId, workday).orElseThrow();
        assertEquals(0, dailyAfterRevoke.getLeaveMinutes());
    }

    @Test
    @Order(3)
    @DisplayName("2. Dynamic Form Versioning, Return-to-Draft (差戻し), and Resubmission Diff")
    void dynamicFormVersioningReturnToDraftAndResubmissionDiff() throws Exception {
        // 2a. Applicant submits standalone dynamic form (GENERAL_EXPENSE) with amount = 15000 (>= 10000 -> 2 steps)
        String submitBody = objectMapper.writeValueAsString(Map.of(
                "formTemplateCode", "GENERAL_EXPENSE",
                "title", "Hsinchu Client Travel Expense",
                "formData", Map.of(
                        "expenseCategory", "TRAVEL",
                        "amount", 15000,
                        "expenseDate", "2026-11-10",
                        "purpose", "Hsinchu science park client discussion"
                )
        ));
        MvcResult submitRes = mockMvc.perform(post("/api/v1/workflow/instances/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.formVersion").value(1))
                .andExpect(jsonPath("$.data.submissionCount").value(1))
                .andExpect(jsonPath("$.data.currentStepOrder").value(1))
                .andReturn();
        dynamicFormInstanceId = objectMapper.readTree(submitRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        WorkflowInstance inst1 = workflowInstanceRepository.findById(dynamicFormInstanceId).orElseThrow();
        assertEquals(directLeaderId, inst1.getCurrentApproverId());
        assertNotNull(inst1.getSchemaSnapshotJson());
        assertEquals(4, inst1.getSchemaSnapshotJson().size());

        // 2b. Approver (direct leader) returns the form to draft (差戻し)
        mockMvc.perform(post("/api/v1/workflow/instances/" + dynamicFormInstanceId + "/return-to-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + directLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Please include taxi details and updated purpose"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        WorkflowInstance instDraft = workflowInstanceRepository.findById(dynamicFormInstanceId).orElseThrow();
        assertEquals(WorkflowInstanceStatus.DRAFT, instDraft.getStatus());
        assertNotNull(instDraft.getPreviousFormDataJson());
        assertEquals(15000, instDraft.getPreviousFormDataJson().get("amount"));

        // 2c. Applicant modifies amount from 15000 to 25000 and purpose, then resubmits
        String resubmitBody = objectMapper.writeValueAsString(Map.of(
                "instanceId", dynamicFormInstanceId,
                "formTemplateCode", "GENERAL_EXPENSE",
                "title", "Hsinchu Client Travel Expense (Revised)",
                "formData", Map.of(
                        "expenseCategory", "TRAVEL",
                        "amount", 25000,
                        "expenseDate", "2026-11-10",
                        "purpose", "Hsinchu client meeting with client dinner and taxi fares"
                )
        ));
        mockMvc.perform(post("/api/v1/workflow/instances/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resubmitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.submissionCount").value(2));

        WorkflowInstance instResubmitted = workflowInstanceRepository.findById(dynamicFormInstanceId).orElseThrow();
        assertEquals(WorkflowInstanceStatus.PENDING, instResubmitted.getStatus());
        assertEquals(2, instResubmitted.getSubmissionCount());
        assertNotNull(instResubmitted.getDiffJson());
        assertFalse(instResubmitted.getDiffJson().isEmpty(), "Resubmission diff must record modified fields");

        boolean amountDiffFound = false;
        boolean purposeDiffFound = false;
        for (Map<String, Object> diffEntry : instResubmitted.getDiffJson()) {
            if ("amount".equals(diffEntry.get("fieldKey"))) {
                amountDiffFound = true;
                assertEquals(15000, diffEntry.get("oldValue"));
                assertEquals(25000, diffEntry.get("newValue"));
            }
            if ("purpose".equals(diffEntry.get("fieldKey"))) {
                purposeDiffFound = true;
                assertEquals("Hsinchu science park client discussion", diffEntry.get("oldValue"));
                assertEquals("Hsinchu client meeting with client dinner and taxi fares", diffEntry.get("newValue"));
            }
        }
        assertTrue(amountDiffFound, "Diff must contain amount field change");
        assertTrue(purposeDiffFound, "Diff must contain purpose field change");

        // 2d. Admin updates GENERAL_EXPENSE template schema via PUT /api/v1/workflow/templates/{id} -> version increments to 2
        generalExpenseTemplateId = formTemplateRepository.findByCode("GENERAL_EXPENSE").orElseThrow().getId();
        List<Map<String, Object>> newSchema = List.of(
                Map.of("key", "expenseCategory", "type", "SELECT", "required", true,
                        "label", Map.of("zh-TW", "費用類別", "en-US", "Expense Category", "ja-JP", "経費区分")),
                Map.of("key", "amount", "type", "NUMBER", "required", true, "min", 1,
                        "label", Map.of("zh-TW", "申請金額", "en-US", "Amount", "ja-JP", "申請金額")),
                Map.of("key", "expenseDate", "type", "DATE", "required", true,
                        "label", Map.of("zh-TW", "發生日期", "en-US", "Expense Date", "ja-JP", "発生日")),
                Map.of("key", "purpose", "type", "TEXTAREA", "required", true,
                        "label", Map.of("zh-TW", "事由說明", "en-US", "Business Purpose", "ja-JP", "申請理由")),
                Map.of("key", "invoiceTaxId", "type", "TEXT", "required", false,
                        "label", Map.of("zh-TW", "統一編號", "en-US", "Tax ID", "ja-JP", "登録番号"))
        );

        String templateUpdateBody = objectMapper.writeValueAsString(Map.of(
                "code", "GENERAL_EXPENSE",
                "name", Map.of("zh-TW", "通用費用與公務核銷單 V2", "en-US", "General Expense V2", "ja-JP", "汎用経費 V2"),
                "category", "GENERAL",
                "schemaJson", newSchema,
                "enabled", true,
                "description", "Version 2 with invoiceTaxId field"
        ));
        mockMvc.perform(put("/api/v1/workflow/templates/" + generalExpenseTemplateId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateUpdateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(2));

        // 2e. Verify existing instance preserves immutable formVersion == 1 and 4-field schemaSnapshotJson
        mockMvc.perform(get("/api/v1/workflow/instances/" + dynamicFormInstanceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formVersion").value(1))
                .andExpect(jsonPath("$.data.schemaSnapshotJson.length()").value(4));
    }

    @Test
    @Order(4)
    @DisplayName("3. Deputy Delegation (wf_deputy) & HR Force Reassign (REASSIGN)")
    void deputyDelegationAndHrForceReassign() throws Exception {
        // 3a. Self-delegation validation: directLeader designates himself as deputy -> 400 WF_DEPUTY_SELF_DELEGATION_FORBIDDEN (3012)
        String selfDeputyBody = objectMapper.writeValueAsString(Map.of(
                "deputyUserId", directLeaderId,
                "startDate", "2026-11-01",
                "endDate", "2026-11-30",
                "reason", "Self delegation error test"
        ));
        mockMvc.perform(post("/api/v1/workflow/deputies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + directLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfDeputyBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3012));

        // 3b. Create valid deputy delegation: directLeader delegates to p4a_deputy_user
        String validDeputyBody = objectMapper.writeValueAsString(Map.of(
                "deputyUserId", deputyUserId,
                "startDate", "2026-11-01",
                "endDate", "2026-11-30",
                "reason", "Annual leave coverage"
        ));
        MvcResult depRes = mockMvc.perform(post("/api/v1/workflow/deputies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + directLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDeputyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andReturn();
        createdDeputyId = objectMapper.readTree(depRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 3c. Overlapping active deputy period for same delegator -> 409 WF_DEPUTY_OVERLAPPING_PERIOD (3013)
        String overlapDeputyBody = objectMapper.writeValueAsString(Map.of(
                "deputyUserId", reassignTargetUserId,
                "startDate", "2026-11-15",
                "endDate", "2026-12-15",
                "reason", "Overlapping period test"
        ));
        mockMvc.perform(post("/api/v1/workflow/deputies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + directLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overlapDeputyBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(3013));

        // 3d. Valid deputy sees delegator's pending task in inbox
        mockMvc.perform(get("/api/v1/workflow/instances/inbox")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deputyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + dynamicFormInstanceId + ")]").exists());

        // 3e. Deputy approves Step 1 on behalf of directLeader -> wf_task_log records actorUserId & delegatorUserId
        mockMvc.perform(post("/api/v1/workflow/instances/" + dynamicFormInstanceId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deputyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Approved by deputy on behalf of leader"))))
                .andExpect(status().isOk());

        // Verify task log recorded actorUserId = deputyUserId, delegatorUserId = directLeaderId
        List<WorkflowTaskLog> logs = workflowTaskLogRepository.findByInstanceIdOrderByActedAtAscIdAsc(dynamicFormInstanceId);
        WorkflowTaskLog deputyApproveLog = logs.stream()
                .filter(l -> l.getAction() == WorkflowTaskAction.APPROVE && deputyUserId.equals(l.getActorUserId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Task log must record deputy approval action"));
        assertEquals(directLeaderId, deputyApproveLog.getDelegatorUserId());
        assertEquals(directLeaderId, deputyApproveLog.getAssignedApproverId());

        // Since amount = 25000 >= 10000, Step 2 (EXP_STEP_2_HIGH_AMT) is now active!
        WorkflowInstance step2Instance = workflowInstanceRepository.findById(dynamicFormInstanceId).orElseThrow();
        assertEquals(WorkflowInstanceStatus.PENDING, step2Instance.getStatus());
        assertEquals(2, step2Instance.getCurrentStepOrder());
        assertNotNull(step2Instance.getCurrentApproverId(), "Step 2 approver must be resolved");
        Long step2OriginalApproverId = step2Instance.getCurrentApproverId();

        // 3f. HR/Admin force reassigns the pending Step 2 task via POST /reassign to p4a_reassign_user
        mockMvc.perform(post("/api/v1/workflow/instances/" + dynamicFormInstanceId + "/reassign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newApproverUserId", reassignTargetUserId,
                                "comment", "HR force reassignment to backup reviewer"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentApproverId").value(reassignTargetUserId));

        // Verify wf_task_log records action REASSIGN with reassignedToUserId
        List<WorkflowTaskLog> updatedLogs = workflowTaskLogRepository.findByInstanceIdOrderByActedAtAscIdAsc(dynamicFormInstanceId);
        WorkflowTaskLog reassignLog = updatedLogs.stream()
                .filter(l -> l.getAction() == WorkflowTaskAction.REASSIGN)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Task log must record REASSIGN action"));
        assertEquals(reassignTargetUserId, reassignLog.getReassignedToUserId());
        assertEquals(step2OriginalApproverId, reassignLog.getAssignedApproverId());
    }

    @Test
    @Order(5)
    @DisplayName("4. In-App Notifications & Idempotent Monthly Leave Balance Digest (ntf_digest_log)")
    void inAppNotificationsAndIdempotentMonthlyLeaveDigest() throws Exception {
        // 4a. Verify workflow reassignment generated unread notification for p4a_reassign_user
        MvcResult notifListRes = mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reassignTargetToken)
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        JsonNode notifArray = objectMapper.readTree(notifListRes.getResponse().getContentAsString()).path("data");
        assertTrue(notifArray.size() >= 1, "Reassigned user must receive notification");
        long notifId = notifArray.get(0).path("id").asLong();

        // Check unread count >= 1
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reassignTargetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 4b. Mark single notification as read
        mockMvc.perform(put("/api/v1/notifications/" + notifId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reassignTargetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        // 4c. Mark all as read -> unread count becomes 0
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reassignTargetToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reassignTargetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // 4d. Trigger monthly leave digest for period "2026-11" (first run: sentCount > 0, skippedCount == 0)
        emailNotificationChannel.clearSentMessages();

        String digestTriggerBody = objectMapper.writeValueAsString(Map.of(
                "periodYearMonth", "2026-11",
                "asOfDate", "2026-11-10"
        ));
        MvcResult firstDigestRes = mockMvc.perform(post("/api/v1/notifications/digest/trigger")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(digestTriggerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodYearMonth").value("2026-11"))
                .andExpect(jsonPath("$.data.skippedCount").value(0))
                .andReturn();

        JsonNode firstDigestData = objectMapper.readTree(firstDigestRes.getResponse().getContentAsString()).path("data");
        int totalActive = firstDigestData.path("totalActiveUsers").asInt();
        int sentFirst = firstDigestData.path("sentCount").asInt();
        assertTrue(totalActive > 0, "There must be active users in the system");
        assertEquals(totalActive, sentFirst, "First digest run must deliver to 100% of active users");
        assertFalse(emailNotificationChannel.getSentMessages().isEmpty(), "Email simulation channel must capture sent messages");

        // 4e. Trigger monthly leave digest a second time for period "2026-11" -> 100% idempotent (sentCount == 0, skippedCount == totalActive)
        MvcResult secondDigestRes = mockMvc.perform(post("/api/v1/notifications/digest/trigger")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(digestTriggerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodYearMonth").value("2026-11"))
                .andExpect(jsonPath("$.data.sentCount").value(0))
                .andExpect(jsonPath("$.data.skippedCount").value(totalActive))
                .andReturn();

        JsonNode secondDigestData = objectMapper.readTree(secondDigestRes.getResponse().getContentAsString()).path("data");
        assertEquals(0, secondDigestData.path("sentCount").asInt(), "Second run must send 0 notifications (idempotent)");
        assertEquals(totalActive, secondDigestData.path("skippedCount").asInt(), "Second run must skip all users already delivered");
    }
}
