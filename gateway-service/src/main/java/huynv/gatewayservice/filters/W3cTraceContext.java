package huynv.gatewayservice.filters;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Provides W3C Trace Context parsing and generation for `traceparent` header propagation.
 */
public final class W3cTraceContext {

    private static final SecureRandom secureRandom = new SecureRandom();

    private W3cTraceContext() {
    }

    /**
     * Parses a W3C `traceparent` header value and extracts the trace id when valid.
     *
     * @param traceparent Traceparent header value.
     * @return Returns the 32-hex trace id when valid, or null when missing or malformed.
     */
    public static String parseTraceId(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String value = traceparent.trim();
        // Expected: version(2)-traceId(32)-spanId(16)-flags(2)
        // Example: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
        String[] parts = value.split("-", -1);
        if (parts.length != 4) {
            return null;
        }
        if (parts[0].length() != 2 || parts[1].length() != 32 || parts[2].length() != 16 || parts[3].length() != 2) {
            return null;
        }
        String traceId = parts[1].toLowerCase(Locale.ROOT);
        if (!isLowerHex(traceId) || isAllZeros(traceId)) {
            return null;
        }
        String spanId = parts[2].toLowerCase(Locale.ROOT);
        if (!isLowerHex(spanId) || isAllZeros(spanId)) {
            return null;
        }
        String flags = parts[3].toLowerCase(Locale.ROOT);
        if (!isLowerHex(flags)) {
            return null;
        }
        return traceId;
    }

    /**
     * Generates a new W3C `traceparent` header value using random trace and span ids.
     *
     * @return Returns a valid traceparent header value using version "00" and sampled flag "01".
     */
    public static String generateTraceparent() {
        return "00-" + randomHex(16) + "-" + randomHex(8) + "-01";
    }

    /**
     * Extracts the trace id portion from a valid traceparent value.
     *
     * @param traceparent Traceparent header value.
     * @return Returns the trace id extracted from the traceparent header.
     */
    public static String traceIdFromTraceparent(String traceparent) {
        return traceparent.split("-", -1)[1].toLowerCase(Locale.ROOT);
    }

    /**
     * Generates a lower-hex string using cryptographically strong random bytes.
     *
     * @param bytes Number of bytes to generate.
     * @return Returns a lower-hex string of length {@code bytes * 2}.
     */
    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buffer) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * Validates whether a hex string is all zeros, which is invalid for W3C trace and span identifiers.
     *
     * @param value Hex string value to validate.
     * @return Returns true when the value contains only the '0' character.
     */
    private static boolean isAllZeros(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates whether a string contains only hexadecimal characters.
     *
     * @param value String to validate.
     * @return Returns true when the string is composed only of hexadecimal characters.
     */
    private static boolean isLowerHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!isHex) {
                return false;
            }
        }
        return true;
    }
}
