package tests.dialer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.asteriskjava.manager.ManagerConnection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Server;
import com.powerdialer.service.CampaignResultService;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-GLB90048674 (Faz 1B — Fix B): reconnect/decorator-replace yolu.
 *
 * <p>{@code CallOriginator.setNewConnectionDecoratorIfExists()} ESKİ decorator referansını
 * yenisiyle değiştirirken, eski decorator'un event worker'ını DRAIN edip kapatmalı
 * ({@code shutdown()} + {@code awaitTermination}). Aksi halde eski daemon worker thread'i
 * sızar ve kuyruğunda bekleyen DB/CDR yazımları terk edilir (invariant #2 — kayıpsızlık ihlali).
 *
 * <ul>
 *   <li><b>Davranış (kayıpsızlık):</b> gerçek eski decorator'a kontrollü bir worker enjekte
 *       edilir; replace tetiklenince worker terminate olur ve kuyrukta bekleyen task çalışır
 *       (drain — {@code shutdownNow} değil).</li>
 *   <li><b>AMI yan etkisi yok:</b> replace yolu yalnızca worker'ı drain eder; eski
 *       decorator'un {@code managerConnection.logoff()}'u (AMI logoff) ÇAĞRILMAZ.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.class)
public class DecoratorReplaceWorkerDrainRegressionTest {

  private static final String SERVER_IP = "10.0.0.7";

  @Mock
  private LoggerDecorator loggerDecorator;
  @Mock
  private ManagerConnectionComposition connectionComposition;
  @Mock
  private DialerFacade dialerFacade;
  @Mock
  private CallerIdManager callerIdManager;
  @Mock
  private CallCleaner callCleaner;
  @Mock
  private CampaignResultService campaignResultService;
  @Mock
  private ManagerConnectionDecorator newDecorator;
  @Mock
  private ManagerConnection oldManagerConnection;

  private ManagerConnectionDecorator oldDecorator;
  private CallOriginator callOriginator;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(org.mockito.ArgumentMatchers.<Class<?>>any())).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);

    Server server = new Server();
    server.setIp(SERVER_IP);

    oldDecorator = new ManagerConnectionDecorator(SERVER_IP, 5038, "user", "password", connectionComposition);
    oldDecorator.setServer(server);
    // Eski decorator'a AMI (event) bağlantısı bağla; replace yolunun AMI logoff'u ÇAĞIRMADIĞINI ispatlamak için.
    // FIX-GLB90048674 (Faz 2): tek `managerConnection` alanı event/command olarak ikiye bölündü;
    // primary (event) bağlantısı enjekte edilir — replace yolu hiçbir bağlantıyı logoff etmemeli.
    ReflectionTestUtils.setField(oldDecorator, "eventConnection", oldManagerConnection);

    // composition.getOpenedConnections() aynı IP altında FARKLI (yeni) decorator döndürür => replace tetiklenir.
    Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();
    openedConnections.put(SERVER_IP, newDecorator);
    when(connectionComposition.getOpenedConnections()).thenReturn(openedConnections);

    callOriginator = new CallOriginator(oldDecorator, dialerFacade, callerIdManager, callCleaner, campaignResultService);
  }

  @Test
  public void decoratorReplaceShouldDrainOldWorkerAndRunPendingTasksWithoutDropping() throws Exception {
    // Gerçek tek-thread bounded worker (production tipiyle aynı): bir task gate'te bekler,
    // kuyrukta başka task'lar birikir; replace drain'i hepsini çalıştırmalı.
    ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(100), invokeBlockingPutRejectedHandler());
    ReflectionTestUtils.invokeMethod(oldDecorator, "setEventWorker", worker);

    int taskCount = 25;
    AtomicInteger ran = new AtomicInteger(0);
    CountDownLatch gate = new CountDownLatch(1);
    worker.execute(() -> {
      await(gate);
      ran.incrementAndGet();
    });
    for (int i = 1; i < taskCount; i++) {
      worker.execute(ran::incrementAndGet);
    }

    // Gate açılır; replace tetiklenince drain (shutdown + awaitTermination) tüm task'ları bitirmeli.
    gate.countDown();
    ReflectionTestUtils.invokeMethod(callOriginator, "setNewConnectionDecoratorIfExists");

    assertThat("eski worker drain sonrası terminate olmalı", worker.isTerminated(), is(true));
    assertThat("drain: eski kuyruktaki TÜM task'lar çalışmalı, hiçbiri düşmemeli", ran.get(), is(taskCount));
    // Referans yeni decorator'a geçmiş olmalı.
    assertThat(callOriginator.getConnectionDecorator(), is((ManagerConnectionDecorator) newDecorator));
    // Replace yolu AMI logoff'u ÇAĞIRMAMALI (yalnızca worker drain edilir).
    verify(oldManagerConnection, never()).logoff();
  }

  /**
   * FIX-GLB90048674 (Faz 1B — Fix #2): replace/recovery drain timeout'u, shutdown drain
   * timeout'undan KISA olmalı; aksi halde CPS thread'indeki sıcak recovery yolu shutdown
   * süresince (30s) bloklanırdı. Sabitler private static final olduğu için reflection ile
   * okunur; testin amacı iki timeout'un değerini ve ilişkisini (replace < shutdown) kilitlemektir.
   */
  @Test
  public void replaceDrainTimeoutShouldBeShorterThanShutdownDrainTimeout() throws Exception {
    long shutdownTimeout = readLongConstant("EVENT_WORKER_SHUTDOWN_TIMEOUT_SECONDS");
    long replaceTimeout = readLongConstant("EVENT_WORKER_REPLACE_DRAIN_TIMEOUT_SECONDS");

    assertThat("shutdown (preDestroy/logoff) drain timeout'u 30s olmalı", shutdownTimeout, is(30L));
    assertThat("replace/recovery drain timeout'u 5s olmalı", replaceTimeout, is(5L));
    assertThat("replace drain timeout'u shutdown timeout'undan kısa olmalı",
        replaceTimeout < shutdownTimeout, is(true));
  }

  private static long readLongConstant(String fieldName) throws Exception {
    java.lang.reflect.Field field = ManagerConnectionDecorator.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.getLong(null);
  }

  /** Static package-private factory'yi reflection ile çağırır (tests/dialer farklı pakette). */
  private static java.util.concurrent.RejectedExecutionHandler invokeBlockingPutRejectedHandler() throws Exception {
    java.lang.reflect.Method method = ManagerConnectionDecorator.class.getDeclaredMethod("blockingPutRejectedHandler");
    method.setAccessible(true);
    return (java.util.concurrent.RejectedExecutionHandler) method.invoke(null);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail("interrupted while awaiting latch");
    }
  }
}
