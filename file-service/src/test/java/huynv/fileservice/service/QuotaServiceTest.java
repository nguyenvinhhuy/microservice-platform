package huynv.fileservice.service;

import huynv.fileservice.cache.FileCacheService;
import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.domain.FileQuota;
import huynv.fileservice.exception.QuotaExceededException;
import huynv.fileservice.repository.FileQuotaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

  @Mock private FileQuotaRepository fileQuotaRepository;
  @Mock private FileCacheService fileCacheService;
  @Mock private FileServiceProperties properties;

  private QuotaService service;

  private static final UUID TENANT_ID = UUID.randomUUID();

  /**
   * Initialises the {@link QuotaService} under test, stubbing the default quota bytes from
   * {@link FileServiceProperties} with a lenient mock so that only tests exercising the
   * no-existing-quota path actually trigger the stub.
   *
   * @return void — sets up mocks and creates the service instance before each test method.
   */
  @BeforeEach
  void setUp() {
    // Lenient: only "no existing quota" tests reach orElseGet → getDefaultQuotaBytes
    FileServiceProperties.Quota quota = mock(FileServiceProperties.Quota.class);
    lenient().when(properties.getQuota()).thenReturn(quota);
    lenient().when(quota.getDefaultQuotaBytes()).thenReturn(1_000_000L);

    service = new QuotaService(fileQuotaRepository, fileCacheService, properties);
  }

  // -----------------------------------------------------------------------
  // reserve
  // -----------------------------------------------------------------------

  /**
   * Verifies that reserving bytes within the available quota updates the used-bytes counter and
   * persists the quota record, then evicts the cached quota entry for the tenant.
   *
   * @return void — asserts the saved quota has the expected used-bytes value and that the cache is evicted.
   */
  @Test
  void reserve_underQuota_updatesUsedBytesAndSaves() {
    FileQuota existingQuota = new FileQuota(TENANT_ID, 1_000_000L);
    when(fileQuotaRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.of(existingQuota));

    service.reserve(TENANT_ID, 500L);

    ArgumentCaptor<FileQuota> captor = ArgumentCaptor.forClass(FileQuota.class);
    verify(fileQuotaRepository).save(captor.capture());
    assertThat(captor.getValue().getUsedBytes()).isEqualTo(500L);
    verify(fileCacheService).evictQuota(TENANT_ID);
  }

  /**
   * Verifies that attempting to reserve bytes that would exceed the tenant's quota limit throws
   * a {@link QuotaExceededException}.
   *
   * @return void — asserts that {@link QuotaExceededException} is thrown when the reservation would overflow.
   */
  @Test
  void reserve_overQuota_throwsQuotaExceededException() {
    FileQuota existingQuota = new FileQuota(TENANT_ID, 100L);
    existingQuota.reserve(80L); // pre-fill 80 of 100
    when(fileQuotaRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.of(existingQuota));

    assertThatThrownBy(() -> service.reserve(TENANT_ID, 30L))
        .isInstanceOf(QuotaExceededException.class);
  }

  /**
   * Verifies that when no quota record exists for the tenant, a new row is created using the
   * configured default quota limit and the requested reservation amount is recorded.
   *
   * @return void — asserts the newly persisted quota has the correct tenant, used-bytes, and quota-bytes values.
   */
  @Test
  void reserve_noExistingQuota_createsRowFromDefault() {
    when(fileQuotaRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.empty());

    service.reserve(TENANT_ID, 200L);

    ArgumentCaptor<FileQuota> captor = ArgumentCaptor.forClass(FileQuota.class);
    verify(fileQuotaRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
    assertThat(captor.getValue().getUsedBytes()).isEqualTo(200L);
    assertThat(captor.getValue().getQuotaBytes()).isEqualTo(1_000_000L);
  }

  // -----------------------------------------------------------------------
  // release
  // -----------------------------------------------------------------------

  /**
   * Verifies that releasing bytes reduces the used-bytes counter by the released amount, persists
   * the updated quota record, and evicts the cached quota entry for the tenant.
   *
   * @return void — asserts that used-bytes is reduced by the released amount and the cache is evicted.
   */
  @Test
  void release_reducesUsedBytesAndSaves() {
    FileQuota existingQuota = new FileQuota(TENANT_ID, 1_000_000L);
    existingQuota.reserve(500L);
    when(fileQuotaRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.of(existingQuota));

    service.release(TENANT_ID, 200L);

    ArgumentCaptor<FileQuota> captor = ArgumentCaptor.forClass(FileQuota.class);
    verify(fileQuotaRepository).save(captor.capture());
    assertThat(captor.getValue().getUsedBytes()).isEqualTo(300L);
    verify(fileCacheService).evictQuota(TENANT_ID);
  }

  /**
   * Verifies that releasing more bytes than are currently recorded as used clamps the result to
   * zero rather than producing a negative used-bytes value.
   *
   * @return void — asserts that used-bytes is zero after releasing an amount exceeding the current used-bytes.
   */
  @Test
  void release_releaseMoreThanUsed_clampsToZero() {
    FileQuota existingQuota = new FileQuota(TENANT_ID, 1_000_000L);
    existingQuota.reserve(100L);
    when(fileQuotaRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.of(existingQuota));

    service.release(TENANT_ID, 500L); // release more than reserved

    ArgumentCaptor<FileQuota> captor = ArgumentCaptor.forClass(FileQuota.class);
    verify(fileQuotaRepository).save(captor.capture());
    assertThat(captor.getValue().getUsedBytes()).isEqualTo(0L);
  }

  /**
   * Verifies that when no quota record exists for the tenant during a release, a new row is
   * created from the default configuration and the used-bytes is clamped to zero.
   *
   * @return void — asserts that a new quota row is saved with zero used-bytes.
   */
  @Test
  void release_noExistingQuota_createsFromDefaultAndReleasesZero() {
    when(fileQuotaRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.empty());

    service.release(TENANT_ID, 100L);

    ArgumentCaptor<FileQuota> captor = ArgumentCaptor.forClass(FileQuota.class);
    verify(fileQuotaRepository).save(captor.capture());
    assertThat(captor.getValue().getUsedBytes()).isEqualTo(0L); // clamp at zero
  }
}
