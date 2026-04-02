package huynv.eventinfra.retry;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Defines Kafka header names and helpers used by the topic-based retry pipeline.
 */
public final class RetryHeaders {

    public static final String ATTEMPT = "x-notification-attempt";
    public static final String FIRST_SEEN_AT_MS = "x-notification-first-seen-at-ms";
    public static final String RETRY_DUE_AT_MS = "x-notification-retry-due-at-ms";
    public static final String RETRY_TARGET_TOPIC = "x-notification-retry-target-topic";
    public static final String ORIGINAL_TOPIC = "x-notification-original-topic";
    public static final String ORIGINAL_PARTITION = "x-notification-original-partition";
    public static final String ORIGINAL_OFFSET = "x-notification-original-offset";
    public static final String ERROR_CLASS = "x-notification-error-class";
    public static final String ERROR_MESSAGE = "x-notification-error-message";

    private RetryHeaders() {
    }

    /**
     * Reads a UTF-8 header as a string.
     *
     * @param headers Kafka headers to read from.
     * @param key Header key to read.
     * @return Returns the header value as a string or null when missing.
     */
    public static String readString(Headers headers, String key) {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(key, "key");
        Header header = headers.lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Reads a long value encoded as an 8-byte big-endian header or a UTF-8 string.
     *
     * @param headers Kafka headers to read from.
     * @param key Header key to read.
     * @return Returns the decoded long or null when missing or malformed.
     */
    public static Long readLong(Headers headers, String key) {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(key, "key");
        Header header = headers.lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        byte[] value = header.value();
        if (value.length == Long.BYTES) {
            return ByteBuffer.wrap(value).getLong();
        }
        try {
            String text = new String(value, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return null;
            }
            return Long.parseLong(text.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Encodes a string into UTF-8 bytes for Kafka header storage.
     *
     * @param value Header string value.
     * @return Returns UTF-8 bytes or null when value is null.
     */
    public static byte[] toUtf8(String value) {
        if (value == null) {
            return null;
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encodes a long into an 8-byte big-endian representation for Kafka header storage.
     *
     * @param value Long value to encode.
     * @return Returns the encoded bytes.
     */
    public static byte[] toLongBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }
}

