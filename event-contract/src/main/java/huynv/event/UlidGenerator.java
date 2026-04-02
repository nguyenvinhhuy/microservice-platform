package huynv.event;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Generates ULID identifiers suitable for lexicographic ordering in event logs.
 */
public final class UlidGenerator {

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private UlidGenerator() {
    }

    /**
     * Generates a 26-character ULID string using the current time and random entropy.
     *
     * @return Returns a new ULID string suitable for eventId usage.
     */
    public static String nextUlid() {
        return nextUlid(Instant.now());
    }

    /**
     * Generates a 26-character ULID string using the provided timestamp and random entropy.
     *
     * @param instant Timestamp used as the ULID time component.
     * @return Returns a new ULID string suitable for eventId usage.
     */
    public static String nextUlid(Instant instant) {
        long timeMs = instant.toEpochMilli();
        byte[] entropy = new byte[10];
        RANDOM.nextBytes(entropy);
        return encodeUlid(timeMs, entropy);
    }

    private static String encodeUlid(long timeMs, byte[] entropy) {
        char[] out = new char[26];
        long time = timeMs & 0xFFFFFFFFFFFFL;
        out[0] = CROCKFORD[(int) ((time >>> 45) & 31)];
        out[1] = CROCKFORD[(int) ((time >>> 40) & 31)];
        out[2] = CROCKFORD[(int) ((time >>> 35) & 31)];
        out[3] = CROCKFORD[(int) ((time >>> 30) & 31)];
        out[4] = CROCKFORD[(int) ((time >>> 25) & 31)];
        out[5] = CROCKFORD[(int) ((time >>> 20) & 31)];
        out[6] = CROCKFORD[(int) ((time >>> 15) & 31)];
        out[7] = CROCKFORD[(int) ((time >>> 10) & 31)];
        out[8] = CROCKFORD[(int) ((time >>> 5) & 31)];
        out[9] = CROCKFORD[(int) (time & 31)];

        int bit = 0;
        int idx = 10;
        int buffer = 0;
        int bufferBits = 0;
        while (idx < 26) {
            while (bufferBits < 5) {
                if (bit >= entropy.length * 8) {
                    buffer <<= (5 - bufferBits);
                    bufferBits = 5;
                    break;
                }
                int b = entropy[bit / 8] & 0xFF;
                int shift = 7 - (bit % 8);
                buffer = (buffer << 1) | ((b >>> shift) & 1);
                bufferBits++;
                bit++;
            }
            int val = (buffer >>> (bufferBits - 5)) & 31;
            bufferBits -= 5;
            out[idx++] = CROCKFORD[val];
        }
        return new String(out);
    }
}

