package huynv.notificationservice.service.channel;

import huynv.eventinfra.config.NotificationProperties;
import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationStatus;
import huynv.notificationservice.exception.NotificationDeliveryException;
import huynv.notificationservice.service.TemplateRenderingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Delivers notifications through email using templates and optional SMTP integration.
 */
@Component
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final NotificationProperties properties;
    private final TemplateRenderingService templateRenderingService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /**
     * Creates an email delivery channel using optional SMTP sender integration.
     *
     * @param properties Notification properties controlling channel enablement and sender configuration.
     * @param templateRenderingService Template renderer used to render email bodies from templates.
     * @param mailSenderProvider Provider used to access JavaMailSender when SMTP delivery is enabled.
     * @return Initializes an email channel strategy.
     */
    public EmailChannel(NotificationProperties properties,
                        TemplateRenderingService templateRenderingService,
                        ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.templateRenderingService = Objects.requireNonNull(templateRenderingService, "templateRenderingService");
        this.mailSenderProvider = Objects.requireNonNull(mailSenderProvider, "mailSenderProvider");
    }

    @Override
    public NotificationChannelType channelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public boolean isEnabled() {
        return properties.getChannels().isEmailEnabled();
    }

    /**
     * Sends an email notification by rendering the configured template and optionally delivering via SMTP.
     *
     * @param message Notification message containing tenant context, rendering inputs, and subject.
     * @return Performs side effects by rendering templates and sending an email when SMTP is enabled.
     */
    @Override
    public NotificationStatus send(NotificationMessage message) {
        Objects.requireNonNull(message, "message");
        if (!isEnabled()) {
            return NotificationStatus.SKIPPED;
        }
        if (message.userId() == null) {
            log.info("Email delivery skipped because userId is missing type={}", message.notificationType());
            return NotificationStatus.SKIPPED;
        }

        String html = templateRenderingService.renderForTenant(message.tenantId(), message.templateName(), message.templateModel());
        if (!properties.getEmail().isSmtpEnabled()) {
            log.info("Email delivery simulated smtpEnabled=false toUserId={} subject={}", message.userId(), message.subject());
            return NotificationStatus.SENT;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new NotificationDeliveryException("Email delivery failed because JavaMailSender is not configured.");
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(properties.getEmail().getFromAddress());
            mail.setTo(toAddress(message.userId()));
            mail.setSubject(message.subject());
            mail.setText(html);
            mailSender.send(mail);
            return NotificationStatus.SENT;
        } catch (Exception ex) {
            throw new NotificationDeliveryException("Email delivery failed userId=" + message.userId() + ".", ex);
        }
    }

    /**
     * Derives a deterministic placeholder email address from a numeric user identifier.
     *
     * @param userId User identifier used to form a stable recipient address.
     * @return Returns a synthetic email address used for development and placeholder delivery.
     */
    private static String toAddress(Long userId) {
        return "user-" + userId + "@example.local";
    }
}

