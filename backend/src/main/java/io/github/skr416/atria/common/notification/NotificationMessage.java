package io.github.skr416.atria.common.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Standardized notification payload for in-app notices, emails, and outbound Bot webhooks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    /**
     * Event type (e.g., WORKFLOW_PENDING, WORKFLOW_RETURNED, WORKFLOW_REVOKED).
     */
    private String eventType;

    /**
     * Target recipient user IDs.
     */
    private List<Long> recipientUserIds;

    /**
     * Notification title.
     */
    private String title;

    /**
     * Notification content body.
     */
    private String body;

    /**
     * Deep link URL into the portal/admin UI.
     */
    private String actionUrl;

    /**
     * Extra structured metadata for outbound bot webhooks (Slack / Teams / LINE / Custom Bot).
     */
    private Map<String, Object> metadata;
}
