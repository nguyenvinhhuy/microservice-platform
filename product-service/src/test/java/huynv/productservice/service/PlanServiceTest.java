package huynv.productservice.service;

import huynv.productservice.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanServiceTest {

  private PlanService service;

  /**
   * Initializes a fresh {@link PlanService} instance before each test.
   *
   * @return void — sets up the service field used by all test methods.
   */
  @BeforeEach
  void setUp() {
    service = new PlanService();
  }

  /**
   * Clears the {@link UserContext} thread-local after each test to prevent state leaking between tests.
   *
   * @return void — resets thread-local user context state.
   */
  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  /**
   * Verifies that a tenant whose roles include {@code ROLE_PRO} receives the elevated PRO quota of 10 000.
   *
   * @return void — asserts that {@code resolvePlanQuota} returns 10000 for a PRO-role user.
   */
  @Test
  void resolvePlanQuota_proRole_returns10000() {
    UserContext ctx = new UserContext();
    ctx.setTenantId(1L);
    ctx.setRoles(List.of("ROLE_USER", "ROLE_PRO"));
    UserContext.setCurrentUserContext(ctx);

    assertThat(service.resolvePlanQuota(1L)).isEqualTo(10000);
  }

  /**
   * Verifies that a tenant whose roles include {@code ROLE_ADMIN} but not {@code ROLE_PRO} receives
   * the default quota of 100.
   *
   * @return void — asserts that {@code resolvePlanQuota} returns 100 when {@code ROLE_PRO} is absent.
   */
  @Test
  void resolvePlanQuota_noProRole_returnsDefault100() {
    UserContext ctx = new UserContext();
    ctx.setTenantId(1L);
    ctx.setRoles(List.of("ROLE_USER", "ROLE_ADMIN"));
    UserContext.setCurrentUserContext(ctx);

    assertThat(service.resolvePlanQuota(1L)).isEqualTo(100);
  }

  /**
   * Verifies that a tenant with an empty role list receives the default quota of 100.
   *
   * @return void — asserts that {@code resolvePlanQuota} returns 100 when the role list is empty.
   */
  @Test
  void resolvePlanQuota_emptyRoles_returnsDefault100() {
    UserContext ctx = new UserContext();
    ctx.setTenantId(1L);
    ctx.setRoles(List.of());
    UserContext.setCurrentUserContext(ctx);

    assertThat(service.resolvePlanQuota(1L)).isEqualTo(100);
  }

  /**
   * Verifies that when no {@link UserContext} is present in the thread-local (null context) the service
   * falls back to the default quota of 100 rather than throwing.
   *
   * @return void — asserts that {@code resolvePlanQuota} returns 100 when the thread-local context is null.
   */
  @Test
  void resolvePlanQuota_nullContext_returnsDefault100() {
    // No context set — ThreadLocal returns null
    assertThat(service.resolvePlanQuota(1L)).isEqualTo(100);
  }
}
