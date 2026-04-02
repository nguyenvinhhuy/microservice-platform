package huynv.notificationservice.service.provider.sms;

import huynv.notificationservice.service.provider.ProviderException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Simulates SMS delivery for environments without a real SMS provider integration.
 */
@Component
public class SimulatedSmsProvider implements SmsProvider {

    @Override
    public void send(SmsSendRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.to() == null || request.to().isBlank()) {
            throw new ProviderException("SMS recipient phone number must be provided.", false);
        }
    }

    @Override
    public String providerName() {
        return "SIMULATED";
    }
}

