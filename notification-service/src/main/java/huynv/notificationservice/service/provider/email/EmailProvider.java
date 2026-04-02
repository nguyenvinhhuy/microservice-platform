package huynv.notificationservice.service.provider.email;

/**
 * Sends email notifications using a pluggable provider implementation.
 */
public interface EmailProvider {

    /**
     * Sends an email message using the provider implementation.
     *
     * @param request Email send request containing recipient and content.
     * @return Performs a side effect by sending an email through an external provider.
     */
    void send(EmailSendRequest request);

    /**
     * Returns the provider name for logging and history metadata.
     *
     * @return Returns the provider name.
     */
    String providerName();
}

