package io.github.skr416.atria.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skr416.atria.attendance.model.LeaveBalanceTx;
import io.github.skr416.atria.attendance.model.LeaveBalanceTxType;
import io.github.skr416.atria.attendance.model.PunchLog;
import io.github.skr416.atria.attendance.model.PunchSource;
import io.github.skr416.atria.attendance.repository.CompLeaveGrantRepository;
import io.github.skr416.atria.attendance.repository.LeaveBalanceRepository;
import io.github.skr416.atria.attendance.repository.LeaveBalanceTxRepository;
import io.github.skr416.atria.attendance.repository.PunchLogRepository;
import io.github.skr416.atria.common.util.HmacSignatureVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test suite for Phase 3b:
 * Leave Types, TW/JP Seniority Annual Leave Grants, FIFO Leave Balance Ledger & Tx Audit,
 * Morning/Afternoon Leave Shift Threshold Adjustment, Punch Correction Immutable Log,
 * TW 1.34/1.67 & JP Overtime Rate/Cap Engine, Comp-Leave Conversion & Expiry Payout,
 * and Monthly Close Pending/Closed/Comp-Used Guards (ADR-0008).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phase3bLeaveOvertimeIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveBalanceTxRepository leaveBalanceTxRepository;

    @Autowired
    private CompLeaveGrantRepository compLeaveGrantRepository;

    @Autowired
    private PunchLogRepository punchLogRepository;

    @Value("${atria.attendance.bot.hmac-secret}")
    private String botHmacSecret;

    private static Long twDeptId;
    private static Long jpDeptId;
    private static Long twEmpUserId;
    private static Long jpEmpUserId;
    private static Long earlyBucketId;
    private static Long annualBucketId;

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

    /**
     * Finds a working day in the current month (relative to LocalDate.now()) that is within maxRetroactiveDays (14d).
     */
    private LocalDate findRecentWorkday(int offsetIndex) {
        LocalDate base = LocalDate.now().minusDays(10);
        int found = 0;
        for (int i = 0; i < 30; i++) {
            LocalDate candidate = base.plusDays(i);
            int dow = candidate.getDayOfWeek().getValue();
            if (dow >= 1 && dow <= 5) {
                if (found == offsetIndex) {
                    return candidate;
                }
                found++;
            }
        }
        return LocalDate.now();
    }

    @Test
    @Order(1)
    @DisplayName("1. Seeded leave types, TW/JP seniority grant rules, overtime rates (1.34/1.67) and caps are queryable")
    void seededRulesShouldBeQueryableAndTrilingual() throws Exception {
        String adminToken = login("admin", "Admin@12345");

        // 1a. Verify seeded leave types (ANNUAL, COMP_LEAVE, SICK, PERSONAL, MARRIAGE, BEREAVEMENT)
        mockMvc.perform(get("/api/v1/attendance/leave-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].code").value("ANNUAL"))
                .andExpect(jsonPath("$.data[0].localizedName").value("年次有給休暇"));

        // 1b. Verify TW & JP seniority grant rules
        mockMvc.perform(get("/api/v1/attendance/leave-grant-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("calendarCode", "TW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].grantDays").value(3));

        // 1c. Verify TW overtime rate rules (1.34 / 1.67 / 2.67 / 2.00)
        mockMvc.perform(get("/api/v1/attendance/overtime-rate-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("calendarCode", "TW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].multiplier").value(1.34))
                .andExpect(jsonPath("$.data[1].multiplier").value(1.67));
    }

    @Test
    @Order(2)
    @DisplayName("2. Anniversary annual leave auto-grant allocates TW (10d for 2y) and JP (10d + 2400m 5-day obligation) and skips missing hire_date")
    void annualLeaveGrantJobShouldAllocateTwAndJpBucketsIdempotently() throws Exception {
        String adminToken = login("admin", "Admin@12345");

        // Create TW & JP departments
        String twDeptBody = objectMapper.writeValueAsString(Map.of(
                "code", "TW_P3B",
                "name", Map.of("zh-TW", "台北差勤部", "en-US", "Taipei Attendance Dept", "ja-JP", "台北勤怠部"),
                "parentId", 1,
                "timezone", "Asia/Taipei",
                "calendarCode", "TW",
                "sortOrder", 20
        ));
        MvcResult twDeptRes = mockMvc.perform(post("/api/v1/iam/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twDeptBody))
                .andExpect(status().isOk())
                .andReturn();
        twDeptId = objectMapper.readTree(twDeptRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        String jpDeptBody = objectMapper.writeValueAsString(Map.of(
                "code", "JP_P3B",
                "name", Map.of("zh-TW", "東京差勤部", "en-US", "Tokyo Attendance Dept", "ja-JP", "東京勤怠部"),
                "parentId", 1,
                "timezone", "Asia/Tokyo",
                "calendarCode", "JP",
                "sortOrder", 21
        ));
        MvcResult jpDeptRes = mockMvc.perform(post("/api/v1/iam/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jpDeptBody))
                .andExpect(status().isOk())
                .andReturn();
        jpDeptId = objectMapper.readTree(jpDeptRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        LocalDate today = LocalDate.now();
        // TW employee hired 2 years and 1 month ago -> 25 months tenure -> TW tier 24..36m = 10 days (4800 mins)
        String twUserBody = objectMapper.writeValueAsString(Map.of(
                "username", "tw_p3b_emp",
                "displayName", "TW Phase3b Employee",
                "email", "tw_p3b_emp@atria.local",
                "initialPassword", "Emp@12345",
                "departmentId", twDeptId,
                "hireDate", today.minusMonths(25).toString(),
                "locale", "zh-TW",
                "enabled", true,
                "mustChangePassword", false,
                "roleIds", List.of(4)
        ));
        MvcResult twUserRes = mockMvc.perform(post("/api/v1/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twUserBody))
                .andExpect(status().isOk())
                .andReturn();
        twEmpUserId = objectMapper.readTree(twUserRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // JP employee hired 8 months ago -> 8 months tenure -> JP tier 6..18m = 10 days (4800 mins, mandatory 5-day = 2400 mins)
        String jpUserBody = objectMapper.writeValueAsString(Map.of(
                "username", "jp_p3b_emp",
                "displayName", "JP Phase3b Employee",
                "email", "jp_p3b_emp@atria.local",
                "initialPassword", "Emp@12345",
                "departmentId", jpDeptId,
                "hireDate", today.minusMonths(8).toString(),
                "locale", "ja-JP",
                "enabled", true,
                "mustChangePassword", false,
                "roleIds", List.of(4)
        ));
        MvcResult jpUserRes = mockMvc.perform(post("/api/v1/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jpUserBody))
                .andExpect(status().isOk())
                .andReturn();
        jpEmpUserId = objectMapper.readTree(jpUserRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Trigger Annual Leave Auto-Grant
        MvcResult grantRes = mockMvc.perform(post("/api/v1/attendance/leave-balances/grant-annual")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("asOfDate", today.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode grantData = objectMapper.readTree(grantRes.getResponse().getContentAsString()).path("data");
        assertTrue(grantData.path("grantedCount").asInt() >= 2);
        // Bootstrap admin has no hire_date -> must appear in skippedMissingHireDateUsernames
        boolean adminSkipped = false;
        for (JsonNode uname : grantData.path("skippedMissingHireDateUsernames")) {
            if ("admin".equals(uname.asText())) {
                adminSkipped = true;
            }
        }
        assertTrue(adminSkipped, "System account without hire_date must be reported in skippedMissingHireDateUsernames");

        // Second execution on the same date must be idempotent (0 new grants for already granted cycles)
        mockMvc.perform(post("/api/v1/attendance/leave-balances/grant-annual")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("asOfDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grantedCount").value(0));

        // Verify JP employee's Leave Dashboard shows 4800 mins ANNUAL balance and JP 5-Day Obligation (target 2400 mins)
        String jpToken = login("jp_p3b_emp", "Emp@12345");
        mockMvc.perform(get("/api/v1/attendance/leave-dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calendarCode").value("JP"))
                .andExpect(jsonPath("$.data.balances[0].leaveTypeCode").value("ANNUAL"))
                .andExpect(jsonPath("$.data.balances[0].availableMinutes").value(4800))
                .andExpect(jsonPath("$.data.jpFiveDayObligation.applicable").value(true))
                .andExpect(jsonPath("$.data.jpFiveDayObligation.targetMinutes").value(2400))
                .andExpect(jsonPath("$.data.jpFiveDayObligation.remainingObligationMinutes").value(2400))
                .andExpect(jsonPath("$.data.jpFiveDayObligation.fulfilled").value(false));
    }

    @Test
    @Order(3)
    @DisplayName("3. Multi-bucket FIFO (valid_until ASC) freeze, reject exact per-bucket restore (att_leave_balance_tx), and applicant withdraw")
    void fifoBalanceDeductionAndExactPerBucketRestoreOnRejectAndCancel() throws Exception {
        String adminToken = login("admin", "Admin@12345");
        String twEmpToken = login("tw_p3b_emp", "Emp@12345");

        LocalDate today = LocalDate.now();
        // Record the existing 4800-min annual bucket ID for tw_p3b_emp
        annualBucketId = leaveBalanceRepository.findByUserIdAndLeaveTypeCodeOrderByValidUntilAscIdAsc(twEmpUserId, "ANNUAL")
                .get(0).getId();

        // Manually grant an earlier-expiring ANNUAL bucket (120 mins, expires in 20 days -> earlier than the 1-year bucket!)
        String manualGrantBody = objectMapper.writeValueAsString(Map.of(
                "userId", twEmpUserId,
                "leaveTypeCode", "ANNUAL",
                "grantMinutes", 120,
                "validFrom", today.minusDays(30).toString(),
                "validUntil", today.plusDays(20).toString(),
                "note", "Deferred prior-year annual leave (120 mins, expires soon)"
        ));
        MvcResult manualRes = mockMvc.perform(post("/api/v1/attendance/leave-balances/manual-grant")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualGrantBody))
                .andExpect(status().isOk())
                .andReturn();
        earlyBucketId = objectMapper.readTree(manualRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Pick a workday in current month: employee submits 4-hour (240 mins) leave from 09:00 to 13:00 (or 10:00 to 15:00 minus 1h lunch = 240m)
        LocalDate workDate1 = findRecentWorkday(0);
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        Instant leaveStart = ZonedDateTime.of(workDate1.getYear(), workDate1.getMonthValue(), workDate1.getDayOfMonth(), 10, 0, 0, 0, taipei).toInstant();
        Instant leaveEnd = ZonedDateTime.of(workDate1.getYear(), workDate1.getMonthValue(), workDate1.getDayOfMonth(), 15, 0, 0, 0, taipei).toInstant();

        String leaveSubmitBody = objectMapper.writeValueAsString(Map.of(
                "leaveTypeCode", "ANNUAL",
                "startTime", leaveStart.toString(),
                "endTime", leaveEnd.toString(),
                "reason", "Cross-lunch 10:00-15:00 leave (4h net after 12:00-13:00 break deduction)"
        ));
        MvcResult submitRes = mockMvc.perform(post("/api/v1/attendance/requests/leave")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + twEmpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leaveSubmitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requestedMinutes").value(240))
                .andReturn();
        long reqId1 = objectMapper.readTree(submitRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Verify FIFO freeze: earlyBucket (120m) has pendingMinutes=120 (available=0), annualBucket (4800m) has pendingMinutes=120 (available=4680)
        var earlyBucketAfterFreeze = leaveBalanceRepository.findById(earlyBucketId).orElseThrow();
        var annualBucketAfterFreeze = leaveBalanceRepository.findById(annualBucketId).orElseThrow();
        assertEquals(120, earlyBucketAfterFreeze.getPendingMinutes(), "Earliest-expiring bucket must be frozen first (FIFO)");
        assertEquals(0, earlyBucketAfterFreeze.getAvailableMinutes());
        assertEquals(120, annualBucketAfterFreeze.getPendingMinutes(), "Remainder must spill over to second bucket");
        assertEquals(4680, annualBucketAfterFreeze.getAvailableMinutes());

        List<LeaveBalanceTx> freezeTxs = leaveBalanceTxRepository.findByRequestIdAndTxTypeOrderByIdAsc(reqId1, LeaveBalanceTxType.FREEZE);
        assertEquals(2, freezeTxs.size(), "Must record 2 per-bucket FREEZE rows in att_leave_balance_tx");

        // Now reject reqId1 via stub-reject -> must restore 100% accurately to BOTH buckets!
        mockMvc.perform(post("/api/v1/attendance/requests/" + reqId1 + "/stub-reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Schedule conflict, please reschedule"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        var earlyBucketAfterReject = leaveBalanceRepository.findById(earlyBucketId).orElseThrow();
        var annualBucketAfterReject = leaveBalanceRepository.findById(annualBucketId).orElseThrow();
        assertEquals(0, earlyBucketAfterReject.getPendingMinutes());
        assertEquals(120, earlyBucketAfterReject.getAvailableMinutes(), "Bucket 1 must be 100% restored");
        assertEquals(0, annualBucketAfterReject.getPendingMinutes());
        assertEquals(4800, annualBucketAfterReject.getAvailableMinutes(), "Bucket 2 must be 100% restored");

        List<LeaveBalanceTx> restoreTxs = leaveBalanceTxRepository.findByRequestIdAndTxTypeOrderByIdAsc(reqId1, LeaveBalanceTxType.RESTORE_PENDING);
        assertEquals(2, restoreTxs.size(), "Must record 2 per-bucket RESTORE_PENDING rows in att_leave_balance_tx");

        // Test applicant withdraw (PENDING -> CANCELLED) and min_unit_minutes ceiling rounding (40m -> 60m)
        Instant shortStart = ZonedDateTime.of(workDate1.getYear(), workDate1.getMonthValue(), workDate1.getDayOfMonth(), 14, 0, 0, 0, taipei).toInstant();
        Instant shortEnd = ZonedDateTime.of(workDate1.getYear(), workDate1.getMonthValue(), workDate1.getDayOfMonth(), 14, 40, 0, 0, taipei).toInstant();
        MvcResult shortRes = mockMvc.perform(post("/api/v1/attendance/requests/leave")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + twEmpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "leaveTypeCode", "ANNUAL",
                                "startTime", shortStart.toString(),
                                "endTime", shortEnd.toString(),
                                "reason", "40-minute personal errand rounded up to 60m min unit"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedMinutes").value(60))
                .andReturn();
        long shortReqId = objectMapper.readTree(shortRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/attendance/requests/" + shortReqId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + twEmpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertEquals(120, leaveBalanceRepository.findById(earlyBucketId).orElseThrow().getAvailableMinutes());
    }

    @Test
    @Order(4)
    @DisplayName("4. Approved morning half-day leave (09:00-13:00) shifts lateness threshold to 13:00 so afternoon clock-in is NORMAL")
    void approvedMorningLeaveShouldShiftLatenessThresholdAndEvaluateNormal() throws Exception {
        String adminToken = login("admin", "Admin@12345");
        String twEmpToken = login("tw_p3b_emp", "Emp@12345");

        LocalDate workDate2 = findRecentWorkday(1);
        ZoneId taipei = ZoneId.of("Asia/Taipei");

        // Employee clocks in at 13:00 and clocks out at 18:00 on workDate2 via Bot punch
        Instant clockIn1300 = ZonedDateTime.of(workDate2.getYear(), workDate2.getMonthValue(), workDate2.getDayOfMonth(), 13, 0, 0, 0, taipei).toInstant();
        Instant clockOut1800 = ZonedDateTime.of(workDate2.getYear(), workDate2.getMonthValue(), workDate2.getDayOfMonth(), 18, 0, 0, 0, taipei).toInstant();
        long nowEpoch = Instant.now().getEpochSecond();
        String sig = HmacSignatureVerifier.sign(botHmacSecret, nowEpoch, "tw_p3b_emp:slack-bot");

        mockMvc.perform(post("/api/v1/attendance/punch/bot")
                        .header("X-Atria-Bot-Timestamp", String.valueOf(nowEpoch))
                        .header("X-Atria-Bot-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "tw_p3b_emp",
                                "botClientId", "slack-bot",
                                "punchTime", clockIn1300.toString()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/punch/bot")
                        .header("X-Atria-Bot-Timestamp", String.valueOf(nowEpoch))
                        .header("X-Atria-Bot-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "tw_p3b_emp",
                                "botClientId", "slack-bot",
                                "punchTime", clockOut1800.toString()
                        ))))
                .andExpect(status().isOk());

        // Before morning leave is approved, verify workDate2's daily record is LATE (240 mins late)
        String ym = YearMonth.from(workDate2).toString();
        MvcResult beforeOverviewRes = mockMvc.perform(get("/api/v1/attendance/monthly-overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + twEmpToken)
                        .param("yearMonth", ym))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode beforeRecords = objectMapper.readTree(beforeOverviewRes.getResponse().getContentAsString()).path("data").path("dailyRecords");
        boolean foundLateBefore = false;
        for (JsonNode rec : beforeRecords) {
            if (workDate2.toString().equals(rec.path("workDate").asText())) {
                assertEquals("LATE", rec.path("status").asText());
                assertEquals(240, rec.path("lateMinutes").asInt());
                foundLateBefore = true;
            }
        }
        assertTrue(foundLateBefore, "Before morning leave approval, clocking in at 13:00 must be LATE (240m)");

        // Now employee submits morning half-day leave (09:00-13:00 = 180 mins net work overlap) and approves it
        Instant leave0900 = ZonedDateTime.of(workDate2.getYear(), workDate2.getMonthValue(), workDate2.getDayOfMonth(), 9, 0, 0, 0, taipei).toInstant();
        Instant leave1300 = ZonedDateTime.of(workDate2.getYear(), workDate2.getMonthValue(), workDate2.getDayOfMonth(), 13, 0, 0, 0, taipei).toInstant();

        MvcResult leaveRes = mockMvc.perform(post("/api/v1/attendance/requests/leave")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + twEmpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "leaveTypeCode", "ANNUAL",
                                "startTime", leave0900.toString(),
                                "endTime", leave1300.toString(),
                                "reason", "Morning half-day leave 09:00-13:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedMinutes").value(180))
                .andReturn();
        long morningLeaveId = objectMapper.readTree(leaveRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Approve the morning leave request
        mockMvc.perform(post("/api/v1/attendance/requests/" + morningLeaveId + "/stub-approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // Verify daily record for workDate2 was recomputed: lateMinutes = 0, workedMinutes = 300, leaveMinutes = 180, status = NORMAL!
        MvcResult overviewRes = mockMvc.perform(get("/api/v1/attendance/monthly-overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + twEmpToken)
                        .param("yearMonth", ym))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(overviewRes.getResponse().getContentAsString()).path("data").path("dailyRecords");
        boolean verified = false;
        for (JsonNode rec : records) {
            if (workDate2.toString().equals(rec.path("workDate").asText())) {
                assertEquals(300, rec.path("workedMinutes").asInt());
                assertEquals(180, rec.path("leaveMinutes").asInt());
                assertEquals(0, rec.path("lateMinutes").asInt(), "Morning leave covering 09:00-13:00 must shift lateness threshold to 13:00");
                assertEquals(0, rec.path("earlyLeaveMinutes").asInt());
                assertEquals("NORMAL", rec.path("status").asText());
                verified = true;
            }
        }
        assertTrue(verified, "Recomputed daily record for workDate2 must exist and be NORMAL");
    }

    @Test
    @Order(5)
    @DisplayName("5. Approved PUNCH_CORRECTION appends immutable CORRECTION log to att_punch_log and recomputes daily record")
    void approvedPunchCorrectionShouldAppendImmutableLogAndRecomputeDailyRecord() throws Exception {
        String adminToken = login("admin", "Admin@12345");
        String twEmpToken = login("tw_p3b_emp", "Emp@12345");

        LocalDate workDate3 = findRecentWorkday(2);
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        Instant morningIn = ZonedDateTime.of(workDate3.getYear(), workDate3.getMonthValue(), workDate3.getDayOfMonth(), 9, 0, 0, 0, taipei).toInstant();
        Instant eveningOut = ZonedDateTime.of(workDate3.getYear(), workDate3.getMonthValue(), workDate3.getDayOfMonth(), 18, 0, 0, 0, taipei).toInstant();

        // Only 1 raw punch at 09:00 -> settle date so it becomes MISSING_PUNCH
        long nowEpoch = Instant.now().getEpochSecond();
        String sig = HmacSignatureVerifier.sign(botHmacSecret, nowEpoch, "tw_p3b_emp:slack-bot");
        mockMvc.perform(post("/api/v1/attendance/punch/bot")
                        .header("X-Atria-Bot-Timestamp", String.valueOf(nowEpoch))
                        .header("X-Atria-Bot-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "tw_p3b_emp",
                                "botClientId", "slack-bot",
                                "punchTime", morningIn.toString()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/settlement/run")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workDate", workDate3.toString()))
                .andExpect(status().isOk());

        // Submit PUNCH_CORRECTION for 18:00 clock-out
        MvcResult corrRes = mockMvc.perform(post("/api/v1/attendance/requests/punch-correction")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + twEmpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workDate", workDate3.toString(),
                                "correctedPunchTime", eveningOut.toString(),
                                "reason", "Forgot to clock out at 18:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestType").value("PUNCH_CORRECTION"))
                .andReturn();
        long corrReqId = objectMapper.readTree(corrRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Approve punch correction
        mockMvc.perform(post("/api/v1/attendance/requests/" + corrReqId + "/stub-approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // Verify immutable CORRECTION log in att_punch_log referencing corrReqId
        List<PunchLog> logs = punchLogRepository.findByUserIdAndWorkDateOrderByRawPunchTimeAsc(twEmpUserId, workDate3);
        assertEquals(2, logs.size());
        assertEquals(PunchSource.BOT, logs.get(0).getSource());
        assertEquals(PunchSource.CORRECTION, logs.get(1).getSource());
        assertEquals(corrReqId, logs.get(1).getCorrectionRequestId());
    }

    @Test
    @Order(6)
    @DisplayName("6. TW overtime (1.34/1.67 CEILING) converts to COMP_LEAVE bucket, blocks revocation once used (2025), and settles expiry payout")
    void overtimeCompLeaveConversionRevokeGuardAndExpiryPayout() throws Exception {
        String adminToken = login("admin", "Admin@12345");
        String twEmpToken = login("tw_p3b_emp", "Emp@12345");

        LocalDate workDate4 = findRecentWorkday(3);
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        Instant otStart = ZonedDateTime.of(workDate4.getYear(), workDate4.getMonthValue(), workDate4.getDayOfMonth(), 18, 0, 0, 0, taipei).toInstant();
        Instant otEnd = ZonedDateTime.of(workDate4.getYear(), workDate4.getMonthValue(), workDate4.getDayOfMonth(), 21, 0, 0, 0, taipei).toInstant();

        // 180 mins weekday overtime in TW:
        // first 120m * 1.34 = 160.8; next 60m * 1.67 = 100.2 -> total 261.0 -> 261 weighted minutes!
        MvcResult otRes = mockMvc.perform(post("/api/v1/attendance/requests/overtime")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + twEmpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workDate", workDate4.toString(),
                                "startTime", otStart.toString(),
                                "endTime", otEnd.toString(),
                                "overtimeCompType", "COMP_LEAVE",
                                "compLeaveMode", "RATE_MULTIPLIED",
                                "reason", "Release deployment overtime 3 hours"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedMinutes").value(180))
                .andExpect(jsonPath("$.data.weightedMinutes").value(261))
                .andReturn();
        long otReqId = objectMapper.readTree(otRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Approve overtime request -> creates CompLeaveGrant (261 mins) + linked LeaveBalance bucket
        mockMvc.perform(post("/api/v1/attendance/requests/" + otReqId + "/stub-approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        var compGrant = compLeaveGrantRepository.findBySourceRequestId(otReqId).orElseThrow();
        assertEquals(180, compGrant.getOriginalOvertimeMinutes());
        assertEquals(261, compGrant.getGrantedMinutes());
        assertEquals(2, compGrant.getBandBreakdownJson().size());

        // Employee submits a 60-min COMP_LEAVE request (freezing 60 mins on the comp-leave bucket)
        LocalDate workDate5 = findRecentWorkday(4);
        Instant compUseStart = ZonedDateTime.of(workDate5.getYear(), workDate5.getMonthValue(), workDate5.getDayOfMonth(), 16, 0, 0, 0, taipei).toInstant();
        Instant compUseEnd = ZonedDateTime.of(workDate5.getYear(), workDate5.getMonthValue(), workDate5.getDayOfMonth(), 17, 0, 0, 0, taipei).toInstant();

        MvcResult compLeaveReqRes = mockMvc.perform(post("/api/v1/attendance/requests/leave")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + twEmpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "leaveTypeCode", "COMP_LEAVE",
                                "startTime", compUseStart.toString(),
                                "endTime", compUseEnd.toString(),
                                "reason", "Using 60 mins of compensatory leave"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        long compLeaveReqId = objectMapper.readTree(compLeaveReqRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Attempting to revoke the source OVERTIME request while its comp-leave bucket has pending/used minutes must fail with 2025!
        mockMvc.perform(post("/api/v1/attendance/requests/" + otReqId + "/stub-revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Attempt revoke while comp leave is in use"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2025));

        // Approve the 60-min COMP_LEAVE request (so usedMinutes = 60, unused = 201 out of 261)
        mockMvc.perform(post("/api/v1/attendance/requests/" + compLeaveReqId + "/stub-approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Execute Taiwan LSA Art. 32-1 expiry settlement on the remaining 201 mins of compGrant
        mockMvc.perform(post("/api/v1/attendance/comp-leave-grants/" + compGrant.getId() + "/settle-expired")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grantedMinutes").value(261))
                .andExpect(jsonPath("$.data.usedMinutes").value(60))
                .andExpect(jsonPath("$.data.unusedMinutes").value(201))
                .andExpect(jsonPath("$.data.restoredBands.length()").value(2));
    }

    @Test
    @Order(7)
    @DisplayName("7. DatabasePendingRequestChecker blocks monthly close (2010), and closed month blocks revocation (2005)")
    void pendingRequestBlocksMonthlyCloseAndClosedMonthBlocksRevocation() throws Exception {
        String adminToken = login("admin", "Admin@12345");
        String twEmpToken = login("tw_p3b_emp", "Emp@12345");

        LocalDate workDate = findRecentWorkday(4);
        String targetYearMonth = YearMonth.from(workDate).toString();
        ZoneId taipei = ZoneId.of("Asia/Taipei");

        // Create an unresolved PENDING leave request in targetYearMonth
        Instant sTime = ZonedDateTime.of(workDate.getYear(), workDate.getMonthValue(), workDate.getDayOfMonth(), 14, 0, 0, 0, taipei).toInstant();
        Instant eTime = ZonedDateTime.of(workDate.getYear(), workDate.getMonthValue(), workDate.getDayOfMonth(), 15, 0, 0, 0, taipei).toInstant();

        MvcResult pendingRes = mockMvc.perform(post("/api/v1/attendance/requests/leave")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + twEmpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "leaveTypeCode", "PERSONAL",
                                "startTime", sTime.toString(),
                                "endTime", eTime.toString(),
                                "reason", "Pending personal leave blocking monthly close"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        long pendingReqId = objectMapper.readTree(pendingRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Attempting to close targetYearMonth for TW must be blocked by DatabasePendingRequestChecker with 409 (2010)!
        mockMvc.perform(post("/api/v1/attendance/monthly-closes/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarCode", "TW",
                                "yearMonth", targetYearMonth,
                                "note", "Should be blocked by real DB pending request"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2010));

        // Approve the pending request so no PENDING requests remain in targetYearMonth
        mockMvc.perform(post("/api/v1/attendance/requests/" + pendingReqId + "/stub-approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Now close targetYearMonth for TW -> succeeds!
        mockMvc.perform(post("/api/v1/attendance/monthly-closes/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarCode", "TW",
                                "yearMonth", targetYearMonth,
                                "note", "Close month after resolving all pending requests"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // Attempting to revoke an approved request in a CLOSED month must fail with 403 (ATT_MONTHLY_PERIOD_CLOSED = 2005)!
        mockMvc.perform(post("/api/v1/attendance/requests/" + pendingReqId + "/stub-revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Attempt revoke in closed month"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2005));

        // Reopen targetYearMonth and verify revocation succeeds!
        mockMvc.perform(post("/api/v1/attendance/monthly-closes/reopen")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarCode", "TW",
                                "yearMonth", targetYearMonth,
                                "note", "Reopen for revocation test"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/requests/" + pendingReqId + "/stub-revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Revoke after month reopened"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    @Order(8)
    @DisplayName("8. Sick leave attachment enforcement (2003) and Overtime Cap BLOCK enforcement (2020)")
    void sickLeaveAttachmentAndOvertimeCapBlockEnforcement() throws Exception {
        String adminToken = login("admin", "Admin@12345");
        String jpEmpToken = login("jp_p3b_emp", "Emp@12345");

        LocalDate workDate = findRecentWorkday(3);
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        Instant sTime = ZonedDateTime.of(workDate.getYear(), workDate.getMonthValue(), workDate.getDayOfMonth(), 14, 0, 0, 0, tokyo).toInstant();
        Instant eTime = ZonedDateTime.of(workDate.getYear(), workDate.getMonthValue(), workDate.getDayOfMonth(), 16, 0, 0, 0, tokyo).toInstant();

        // 8a. Submitting SICK leave (attachment_required=true) without attachmentPath must fail with 400 (2003)
        mockMvc.perform(post("/api/v1/attendance/requests/leave")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jpEmpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "leaveTypeCode", "SICK",
                                "startTime", sTime.toString(),
                                "endTime", eTime.toString(),
                                "reason", "Clinic visit without attachment"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2003));

        // 8b. Create a strict monthly BLOCK cap rule (e.g. 60 mins) for JP and verify submitting 120 mins overtime triggers 2020
        mockMvc.perform(post("/api/v1/attendance/overtime-cap-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarCode", "JP",
                                "ruleCode", "JP_TEST_STRICT_60M_BLOCK",
                                "name", Map.of("zh-TW", "測試嚴格上限", "en-US", "Test Strict Cap", "ja-JP", "テスト上限"),
                                "periodMonths", 1,
                                "maxMinutes", 60,
                                "enforcementAction", "BLOCK",
                                "enabled", true
                        ))))
                .andExpect(status().isOk());

        Instant otStart = ZonedDateTime.of(workDate.getYear(), workDate.getMonthValue(), workDate.getDayOfMonth(), 18, 0, 0, 0, tokyo).toInstant();
        Instant otEnd = ZonedDateTime.of(workDate.getYear(), workDate.getMonthValue(), workDate.getDayOfMonth(), 20, 0, 0, 0, tokyo).toInstant();

        mockMvc.perform(post("/api/v1/attendance/requests/overtime")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jpEmpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workDate", workDate.toString(),
                                "startTime", otStart.toString(),
                                "endTime", otEnd.toString(),
                                "overtimeCompType", "PAY",
                                "reason", "Exceeds 60m strict monthly cap"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2020));
    }
}
