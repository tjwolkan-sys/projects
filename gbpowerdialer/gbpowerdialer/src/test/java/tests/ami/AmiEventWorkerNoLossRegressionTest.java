package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.model.Server;
import com.powerdialer.util.LoggerDecorator;

/**
 * Invariant #2 (hiçbir task/DB yazımı kaybolmaz):
 *
 * <ul>
 *   <li><b>blocking-put rejected handler:</b> kuyruk dolu olsa bile submit
 *       throw ETMEZ ve task DÜŞMEZ; submitter bloklanır, yer açılınca task
 *       enqueue edilip çalışır.</li>
 *   <li><b>drain-on-shutdown:</b> yavaş tek-thread worker'a N task verilip
 *       {@code logoff()} (shutdown + awaitTermination) çağrılınca kuyrukta
 *       bekleyen TÜM task'lar çalışır ({@code shutdownNow} kullanılmaz).</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AmiEventWorkerNoLossRegressionTest {

  @Mock
  private LoggerDecorator loggerDecorator;
  @Mock
  private ManagerConnectionComposition connectionComposition;

  private ManagerConnectionDecorator decorator;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);

    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    decorator.setServer(server());
  }

  @Test
  public void blockingPutRejectedHandlerShouldBlockAndEnqueueInsteadOfDroppingOrThrowing() throws Exception {
    RejectedExecutionHandler handler = invokeBlockingPutRejectedHandler();
    // core=max=1, kapasite=1 => 1 task çalışırken 1 task kuyrukta; 3.sü reject edilir.
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(1), handler);
    CountDownLatch blockFirstTask = new CountDownLatch(1);
    AtomicInteger completed = new AtomicInteger(0);
    try {
      // 1) çalışan task: worker thread'i bloke tutar
      executor.execute(() -> {
        await(blockFirstTask);
        completed.incrementAndGet();
      });
      // 2) kuyruğa girer (kapasite 1)
      executor.execute(completed::incrementAndGet);

      // 3) kuyruk dolu => rejected handler devreye girer. Submitter'ı ayrı thread'de
      // çalıştırıyoruz; blocking-put olduğu için yer açılana kadar BLOKLAR (drop/throw etmez).
      AtomicInteger thirdState = new AtomicInteger(0); // 0=running,1=ok,2=threw
      Thread submitter = new Thread(() -> {
        try {
          executor.execute(completed::incrementAndGet);
          thirdState.set(1);
        } catch (RuntimeException e) {
          thirdState.set(2);
        }
      });
      submitter.start();

      // Submitter blocking-put'ta bekliyor olmalı (henüz tamamlanmadı, throw etmedi).
      Thread.sleep(200);
      assertThat("blocking-put submitter yer açılana kadar bloklanmalı (throw/return etmemeli)",
          thirdState.get(), is(0));
      assertThat("hiçbir task düşmemeli; henüz hiçbiri tamamlanmadı (ilk task bloke)",
          completed.get(), is(0));

      // İlk task'ı serbest bırak => kuyruktan ardışık olarak hepsi koşar, blocking-put yer bulur.
      blockFirstTask.countDown();
      submitter.join(2000);
      assertThat("blocking-put submitter throw etmeden tamamlanmalı", thirdState.get(), is(1));

      executor.shutdown();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
      assertThat("3 task da çalışmalı, hiçbiri düşmemeli", completed.get(), is(3));
    } finally {
      if (!executor.isShutdown()) {
        executor.shutdownNow();
      }
      blockFirstTask.countDown();
    }
  }

  @Test
  public void logoffShouldDrainQueuedWorkerTasksWithoutDropping() throws Exception {
    // Gerçek tek-thread bounded worker enjekte edilir (production tipiyle aynı davranış).
    ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(100), invokeBlockingPutRejectedHandler());
    ReflectionTestUtils.invokeMethod(decorator, "setEventWorker", worker);

    int taskCount = 50;
    AtomicInteger ran = new AtomicInteger(0);
    CountDownLatch gate = new CountDownLatch(1);
    // İlk task gate'te bekler; böylece logoff çağrıldığında kuyrukta bekleyen task'lar olur.
    worker.execute(() -> {
      await(gate);
      ran.incrementAndGet();
    });
    for (int i = 1; i < taskCount; i++) {
      worker.execute(ran::incrementAndGet);
    }

    // Drain-on-shutdown: logoff shutdown() + awaitTermination yapar (shutdownNow DEĞİL).
    // Gate'i açıp logoff'u (package-private => reflection) çağırıyoruz; tüm task'lar drain edilmeli.
    gate.countDown();
    ReflectionTestUtils.invokeMethod(decorator, "logoff");

    assertThat("logoff sonrası worker terminate olmalı", worker.isTerminated(), is(true));
    assertThat("drain: kuyruktaki TÜM task'lar çalışmalı, hiçbiri düşmemeli", ran.get(), is(taskCount));
  }

  /** Static package-private factory'yi reflection ile çağırır (tests/ami farklı pakette). */
  private static RejectedExecutionHandler invokeBlockingPutRejectedHandler() throws Exception {
    Method method = ManagerConnectionDecorator.class.getDeclaredMethod("blockingPutRejectedHandler");
    method.setAccessible(true);
    return (RejectedExecutionHandler) method.invoke(null);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail("interrupted while awaiting latch");
    }
  }

  private Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    return server;
  }
}
