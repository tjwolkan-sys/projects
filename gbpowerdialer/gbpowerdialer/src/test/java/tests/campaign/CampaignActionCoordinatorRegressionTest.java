package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.powerdialer.exceptions.CampaignIllegalStateException;
import com.powerdialer.service.CampaignActionCoordinator;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Collections;
import org.junit.After;
import org.junit.Test;

/**
 * FIX-333607: campaign aksiyonları tek global kilit yerine campaign bazlı kilitle korunur.
 *
 * <p>02.10.2026 Turkcell olayında tek bir GAMMA start'ı (10,5 dk) global
 * {@code CampaignService.action} kilidini tuttuğu için set'teki bütün campaign'lerin
 * start/stop/pause aksiyonları bekledi. Bu testler farklı campaign'lerin birbirini beklememesini,
 * aynı campaign'in aksiyonlarının sıralı kalmasını (Bug #5637 koruması) ve beklemenin sınırlı
 * olmasını kilitler.</p>
 */
public class CampaignActionCoordinatorRegressionTest {

  private static final long SHORT_WAIT_MS = 100L;

  private final ManualExecutor manualExecutor = new ManualExecutor();
  private Thread holder;

  @After
  public void tearDown() throws InterruptedException {
    if (holder != null) {
      holder.interrupt();
      holder.join(5000L);
    }
  }

  @Test
  public void differentCampaignIsNotBlockedWhileAnotherCampaignActionRuns() throws Exception {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);
    CountDownLatch release = holdCampaign(coordinator, 1L);

    AtomicInteger executed = new AtomicInteger();
    coordinator.runExclusive(2L, "STOP_REGULAR", executed::incrementAndGet);

