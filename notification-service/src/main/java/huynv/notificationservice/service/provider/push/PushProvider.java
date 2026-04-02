package huynv.notificationservice.service.provider.push;

/**
 * Sends push notifications using a pluggable provider implementation.
 */
public interface PushProvider {

    /**
     * Sends a push notification using the provider implementation.
     *
     * @param request Push send request containing tokens and content.
     * @return Performs a side effect by sending a push notification through an external provider.
     */
    void send(PushSendRequest request);

    /**
     * Returns the provider name for logging and history metadata.
     *
     * @return Returns the provider name.
     */
    String providerName();
}

