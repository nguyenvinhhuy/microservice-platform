package huynv.eventinfra.util;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Provides helper methods for managing MDC keys for structured logging.
 */
public final class MdcUtil {

    private MdcUtil() {
    }

    /**
     * Sets common MDC keys used for correlation and multi-tenancy in structured logs.
     *
     * @param values Map of MDC key-value pairs to apply for the current thread.
     * @return Performs a side effect by populating MDC for the current thread.
     */
    public static void putAll(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (entry.getValue() == null) {
                MDC.remove(entry.getKey());
                continue;
            }
            MDC.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Clears MDC to avoid leaking correlation context across reused threads.
     *
     * @return Performs a side effect by clearing MDC for the current thread.
     */
    public static void clear() {
        MDC.clear();
    }
}


