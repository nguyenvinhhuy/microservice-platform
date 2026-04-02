package huynv.notificationservice.service.provider.sms;

/**
 * Sends SMS notifications using a pluggable provider implementation.
 */
public interface SmsProvider {

    /**
     * Sends an SMS message using the provider implementation.
     *
     * @param request SMS send request containing recipient and content.
     * @return Performs a side effect by sending an SMS through an external provider.
     */
    void send(SmsSendRequest request);

    /**
     * Returns the provider name for logging and history metadata.
     *
     * @return Returns the provider name.
     */
    String providerName();
}

