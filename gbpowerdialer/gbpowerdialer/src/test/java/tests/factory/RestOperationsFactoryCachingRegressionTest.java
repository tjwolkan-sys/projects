package tests.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import com.powerdialer.factory.RestOperationsFactory;

/**
 * Task 325469: LSC hot path'inde her cagri basina yeni RestTemplate kurulumu
 * Tomcat classloader kilidinde konvoy yaratip CPU kilitlenmesine yol acti.
 * Bu test factory'nin instance'lari timeout kombinasyonu basina bir kez
 * yaratip paylastigini kilitler.
 */
@RunWith(MockitoJUnitRunner.class)
public class RestOperationsFactoryCachingRegressionTest {

  @Mock
  private RestOperations defaultRestTemplate;

  @InjectMocks
  private RestOperationsFactory factory;

  @Test
  public void sameTimeoutReturnsSameInstance() {
    assertSame(factory.build(5000), factory.build(5000));
    assertSame(factory.build(3000, 4000), factory.build(3000, 4000));
  }

  @Test
  public void differentTimeoutsReturnDifferentInstances() {
    assertNotSame(factory.build(5000), factory.build(7000));
  }

  @Test
  public void negativeSingleTimeoutReturnsSharedTimeoutlessInstanceNotDefaultBean() {
    RestOperations first = factory.build(-1);
    RestOperations second = factory.build(-1);
    assertSame(first, second);
    assertNotSame(defaultRestTemplate, first);
    assertTrue(first instanceof RestTemplate);
  }

  @Test
  public void bothNegativeTimeoutsStillReturnDefaultBean() {
    assertSame(defaultRestTemplate, factory.build(-1, -1));
  }

  @Test
  public void concurrentBuildsForSameTimeoutCreateSingleInstance() throws Exception {
    int threads = 16;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    Map<RestOperations, Boolean> distinct = new ConcurrentHashMap<>();
    for (int i = 0; i < threads; i++) {
      pool.execute(() -> {
        ready.countDown();
        try {
          go.await();
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        }
        distinct.put(factory.build(5000), Boolean.TRUE);
      });
    }
    assertTrue(ready.await(5, TimeUnit.SECONDS));
    go.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    assertEquals("Es zamanli build ayni timeout icin tek instance uretmeli", 1, distinct.size());
  }
}
