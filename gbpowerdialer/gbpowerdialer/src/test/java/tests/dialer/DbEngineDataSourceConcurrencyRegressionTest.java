package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

import com.powerdialer.engine.db.DBEngine;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

/**
 * FIX-333607: campaign aksiyonları paralel çalışınca {@code DBEngine.dataSources} eşzamanlı
 * yazılabilir. Eski hali senkronize olmayan {@code HashMap} idi ve aynı campaign için iki thread iki
 * ayrı datasource oluşturabiliyordu (02.10.2026: {@code InstanceAlreadyExistsException:
 * DataSource=campaign91}). Oluşturma campaign bazında tekil olmalı, farklı campaign'ler birbirini
 * beklememeli (müşteri DB'si yanıt vermezse diğer campaign'ler etkilenmemeli).
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({DBEngine.class})
@PowerMockIgnore({"javax.management.*", "jdk.internal.reflect.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class DbEngineDataSourceConcurrencyRegressionTest {

  private DBEngine dbEngine;
  private final CountDownLatch firstCreationEntered = new CountDownLatch(1);
  private final CountDownLatch releaseFirstCreation = new CountDownLatch(1);
  private final Map<Long, AtomicInteger> creations = new ConcurrentHashMap<>();

  @Before
  public void init() throws Exception {
    dbEngine = PowerMockito.spy(new DBEngine());
    Whitebox.setInternalState(dbEngine, "self", dbEngine);
    PowerMockito.doAnswer(inv -> {
      Long owner = inv.getArgument(0);
      creations.computeIfAbsent(owner, k -> new AtomicInteger()).incrementAndGet();
      if (Long.valueOf(1L).equals(owner)) {
        firstCreationEntered.countDown();
        releaseFirstCreation.await(10, TimeUnit.SECONDS);
      }
      BasicDataSource dataSource = new BasicDataSource();
      Map<Long, BasicDataSource> dataSources = Whitebox.getInternalState(dbEngine, "dataSources");
      dataSources.put(owner, dataSource);
      return dataSource;
    }).when(dbEngine, "createDataSourceAndCheckConnection", anyLong(), any(), any(), any());
  }

  @Test
  public void concurrentRequestsForSameCampaignCreateSingleDataSource() throws Exception {
    AtomicReference<BasicDataSource> first = new AtomicReference<>();
    Thread creator = new Thread(() -> first.set(getQuietly(1L)), "first-creator");
    creator.start();
    assertTrue(firstCreationEntered.await(5, TimeUnit.SECONDS));

    AtomicReference<BasicDataSource> second = new AtomicReference<>();
    Thread waiter = new Thread(() -> second.set(getQuietly(1L)), "second-requester");
    waiter.start();
    awaitWaiting(waiter);

    assertEquals("İkinci istek yeni datasource oluşturmamalı", 1, creations.get(1L).get());
    releaseFirstCreation.countDown();
    creator.join(5000L);
    waiter.join(5000L);

    assertEquals(1, creations.get(1L).get());
    assertSame(first.get(), second.get());
  }

  @Test
  public void creationForOtherCampaignIsNotBlockedBySlowCampaignDatabase() throws Exception {
    Thread creator = new Thread(() -> getQuietly(1L), "slow-creator");
    creator.start();
    assertTrue(firstCreationEntered.await(5, TimeUnit.SECONDS));

    BasicDataSource other = dbEngine.getDataSource(2L, "jdbc:h2:mem:other", "sa", "");

    assertEquals(1, creations.get(2L).get());
    assertFalse("Yavaş campaign hâlâ oluşturma aşamasında olmalı", creator.getState() == Thread.State.TERMINATED);
    assertSame(other, dbEngine.getDataSource(2L, "jdbc:h2:mem:other", "sa", ""));
    releaseFirstCreation.countDown();
    creator.join(5000L);
  }

  @Test
  public void checkConnectionWithNullOwnerStillReturnsFalse() {
    assertFalse(dbEngine.checkConnection(null, null, null, null));
  }

  private BasicDataSource getQuietly(Long owner) {
    try {
      return dbEngine.getDataSource(owner, "jdbc:h2:mem:c" + owner, "sa", "");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void awaitWaiting(Thread thread) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (thread.getState() != Thread.State.BLOCKED && thread.getState() != Thread.State.WAITING
        && thread.getState() != Thread.State.TIMED_WAITING && thread.getState() != Thread.State.TERMINATED) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Thread beklemeye geçmedi: " + thread.getState());
      }
      Thread.yield();
    }
  }
}
