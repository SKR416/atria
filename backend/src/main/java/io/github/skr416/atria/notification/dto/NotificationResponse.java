package io.github.skr416.atria.notification.dto;

import io.github.skr416.atria.notification.model.NotificationCategory;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for an in-app notification item.
 */
public record NotificationResponse(
        Long id,
        Long recipientUserId,
        NotificationCategory category,
        String localizedTitle,
        String localizedContent,
        Map<String, String> title,
        Map<String, String> content,
        String referenceType,
        Long referenceId,
        boolean read,
        Instant readAt,
        boolean emailSent,
        Instant createdAt
) {
}
