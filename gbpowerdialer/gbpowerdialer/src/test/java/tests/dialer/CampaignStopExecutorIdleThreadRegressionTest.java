package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.powerdialer.engine.dialer.CampaignContext;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.Test;

/**
 * FIX-333607: her {@link CampaignContext} kendi stop executor'ını açıyor ve hiç kapatmıyordu.
 * {@code Executors.newSingleThreadExecutor} çekirdek thread'ini sonsuza dek tuttuğu için her
 * stop/restart'ta bir daemon thread sızıyordu. Executor tek thread'li ve FIFO kalır; boşta kalan
 * thread kendiliğinden kapanır, sonradan gelen stop işi yine kabul edilir.
 */
public class CampaignStopExecutorIdleThreadRegressionTest {

  @Test
  public void stopExecutorIsSingleThreadedAndReleasesIdleThread() {
    ExecutorService executor = CampaignContext.createCampaignStopExecutor();
    try {
      assertTrue(executor instanceof ThreadPoolExecutor);
      ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
      assertEquals(1, pool.getCorePoolSize());
      assertEquals(1, pool.getMaximumPoolSize());
      assertTrue("Boşta kalan stop thread'i kapanmalı", pool.allowsCoreThreadTimeOut());
    } finally {
      executor.shutdownNow();
    }
  }
}