    assertEquals(1, executed.get());
    release.countDown();
  }

  @Test
  public void sameCampaignSecondActionFailsWithInProgressAfterBoundedWait() throws Exception {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);
    CountDownLatch release = holdCampaign(coordinator, 1L);

    AtomicInteger executed = new AtomicInteger();
    try {
      coordinator.runExclusive(1L, "STOP_REGULAR", executed::incrementAndGet);
      fail("Aynı campaign'in ikinci aksiyonu kilit alınamadan çalışmamalı");
    } catch (CampaignIllegalStateException e) {
      assertEquals(CampaignActionCoordinator.ACTION_IN_PROGRESS_MESSAGE_KEY, e.getMessage());
    }
    assertEquals(0, executed.get());
    release.countDown();
  }

  @Test
  public void sameCampaignActionRunsAfterPreviousActionReleasesLock() throws Exception {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(5000L, manualExecutor);
    CountDownLatch release = holdCampaign(coordinator, 1L);
    release.countDown();
    holder.join(5000L);

    AtomicInteger executed = new AtomicInteger();
    coordinator.runExclusive(1L, "START", executed::incrementAndGet);

    assertEquals(1, executed.get());
  }

  @Test
  public void nestedActionOfSameCampaignOnSameThreadDoesNotDeadlock() {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);
    AtomicInteger executed = new AtomicInteger();

    coordinator.runExclusive(1L, "RESTART", () ->
        coordinator.runExclusive(1L, "STOP_FORCEFUL", executed::incrementAndGet));

    assertEquals(1, executed.get());
  }

  @Test
  public void lockIsReleasedWhenActionThrows() {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);
    try {
      coordinator.runExclusive(1L, "START", () -> {
        throw new IllegalStateException("boom");
      });
      fail("Aksiyon hatası çağırana iletilmeli");
    } catch (IllegalStateException expected) {
      // beklenen
    }

    AtomicInteger executed = new AtomicInteger();
    coordinator.runExclusive(1L, "START", executed::incrementAndGet);
    assertEquals(1, executed.get());
  }

  @Test
  public void submitDoesNotBlockCallerWhenCampaignIsBusy() throws Exception {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);
    CountDownLatch release = holdCampaign(coordinator, 1L);

    assertTrue(coordinator.submit(1L, "STOP_REGULAR", () -> { }));
    assertEquals(1, manualExecutor.pending());
    release.countDown();
  }

  @Test
  public void submitCoalescesSameCampaignAndActionWhileInFlight() {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);
    AtomicInteger executed = new AtomicInteger();

    assertTrue(coordinator.submit(1L, "STOP_REGULAR", executed::incrementAndGet));
    assertFalse(coordinator.submit(1L, "STOP_REGULAR", executed::incrementAndGet));
    assertTrue(coordinator.submit(1L, "PAUSE", executed::incrementAndGet));
    assertTrue(coordinator.submit(2L, "STOP_REGULAR", executed::incrementAndGet));
    assertEquals("Campaign başına tek sıralı iş (lane) havuza gider", 2, manualExecutor.pending());

    manualExecutor.runAll();
    assertEquals(3, executed.get());

    assertTrue("İş bittikten sonra aynı aksiyon tekrar kuyruğa alınabilmeli",
        coordinator.submit(1L, "STOP_REGULAR", executed::incrementAndGet));
  }

  @Test
  public void submittedActionWaitsForSameCampaignLockAndIsDroppedOnTimeout() throws Exception {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);
    CountDownLatch release = holdCampaign(coordinator, 1L);
    AtomicInteger executed = new AtomicInteger();

    assertTrue(coordinator.submit(1L, "STOP_REGULAR", executed::incrementAndGet));
    manualExecutor.runAll();

    assertEquals("Kilit alınamayan asenkron aksiyon çalışmamalı", 0, executed.get());
    assertTrue("Düşen aksiyondan sonra aynı aksiyon tekrar kuyruğa alınabilmeli",
        coordinator.submit(1L, "STOP_REGULAR", executed::incrementAndGet));
    release.countDown();
  }

  @Test
  public void failingSubmittedActionDoesNotPropagateAndCanBeResubmitted() {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);

    assertTrue(coordinator.submit(1L, "START", () -> {
      throw new IllegalStateException("boom");
    }));
    manualExecutor.runAll();

    assertTrue(coordinator.submit(1L, "START", () -> { }));
  }

  /**
   * Scheduler aynı turda RESET ardından START gönderir; eskiden ikisi senkron sırayla çalışıyordu.
   * Havuz thread'leri işleri hangi sırayla alırsa alsın aynı campaign'in aksiyonları gönderim
   * sırasıyla çalışmalı, farklı campaign'ler ayrı kalmalı.
   */
  @Test
  public void submittedActionsOfSameCampaignRunInSubmissionOrderEvenIfPoolRunsLastFirst() {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);
    List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();

    coordinator.submit(1L, "RESET", () -> order.add("1:RESET"));
    coordinator.submit(1L, "START", () -> order.add("1:START"));
    coordinator.submit(2L, "START", () -> order.add("2:START"));
    manualExecutor.runAllLastFirst();

    assertTrue("Campaign 1 RESET, START'tan önce çalışmalı: " + order,
        order.indexOf("1:RESET") >= 0 && order.indexOf("1:RESET") < order.indexOf("1:START"));
    assertTrue("Campaign 2 aksiyonu da çalışmalı: " + order, order.contains("2:START"));
  }

  /** Scheduler bu bilgiyle kuyrukta veya çalışırken bayat state ile karar vermez. */
  @Test
  public void hasPendingActionCoversQueuedRunningAndSynchronousActions() throws Exception {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);
    assertFalse(coordinator.hasPendingAction(1L));

    coordinator.submit(1L, "START", () -> { });
    assertTrue("Kuyruktaki iş bekleyen aksiyondur", coordinator.hasPendingAction(1L));
    assertFalse("Başka campaign etkilenmez", coordinator.hasPendingAction(2L));
    manualExecutor.runAll();
    assertFalse("Biten iş bekleyen aksiyon değildir", coordinator.hasPendingAction(1L));

    CountDownLatch release = holdCampaign(coordinator, 1L);
    assertTrue("Senkron çalışan aksiyon da bekleyen aksiyondur", coordinator.hasPendingAction(1L));
    release.countDown();
  }

  /**
   * RESTART'ın stop job'ı bitince START ({@code CampaignListener}) aynı campaign'in RESTART aksiyonu
   * henüz kilidi bırakmamışsa bekler; eskiden global kilitte süresiz beklerdi. Bu iş UI'daki 10 sn
   * sınırıyla düşmemeli.
   */
  @Test
  public void runExclusiveWithLongerWaitOutlastsDefaultWait() throws Exception {
    CampaignActionCoordinator coordinator = new CampaignActionCoordinator(SHORT_WAIT_MS, manualExecutor);
    CountDownLatch release = holdCampaign(coordinator, 1L);
    Thread releaser = new Thread(() -> {
      try {
        Thread.sleep(3 * SHORT_WAIT_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      release.countDown();
    }, "releaser");
    releaser.start();

    AtomicInteger executed = new AtomicInteger();
    coordinator.runExclusive(1L, "START", executed::incrementAndGet, 5000L);

    assertEquals(1, executed.get());
    releaser.join(5000L);
  }

  /**
   * Verilen campaign'in kilidini ayrı bir thread'de tutar; dönen latch açılınca kilit bırakılır.
   */
  private CountDownLatch holdCampaign(CampaignActionCoordinator coordinator, Long campaignId) throws InterruptedException {
    CountDownLatch acquired = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    holder = new Thread(() -> coordinator.runExclusive(campaignId, "START", () -> {
      acquired.countDown();
      try {
        release.await(30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }), "campaign-holder");
    holder.start();
    assertTrue("Tutucu thread kilidi almalı", acquired.await(5, TimeUnit.SECONDS));
    return release;
  }

  /** Testin kontrol ettiği, görevleri çağrılana kadar bekleten executor. */
  private static final class ManualExecutor extends AbstractExecutorService {
    private final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
      synchronized (tasks) {
        tasks.add(command);
      }
    }

    int pending() {
      synchronized (tasks) {
        return tasks.size();
      }
    }

    void runAll() {
      while (true) {
        Runnable next;
        synchronized (tasks) {
          next = tasks.poll();
        }
        if (next == null) {
          return;
        }
        next.run();
      }
    }

    /** Havuzun işleri ters sırada alması (thread zamanlaması) senaryosu. */
    void runAllLastFirst() {
      while (true) {
        Runnable next;
        synchronized (tasks) {
          next = tasks.pollLast();
        }
        if (next == null) {
          return;
        }
        next.run();
      }
    }

    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
      return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }
  }
}
