package huynv.dlqreplayerservice.api;

/**
 * Defines an admin request to replay a stored DLQ event back to its original topic.
 */
public record DlqReplayRequest(
        Long id,
        String overrideTopic
) {
}

