package huynv.notificationservice.service.provider.push;

import huynv.notificationservice.service.provider.ProviderException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Simulates push delivery for environments without a real push provider integration.
 */
@Component
public class SimulatedPushProvider implements PushProvider {

    @Override
    public void send(PushSendRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.tokens() == null || request.tokens().isEmpty()) {
            throw new ProviderException("Push tokens must be provided.", false);
        }
    }

    @Override
    public String providerName() {
        return "SIMULATED";
    }
}

