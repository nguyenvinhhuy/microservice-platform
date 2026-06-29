package huynv.userservice.service;

import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxRepository;
import huynv.userservice.domain.UserEntity;
import huynv.userservice.dto.UpdateUserProfileRequest;
import huynv.userservice.dto.UserProfileResponse;
import huynv.userservice.repository.UserRepository;
import huynv.userservice.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the main user profile mutation flow together with transactional outbox persistence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class UserApplicationServiceIntegrationTest {

    @Autowired
    private UserApplicationService userApplicationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KafkaOutboxRepository kafkaOutboxRepository;

    /**
     * Ensures updating the current user profile creates the profile row and persists an outbox event in the same transaction.
     *
     * @return Verifies the saved profile state and the resulting outbox row.
     */
    @Test
    void updateCurrentUserProfileCreatesProfileAndOutboxMessage() {
        UUID keycloakUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID tenantId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(keycloakUserId, tenantId, Set.of("ROLE_USER"));
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "user@example.com",
                "Platform User",
                "+1234567890",
                "https://cdn.example.com/avatar.png",
                null,
                "en-US",
                "UTC"
        );

        UserProfileResponse response = userApplicationService.updateCurrentUserProfile(authenticatedUser, request, "corr-1", "cause-1");

        UserEntity savedUser = userRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, response.id()).orElseThrow();
        assertThat(savedUser.getEmail()).isEqualTo("user@example.com");
        assertThat(savedUser.getKeycloakUserId()).isEqualTo(keycloakUserId);
        assertThat(kafkaOutboxRepository.findAll())
                .singleElement()
                .satisfies(outboxMessage -> {
                    assertThat(outboxMessage.getTopic()).isEqualTo("user.events");
                    assertThat(outboxMessage.getPurpose()).isEqualTo(KafkaOutboxPurpose.INTERNAL);
                    assertThat(outboxMessage.getMessageKey()).isEqualTo(tenantId + ":" + response.id());
                });
    }
}

