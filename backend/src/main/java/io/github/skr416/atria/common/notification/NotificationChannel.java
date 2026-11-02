package io.github.skr416.atria.common.notification;

/**
 * Pluggable outbound notification channel (InApp, Email, Outbound Webhook Bot).
 */
public interface NotificationChannel {

    /**
     * Channel identifier (e.g., "IN_APP", "EMAIL", "WEBHOOK_BOT").
     */
    String channelType();

    /**
     * Dispatches a notification message.
     */
    void send(NotificationMessage message);
}
