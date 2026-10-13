package tests.redis;

import com.powerdialer.engine.synchronization.UserSynchronizer;
import org.junit.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.Assert.assertEquals;

/**
 * Task 333467 contract testi.
 *
 * <p>Tum {@code @Scheduled} isler tek thread'i paylastigi icin {@code fixedRate} ile 20 sn'yi asan
 * turlar birikip gunluk cron'lari geciktiriyordu. Senkron turunun bitisten itibaren zamanlandigini
 * kilitler.</p>
 */
public class UserSynchronizerFixedDelayScheduleContractTest {

  @Test
  public void userSynchronizationIsScheduledWithFixedDelay() throws NoSuchMethodException {
    Scheduled scheduled = UserSynchronizer.class.getDeclaredMethod("synchronizeUsers").getAnnotation(Scheduled.class);

    assertEquals(20000L, scheduled.fixedDelay());
    assertEquals(-1L, scheduled.fixedRate());
  }
}
