package tests.util;

import com.powerdialer.util.StripedExecutor;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * SORUN 4 (bellek statu bayatligi) Fix-1 davranis kilidi: ayni agent'in (ayni key) statu
 * mesajlari ASLA ters sirada islenemez; farkli agent'lar paralel kalir. Eski 4-8 thread'li
 * yonlendirmesiz havuzda ilk test [2,1] uretebilirdi (yaris); serit modelinde imkansizdir.
 */
public class StripedExecutorFifoOrderingTest {

  @Test
  public void sameKey_tasksRunInSubmissionOrder_evenWhenFirstTaskIsSlow() throws Exception {
    StripedExecutor executor = new StripedExecutor(4, "fifo-test-");
    try {
      List<Integer> order = Collections.synchronizedList(new ArrayList<Integer>());
      CountDownLatch firstTaskGate = new CountDownLatch(1);
      CountDownLatch bothDone = new CountDownLatch(2);

      executor.execute(42L, () -> {
        try {
          firstTaskGate.await(2, TimeUnit.SECONDS); // ilk gorev yavas (gecikmis mesaj simulasyonu)
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        }
        order.add(1);
        bothDone.countDown();
      });
      executor.execute(42L, () -> {
        order.add(2);
        bothDone.countDown();
      });

      firstTaskGate.countDown();
      assertTrue("gorevler zamaninda bitmedi", bothDone.await(5, TimeUnit.SECONDS));
      assertEquals(Arrays.asList(1, 2), order);
    } finally {
      executor.shutdown();
    }
  }

  @Test
  public void differentKeys_runOnIndependentStripes() throws Exception {
    StripedExecutor executor = new StripedExecutor(4, "parallel-test-");
    try {
      CountDownLatch key1Done = new CountDownLatch(1);
      CountDownLatch key0Done = new CountDownLatch(1);

      // key0 gorevi, key1 gorevinin bitmesini bekler: tek global thread olsaydi key1 hic
      // calisamaz ve bu test timeout ile duserdi. Seritler bagimsiz oldugu icin tamamlanir.
      executor.execute(0L, () -> {
        try {
          if (key1Done.await(5, TimeUnit.SECONDS)) {
            key0Done.countDown();
          }
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        }
      });
      executor.execute(1L, key1Done::countDown);

      assertTrue("farkli key'ler paralel calismali", key0Done.await(5, TimeUnit.SECONDS));
    } finally {
      executor.shutdown();
    }
  }

  @Test
  public void sameKey_alwaysRunsOnSameThread() throws Exception {
    StripedExecutor executor = new StripedExecutor(4, "affinity-test-");
    try {
      List<String> threadNames = Collections.synchronizedList(new ArrayList<String>());
      CountDownLatch done = new CountDownLatch(3);
      for (int i = 0; i < 3; i++) {
        executor.execute(99L, () -> {
          threadNames.add(Thread.currentThread().getName());
          done.countDown();
        });
      }
      assertTrue(done.await(5, TimeUnit.SECONDS));
      assertEquals(3, threadNames.size());
      assertEquals("ayni key hep ayni thread'de islenmeli", 1, new HashSet<>(threadNames).size());
    } finally {
      executor.shutdown();
    }
  }

  @Test
  public void negativeKey_isMappedToValidStripe() throws Exception {
    StripedExecutor executor = new StripedExecutor(4, "negative-test-");
    try {
      CountDownLatch done = new CountDownLatch(1);
      executor.execute(-7L, done::countDown);
      assertTrue("negatif key gecerli bir serite eslenmeli", done.await(5, TimeUnit.SECONDS));
    } finally {
      executor.shutdown();
    }
  }

}
