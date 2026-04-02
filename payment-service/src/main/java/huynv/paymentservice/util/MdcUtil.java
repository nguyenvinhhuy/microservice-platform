package huynv.paymentservice.util;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Provides helper utilities for safely setting and clearing MDC context.
 */
public final class MdcUtil {

    /**
     * Prevents instantiation of a static utility type.
     *
     * @return Prevents instantiation and enforces static access only.
     */
    private MdcUtil() {
    }

    /**
     * Runs the provided runnable with MDC keys applied and clears MDC afterwards.
     *
     * @param context Context values to apply to MDC for the current thread.
     * @param runnable Work to run with MDC context set.
     * @return Executes the runnable and always clears MDC keys.
     */
    public static void runWithContext(Map<String, String> context, Runnable runnable) {
        try {
            if (context != null) {
                context.forEach((k, v) -> {
                    if (v != null) {
                        MDC.put(k, v);
                    }
                });
            }
            runnable.run();
        } finally {
            if (context != null) {
                context.keySet().forEach(MDC::remove);
            }
        }
    }
}
