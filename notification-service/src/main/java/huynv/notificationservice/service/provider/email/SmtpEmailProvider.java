package huynv.notificationservice.service.provider.email;

import huynv.eventinfra.config.NotificationProperties;
import huynv.notificationservice.service.provider.ProviderException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Sends emails through SMTP when enabled, otherwise simulates delivery.
 */
@Component
public class SmtpEmailProvider implements EmailProvider {

    private final NotificationProperties properties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /**
     * Creates an SMTP provider using optional JavaMailSender integration.
     *
     * @param properties Notification properties controlling SMTP enablement.
     * @param mailSenderProvider Provider used to access JavaMailSender.
     * @return Initializes an SMTP email provider.
     */
    public SmtpEmailProvider(NotificationProperties properties, ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mailSenderProvider = Objects.requireNonNull(mailSenderProvider, "mailSenderProvider");
    }

    @Override
    public void send(EmailSendRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.to() == null || request.to().isBlank()) {
            throw new ProviderException("Email recipient address must be provided.", false);
        }
        if (!properties.getEmail().isSmtpEnabled()) {
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new ProviderException("JavaMailSender is not configured for SMTP delivery.", true);
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String from = request.from();
            if (from == null || from.isBlank()) {
                from = properties.getEmail().getFromAddress();
            }
            message.setFrom(from);
            message.setTo(request.to());
            message.setSubject(request.subject());
            message.setText(request.body());
            sender.send(message);
        } catch (Exception ex) {
            throw new ProviderException("SMTP send failed.", true, ex);
        }
    }

    @Override
    public String providerName() {
        return properties.getEmail().isSmtpEnabled() ? "SMTP" : "SIMULATED";
    }
}

