package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.powerdialer.engine.dialer.CallOriginator;

/**
 * Task 325469: newCachedThreadPool sinirsiz buyuyerek (dump: 113 thread,
 * sayac #2138) CPU kilitlenmesini besledi. Bu test lsc-executor havuzunun
 * ust sinirini, mevcut cached semantigini (core 0 / 60 s idle /
 * SynchronousQueue) ve doygunlukta caller-runs geri basincini kilitler.
 */
public class CallOriginatorLscExecutorBoundRegressionTest {

  @Test
  public void poolKeepsCachedSemanticsButIsBoundedByMaxLines() {
    ExecutorService executor = CallOriginator.createLscExecutor("10.0.0.1", Integer.valueOf(50));
    ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
    assertEquals(0, pool.getCorePoolSize());
    assertEquals(50, pool.getMaximumPoolSize());
    assertEquals(60L, pool.getKeepAliveTime(TimeUnit.SECONDS));
    assertTrue(pool.getQueue() instanceof SynchronousQueue);
    assertTrue(pool.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy);
    pool.shutdown();
  }

  @Test
  public void nonPositiveOrNullMaxLinesFallsBackToSingleThreadBound() {
    ThreadPoolExecutor zero = (ThreadPoolExecutor) CallOriginator.createLscExecutor("10.0.0.1", Integer.valueOf(0));
    ThreadPoolExecutor nullValue = (ThreadPoolExecutor) CallOriginator.createLscExecutor("10.0.0.1", null);
    assertEquals(1, zero.getMaximumPoolSize());
    assertEquals(1, nullValue.getMaximumPoolSize());
    zero.shutdown();
    nullValue.shutdown();
  }

  @Test
  public void workerThreadNameContainsServerIp() throws Exception {
    ExecutorService executor = CallOriginator.createLscExecutor("10.1.2.3", Integer.valueOf(2));
    AtomicReference<String> threadName = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    executor.execute(() -> {
      threadName.set(Thread.currentThread().getName());
      done.countDown();
    });
    assertTrue(done.await(5, TimeUnit.SECONDS));
    assertTrue("Thread adi server IP icermeli: " + threadName.get(),
        threadName.get().startsWith("lsc-executor-10.1.2.3-"));
    executor.shutdown();
  }

  @Test
  public void saturatedPoolRunsTaskOnCallerThreadInsteadOfRejectingOrGrowing() throws Exception {
    ExecutorService executor = CallOriginator.createLscExecutor("10.0.0.1", Integer.valueOf(2));
    ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
    CountDownLatch blocker = new CountDownLatch(1);
    CountDownLatch twoRunning = new CountDownLatch(2);
    Runnable blocking = () -> {
      twoRunning.countDown();
      try {
        blocker.await();
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    };
    executor.execute(blocking);
    executor.execute(blocking);
    assertTrue(twoRunning.await(5, TimeUnit.SECONDS));

    AtomicReference<String> thirdTaskThread = new AtomicReference<>();
    executor.execute(() -> thirdTaskThread.set(Thread.currentThread().getName()));

    assertEquals("Doygunlukta task caller thread'de kosmali (CallerRunsPolicy)",
        Thread.currentThread().getName(), thirdTaskThread.get());
    assertTrue("Havuz ust siniri asilmamali", pool.getLargestPoolSize() <= 2);
    blocker.countDown();
    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
  }
}
