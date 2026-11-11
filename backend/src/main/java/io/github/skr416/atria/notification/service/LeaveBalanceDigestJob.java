package io.github.skr416.atria.notification.service;

import io.github.skr416.atria.attendance.dto.leave.ExpiringCompLeaveDto;
import io.github.skr416.atria.attendance.dto.leave.JpFiveDayObligationDto;
import io.github.skr416.atria.attendance.dto.leave.LeaveDashboardResponse;
import io.github.skr416.atria.attendance.dto.leave.LeaveTypeBalanceSummaryDto;
import io.github.skr416.atria.attendance.service.LeaveDashboardQuery;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.UserRepository;
import io.github.skr416.atria.notification.dto.DigestBatchResultDto;
import io.github.skr416.atria.notification.dto.NotificationResponse;
import io.github.skr416.atria.notification.dto.NotificationSendCommand;
import io.github.skr416.atria.notification.model.DigestDeliveryLog;
import io.github.skr416.atria.notification.model.NotificationCategory;
import io.github.skr416.atria.notification.repository.DigestDeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled and manually triggered batch job delivering trilingual monthly leave balance summaries,
 * 90-day expiring comp-leave alerts, and JP 5-day obligation progress with idempotency (ADR-0008 Decisions 8 & 10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveBalanceDigestJob {

    private final UserRepository userRepository;
    private final DigestDeliveryLogRepository digestDeliveryLogRepository;
    private final LeaveDashboardQuery leaveDashboardQuery;
    private final NotificationService notificationService;

    /**
     * Scheduled on the 1st day of every month at 01:00 AM.
     */
    @Scheduled(cron = "0 0 1 1 * ?")
    public void runScheduledMonthlyDigest() {
        String currentPeriod = YearMonth.now().toString();
        LocalDate asOfDate = LocalDate.now();
        log.info("Starting scheduled monthly leave digest for period: {}", currentPeriod);
        DigestBatchResultDto result = triggerMonthlyDigest(currentPeriod, asOfDate);
        log.info("Completed scheduled monthly leave digest: sent={}, skipped={}, total={}",
                result.sentCount(), result.skippedCount(), result.totalActiveUsers());
    }

    /**
     * Executes the monthly leave balance digest delivery for all active users idempotently.
     */
    @Transactional
    public DigestBatchResultDto triggerMonthlyDigest(String periodYearMonth, LocalDate asOfDate) {
        String period = (periodYearMonth != null && !periodYearMonth.isBlank())
                ? periodYearMonth.trim()
                : YearMonth.now().toString();
        LocalDate targetDate = asOfDate != null ? asOfDate : LocalDate.now();

        List<User> activeUsers = userRepository.findAll().stream()
                .filter(User::isEnabled)
                .toList();

        int totalActiveUsers = activeUsers.size();
        int sentCount = 0;
        int skippedCount = 0;

        for (User user : activeUsers) {
            Long userId = user.getId();

            // Idempotency: skip if already delivered for this user and period
            if (digestDeliveryLogRepository.findByUserIdAndPeriodYearMonth(userId, period).isPresent()) {
                skippedCount++;
                continue;
            }

            LeaveDashboardResponse dashboard = leaveDashboardQuery.getDashboard(userId, targetDate);

            int annualRemainingMinutes = 0;
            if (dashboard.balances() != null) {
                annualRemainingMinutes = dashboard.balances().stream()
                        .filter(b -> "ANNUAL".equalsIgnoreCase(b.leaveTypeCode()))
                        .findFirst()
                        .map(LeaveTypeBalanceSummaryDto::availableMinutes)
                        .orElse(0);
            }

            int expiringCompLeaveMinutes = 0;
            if (dashboard.expiringCompLeavesWithin90Days() != null) {
                expiringCompLeaveMinutes = dashboard.expiringCompLeavesWithin90Days().stream()
                        .mapToInt(ExpiringCompLeaveDto::remainingMinutes)
                        .sum();
            }

            JpFiveDayObligationDto jpObligation = dashboard.jpFiveDayObligation();
            boolean jpObligationApplicable = jpObligation != null && jpObligation.applicable();
            boolean jpObligationFulfilled = jpObligation != null && jpObligation.fulfilled();
            int jpObligationShortfallMinutes = (jpObligationApplicable && !jpObligationFulfilled)
                    ? jpObligation.remainingObligationMinutes() : 0;
            int jpAnnualLeaveTakenMinutes = jpObligation != null ? jpObligation.usedMinutes() : 0;

            Map<String, String> title = Map.of(
                    "zh-TW", "【假勤月結摘要】" + period + " 假勤與補休餘額提醒",
                    "en-US", "[Monthly Leave Digest] " + period + " Leave Balance & Comp-Leave Notice",
                    "ja-JP", "【月次休暇ダイジェスト】" + period + " 有給休暇・代休残高のお知らせ"
            );

            Map<String, String> content = Map.of(
                    "zh-TW", buildZhContent(period, annualRemainingMinutes, expiringCompLeaveMinutes, jpObligationShortfallMinutes),
                    "en-US", buildEnContent(period, annualRemainingMinutes, expiringCompLeaveMinutes, jpObligationShortfallMinutes),
                    "ja-JP", buildJaContent(period, annualRemainingMinutes, expiringCompLeaveMinutes, jpObligationShortfallMinutes)
            );

            NotificationSendCommand command = new NotificationSendCommand(
                    userId,
                    NotificationCategory.LEAVE_DIGEST,
                    title,
                    content,
                    "LEAVE_DIGEST",
                    null,
                    true
            );

            NotificationResponse notificationResponse = notificationService.sendNotification(command);

            Map<String, Object> summaryJson = new LinkedHashMap<>();
            summaryJson.put("periodYearMonth", period);
            summaryJson.put("annualRemainingMinutes", annualRemainingMinutes);
            summaryJson.put("compLeaveExpiringWithin90DaysMinutes", expiringCompLeaveMinutes);
            summaryJson.put("jpObligationShortfallMinutes", jpObligationShortfallMinutes);
            if (jpObligation != null) {
                summaryJson.put("jpFiveDayObligationMet", jpObligationFulfilled);
                summaryJson.put("jpAnnualLeaveTakenMinutes", jpAnnualLeaveTakenMinutes);
                summaryJson.put("jpAnnualLeaveShortfallMinutes", jpObligationShortfallMinutes);
            }

            DigestDeliveryLog digestLog = new DigestDeliveryLog();
            digestLog.setUserId(userId);
            digestLog.setPeriodYearMonth(period);
            digestLog.setCalendarCode(dashboard.calendarCode() != null ? dashboard.calendarCode() : "TW");
            digestLog.setNotificationId(notificationResponse != null ? notificationResponse.id() : null);
            digestLog.setSummaryJson(summaryJson);
            digestLog.setDeliveredAt(Instant.now());

            digestDeliveryLogRepository.save(digestLog);
            sentCount++;
        }

        return new DigestBatchResultDto(period, totalActiveUsers, sentCount, skippedCount);
    }

    private String buildZhContent(String period, int annualMinutes, int compMinutes, int jpShortfall) {
        StringBuilder sb = new StringBuilder();
        sb.append("您好，").append(period).append(" 月份假勤摘要：特休剩餘 ")
                .append(annualMinutes / 60).append(" 小時 ").append(annualMinutes % 60).append(" 分。");
        if (compMinutes > 0) {
            sb.append(" 90 天內即將到期補休：").append(compMinutes / 60).append(" 小時 ")
                    .append(compMinutes % 60).append(" 分，請儘速排休。");
        }
        if (jpShortfall > 0) {
            sb.append(" 【日本勞基法注意】年度法定 5 日有休尚差 ")
                    .append(jpShortfall / 60).append(" 小時 ")
                    .append(jpShortfall % 60).append(" 分，請於期限前完成請假。");
        }
        return sb.toString();
    }

    private String buildEnContent(String period, int annualMinutes, int compMinutes, int jpShortfall) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello, your leave summary for ").append(period).append(": Annual leave balance ")
                .append(annualMinutes / 60).append("h ").append(annualMinutes % 60).append("m.");
        if (compMinutes > 0) {
            sb.append(" Expiring comp-leave within 90 days: ")
                    .append(compMinutes / 60).append("h ").append(compMinutes % 60).append("m. Please plan your leaves soon.");
        }
        if (jpShortfall > 0) {
            sb.append(" [Japan Labor Standards Act Warning] Mandatory 5-day annual leave shortfall: ")
                    .append(jpShortfall / 60).append("h ").append(jpShortfall % 60).append("m remaining before deadline.");
        }
        return sb.toString();
    }

    private String buildJaContent(String period, int annualMinutes, int compMinutes, int jpShortfall) {
        StringBuilder sb = new StringBuilder();
        sb.append("お疲れ様です。").append(period).append(" の休暇サマリー：有給残高 ")
                .append(annualMinutes / 60).append(" 時間 ").append(annualMinutes % 60).append(" 分。");
        if (compMinutes > 0) {
            sb.append(" 90日以内に失効予定の代休：")
                    .append(compMinutes / 60).append(" 時間 ").append(compMinutes % 60).append(" 分。お早めにご取得ください。");
        }
        if (jpShortfall > 0) {
            sb.append(" 【労基法注意】年次有給休暇の年5日取得義務まであと ")
                    .append(jpShortfall / 60).append(" 時間 ")
                    .append(jpShortfall % 60).append(" 分の取得が必要です。");
        }
        return sb.toString();
    }
}
