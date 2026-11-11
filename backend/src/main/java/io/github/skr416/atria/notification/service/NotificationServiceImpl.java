package io.github.skr416.atria.notification.service;

import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.UserRepository;
import io.github.skr416.atria.notification.dto.NotificationResponse;
import io.github.skr416.atria.notification.dto.NotificationSendCommand;
import io.github.skr416.atria.notification.model.NotificationRecord;
import io.github.skr416.atria.notification.repository.NotificationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation of {@link NotificationService} providing transactional notification persistence,
 * user-scoped reading queries, and email dispatch integration (ADR-0008 Decisions 8 & 10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRecordRepository notificationRecordRepository;
    private final UserRepository userRepository;
    private final EmailNotificationChannel emailNotificationChannel;

    @Override
    @Transactional
    public NotificationResponse sendNotification(NotificationSendCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        NotificationRecord record = new NotificationRecord();
        record.setRecipientUserId(command.recipientUserId());
        record.setCategory(command.category());
        record.setTitle(command.title() != null ? command.title() : Map.of());
        record.setContent(command.content() != null ? command.content() : Map.of());
        record.setReferenceType(command.referenceType());
        record.setReferenceId(command.referenceId());
        record.setRead(false);
        record.setEmailSent(false);

        if (command.sendEmail()) {
            User recipient = userRepository.findById(command.recipientUserId()).orElse(null);
            if (recipient != null && recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
                String targetLocale = recipient.getLocale() != null ? recipient.getLocale() : "zh-TW";
                String subject = record.getTitle().getOrDefault(targetLocale,
                        record.getTitle().getOrDefault("zh-TW",
                                record.getTitle().getOrDefault("en-US", "")));
                String body = record.getContent().getOrDefault(targetLocale,
                        record.getContent().getOrDefault("zh-TW",
                                record.getContent().getOrDefault("en-US", "")));
                emailNotificationChannel.sendEmail(recipient.getEmail(), recipient.getUsername(), subject, body);
                record.setEmailSent(true);
            } else {
                log.warn("Recipient user {} has no valid email address; skipped email dispatch", command.recipientUserId());
            }
        }

        NotificationRecord saved = notificationRecordRepository.save(record);
        return toResponse(saved, LocaleContextHolder.getLocale());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> listMyNotifications(Long userId, boolean unreadOnly) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        List<NotificationRecord> records = unreadOnly
                ? notificationRecordRepository.findByRecipientUserIdAndReadFalseOrderByCreatedAtDescIdDesc(userId)
                : notificationRecordRepository.findByRecipientUserIdOrderByCreatedAtDescIdDesc(userId);

        Locale currentLocale = LocaleContextHolder.getLocale();
        return records.stream()
                .map(r -> toResponse(r, currentLocale))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return notificationRecordRepository.countUnreadByRecipientUserId(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        if (notificationId == null || userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        NotificationRecord record = notificationRecordRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NTF_NOTIFICATION_NOT_FOUND));

        if (!record.getRecipientUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NTF_NOTIFICATION_NOT_FOUND);
        }

        if (!record.isRead()) {
            record.setRead(true);
            record.setReadAt(Instant.now());
            record = notificationRecordRepository.save(record);
        }

        return toResponse(record, LocaleContextHolder.getLocale());
    }

    @Override
    @Transactional
    public int markAllAsRead(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        List<NotificationRecord> unreadList = notificationRecordRepository.findByRecipientUserIdAndReadFalse(userId);
        if (unreadList.isEmpty()) {
            return 0;
        }

        Instant now = Instant.now();
        for (NotificationRecord record : unreadList) {
            record.setRead(true);
            record.setReadAt(now);
        }
        notificationRecordRepository.saveAll(unreadList);
        return unreadList.size();
    }

    private NotificationResponse toResponse(NotificationRecord record, Locale locale) {
        String langTag = locale != null ? locale.toLanguageTag() : "zh-TW";
        Map<String, String> titleMap = record.getTitle() != null ? record.getTitle() : Map.of();
        Map<String, String> contentMap = record.getContent() != null ? record.getContent() : Map.of();

        String localizedTitle = titleMap.getOrDefault(langTag,
                titleMap.getOrDefault("zh-TW",
                        titleMap.getOrDefault("en-US", "")));
        String localizedContent = contentMap.getOrDefault(langTag,
                contentMap.getOrDefault("zh-TW",
                        contentMap.getOrDefault("en-US", "")));

        return new NotificationResponse(
                record.getId(),
                record.getRecipientUserId(),
                record.getCategory(),
                localizedTitle,
                localizedContent,
                titleMap,
                contentMap,
                record.getReferenceType(),
                record.getReferenceId(),
                record.isRead(),
                record.getReadAt(),
                record.isEmailSent(),
                record.getCreatedAt()
        );
    }
}
