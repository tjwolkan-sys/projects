package com.powerdialer.engine.asterisk.fastagi;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * Ownership-aware lock that prevents two concurrent dialer calls from dialing the same agent at the
 * same time. The lock is acquired by {@link DialWithAbandonTimeoutScript} when it dials an agent and
 * released when that dial ends.
 *
 * <p>The owning dialer call id is tracked alongside the agent so the lock can be released from
 * outside the AGI thread. This is needed for the transfer case: when an agent leaves the original
 * bridge via transfer, the AGI {@code Dial} for that call stays blocked (the customer is now talking
 * to the transfer target), so its {@code finally}/release does not run until the whole call ends.
 * The AMI transfer handler releases the lock early so the freed agent can be dialed again.
 *
 * <p>Release is checked against the owner ({@link #release(Long, String)} only removes when the
 * given call still holds the lock). This closes the lock-handoff race: after the transfer releases
 * the lock and a new call re-acquires it, the original call's late {@code finally} release is a
 * no-op and does not drop the new call's lock.
 */
@Component
public class AgentDialLockRegistry {

  /** agent userId -&gt; owning dialer call id. */
  private final ConcurrentMap<Long, String> locksByUserId = new ConcurrentHashMap<>();

  /**
   * Try to acquire the dial lock for {@code userId} on behalf of {@code callId}.
   * @param userId agent id
   * @param callId dialer call id acquiring the lock
   * @return true if the lock was acquired (no other call holds it)
   */
  public boolean tryLock(Long userId, String callId) {
    return userId != null && callId != null && locksByUserId.putIfAbsent(userId, callId) == null;
  }

  /**
   * Release the dial lock for {@code userId} only if {@code callId} still owns it. Idempotent and
   * safe to call from any thread; a release for a call that no longer owns the lock is a no-op.
   * @param userId agent id
   * @param callId dialer call id that acquired the lock
   * @return true if this call owned the lock and it was released
   */
  public boolean release(Long userId, String callId) {
    return userId != null && callId != null && locksByUserId.remove(userId, callId);
  }

  /**
   * @param userId agent id
   * @return true if any call currently holds the dial lock for {@code userId}
   */
  public boolean isLocked(Long userId) {
    return userId != null && locksByUserId.containsKey(userId);
  }
}
