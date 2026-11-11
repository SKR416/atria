package io.github.skr416.atria.notification.dto;

import io.github.skr416.atria.notification.model.NotificationCategory;

import java.util.Map;

/**
 * Cross-module command for dispatching trilingual in-app and optional email notifications.
 */
public record NotificationSendCommand(
        Long recipientUserId,
        NotificationCategory category,
        Map<String, String> title,
        Map<String, String> content,
        String referenceType,
        Long referenceId,
        boolean sendEmail
) {
}
