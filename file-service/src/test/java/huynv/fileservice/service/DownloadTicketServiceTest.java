package huynv.fileservice.service;

import huynv.fileservice.domain.DownloadTicket;
import huynv.fileservice.exception.ConflictException;
import huynv.fileservice.repository.DownloadTicketRepository;
import huynv.fileservice.security.JwtUserContextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies short-lived download ticket issuance, redemption, and cleanup behavior.
 */
@ExtendWith(MockitoExtension.class)
class DownloadTicketServiceTest {

    @Mock
    private DownloadTicketRepository downloadTicketRepository;

    @Mock
    private JwtUserContextExtractor jwtUserContextExtractor;

    @InjectMocks
    private DownloadTicketService downloadTicketService;

    /**
     * Verifies that ticket issuance persists a tenant-bound and user-bound ticket row.
     *
     * @return Performs assertions against the persisted ticket state.
     */
    @Test
    void issuePersistsTenantBoundTicket() {
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(jwtUserContextExtractor.tryExtractUserId(authentication)).thenReturn(userId);

        String token = downloadTicketService.issue(tenantId, authentication, fileId, expiresAt, true);

        ArgumentCaptor<DownloadTicket> ticketCaptor = ArgumentCaptor.forClass(DownloadTicket.class);
        verify(downloadTicketRepository).save(ticketCaptor.capture());
        DownloadTicket persistedTicket = ticketCaptor.getValue();
        assertThat(token).isNotBlank();
        assertThat(persistedTicket.getTenantId()).isEqualTo(tenantId);
        assertThat(persistedTicket.getFileId()).isEqualTo(fileId);
        assertThat(persistedTicket.getUserId()).isEqualTo(userId);
        assertThat(persistedTicket.isSingleUse()).isTrue();
        assertThat(persistedTicket.getExpiresAt()).isEqualTo(expiresAt);
    }

    /**
     * Verifies that redeeming a single-use ticket marks it as used and persists the update.
     *
     * @return Performs assertions against the redeemed ticket state.
     */
    @Test
    void redeemMarksSingleUseTicketAsUsed() {
        String token = "ticket-value";
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        DownloadTicket storedTicket = new DownloadTicket(UUID.randomUUID(), downloadTicketService.sha256(token), tenantId, userId, fileId, true, Instant.now().plusSeconds(300));
        when(downloadTicketRepository.findByTokenHash(downloadTicketService.sha256(token))).thenReturn(Optional.of(storedTicket));
        when(jwtUserContextExtractor.tryExtractUserId(authentication)).thenReturn(userId);

        DownloadTicket redeemedTicket = downloadTicketService.redeem(token, authentication);

        assertThat(redeemedTicket.isUsed()).isTrue();
        verify(downloadTicketRepository).save(storedTicket);
    }

    /**
     * Verifies that redeeming a user-bound ticket with the wrong subject revokes the ticket.
     *
     * @return Performs assertions against the thrown conflict exception and revoked ticket state.
     */
    @Test
    void redeemRejectsSubjectMismatchAndRevokesTicket() {
        String token = "ticket-value";
        UUID tenantId = UUID.randomUUID();
        UUID expectedUserId = UUID.randomUUID();
        UUID actualUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        DownloadTicket storedTicket = new DownloadTicket(UUID.randomUUID(), downloadTicketService.sha256(token), tenantId, expectedUserId, fileId, false, Instant.now().plusSeconds(300));
        when(downloadTicketRepository.findByTokenHash(downloadTicketService.sha256(token))).thenReturn(Optional.of(storedTicket));
        when(jwtUserContextExtractor.tryExtractUserId(authentication)).thenReturn(actualUserId);

        assertThatThrownBy(() -> downloadTicketService.redeem(token, authentication))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not valid for the current user");

        assertThat(storedTicket.isRevoked()).isTrue();
        verify(downloadTicketRepository).save(storedTicket);
    }

    /**
     * Verifies that cleanup removes expired tickets from persistent storage.
     *
     * @return Performs assertions against the repository cleanup interaction.
     */
    @Test
    void cleanupExpiredDeletesExpiredTickets() {
        List<DownloadTicket> expiredTickets = List.of();
        when(downloadTicketRepository.findByExpiresAtBefore(any(Instant.class))).thenReturn(expiredTickets);

        downloadTicketService.cleanupExpired();

        verify(downloadTicketRepository).findByExpiresAtBefore(any(Instant.class));
        verify(downloadTicketRepository).deleteAll(expiredTickets);
    }
}


