package com.powerdialer.service;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.powerdialer.exceptions.CampaignIllegalStateException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FIX-333607: campaign aksiyonlarını (start, stop, pause, reload, reset...) campaign bazında
 * sıralar; farklı campaign'lerin aksiyonları birbirini beklemez.
 *
 * <p>Eskiden {@code CampaignService.action} tek global {@code synchronized} kilitti (2017, Bug
 * #5637: aynı campaign'e eşzamanlı start/stop yarışı). 02.10.2026 Turkcell olayında tek bir GAMMA
 * start'ı bu kilidi 10,5 dakika tuttu ve set'teki tüm campaign aksiyonları bekledi. Campaign bazlı
 * {@link ReentrantLock} aynı campaign için Bug #5637 korumasını korur, campaign'ler arası
 * bekleşmeyi kaldırır.</p>
 *
 * <p>İki giriş noktası vardır:</p>
 * <ul>
 *   <li>{@link #runExclusive}: çağıran thread'de çalışır, aynı campaign kilidini en fazla
 *       {@code lockWaitMs} bekler; alamazsa {@code campaign.action.inProgress} hatası verir.</li>
 *   <li>{@link #submit}: scheduler ve stop condition gibi tetikleyiciler içindir; çağıranı hiç
 *       bekletmez. Aynı campaign + aksiyon için bekleyen iş varsa yenisini eklemez. Aynı campaign'in
 *       işleri gönderim sırasıyla (FIFO) çalışır; scheduler'ın RESET ardından START'ı eskiden olduğu
 *       gibi sırayla uygulanır.</li>
 * </ul>
 */
public class CampaignActionCoordinator {

  public static final String ACTION_IN_PROGRESS_MESSAGE_KEY = "campaign.action.inProgress";

  static final long DEFAULT_LOCK_WAIT_MS = 10_000L;
  static final long LOCK_WAIT_WARN_MS = 2_000L;
  static final long ACTION_DURATION_WARN_MS = 5_000L;
  private static final int ASYNC_THREADS = 8;
  private static final int ASYNC_QUEUE_CAPACITY = 1_000;

  private static final Logger LOG = LoggerFactory.getLogger(CampaignActionCoordinator.class);

  private final ConcurrentMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
  private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
  /** Campaign başına sıralı kuyruk; işler ortak havuzda, aynı campaign'inkiler tek tek çalışır. */
  private final ConcurrentMap<Long, Executor> lanes = new ConcurrentHashMap<>();
  private final long lockWaitMs;
  private final ExecutorService asyncExecutor;

  public CampaignActionCoordinator() {
    this(DEFAULT_LOCK_WAIT_MS, createAsyncExecutor());
  }

  public CampaignActionCoordinator(long lockWaitMs, ExecutorService asyncExecutor) {
    this.lockWaitMs = lockWaitMs;
    this.asyncExecutor = asyncExecutor;
  }

  /**
   * Aksiyonu aynı campaign'in diğer aksiyonlarıyla sıralı, farklı campaign'lerle paralel çalıştırır.
   * Aynı thread içinden iç içe çağrı kilitlenmez.
   *
   * @throws CampaignIllegalStateException kilit {@code lockWaitMs} içinde alınamazsa
   */
  public void runExclusive(Long campaignId, String actionName, Runnable action) {
    runExclusive(campaignId, actionName, action, lockWaitMs);
  }

  /**
   * {@link #runExclusive(Long, String, Runnable)} ile aynı; kilit için en fazla {@code lockWaitMs}
   * bekler. Sistem içi takip aksiyonları (RESTART sonrası START) içindir.
   */
  public void runExclusive(Long campaignId, String actionName, Runnable action, long lockWaitMs) {
    ReentrantLock lock = locks.computeIfAbsent(campaignId, id -> new ReentrantLock());
    long waitStart = System.nanoTime();
    boolean acquired;
    try {
      acquired = lock.tryLock(lockWaitMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CampaignIllegalStateException(ACTION_IN_PROGRESS_MESSAGE_KEY);
    }
    long waitedMs = elapsedMs(waitStart);
    if (!acquired) {
      LOG.warn("[CAMPAIGN-ACTION] {} for campaign {} rejected: another action of the same campaign is still in progress after {} ms",
          actionName, campaignId, waitedMs);
      throw new CampaignIllegalStateException(ACTION_IN_PROGRESS_MESSAGE_KEY);
    }
    if (waitedMs >= LOCK_WAIT_WARN_MS) {
      LOG.warn("[CAMPAIGN-ACTION] {} for campaign {} waited {} ms for the campaign action lock", actionName, campaignId, waitedMs);
    }
    long actionStart = System.nanoTime();
    try {
      action.run();
    } finally {
      lock.unlock();
      long durationMs = elapsedMs(actionStart);
      if (durationMs >= ACTION_DURATION_WARN_MS) {
        LOG.warn("[CAMPAIGN-ACTION] {} for campaign {} took {} ms", actionName, campaignId, durationMs);
      }
    }
  }

  /**
   * Aksiyonu asenkron çalıştırmak üzere kuyruğa bırakır; çağıran thread beklemez.
   *
   * @return iş kuyruğa alındıysa {@code true}; aynı campaign + aksiyon zaten bekliyorsa veya kuyruk
   *         doluysa {@code false}
   */
  public boolean submit(Long campaignId, String actionName, Runnable action) {
    String key = campaignId + ":" + actionName;
    if (!inFlight.add(key)) {
      LOG.debug("[CAMPAIGN-ACTION] {} for campaign {} is already queued, skipped", actionName, campaignId);
      return false;
    }
    try {
      lanes.computeIfAbsent(campaignId, id -> MoreExecutors.newSequentialExecutor(asyncExecutor))
          .execute(() -> runSubmitted(key, campaignId, actionName, action));
      return true;
    } catch (RejectedExecutionException e) {
      inFlight.remove(key);
      LOG.warn("[CAMPAIGN-ACTION] {} for campaign {} could not be queued: {}", actionName, campaignId, e.toString());
      return false;
    }
  }

  /**
   * Campaign'in kuyrukta bekleyen veya çalışan (asenkron ya da senkron) aksiyonu var mı. Scheduler,
   * aksiyon bitmeden bayat state ile yeni karar vermemek için kullanır.
   */
  public boolean hasPendingAction(Long campaignId) {
    ReentrantLock lock = locks.get(campaignId);
    if (lock != null && lock.isLocked()) {
      return true;
    }
    String prefix = campaignId + ":";
    return inFlight.stream().anyMatch(key -> key.startsWith(prefix));
  }

  public void shutdown() {
    asyncExecutor.shutdown();
  }

  private void runSubmitted(String key, Long campaignId, String actionName, Runnable action) {
    try {
      runExclusive(campaignId, actionName, action);
    } catch (CampaignIllegalStateException e) {
      if (!ACTION_IN_PROGRESS_MESSAGE_KEY.equals(e.getMessage())) {
        LOG.warn("[CAMPAIGN-ACTION] async {} for campaign {} was not applied: {}", actionName, campaignId, e.toString());
      }
    } catch (RuntimeException e) {
      LOG.error("[CAMPAIGN-ACTION] async " + actionName + " for campaign " + campaignId + " failed", e);
    } finally {
      inFlight.remove(key);
    }
  }

  private static ExecutorService createAsyncExecutor() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(ASYNC_THREADS, ASYNC_THREADS, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(ASYNC_QUEUE_CAPACITY),
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("campaign-action-async-%d").build());
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  private static long elapsedMs(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }
}
