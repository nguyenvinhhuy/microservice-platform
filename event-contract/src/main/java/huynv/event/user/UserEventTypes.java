package huynv.event.user;

/**
 * Defines canonical Kafka event type names published by user-service.
 */
public final class UserEventTypes {

    /**
     * Creates a constants holder for user event type names.
     *
     * @return Throws an exception because this utility class must not be instantiated.
     */
    private UserEventTypes() {
        throw new UnsupportedOperationException("UserEventTypes is a utility class.");
    }

    public static final String USER_CREATED_V1 = "user.created.v1";
    public static final String USER_UPDATED_V1 = "user.updated.v1";
    public static final String USER_PREFERENCES_UPDATED_V1 = "user.preferences.updated.v1";
    public static final String USER_ADDRESS_CREATED_V1 = "user.address.created.v1";
}

