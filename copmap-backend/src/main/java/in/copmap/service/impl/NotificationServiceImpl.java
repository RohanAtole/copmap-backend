package in.copmap.service.impl;

import in.copmap.entity.Alert;
import in.copmap.entity.User;
import in.copmap.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Notification service implementation.
 *
 * In mock mode (default), all notifications are logged instead of actually sent.
 * Set NOTIFICATION_MOCK=false and configure mail settings for real email.
 *
 * Extension points:
 *  - FCM: inject FirebaseMessaging and call send(Message) with fcmToken
 *  - WhatsApp (Twilio): inject TwilioRestClient
 *  - SMS: inject SNS or Twilio SMS client
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.notification.mock-mode:true}")
    private boolean mockMode;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Override
    @Async
    public void notifyOfficer(User officer, String title, String body) {
        if (mockMode) {
            log.info("[MOCK NOTIFICATION] To officer={} | Title={} | Body={}",
                    officer.getBadgeNumber(), title, body);
            return;
        }

        // Real email notification
        if (officer.getEmail() != null && !officer.getEmail().isBlank()) {
            sendEmail(officer.getEmail(), title, body);
        }

        // FCM push (stub — uncomment and configure with Firebase Admin SDK)
        // if (officer.getFcmToken() != null) {
        //     Message message = Message.builder()
        //         .setNotification(Notification.builder().setTitle(title).setBody(body).build())
        //         .setToken(officer.getFcmToken())
        //         .build();
        //     FirebaseMessaging.getInstance().send(message);
        // }
    }

    @Override
    @Async
    public void broadcastSOSNotification(Alert alert) {
        if (mockMode) {
            log.error("[MOCK SOS BROADCAST] Alert: {} | Officer: {} | Message: {}",
                    alert.getId(),
                    alert.getOfficer() != null ? alert.getOfficer().getBadgeNumber() : "unknown",
                    alert.getMessage());
            return;
        }

        // In production: broadcast to all STATION_OFFICER users in the same station
        // For now, log it
        log.error("SOS BROADCAST: {}", alert.getMessage());
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("[CopMap] " + subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
