package tests.dialer.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.dialer.routing.FctChannelRegistry;

/**
 * Aynı FCT'ye birden fazla originator thread'i (server başına bir thread) eşzamanlı eriştiğinde son
 * kanalın iki çağrıya satılmaması gerekir: kapasite kontrolü ve rezervasyon aynı per-numara kritik
 * bölümünde yapılır.
 */
public class FctChannelRegistryConcurrencyTest {

  private static final String NUMBER = "05550000009";
  private static final int THREADS = 24;
  private static final int CAPACITY = 5;

  @Test
  public void shouldGrantExactlyCapacityUnderConcurrentAcquire() throws Exception {
    final FctChannelRegistry registry = newRegistry();
    final CountDownLatch startGate = new CountDownLatch(1);
    final CountDownLatch finished = new CountDownLatch(THREADS);
    final AtomicInteger granted = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);

    try {
      for (int i = 0; i < THREADS; i++) {
        final String callId = "call-" + i;
        pool.execute(new Runnable() {
          @Override
          public void run() {
            try {
              startGate.await();
              if (registry.tryAcquire(NUMBER, "MOBILE", "1", CAPACITY, callId, 1L)) {
                granted.incrementAndGet();
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              finished.countDown();
            }
          }
        });
      }

      startGate.countDown();
      assertTrue("tüm thread'ler bitmeli", finished.await(30, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }

    assertEquals("kapasite kadar rezervasyon verilmeli", CAPACITY, granted.get());
    assertEquals("registry doluluğu kapasiteyi aşmamalı", CAPACITY, registry.occupancy(NUMBER));
  }

  private FctChannelRegistry newRegistry() {
    FctChannelRegistry registry = new FctChannelRegistry();
    ReflectionTestUtils.setField(registry, "gateEnabled", true);
    ReflectionTestUtils.setField(registry, "ttlMs", 3900000L);
    ReflectionTestUtils.setField(registry, "staleWarnMs", 600000L);
    ReflectionTestUtils.setField(registry, "cooldownMs", 60000L);
    ReflectionTestUtils.setField(registry, "headroomPercent", 0);
    ReflectionTestUtils.setField(registry, "metaRetentionMs", 1800000L);
    ReflectionTestUtils.setField(registry, "eventLogSize", 200);
    return registry;
  }
}
