package huynv.notificationservice.service.contact;

import huynv.notificationservice.exception.NonRetryableNotificationException;
import huynv.notificationservice.exception.RetryableDependencyException;
import huynv.eventinfra.resilience.ResilienceExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves user contact information from user-service when available.
 */
@Component
public class UserServiceUserContactResolver implements UserContactResolver {

    private final RestClient userServiceRestClient;
    private final ResilienceExecutor resilienceExecutor;

    /**
     * Creates a contact resolver backed by user-service REST APIs.
     *
     * @param userServiceRestClient Rest client configured for user-service.
     * @param resilienceExecutor Resilience executor used to protect downstream calls.
     * @return Initializes a user-service contact resolver.
     */
    public UserServiceUserContactResolver(RestClient userServiceRestClient, ResilienceExecutor resilienceExecutor) {
        this.userServiceRestClient = Objects.requireNonNull(userServiceRestClient, "userServiceRestClient");
        this.resilienceExecutor = Objects.requireNonNull(resilienceExecutor, "resilienceExecutor");
    }

    /**
     * Resolves contact information by calling user-service using tenant-scoped headers.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier to resolve.
     * @return Returns the contact information when the user-service responds successfully.
     */
    @Override
    public Optional<UserContact> resolve(Long tenantId, Long userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        return resilienceExecutor.execute("userService", () -> doResolve(tenantId, userId));
    }

    /**
     * Executes the contact resolution REST call and translates HTTP errors into retryable or non-retryable outcomes.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier to resolve.
     * @return Returns the contact information, or an empty optional when the user does not exist.
     */
    private Optional<UserContact> doResolve(Long tenantId, Long userId) {
        try {
            UserContactResponse response = userServiceRestClient.get()
                    .uri("/internal/users/{id}/contact", userId)
                    .header("X-Tenant-Id", tenantId.toString())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .body(UserContactResponse.class);
            if (response == null) {
                throw new RetryableDependencyException("User service returned empty response userId=" + userId + ".", null);
            }
            return Optional.of(new UserContact(response.email(), response.phoneNumber(), response.pushTokens() == null ? List.of() : response.pushTokens()));
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                return Optional.empty();
            }
            if (status >= 400 && status < 500) {
                throw new NonRetryableNotificationException("User service rejected request status=" + status + " userId=" + userId + ".", ex);
            }
            throw new RetryableDependencyException("User service failed status=" + status + " userId=" + userId + ".", ex);
        } catch (RetryableDependencyException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RetryableDependencyException("User service call failed userId=" + userId + ".", ex);
        }
    }

    /**
     * Represents the expected user-service contact response.
     *
     * @param email Email address.
     * @param phoneNumber Phone number.
     * @param pushTokens Push token list.
     */
    public record UserContactResponse(String email, String phoneNumber, List<String> pushTokens) {
    }
}

