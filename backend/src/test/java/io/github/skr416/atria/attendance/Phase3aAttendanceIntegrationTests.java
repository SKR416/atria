package io.github.skr416.atria.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skr416.atria.attendance.dto.close.BlockingPendingRequestDto;
import io.github.skr416.atria.attendance.model.AttendanceStatus;
import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.DailyAttendanceRecord;
import io.github.skr416.atria.attendance.model.PunchLog;
import io.github.skr416.atria.attendance.model.PunchSource;
import io.github.skr416.atria.attendance.repository.DailyAttendanceRecordRepository;
import io.github.skr416.atria.attendance.repository.PunchLogRepository;
import io.github.skr416.atria.attendance.service.PendingRequestChecker;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive Phase 3a integration tests covering ADR-0003 & ADR-0008 (Decisions 1, 2, 3, 7):
 * - TW & JP statutory annual calendar generators (lunar holidays, observed shifts, Happy Monday,
 *   equinox formula, substitute holidays 振替休日, and citizens' holiday 国民の休日)
 * - HR single-day calendar override (e.g. MAKEUP_WORKDAY) & regional punch rules
 * - Web self-service punch, append-only att_punch_log, anti-double-click (2001), and derived att_daily_record
 * - Cross-midnight cutoff (04:00 AM) and 1-hour lunch break (12:00-13:00) deduction
 * - Bot proxy punch with HMAC-SHA256 anti-replay verification (1006)
 * - Daily settlement job finalizing MISSING_PUNCH and ABSENT statuses
 * - Monthly closing lock blocking on PENDING requests (2010), locking modifications (2005), and reopening
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phase3aAttendanceIntegrationTests {

    private static final AtomicBoolean SIMULATE_PENDING_REQUEST = new AtomicBoolean(false);

    @TestConfiguration
    static class PendingRequestTestConfig {
        @Bean
        public PendingRequestChecker testPendingRequestChecker() {
            return (calendarCode, yearMonth) -> {
                if (SIMULATE_PENDING_REQUEST.get()
                        && "TW".equalsIgnoreCase(calendarCode)
                        && YearMonth.of(2026, 3).equals(yearMonth)) {
                    return List.of(
                            BlockingPendingRequestDto.builder()
                                    .requestId(9001L)
                                    .userId(100L)
                                    .username("tw_emp1")
                                    .displayName("Lin Taiwan")
                                    .requestType("LEAVE")
                                    .startDate(LocalDate.of(2026, 3, 10))
                                    .endDate(LocalDate.of(2026, 3, 10))
                                    .reason("Pending annual leave awaiting manager approval")
                                    .build()
                    );
                }
                return List.of();
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PunchLogRepository punchLogRepository;

    @Autowired
    private DailyAttendanceRecordRepository dailyAttendanceRecordRepository;

    @Value("${atria.attendance.bot.hmac-secret}")
    private String botHmacSecret;

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
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    @Test
    @Order(1)
    @DisplayName("1. TW & JP statutory year calendar generators and HR single-day override")
    void statutoryCalendarGeneratorsAndHrOverrideShouldWork() throws Exception {
        String adminToken = login("admin", "Admin@12345");

        // 1a. Generate 2026 TW calendar
        String genTwBody = objectMapper.writeValueAsString(Map.of(
                "calendarCode", "TW",
                "year", 2026,
                "overwriteExisting", true
        ));
        MvcResult twRes = mockMvc.perform(post("/api/v1/attendance/calendars/generate-year")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(genTwBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode twDays = objectMapper.readTree(twRes.getResponse().getContentAsString()).path("data");
        assertEquals(365, twDays.size(), "2026 has 365 days");

        // Verify 2026-02-16 (Lunar New Year's Eve) & 2026-02-17 (LNY Day 1) are NATIONAL_HOLIDAY
        // Verify 2026-02-28 (Saturday Peace Memorial Day) observed on Friday 2026-02-27 as NATIONAL_HOLIDAY
        boolean foundLnyEve = false;
        boolean foundObserved228 = false;
        for (JsonNode d : twDays) {
            String date = d.path("workDate").asText();
            if ("2026-02-16".equals(date)) {
                assertEquals("NATIONAL_HOLIDAY", d.path("dayType").asText());
                foundLnyEve = true;
            }
            if ("2026-02-27".equals(date)) {
                assertEquals("NATIONAL_HOLIDAY", d.path("dayType").asText(),
                        "2026-02-28 falls on Saturday so Friday 2026-02-27 must be observed NATIONAL_HOLIDAY");
                foundObserved228 = true;
            }
        }
        assertTrue(foundLnyEve && foundObserved228);

        // 1b. Generate 2026 JP calendar and verify Silver Week Citizens' Holiday (2026-09-22) & Golden Week Substitute Holiday (2026-05-06)
        String genJpBody = objectMapper.writeValueAsString(Map.of(
                "calendarCode", "JP",
                "year", 2026,
                "overwriteExisting", true
        ));
        MvcResult jpRes = mockMvc.perform(post("/api/v1/attendance/calendars/generate-year")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(genJpBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jpDays = objectMapper.readTree(jpRes.getResponse().getContentAsString()).path("data");
        assertEquals(365, jpDays.size());

        boolean foundGoldenWeekSubstitute = false;
        boolean foundSilverWeekCitizensHoliday = false;
        for (JsonNode d : jpDays) {
            String date = d.path("workDate").asText();
            if ("2026-05-06".equals(date)) {
                // 2026-05-03 is Sunday (Constitution Memorial Day), 05-04 & 05-05 are holidays -> 05-06 is Substitute Holiday (振替休日)
                assertEquals("NATIONAL_HOLIDAY", d.path("dayType").asText());
                foundGoldenWeekSubstitute = true;
            }
            if ("2026-09-22".equals(date)) {
                // 2026-09-21 is Respect for the Aged Day (3rd Mon) and 2026-09-23 is Autumnal Equinox -> 09-22 is Citizens' Holiday (国民の休日)
                assertEquals("NATIONAL_HOLIDAY", d.path("dayType").asText());
                foundSilverWeekCitizensHoliday = true;
            }
        }
        assertTrue(foundGoldenWeekSubstitute, "2026-05-06 must be JP Substitute Holiday (振替休日)");
        assertTrue(foundSilverWeekCitizensHoliday, "2026-09-22 must be JP Citizens' Holiday (国民の休日)");

        // 1c. HR overrides Saturday 2026-02-21 in TW as a MAKEUP_WORKDAY (補班日)
        String overrideBody = objectMapper.writeValueAsString(Map.of(
                "dayType", "MAKEUP_WORKDAY",
                "workStartTime", "09:00:00",
                "workEndTime", "18:00:00",
                "breakStartTime", "12:00:00",
                "breakEndTime", "13:00:00",
                "holidayName", Map.of("zh-TW", "春節彈性補班日", "en-US", "LNY Makeup Workday", "ja-JP", "春節振替出勤日"),
                "note", "Adjusted per DGPA announcement"
        ));
        mockMvc.perform(put("/api/v1/attendance/calendars/TW/days/2026-02-21")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dayType").value("MAKEUP_WORKDAY"))
                .andExpect(jsonPath("$.data.workingDay").value(true));
    }

    @Test
    @Order(2)
    @DisplayName("2. Web punch, anti-double-click (2001), Bot HMAC punch (1006), cross-midnight cutoff (04:00) & 1h lunch deduction")
    void webAndBotPunchWithCrossMidnightCutoffShouldWork() throws Exception {
        String adminToken = login("admin", "Admin@12345");

        // Create a TW department and two employees (tw_emp1, tw_emp2)
        String deptBody = objectMapper.writeValueAsString(Map.of(
                "parentId", 1,
                "code", "TW-RD-3A",
                "name", Map.of("zh-TW", "台灣研發部", "en-US", "TW R&D", "ja-JP", "台湾開発部"),
                "timezone", "Asia/Taipei",
                "calendarCode", "TW",
                "sortOrder", 10
        ));
        MvcResult deptRes = mockMvc.perform(post("/api/v1/iam/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deptBody))
                .andExpect(status().isOk())
                .andReturn();
        long twDeptId = objectMapper.readTree(deptRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        for (String uname : List.of("tw_emp1", "tw_emp2")) {
            String userBody = objectMapper.writeValueAsString(Map.of(
                    "username", uname,
                    "email", uname + "@atria.local",
                    "initialPassword", "Emp@12345",
                    "displayName", "Employee " + uname,
                    "departmentId", twDeptId,
                    "hireDate", "2025-01-01",
                    "locale", "zh-TW",
                    "roleIds", Set.of(4),
                    "mustChangePassword", false
            ));
            mockMvc.perform(post("/api/v1/iam/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(userBody))
                    .andExpect(status().isOk());
        }

        String emp1Token = login("tw_emp1", "Emp@12345");

        // 2a. Employee 1 punches via Web portal
        mockMvc.perform(post("/api/v1/attendance/punch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "Morning web clock-in"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.punchLogs[0].source").value("WEB"));

        // Immediate second punch within 60s must be rejected as duplicate (ErrorCode.ATT_PUNCH_DUPLICATE = 2001)
        mockMvc.perform(post("/api/v1/attendance/punch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emp1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "Accidental double click"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2001));

        // 2b. Bot endpoint rejects invalid HMAC signature with 401 (ErrorCode.BOT_SIGNATURE_INVALID = 1006)
        long nowEpoch = Instant.now().getEpochSecond();
        String botReqInvalid = objectMapper.writeValueAsString(Map.of(
                "username", "tw_emp1",
                "botClientId", "slack-bot-prod"
        ));
        mockMvc.perform(post("/api/v1/attendance/punch/bot")
                        .header("X-Atria-Bot-Timestamp", String.valueOf(nowEpoch))
                        .header("X-Atria-Bot-Signature", "deadbeef0000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(botReqInvalid))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        // 2c. Bot endpoint succeeds with valid HMAC-SHA256 signature and tests Cross-Midnight Cutoff (04:00 AM)
        // Workday 2026-03-04 (Wednesday):
        // Punch 1: 2026-03-04 09:00 Asia/Taipei
        // Punch 2: 2026-03-05 02:00 Asia/Taipei (before 04:00 AM cutoff -> attributed to 2026-03-04!)
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        Instant clockInInstant = ZonedDateTime.of(2026, 3, 4, 9, 0, 0, 0, taipei).toInstant();
        Instant crossMidnightOutInstant = ZonedDateTime.of(2026, 3, 5, 2, 0, 0, 0, taipei).toInstant();

        String canonicalPayload = "tw_emp1:slack-bot-prod";
        String validSignature = HmacSignatureVerifier.sign(botHmacSecret, nowEpoch, canonicalPayload);

        String botPunch1 = objectMapper.writeValueAsString(Map.of(
                "username", "tw_emp1",
                "botClientId", "slack-bot-prod",
                "punchTime", clockInInstant.toString(),
                "note", "Clock in 09:00"
        ));
        mockMvc.perform(post("/api/v1/attendance/punch/bot")
                        .header("X-Atria-Bot-Timestamp", String.valueOf(nowEpoch))
                        .header("X-Atria-Bot-Signature", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(botPunch1))
                .andExpect(status().isOk());

        String botPunch2 = objectMapper.writeValueAsString(Map.of(
                "username", "tw_emp1",
                "botClientId", "slack-bot-prod",
                "punchTime", crossMidnightOutInstant.toString(),
                "note", "Late night clock out 02:00 AM next day"
        ));
        mockMvc.perform(post("/api/v1/attendance/punch/bot")
                        .header("X-Atria-Bot-Timestamp", String.valueOf(nowEpoch))
                        .header("X-Atria-Bot-Signature", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(botPunch2))
                .andExpect(status().isOk());

        // Verify both punches landed on logical work_date 2026-03-04 and net worked minutes deducted 1h lunch break:
        // Total elapsed: 09:00 to 02:00 (+1d) = 17 hours (1020 mins) - 60 mins lunch = 960 mins
        MvcResult overviewRes = mockMvc.perform(get("/api/v1/attendance/monthly-overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emp1Token)
                        .param("yearMonth", "2026-03"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode overview = objectMapper.readTree(overviewRes.getResponse().getContentAsString()).path("data");
        JsonNode records = overview.path("dailyRecords");
        boolean foundMarch4Record = false;
        for (JsonNode rec : records) {
            if ("2026-03-04".equals(rec.path("workDate").asText())) {
                assertEquals(2, rec.path("punchCount").asInt());
                assertEquals(960, rec.path("workedMinutes").asInt(), "17h elapsed minus 1h lunch break = 960 mins");
                assertEquals("NORMAL", rec.path("status").asText());
                foundMarch4Record = true;
            }
        }
        assertTrue(foundMarch4Record, "Derived daily record for logical work date 2026-03-04 must exist");
    }

    @Test
    @Order(3)
    @DisplayName("3. Daily settlement job marks single punch as MISSING_PUNCH and no punch on workday as ABSENT")
    void dailySettlementJobShouldFinalizeMissingPunchAndAbsent() throws Exception {
        String adminToken = login("admin", "Admin@12345");

        // On workday 2026-03-05 (Thursday):
        // tw_emp1 punches ONLY ONCE at 09:03; tw_emp2 has NO punches at all.
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        Instant singlePunchTime = ZonedDateTime.of(2026, 3, 5, 9, 3, 0, 0, taipei).toInstant();
        long nowEpoch = Instant.now().getEpochSecond();
        String sig = HmacSignatureVerifier.sign(botHmacSecret, nowEpoch, "tw_emp1:line-bot");

        mockMvc.perform(post("/api/v1/attendance/punch/bot")
                        .header("X-Atria-Bot-Timestamp", String.valueOf(nowEpoch))
                        .header("X-Atria-Bot-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "tw_emp1",
                                "botClientId", "line-bot",
                                "punchTime", singlePunchTime.toString()
                        ))))
                .andExpect(status().isOk());

        // Trigger Daily Settlement for 2026-03-05
        mockMvc.perform(post("/api/v1/attendance/settlement/run")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workDate", "2026-03-05"))
                .andExpect(status().isOk());

        // Check DB records for 2026-03-05
        List<DailyAttendanceRecord> march5Records = dailyAttendanceRecordRepository
                .findByWorkDateBetweenOrderByWorkDateAscUserIdAsc(
                        LocalDate.of(2026, 3, 5),
                        LocalDate.of(2026, 3, 5)
                );
        assertTrue(march5Records.size() >= 2);

        boolean verifiedMissingPunch = false;
        boolean verifiedAbsent = false;
        for (DailyAttendanceRecord rec : march5Records) {
            assertTrue(rec.isSettled(), "Record must be marked settled=true");
            if (rec.getPunchCount() == 1) {
                assertEquals(AttendanceStatus.MISSING_PUNCH, rec.getStatus());
                verifiedMissingPunch = true;
            } else if (rec.getPunchCount() == 0) {
                assertEquals(AttendanceStatus.ABSENT, rec.getStatus());
                verifiedAbsent = true;
            }
        }
        assertTrue(verifiedMissingPunch, "Single punch on workday must settle to MISSING_PUNCH");
        assertTrue(verifiedAbsent, "Zero punches on workday must settle to ABSENT");
    }

    @Test
    @Order(4)
    @DisplayName("4. Monthly close blocks when PENDING requests exist (2010), locks month when clear (2005), and supports reopen")
    void monthlyCloseShouldBlockOnPendingRequestsAndLockMonth() throws Exception {
        String adminToken = login("admin", "Admin@12345");

        // 4a. Simulate an unresolved PENDING request in 2026-03 for TW
        SIMULATE_PENDING_REQUEST.set(true);
        try {
            String closeBody = objectMapper.writeValueAsString(Map.of(
                    "calendarCode", "TW",
                    "yearMonth", "2026-03",
                    "note", "Attempt close with pending request"
            ));
            mockMvc.perform(post("/api/v1/attendance/monthly-closes/close")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(closeBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(2010))
                    .andExpect(jsonPath("$.data[0].requestId").value(9001))
                    .andExpect(jsonPath("$.data[0].username").value("tw_emp1"));
        } finally {
            SIMULATE_PENDING_REQUEST.set(false);
        }

        // 4b. Once pending requests are resolved, closing 2026-03 succeeds
        String closeBody = objectMapper.writeValueAsString(Map.of(
                "calendarCode", "TW",
                "yearMonth", "2026-03",
                "note", "Payroll settlement lock for March 2026"
        ));
        mockMvc.perform(post("/api/v1/attendance/monthly-closes/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.yearMonth").value("2026-03"));

        // 4c. Attempting to record a punch in a CLOSED month (2026-03) is rejected with 403 (ErrorCode.ATT_MONTHLY_PERIOD_CLOSED = 2005)
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        Instant marchPunch = ZonedDateTime.of(2026, 3, 12, 9, 0, 0, 0, taipei).toInstant();
        long nowEpoch = Instant.now().getEpochSecond();
        String sig = HmacSignatureVerifier.sign(botHmacSecret, nowEpoch, "tw_emp1:line-bot");

        mockMvc.perform(post("/api/v1/attendance/punch/bot")
                        .header("X-Atria-Bot-Timestamp", String.valueOf(nowEpoch))
                        .header("X-Atria-Bot-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "tw_emp1",
                                "botClientId", "line-bot",
                                "punchTime", marchPunch.toString()
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2005));

        // 4d. Reopen 2026-03 and verify punch is permitted again
        String reopenBody = objectMapper.writeValueAsString(Map.of(
                "calendarCode", "TW",
                "yearMonth", "2026-03",
                "note", "Reopened for HR adjustment"
        ));
        mockMvc.perform(post("/api/v1/attendance/monthly-closes/reopen")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reopenBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REOPENED"));

        mockMvc.perform(post("/api/v1/attendance/punch/bot")
                        .header("X-Atria-Bot-Timestamp", String.valueOf(nowEpoch))
                        .header("X-Atria-Bot-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "tw_emp1",
                                "botClientId", "line-bot",
                                "punchTime", marchPunch.toString()
                        ))))
                .andExpect(status().isOk());

        // Verify immutable raw punch logs remain intact
        List<PunchLog> logs = punchLogRepository.findByUserIdAndWorkDateBetweenOrderByRawPunchTimeAsc(
                dailyAttendanceRecordRepository.findByWorkDateBetweenOrderByWorkDateAscUserIdAsc(
                        LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 12)).get(0).getUserId(),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );
        assertNotNull(logs);
        assertTrue(logs.stream().anyMatch(l -> l.getSource() == PunchSource.BOT));
    }
}
