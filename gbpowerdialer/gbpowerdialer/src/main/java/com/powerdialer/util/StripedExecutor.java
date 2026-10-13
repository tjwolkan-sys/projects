package com.powerdialer.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ayni routing key'e ait gorevleri HER ZAMAN ayni tek-thread serit uzerinde FIFO calistirir;
 * farkli key'ler farkli seritlerde paralel kalir.
 *
 * <p>Amac (SORUN 4 - bellek statu bayatligi): ayni agent'in art arda statu mesajlarinin
 * (orn. Available -> Mola) cok-thread'li havuzda TERS SIRADA islenmesini imkansiz kilmak.
 * Eski userEventExecutor (4-8 thread, yonlendirmesiz) bu yarisi aciyordu: gecikmis eski mesaj
 * yeni mesaji ezince bellek "Available" derken agent Mola'da kaliyor, bir sonraki statu
 * degisimine kadar duzelmiyordu (STATUS-CHECK memAvail=true/redisAvail=false, staleMs~15dk).
 *
 * <p>Serit kuyruklari sinirsizdir (bilincli): sinirli kuyruk + CallerRunsPolicy sira garantisini
 * bozar (caller, kuyrukta bekleyen daha eski gorevlerden ONCE calisirdi). Handler'lar kisa
 * surdugu ve seritler paralel oldugu icin birikme riski pratikte yoktur.
 *
 * <p>Sinif bilincli olarak final degildir: birim testlerde mock'lanabilir kalmalidir.
 */
public class StripedExecutor {

  private final ExecutorService[] stripes;

  public StripedExecutor(int stripeCount, String threadNamePrefix) {
    if (stripeCount <= 0) {
      throw new IllegalArgumentException("stripeCount must be > 0");
    }
    stripes = new ExecutorService[stripeCount];
    for (int i = 0; i < stripeCount; i++) {
      // Repo standardi ThreadFactoryBuilder (diger executor'larla ayni desen); serit basina tek
      // thread oldugu icin %d yerine sabit serit indeksi kullanilir.
      stripes[i] = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
          .setDaemon(true)
          .setNameFormat(threadNamePrefix + i)
          .build());
    }
  }

  /**
   * Ayni key -> ayni serit -> FIFO. Negatif key'ler de gecerli bir serite esler.
   */
  public void execute(long key, Runnable task) {
    int index = (int) (((key % stripes.length) + stripes.length) % stripes.length);
    stripes[index].execute(task);
  }

  public void shutdown() {
    for (ExecutorService stripe : stripes) {
      stripe.shutdown();
    }
  }

}
