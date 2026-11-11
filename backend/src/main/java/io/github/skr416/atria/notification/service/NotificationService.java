package io.github.skr416.atria.notification.service;

import io.github.skr416.atria.notification.dto.NotificationResponse;
import io.github.skr416.atria.notification.dto.NotificationSendCommand;

import java.util.List;

/**
 * Public notification service contract consumed by upper-layer modules (such as {@code workflow})
 * and internal digest jobs without cyclic dependencies (ADR-0008 Decisions 8 & 10).
 */
public interface NotificationService {

    /**
     * Persists an in-app notification and optionally dispatches via the email channel.
     */
    NotificationResponse sendNotification(NotificationSendCommand command);

    /**
     * Lists notifications for {@code userId}, optionally filtered to unread items only, ordered newest first.
     */
    List<NotificationResponse> listMyNotifications(Long userId, boolean unreadOnly);

    /**
     * Returns the count of unread notifications for {@code userId}.
     */
    long countUnread(Long userId);

    /**
     * Marks a single notification as read for {@code userId}.
     */
    NotificationResponse markAsRead(Long notificationId, Long userId);

    /**
     * Marks all unread notifications as read for {@code userId} and returns the updated count.
     */
    int markAllAsRead(Long userId);
}
