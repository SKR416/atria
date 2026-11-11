package io.github.skr416.atria.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local simulation channel for corporate email dispatch (ADR-0008 Decision 10).
 * Stores sent messages in-memory for integration test verification.
 */
@Component
public class EmailNotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final CopyOnWriteArrayList<SentEmailMessage> sentMessages = new CopyOnWriteArrayList<>();

    public record SentEmailMessage(
            String recipientEmail,
            String recipientUsername,
            String subject,
            String body,
            Instant sentAt
    ) {
    }

    /**
     * Dispatches an email notification to the specified recipient.
     */
    public void sendEmail(String recipientEmail, String recipientUsername, String subject, String body) {
        log.info("[Email Channel] Dispatching to {} <{}> | Subject: {} | Body: {}",
                recipientUsername, recipientEmail, subject, body);
        sentMessages.add(new SentEmailMessage(recipientEmail, recipientUsername, subject, body, Instant.now()));
    }

    /**
     * Returns an unmodifiable snapshot list of dispatched messages for verification.
     */
    public List<SentEmailMessage> getSentMessages() {
        return List.copyOf(sentMessages);
    }

    /**
     * Clears all in-memory sent messages.
     */
    public void clearSentMessages() {
        sentMessages.clear();
    }
}
