package com.walletpe.user.infrastructure.delivery;

import com.walletpe.user.api.exception.NotificationDeliveryException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class LoggingNotificationDeliveryService implements NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationDeliveryService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Environment environment;

    @Value("${app.mail.enabled:false}")
    private boolean explicitMailEnabled;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${app.mail.from:}")
    private String configuredFrom;

    public LoggingNotificationDeliveryService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                              Environment environment) {
        this.mailSenderProvider = mailSenderProvider;
        this.environment = environment;
    }

    public boolean isMailActive() {
        if (explicitMailEnabled) {
            return true;
        }
        // Auto-detect: if a username is configured and MAIL_ENABLED is not explicitly set to false
        String envEnabled = System.getProperty("MAIL_ENABLED", System.getenv().getOrDefault("MAIL_ENABLED", ""));
        return mailUsername != null && !mailUsername.trim().isEmpty() && !"false".equalsIgnoreCase(envEnabled.trim());
    }

    @PostConstruct
    public void logMailStatus() {
        if (isMailActive()) {
            String masked = (mailUsername != null && mailUsername.contains("@"))
                    ? mailUsername.charAt(0) + "***" + mailUsername.substring(mailUsername.indexOf('@'))
                    : (mailUsername != null && !mailUsername.isBlank() ? "***" : "configured");
            log.info("[NotificationDelivery] SMTP is ACTIVE. Host: {}, Port: {}, User: {}", mailHost, mailPort, masked);
        } else {
            log.info("[NotificationDelivery] SMTP is INACTIVE. Configure MAIL_USERNAME in .env to enable real email dispatch.");
        }
    }

    private String getResolvedFromEmail() {
        if (configuredFrom != null && !configuredFrom.trim().isEmpty()
                && !configuredFrom.toLowerCase().endsWith("@mailtrap.io")
                && !configuredFrom.contains("walletpe.com")) {
            return configuredFrom.trim();
        }
        if (mailHost != null && mailHost.toLowerCase().contains("gmail")
                && mailUsername != null && !mailUsername.trim().isEmpty()) {
            return mailUsername.trim();
        }
        if (configuredFrom != null && !configuredFrom.trim().isEmpty()
                && !configuredFrom.toLowerCase().endsWith("@mailtrap.io")) {
            return configuredFrom.trim();
        }
        if (mailUsername != null && !mailUsername.trim().isEmpty()
                && !mailUsername.toLowerCase().endsWith("@mailtrap.io")) {
            return mailUsername.trim();
        }
        // Official Mailtrap demo sender domain (accepted by Mailtrap Live and Sandbox)
        return "hello@demomailtrap.co";
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        if (isMailActive()) {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                log.error("[NotificationDelivery] SMTP is active but JavaMailSender bean is unavailable.");
                throw new NotificationDeliveryException("Email service is temporarily unavailable. Please try again later.");
            }

            try {
                String sender = getResolvedFromEmail();
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(sender);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
                log.info("[NotificationDelivery] Verification email dispatched via SMTP to: {} with subject: {}", to, subject);
            } catch (Exception e) {
                log.error("[NotificationDelivery] Failed to dispatch email to {}: {}", to, e.getMessage());
                throw new NotificationDeliveryException("Unable to send verification email: " + e.getMessage(), e);
            }
            return;
        }

        // When mail is not active
        if (environment.matchesProfiles("test")) {
            log.info("[NotificationDelivery] Test profile active: simulated email dispatch to: {} with subject: {}", to, subject);
            return;
        }

        log.error("[NotificationDelivery] Email delivery requested for {} but SMTP is not configured or disabled.", to);
        throw new NotificationDeliveryException("Email service is not configured. Unable to send verification email.");
    }

    @Override
    public void sendSms(String mobileNumber, String message) {
        log.info("================================================================================");
        log.info("[NotificationDelivery] [SMS SIMULATION] Mobile: {} | {}", mobileNumber, message);
        log.info("================================================================================");
    }
}
