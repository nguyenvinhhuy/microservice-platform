package huynv.notificationservice.service;

import huynv.eventinfra.config.NotificationProperties;
import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationPreference;
import huynv.notificationservice.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

  @Mock private NotificationPreferenceRepository preferenceRepository;
  @Mock private NotificationProperties properties;

  private NotificationPreferenceService service;

  private static final Long TENANT_ID = 1L;
  private static final Long USER_ID = 100L;

  /**
   * Initialises a real {@link NotificationProperties.Channels} instance with all three channels
   * enabled and wires it into a fresh {@link NotificationPreferenceService} before each test.
   * The channels stub is lenient so tests that throw before reaching channel evaluation do not
   * fail due to an unused stubbing.
   *
   * @return void — configures shared service instance and global-channel stubs before each test.
   */
  @BeforeEach
  void setUp() {
    // Real Channels instance — all three channels enabled by default.
    // Lenient: null-param tests throw before reaching isGloballyEnabled().
    NotificationProperties.Channels channels = new NotificationProperties.Channels();
    channels.setEmailEnabled(true);
    channels.setSmsEnabled(true);
    channels.setPushEnabled(true);
    lenient().when(properties.getChannels()).thenReturn(channels);

    service = new NotificationPreferenceService(preferenceRepository, properties);
  }

  // -----------------------------------------------------------------------
  // enabledChannels
  // -----------------------------------------------------------------------

  /**
   * Verifies that when no per-user preferences exist the service returns all three globally
   * enabled channels as the default enabled set.
   *
   * @return void — asserts that EMAIL, SMS, and PUSH are returned when no stored preferences are
   *     found.
   */
  @Test
  void enabledChannels_noStoredPrefs_returnsAllGloballyEnabledChannels() {
    when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(List.of());

    EnumSet<NotificationChannelType> result = service.enabledChannels(TENANT_ID, USER_ID);

    assertThat(result).containsExactlyInAnyOrder(
        NotificationChannelType.EMAIL, NotificationChannelType.SMS, NotificationChannelType.PUSH);
  }

  /**
   * Verifies that when EMAIL is disabled at the global configuration level it is excluded from
   * the default enabled channel set even when no per-user preferences are stored.
   *
   * @return void — asserts that global channel suppression removes EMAIL from the result set.
   */
  @Test
  void enabledChannels_emailDisabledGlobally_excludesEmailFromDefaults() {
    NotificationProperties.Channels restricted = new NotificationProperties.Channels();
    restricted.setEmailEnabled(false);
    restricted.setSmsEnabled(true);
    restricted.setPushEnabled(true);
    when(properties.getChannels()).thenReturn(restricted);
    when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(List.of());

    EnumSet<NotificationChannelType> result = service.enabledChannels(TENANT_ID, USER_ID);

    assertThat(result).doesNotContain(NotificationChannelType.EMAIL);
    assertThat(result).containsExactlyInAnyOrder(NotificationChannelType.SMS, NotificationChannelType.PUSH);
  }

  /**
   * Verifies that when per-user preferences are stored only channels whose preference is marked
   * enabled are included in the returned set, while disabled channels are excluded.
   *
   * @return void — asserts that per-user enabled/disabled flags are correctly applied to the
   *     returned channel set.
   */
  @Test
  void enabledChannels_storedPrefs_onlyEnabledOnesReturned() {
    NotificationPreference emailOn = NotificationPreference.create(TENANT_ID, USER_ID, NotificationChannelType.EMAIL, true);
    NotificationPreference smsOff = NotificationPreference.create(TENANT_ID, USER_ID, NotificationChannelType.SMS, false);
    when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
        .thenReturn(List.of(emailOn, smsOff));

    EnumSet<NotificationChannelType> result = service.enabledChannels(TENANT_ID, USER_ID);

    assertThat(result).containsOnly(NotificationChannelType.EMAIL);
    assertThat(result).doesNotContain(NotificationChannelType.SMS);
  }

  /**
   * Verifies that a user-level preference enabling a channel is overridden by the global kill
   * switch: if EMAIL is disabled globally the channel is excluded even when the stored preference
   * marks it as enabled.
   *
   * @return void — asserts that global channel suppression takes precedence over per-user
   *     preference when both conflict.
   */
  @Test
  void enabledChannels_prefEnabledButGloballyDisabled_channelExcluded() {
    NotificationProperties.Channels noEmail = new NotificationProperties.Channels();
    noEmail.setEmailEnabled(false);
    noEmail.setSmsEnabled(true);
    noEmail.setPushEnabled(true);
    when(properties.getChannels()).thenReturn(noEmail);

    NotificationPreference emailOn = NotificationPreference.create(TENANT_ID, USER_ID, NotificationChannelType.EMAIL, true);
    when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(List.of(emailOn));

    EnumSet<NotificationChannelType> result = service.enabledChannels(TENANT_ID, USER_ID);

    assertThat(result).isEmpty();
  }

  /**
   * Verifies that passing a null tenant ID to enabledChannels raises a
   * {@link NullPointerException} as required by the not-null contract on that parameter.
   *
   * @return void — asserts that a null tenant ID is rejected with the expected exception type.
   */
  @Test
  void enabledChannels_nullTenantId_throwsNullPointerException() {
    assertThatThrownBy(() -> service.enabledChannels(null, USER_ID))
        .isInstanceOf(NullPointerException.class);
  }

  /**
   * Verifies that passing a null user ID to enabledChannels raises a
   * {@link NullPointerException} as required by the not-null contract on that parameter.
   *
   * @return void — asserts that a null user ID is rejected with the expected exception type.
   */
  @Test
  void enabledChannels_nullUserId_throwsNullPointerException() {
    assertThatThrownBy(() -> service.enabledChannels(TENANT_ID, null))
        .isInstanceOf(NullPointerException.class);
  }

  // -----------------------------------------------------------------------
  // list
  // -----------------------------------------------------------------------

  /**
   * Verifies that list delegates to the repository and returns the raw preference list for the
   * given tenant and user, preserving channel metadata.
   *
   * @return void — asserts that the returned list size and channel type match the repository
   *     result.
   */
  @Test
  void list_returnsRepositoryResult() {
    NotificationPreference pref = NotificationPreference.create(TENANT_ID, USER_ID, NotificationChannelType.EMAIL, true);
    when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(List.of(pref));

    List<NotificationPreference> result = service.list(TENANT_ID, USER_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getChannel()).isEqualTo(NotificationChannelType.EMAIL);
  }

  /**
   * Verifies that passing a null tenant ID to list raises a {@link NullPointerException} as
   * required by the not-null contract on that parameter.
   *
   * @return void — asserts that a null tenant ID is rejected with the expected exception type.
   */
  @Test
  void list_nullTenantId_throwsNullPointerException() {
    assertThatThrownBy(() -> service.list(null, USER_ID))
        .isInstanceOf(NullPointerException.class);
  }

  /**
   * Verifies that passing a null user ID to list raises a {@link NullPointerException} as
   * required by the not-null contract on that parameter.
   *
   * @return void — asserts that a null user ID is rejected with the expected exception type.
   */
  @Test
  void list_nullUserId_throwsNullPointerException() {
    assertThatThrownBy(() -> service.list(TENANT_ID, null))
        .isInstanceOf(NullPointerException.class);
  }

  // -----------------------------------------------------------------------
  // upsert
  // -----------------------------------------------------------------------

  /**
   * Verifies that when no preference record exists for the given channel a new
   * {@link NotificationPreference} is created with the specified enabled flag and persisted via
   * the repository.
   *
   * @return void — asserts that a new preference is saved with the correct tenant, user, channel,
   *     and enabled state.
   */
  @Test
  void upsert_noPriorPref_createsNewAndPersists() {
    when(preferenceRepository.findByTenantIdAndUserIdAndChannel(TENANT_ID, USER_ID, NotificationChannelType.PUSH))
        .thenReturn(Optional.empty());
    when(preferenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    NotificationPreference result = service.upsert(TENANT_ID, USER_ID, NotificationChannelType.PUSH, true);

    assertThat(result.getChannel()).isEqualTo(NotificationChannelType.PUSH);
    assertThat(result.isEnabled()).isTrue();
    assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(result.getUserId()).isEqualTo(USER_ID);
    verify(preferenceRepository).save(any(NotificationPreference.class));
  }

  /**
   * Verifies that when a preference record already exists for the given channel its enabled flag
   * is updated in-place and the same entity is saved back to the repository.
   *
   * @return void — asserts that the existing preference entity is mutated and re-persisted with
   *     the new enabled value.
   */
  @Test
  void upsert_existingPref_updatesEnabledFlagAndSaves() {
    NotificationPreference existing = NotificationPreference.create(TENANT_ID, USER_ID, NotificationChannelType.SMS, true);
    when(preferenceRepository.findByTenantIdAndUserIdAndChannel(TENANT_ID, USER_ID, NotificationChannelType.SMS))
        .thenReturn(Optional.of(existing));
    when(preferenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    NotificationPreference result = service.upsert(TENANT_ID, USER_ID, NotificationChannelType.SMS, false);

    assertThat(result.isEnabled()).isFalse();
    verify(preferenceRepository).save(existing);
  }

  /**
   * Verifies that passing a null tenant ID to upsert raises a {@link NullPointerException} as
   * required by the not-null contract on that parameter.
   *
   * @return void — asserts that a null tenant ID is rejected with the expected exception type.
   */
  @Test
  void upsert_nullTenantId_throwsNullPointerException() {
    assertThatThrownBy(() -> service.upsert(null, USER_ID, NotificationChannelType.EMAIL, true))
        .isInstanceOf(NullPointerException.class);
  }

  /**
   * Verifies that passing a null user ID to upsert raises a {@link NullPointerException} as
   * required by the not-null contract on that parameter.
   *
   * @return void — asserts that a null user ID is rejected with the expected exception type.
   */
  @Test
  void upsert_nullUserId_throwsNullPointerException() {
    assertThatThrownBy(() -> service.upsert(TENANT_ID, null, NotificationChannelType.EMAIL, true))
        .isInstanceOf(NullPointerException.class);
  }

  /**
   * Verifies that passing a null channel type to upsert raises a {@link NullPointerException} as
   * required by the not-null contract on that parameter.
   *
   * @return void — asserts that a null channel is rejected with the expected exception type.
   */
  @Test
  void upsert_nullChannel_throwsNullPointerException() {
    assertThatThrownBy(() -> service.upsert(TENANT_ID, USER_ID, null, true))
        .isInstanceOf(NullPointerException.class);
  }
}
