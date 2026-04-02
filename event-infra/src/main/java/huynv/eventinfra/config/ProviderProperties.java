package huynv.eventinfra.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Defines external provider timeout configuration used to ensure delivery calls do not block indefinitely.
 */
@Validated
@ConfigurationProperties(prefix = "provider")
public class ProviderProperties {

    private final Email email = new Email();
    private final Sms sms = new Sms();
    private final Push push = new Push();

    /**
     * Returns email provider timeout configuration.
     *
     * @return Returns email provider timeout configuration.
     */
    public Email getEmail() {
        return email;
    }

    /**
     * Returns SMS provider timeout configuration.
     *
     * @return Returns SMS provider timeout configuration.
     */
    public Sms getSms() {
        return sms;
    }

    /**
     * Returns push provider timeout configuration.
     *
     * @return Returns push provider timeout configuration.
     */
    public Push getPush() {
        return push;
    }

    /**
     * Defines email provider timeout configuration.
     */
    public static final class Email {
        @NotNull
        private Duration timeout = Duration.ofSeconds(3);

        /**
         * Returns the timeout enforced for email provider calls.
         *
         * @return Returns the email provider timeout.
         */
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * Sets the timeout enforced for email provider calls.
         *
         * @param timeout Email provider timeout value.
         * @return Updates the email provider timeout configuration.
         */
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * Defines SMS provider timeout configuration.
     */
    public static final class Sms {
        @NotNull
        private Duration timeout = Duration.ofSeconds(3);

        /**
         * Returns the timeout enforced for SMS provider calls.
         *
         * @return Returns the SMS provider timeout.
         */
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * Sets the timeout enforced for SMS provider calls.
         *
         * @param timeout SMS provider timeout value.
         * @return Updates the SMS provider timeout configuration.
         */
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * Defines push provider timeout configuration.
     */
    public static final class Push {
        @NotNull
        private Duration timeout = Duration.ofSeconds(3);

        /**
         * Returns the timeout enforced for push provider calls.
         *
         * @return Returns the push provider timeout.
         */
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * Sets the timeout enforced for push provider calls.
         *
         * @param timeout Push provider timeout value.
         * @return Updates the push provider timeout configuration.
         */
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}


