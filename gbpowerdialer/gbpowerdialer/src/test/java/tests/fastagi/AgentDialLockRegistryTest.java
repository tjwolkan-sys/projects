package tests.fastagi;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.asterisk.fastagi.AgentDialLockRegistry;

/**
 * Unit tests for {@link AgentDialLockRegistry}, the ownership-aware agent dial lock shared by the
 * AGI dial script and the AMI transfer handler.
 *
 * <p>The critical scenario is the lock-handoff race: after a transfer releases an agent's lock and
 * a new call re-acquires it, the original call's late {@code finally} release must not drop the new
 * call's lock.
 */
public class AgentDialLockRegistryTest {

  private AgentDialLockRegistry registry;

  @Before
  public void setUp() {
    registry = new AgentDialLockRegistry();
  }

  @Test
  public void tryLockShouldSucceedWhenAgentIsFree() {
    assertThat(registry.tryLock(1L, "call-1"), is(true));
    assertThat(registry.isLocked(1L), is(true));
  }

  @Test
  public void tryLockShouldFailWhenAgentAlreadyLockedByAnotherCall() {
    registry.tryLock(1L, "call-1");
    assertThat("a second call must not acquire a held lock", registry.tryLock(1L, "call-2"), is(false));
    assertThat(registry.isLocked(1L), is(true));
  }

  @Test
  public void releaseShouldFreeLockForOwningCall() {
    registry.tryLock(1L, "call-1");
    assertThat(registry.release(1L, "call-1"), is(true));
    assertThat(registry.isLocked(1L), is(false));
  }

  @Test
  public void releaseShouldBeNoOpForNonOwningCall() {
    // Lock-handoff race: C1 locked, transfer released, new call C2 re-acquired. C1's late finally
    // release must NOT drop C2's lock.
    registry.tryLock(1L, "C1");
    registry.release(1L, "C1");                            // transfer handler releases C1's lock
    assertThat(registry.tryLock(1L, "C2"), is(true));      // a new call C2 re-acquires the agent
    assertThat("stale release from C1 must not drop C2's lock",
        registry.release(1L, "C1"), is(false));
    assertThat("C2 still owns the lock", registry.isLocked(1L), is(true));
    assertThat("only the owning call (C2) can release", registry.release(1L, "C2"), is(true));
    assertThat(registry.isLocked(1L), is(false));
  }

  @Test
  public void shouldHandleNullArgumentsSafely() {
    assertThat(registry.tryLock(null, "call-1"), is(false));
    assertThat(registry.tryLock(1L, null), is(false));
    assertThat(registry.release(null, "call-1"), is(false));
    assertThat(registry.release(1L, null), is(false));
    assertThat(registry.isLocked(null), is(false));
  }
}
