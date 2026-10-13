# FIX-325469: LSC RestTemplate + Sınırsız Executor + Log Kaynaklı CPU Kilitlenmesi

- İş Etiketi: fix
- Öncelik: hotfix
- Task: 325469
- Branch: `fix/325469-lsc-log-cpu-fix` (local `develop` 981fea94 üzerinden; PR öncesi `origin/develop` sync gerekli — branch alınırken VPN dışı olunduğu için fetch yapılamadı)
- Tarih: 28.07.2026
- Durum: Onaylandı — açık sorular 28.07.2026'da cevaplandı ve plana işlendi (bkz. `Açık Sorular`); uygulama sürüyor

## Bağlam

Üretimde iki CPU kilitlenme olayı yaşandı:

- 22.07.2026 (Turkcell seti, `GAVXBSNTPDP01`): `kill -3` thread dump'ında 397 thread'in 188'i BLOCKED. 78 thread Tomcat `WebappClassLoaderBase.loadClass` kilidinde, 84 thread logback `CachingDateFormatter` kilidinde (FILE + CONSOLE iki senkron appender). 113 canlı `lsc-executor` thread'i; JVM'in 24 dakikalık ömründe toplam thread sayacı #1938'e ulaştı. Restart ile geçici çözüldü.
- 28.07.2026 12:30–13:05 (non-Turkcell seti): Elastic loglarıyla aynı imza doğrulandı. `lsc-executor` pool sayaçları 2138'e ulaşmış (sürekli thread yarat/öldür churn'ü), 12:52–13:01 arasında log üretimi 17.000 satır/dk'dan 19 satır/dk'ya çöktü, scheduler tick'i 8 dakika atmadı, 13:01:30 restart ile düzeldi.

Bekleyen/kilidi tutan tüm thread'lerin stack'i aynı: `CallOriginator.applyLSCAndOriginate` → `LSCEngine.applyLscsToCall` → `getRestClientForLSC` → `RestOperationsFactory.build` → `new RestTemplate()` → Spring `Jackson2ObjectMapperBuilder.registerWellKnownModulesIfAvailable` → `Class.forName` → Tomcat classloader kilidi. Classpath'te olmayan Jackson modülleri (`jackson-datatype-jdk8`, `jsr310`, `joda`, `kotlin`) her kurulumda yeniden arandığı ve Tomcat olmayan class'ı cache'lemediği için her `new RestTemplate()` tam JAR taraması + `ClassNotFoundException` üretir; tüm `lsc-executor` thread'leri aynı kilitte konvoya girer.

## Mevcut Davranış

1. `LSCEngine.getRestClientForLSC` (`gbpowerdialer/src/main/java/com/powerdialer/engine/LSCEngine.java:524-527`) her LSC uygulamasında (çağrı × LSC başına) `restOperationsFactory.build(...)` çağırır.
2. `RestOperationsFactory` (`gbpowerdialer/src/main/java/com/powerdialer/factory/RestOperationsFactory.java`) her çağrıda **yeni** `RestTemplate` yaratır:
   - `build(int timeout)` timeout < 0 iken converter-tam `new RestTemplate()` (timeout'suz),
   - diğer durumlarda `buildParameterized` ile `new RestTemplate(SimpleClientHttpRequestFactory)`.
   - Yalnız `build(int, int)` overload'ı iki parametre de negatifse `defaultRestTemplate` bean'ini döner. (`defaultRestTemplate` = `applicationContext.xml:79`, 300 ms read/connect timeout'lu — bu nedenle `build(-1)`'i ona yönlendirmek DAVRANIŞ DEĞİŞTİRİR ve yapılmayacaktır.)
   - Diğer üretim kullanıcısı: `StatsRestEngine.processRequest` → `build(10000, 10000)` (scheduler akışı; aynı israfın düşük frekanslısı).
3. `CallOriginator.createExecutors` (`gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallOriginator.java:141-150`) `Executors.newCachedThreadPool` ile **sınırsız** `lsc-executor` havuzu kurar; her AMI server bağlantısının `CallOriginator`'ı kendi havuzunu açar. LSC yavaşlayınca havuz sınırsız büyür (dump: 113 eşzamanlı; Elastic: sayaç 2138).
4. LSC hot path'i çağrı başına 6+ INFO satırı üretir (`LSCEngine.java:216, 257, 272-281, 291, 295, 302, 306, 569, 578`); payload/header içerikleri dahil. `LoggerDecorator.info/warn/error` (`gbpowerdialer/src/main/java/com/powerdialer/util/LoggerDecorator.java:63-101`) argüman verilen her çağrıda `ActionLogService.addActionLog` üzerinden **senkron DB insert** yapar (`ActionLogService.java:68-71`). logback'te FILE + CONSOLE iki senkron appender vardır (`gbpowerdialer/src/main/resources/logback.xml`); log hacmi baseline'da bile ~280 satır/sn'dir.

## Beklenen Davranış

1. `RestOperations` instance'ları timeout kombinasyonu başına **bir kez** yaratılır ve paylaşılır (RestTemplate thread-safe). Timeout semantiği ve converter seti birebir korunur: `build(-1)` yine timeout'suz, default-converter'lı RestTemplate döner (ama cache'lenmiş tek instance); `build(-1,-1)` yine `defaultRestTemplate` döner. Classloader kilidi ve converter kurulumu hot path'ten tamamen çıkar.
2. `lsc-executor` havuzu server başına üst sınırlıdır (`max(1, server.maxLines)`); doygunlukta `CallerRunsPolicy` ile doğal geri basınç uygular (çağrı kaybolmaz, originate tick'i yavaşlar). Thread adı teşhis için server IP içerir: `lsc-executor-<ip>-%d`.
3. **(Açık Soru 1 cevabına göre revize)** Operasyon ActionLog ekranını aktif kullandığı için **ActionLog kaydı korunur**. `LoggerDecorator`'a `audit(Object, Object...)` eklenir: argümanlı çağrıda ActionLog'u bugünkü gibi **INFO seviyesiyle DB'ye kaydeder** (UI/ekran davranışı birebir aynı), slf4j'ye ise **debug** seviyesinde yazar. Root logger INFO olduğundan bu satırlar üretimde logback FILE/CONSOLE appender kilidine hiç girmez; ihtiyaç halinde `LSCEngine` logger'ı DEBUG yapılarak dosya logunda da açılır. LSC rutin akış satırları (payload `LSC URL ...`, `[LSC-AUTH] READ`, attempt=0 `[LSC-RETRY]`, ham `respond:`, LSC başına `LSC result is`) `LOG.info` → `LOG.audit` olur; attempt>0 `[LSC-RETRY]` INFO kalır (gerçek retry anomali işaretidir). Özet satırı (`LSC(s) result is ... for Call ...`, satır 216) ve tüm warn/error'lar aynen **kalır**.
4. A4 kalemleri bu branch'te kod değişikliği içermez; takip işi olarak kayıt altına alınır (bkz. Görev 4).

## Etkilenen Kod Alanları

- `gbpowerdialer/src/main/java/com/powerdialer/factory/RestOperationsFactory.java` (A1 — cache)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallOriginator.java:141-150` (A2 — bounded executor)
- `gbpowerdialer/src/main/java/com/powerdialer/util/LoggerDecorator.java` (A3 — `audit()` ekleme; ActionLog korunur)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/LSCEngine.java:257, 272-281, 291, 295, 302, 306, 569, 578` (A3 — rutin satırlar `info` → `audit`)
- Yeni testler: `gbpowerdialer/src/test/java/tests/factory/RestOperationsFactoryCachingRegressionTest.java`, `tests/dialer/CallOriginatorLscExecutorBoundRegressionTest.java`, `tests/util/LoggerDecoratorAuditRegressionTest.java`; mevcut `gbpowerdialer/src/test/java/com/powerdialer/engine/LSCEngineTest.java` genişletilir.

## Riskler

- **A1 — cache anahtarı birikimi:** Farklı timeout değeri kadar instance map'te kalır (LSC timeout'ları sınırlı sayıda; pratikte birkaç giriş). LSC timeout güncellenirse yeni anahtar doğal oluşur; invalidation gerekmez. Eski instance'lar GC edilmez ama sabit/küçük sayıdadır — kabul edilir.
- **A1 — davranış koruması:** `build(-1)` `defaultRestTemplate`'e YÖNLENDİRİLMEZ (300 ms timeout sürprizi yaratırdı). Timeout'suz paylaşılan instance ile mevcut semantik birebir korunur.
- **A2 — doygunluk davranış farkı:** Bugün doygunlukta sınırsız thread açılıyordu; yeni davranışta `CallerRunsPolicy` ile LSC task'ı originate tick thread'inde koşar ve tick uzar. `availableOriginateSlots` (`CallOriginator.java:209-219`) in-flight LSC'yi bütçeden düştüğü için normal işleyişte doygunluk beklenmez; policy güvenlik ağıdır.
- **A2 — thread adı değişikliği:** `lsc-executor-%d` → `lsc-executor-<ip>-%d`. Log contract'ı değildir; Elastic aramaları `lsc-executor` prefix'i ile çalışmaya devam eder.
- **A3 — operasyonel görünürlük:** ActionLog ekranı birebir korunur (kullanıcı kararı: operasyon inceliyor). Değişen tek görünürlük: rutin LSC satırları gbpowerdialer.log/console'da üretimde görünmez (gerekirse `LSCEngine` logger'ı DEBUG yapılarak açılır). Senkron ActionLog DB insert maliyeti bilinçli korunur; async/batch iyileştirmesi A4-4 takip işidir.
- **Genel:** Redis channel, AMI event, result code, campaign state transition contract'larına dokunulmaz. Yeni kütüphane eklenmez (yalnız JDK + mevcut Spring/Guava). Java 8 source/target korunur.

## Performans Etkisi

Sonuç: **Hot path, ek maliyet yok** (net büyük kazanç).

- Frekans: call başına (LSC-enabled originate, set başına tepe ~5–15 çağrı/sn) × LSC sayısı.
- A1 ekler: çağrı başına 1 `Long` boxing + 1 `ConcurrentHashMap` lookup (O(1)). Kaldırır: `RestTemplate` + 7 converter + 2 `ObjectMapper` kurulumu, olmayan class'lar için classloader tam JAR taraması + `ClassNotFoundException` üretimi, tek classloader kilidinde serileşme.
- A2 ekler: hiçbir şey (aynı SynchronousQueue/60 s idle semantiği; yalnız üst sınır + saturasyonda caller-runs). Kaldırır: sınırsız thread yaratma/öldürme churn'ü (~1,3 thread/sn ölçüldü) ve context-switch baskısı.
- A3 ekler: hiçbir şey (string birleştirme ve ActionLog insert'i ActionLog görünürlüğü için bilinçli korunur — kullanıcı kararı). Kaldırır: çağrı başına 4–6 satırın iki senkron logback appender'ından (FILE + CONSOLE, `CachingDateFormatter` kilidi) geçişini.

## TDD Stratejisi

Önce başarısız/karakterize eden testler yazılır, sonra en küçük üretim kodu değişikliği yapılır (kırmızı → yeşil → güvenli refactor). Gerçek AMI/Redis/DB bağlantısı kurulmaz; Mockito ve constructor/static-factory boundary'leri kullanılır. `CallOriginator` için test edilebilirlik amacıyla pool kurulumunu package-private static factory metoduna çıkarma (davranış birebir) safe-change kapsamındadır.

| Görev | Test sınıfı | Doğruladığı davranış |
|---|---|---|
| A1 | `tests.factory.RestOperationsFactoryCachingRegressionTest` | Aynı timeout → aynı instance; farklı timeout → farklı; `build(-1)` default bean DEĞİL ve tekil; `build(-1,-1)` → default bean; eşzamanlı erişimde tek instance |
| A2 | `tests.dialer.CallOriginatorLscExecutorBoundRegressionTest` | Üst sınır = `max(1, maxLines)`; core 0 / 60 s idle / SynchronousQueue korunur; thread adı IP içerir; doygunlukta caller-runs, reject exception yok |
| A3 | `tests.util.LoggerDecoratorAuditRegressionTest` | `audit()` argümanlı çağrıda ActionLog'u INFO ile kaydeder, slf4j'ye debug yazar, info YAZMAZ; argümansızda ActionLog yazmaz |
| A3 | `LSCEngineTest` (genişletme) | Payload (`LSC URL`) satırı info ile loglanmaz, audit ile loglanır (ActionLog korunur); mevcut LSC akış testleri yeşil kalır |

## Uygulama Kontrol Listesi (Execution Plan)

### Görev 1 (A1): RestOperationsFactory instance cache

**Dosyalar:**
- Test (yeni): `gbpowerdialer/src/test/java/tests/factory/RestOperationsFactoryCachingRegressionTest.java`
- Değişiklik: `gbpowerdialer/src/main/java/com/powerdialer/factory/RestOperationsFactory.java`

- [x] **1.1 Başarısız testi yaz** — yeni dosya, tam içerik:

```java
package tests.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
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
    List<RestOperations> instances = Collections.list(Collections.enumeration(distinct.keySet()));
    assertEquals("Es zamanli build ayni timeout icin tek instance uretmeli", 1, instances.size());
  }
}
```

- [x] **1.2 Testi çalıştır, kırmızı gör:** `mvn -o -Dtest=RestOperationsFactoryCachingRegressionTest test` — beklenen: `sameTimeoutReturnsSameInstance`, `negativeSingleTimeoutReturns...` ve `concurrentBuilds...` FAIL (mevcut kod her çağrıda yeni instance döner).
- [x] **1.3 Üretim kodunu değiştir** — `RestOperationsFactory.java` tam yeni içerik:

```java
package com.powerdialer.factory;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * Factory for creating instances of {@link RestOperations} which has different
 * configs from restTemplate defined in applicationContext.xml
 *
 * Task 325469: RestTemplate kurulumu (converter taramasi + Tomcat classloader
 * kilidi) cagri hot path'inde tekrarlanamayacak kadar pahalidir. RestTemplate
 * thread-safe oldugu icin instance'lar timeout kombinasyonu basina bir kez
 * yaratilir ve paylasilir. Timeout semantigi degismez: build(-1) yine
 * timeout'suz default-converter'li RestTemplate'tir (tekil), build(-1,-1)
 * yine applicationContext'teki defaultRestTemplate'i doner.
 */
@Component
public class RestOperationsFactory {

  @Autowired
  private RestOperations defaultRestTemplate;

  private final ConcurrentHashMap<Long, RestOperations> cacheByTimeouts = new ConcurrentHashMap<>();

  /**
   * Build with specified timeouts for requests
   */
  public RestOperations build(int readTimeout, int connectTimeout) {
    if (readTimeout < 0 && connectTimeout < 0) {
      return defaultRestTemplate;
    } else {
      return getOrCreate(readTimeout, connectTimeout);
    }
  }

  /**
   * Build new RestTemplate with specified timeout for requests
   */
  public RestOperations build(int timeout) {
    if (timeout < 0) {
      return getOrCreate(-1, -1);
    } else {
      return getOrCreate(timeout, timeout);
    }
  }

  private RestOperations getOrCreate(int readTimeout, int connectTimeout) {
    Long key = (((long) readTimeout) << 32) | (connectTimeout & 0xFFFFFFFFL);
    return cacheByTimeouts.computeIfAbsent(key, k -> create(readTimeout, connectTimeout));
  }

  private RestOperations create(int readTimeout, int connectTimeout) {
    if (readTimeout < 0 && connectTimeout < 0) {
      return new RestTemplate();
    }
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setReadTimeout(readTimeout);
    requestFactory.setConnectTimeout(connectTimeout);
    return new RestTemplate(requestFactory);
  }

}
```

- [x] **1.4 Testi çalıştır, yeşil gör:** `mvn -o -Dtest=RestOperationsFactoryCachingRegressionTest test` — beklenen: 5/5 PASS.
- [x] **1.5 Etkilenen mevcut testi koru:** `mvn -o -Dtest=LSCEngineTest test` — beklenen: PASS (factory testte mock'lu; davranış değişmedi).

### Görev 2 (A2): lsc-executor üst sınırı

**Dosyalar:**
- Test (yeni): `gbpowerdialer/src/test/java/tests/dialer/CallOriginatorLscExecutorBoundRegressionTest.java`
- Değişiklik: `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallOriginator.java:141-150`

**Arayüz:** `CallOriginator`'a `public static ExecutorService createLscExecutor(String serverIp, Integer maxLines)` eklenir; `createExecutors()` bunu çağırır. (Private kurulumun test edilebilir boundary'ye çıkarılması — davranış birebir. Not: test `tests.dialer` paketinde olduğu için package-private yeterli olmadı; stateless factory olduğundan `public` görünürlük kabul edildi — uygulama sırasında işlenen küçük plan sapması.)

- [x] **2.1 Başarısız testi yaz** — yeni dosya, tam içerik:

```java
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
```

- [x] **2.2 Testi çalıştır, kırmızı gör:** `mvn -o -Dtest=CallOriginatorLscExecutorBoundRegressionTest test` — beklenen: derleme hatası (`createLscExecutor` yok) → FAIL.
- [x] **2.3 Üretim kodunu değiştir** — `CallOriginator.createExecutors` (satır 141-150) şu hale gelir ve yeni static factory eklenir:

```java
  private void createExecutors() {
    ThreadFactory callOriginatorThreadFactory = new ThreadFactoryBuilder().setDaemon(true)
                                                    .setNameFormat("call-originator-" + connectionDecorator.getServer().getIp() + "-executor-%d")
                                                    .build();
    this.cpsService = Executors.newSingleThreadScheduledExecutor(callOriginatorThreadFactory);
    this.lscExecutor = createLscExecutor(connectionDecorator.getServer().getIp(),
                                         connectionDecorator.getServer().getMaxLines());
  }

  /**
   * Task 325469: lsc-executor sinirsiz cached pool yerine ayni semantikle
   * (core 0, 60 s idle, SynchronousQueue) ama server.maxLines ust sinirli
   * kurulur. availableOriginateSlots in-flight LSC'yi butceden dustugu icin
   * normal akista doygunluk beklenmez; doygunlukta CallerRunsPolicy originate
   * tick thread'inde kosarak dogal geri basinc uygular (cagri kaybolmaz).
   */
  public static ExecutorService createLscExecutor(String serverIp, Integer maxLines) {
    ThreadFactory lscThreadFactory = new ThreadFactoryBuilder().setDaemon(true)
                                         .setNameFormat("lsc-executor-" + serverIp + "-%d")
                                         .build();
    int maxThreads = Math.max(1, maxLines == null ? 0 : maxLines.intValue());
    return new ThreadPoolExecutor(0, maxThreads, 60L, TimeUnit.SECONDS,
        new SynchronousQueue<>(), lscThreadFactory, new ThreadPoolExecutor.CallerRunsPolicy());
  }
```

  Gerekli yeni import'lar: `java.util.concurrent.SynchronousQueue`, `java.util.concurrent.ThreadPoolExecutor` (mevcut import bloğuna eklenir; `Executors`, `ThreadFactory`, `TimeUnit`, `ExecutorService` zaten var).

- [x] **2.4 Testi çalıştır, yeşil gör:** `mvn -o -Dtest=CallOriginatorLscExecutorBoundRegressionTest test` — beklenen: 4/4 PASS.

### Görev 3 (A3): LSC logback diyeti — ActionLog korunur (`LoggerDecorator.audit`)

**Dosyalar:**
- Test (yeni): `gbpowerdialer/src/test/java/tests/util/LoggerDecoratorAuditRegressionTest.java`
- Test (genişletme): `gbpowerdialer/src/test/java/com/powerdialer/engine/LSCEngineTest.java`
- Değişiklik: `gbpowerdialer/src/main/java/com/powerdialer/util/LoggerDecorator.java`, `gbpowerdialer/src/main/java/com/powerdialer/engine/LSCEngine.java`

**Arayüz:** `LoggerDecorator`'a `public void audit(Object message, Object... arguments)` eklenir: ActionLog'u bugünkü `info` ile birebir aynı şekilde (INFO seviyesiyle, argüman işleyerek) kaydeder; slf4j'ye **debug** seviyesinde yazar. Operasyonun ActionLog ekranı değişmez; logback appender'ları üretimde devre dışı kalır.

- [x] **3.1 Başarısız LoggerDecorator testini yaz** — yeni dosya, tam içerik:

```java
package tests.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import com.powerdialer.model.ActionLog;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * Task 325469: LSC hot path INFO loglari iki senkron logback appender'inin
 * (FILE + CONSOLE) CachingDateFormatter kilidinde konvoy yaratti. Operasyon
 * ActionLog ekranini kullandigi icin ActionLog kaydi korunur; audit() ActionLog'u
 * INFO seviyesiyle kaydeder ama slf4j'ye debug yazar (uretimde appender'a girmez).
 */
@RunWith(MockitoJUnitRunner.class)
public class LoggerDecoratorAuditRegressionTest {

  @Mock
  private Logger slf4jLogger;

  @Mock
  private ActionLogService actionLogService;

  @Test
  public void auditWithArgumentsWritesActionLogAsInfoButLogsAsDebug() {
    LoggerDecorator decorator = new LoggerDecorator(slf4jLogger, actionLogService);

    decorator.audit("LSC URL: http://x", Constants.LOG_MODULE.LSC);

    ArgumentCaptor<ActionLog> captor = ArgumentCaptor.forClass(ActionLog.class);
    verify(actionLogService).addActionLog(captor.capture());
    assertEquals("LSC URL: http://x", captor.getValue().getLog());
    assertEquals(Constants.LOG_LEVEL.INFO, captor.getValue().getLogLevel());
    assertEquals(Constants.LOG_MODULE.LSC, captor.getValue().getLogModule());
    verify(slf4jLogger).debug("LSC URL: http://x");
    verify(slf4jLogger, never()).info("LSC URL: http://x");
  }

  @Test
  public void auditWithoutArgumentsSkipsActionLogLikeInfoDoes() {
    LoggerDecorator decorator = new LoggerDecorator(slf4jLogger, actionLogService);

    decorator.audit("sadece dosya izi");

    verifyNoInteractions(actionLogService);
    verify(slf4jLogger).debug("sadece dosya izi");
  }
}
```

- [x] **3.2 Testi çalıştır, kırmızı gör:** `mvn -o -Dtest=LoggerDecoratorAuditRegressionTest test` — beklenen: derleme hatası (`audit` yok) → FAIL.
- [x] **3.3 LoggerDecorator'a metodu ekle** — `error(Object, Throwable, Object...)` metodundan sonra:

```java
  /**
   * Task 325469: Operasyon ActionLog ekranindan LSC akisini izledigi icin
   * ActionLog kaydi info ile birebir ayni sekilde (INFO seviyesiyle) korunur;
   * dosya/console appender'ina ise debug seviyesinde yazilir. Uretimde root
   * logger INFO oldugundan bu satirlar logback appender kilidine hic girmez;
   * gerekirse ilgili logger DEBUG yapilarak dosya logunda da acilir.
   */
  public void audit(Object message, Object... arguments) {
    if (arguments.length > 0)
      actionLogService.addActionLog(createActionLog(LOG_LEVEL.INFO, message, arguments));
    log.debug(message.toString());
  }
```

- [x] **3.4 Testi çalıştır, yeşil gör:** `mvn -o -Dtest=LoggerDecoratorAuditRegressionTest test` — beklenen: 2/2 PASS.
- [x] **3.5 Başarısız LSCEngine karakterizasyon testini ekle** — `LSCEngineTest.java` içine (mevcut harness'ı kullanır; `testPassGetJsonLsc` ile aynı kurulum), sınıfa yeni test metodu:

```java
  /**
   * Task 325469: Payload iceren "LSC URL" satiri info seviyesinde
   * loglanmamali (logback yuku), audit ile loglanmali (ActionLog korunur);
   * ozet "LSC(s) result is" satiri info olarak kalmali.
   */
  @Test
  public void testLscPayloadLogsAreAuditNotInfoLevel() {
    LSC getJsonLsc = new LSC();
    getJsonLsc.setId(1L);
    getJsonLsc.setUrl("http://lsc.test.com/get/{testVar}");
    getJsonLsc.setDeleted(false);
    getJsonLsc.setEnabled(true);
    getJsonLsc.setReplyKey("$.status");
    getJsonLsc.setReplyResult("1");
    getJsonLsc.setReplyType(Constants.REPLY_TYPE.JSON);
    getJsonLsc.setHttpMethod(Constants.HTTP_METHOD.GET);
    getJsonLsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign)));
    lenient().when(lscService.getWithInitializedCampaigns(1L)).thenReturn(getJsonLsc);
    when(lscService.getLscsForCampaign(1L, true)).thenReturn(Collections.singletonList(getJsonLsc));
    doReturn(new ResponseEntity<>("{\"status\":1}", HttpStatus.OK))
        .when(defaultRestTemplate)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class), anyString());

    lscEngine.addRunningCampaign(runningCampaign);
    Call call = new Call();
    call.setCampaign(runningCampaign);
    call.setRawData(new JSONObject("{\"testVar\":124}"));
    lscEngine.applyLscsToCall(call);

    LoggerDecorator lscLog = loggerDecorator.getLogger(LSCEngine.class);
    boolean payloadInfoLogged = Mockito.mockingDetails(lscLog).getInvocations().stream()
        .filter(invocation -> "info".equals(invocation.getMethod().getName()))
        .anyMatch(invocation -> String.valueOf((Object) invocation.getArgument(0)).startsWith("LSC URL"));
    boolean payloadAuditLogged = Mockito.mockingDetails(lscLog).getInvocations().stream()
        .filter(invocation -> "audit".equals(invocation.getMethod().getName()))
        .anyMatch(invocation -> String.valueOf((Object) invocation.getArgument(0)).startsWith("LSC URL"));
    assertFalse("Payload satiri info loglanmamali (logback yuku)", payloadInfoLogged);
    assertTrue("Payload satiri audit loglanmali (ActionLog korunur)", payloadAuditLogged);
  }
```

  Not: Bu metod mevcut test sınıfının import'larını kullanır; eksikse eklenecekler: `org.mockito.Mockito`, `static org.junit.Assert.assertFalse`, `static org.junit.Assert.assertTrue`. Mevcut `testPassGetJsonLsc` testindeki `exchange` stub deseni birebir izlenir; stub imzası mevcut testtekiyle aynı tutulur (uygulama sırasında mevcut stub'dan kopyalanır).
- [x] **3.6 Testi çalıştır, kırmızı gör:** `mvn -o -Dtest=LSCEngineTest#testLscPayloadLogsAreAuditNotInfoLevel test` — beklenen: FAIL (bugün payload satırı info loglanıyor).
- [x] **3.7 LSCEngine rutin satırlarını `audit`'e çevir** — nokta nokta (eski → yeni; argümanlar birebir korunur ki ActionLog içeriği değişmesin):

  (a) Satır 257 `[LSC-RETRY]` — rutin ilk deneme audit olur, gerçek retry INFO kalır:
```java
      if (attempt > 0) {
        LOG.info("[LSC-RETRY] lscId=" + lsc.getId() + " | attempt=" + attempt + " / " + maxRetry, Constants.LOG_MODULE.LSC);
      } else {
        LOG.audit("[LSC-RETRY] lscId=" + lsc.getId() + " | attempt=" + attempt + " / " + maxRetry, Constants.LOG_MODULE.LSC);
      }
```

  (b) Satır 272-281 `[LSC-AUTH] READ` bloğu — yalnız metod adı değişir: `LOG.info(` → `LOG.audit(` (argümanlar aynı kalır).

  (c) Satır 291, 295, 302, 306 — dört satırın her biri yalnız metod adı değişir:
```java
            LOG.audit("LSC URL: " + url +  " headers: " + headers + " payload: " + payload, Constants.LOG_MODULE.LSC, lsc);
```

  (d) Satır 569 ham response:
```java
    LOG.audit(lsc + " respond: '" + responseObject.getBody(), Constants.LOG_MODULE.LSC, lsc);
```

  (e) Satır 578 LSC başına sonuç:
```java
    LOG.audit("LSC result is " + result, result, Constants.LOG_MODULE.LSC, lsc);
```

  Değişmeyenler (bilinçli): satır 216 özet `LSC(s) result is ... for Call ...` INFO kalır (çağrı başına tek satır; Elastic teşhis imzamız; argümansız olduğundan ActionLog'a zaten gitmiyordu); tüm `LOG.warn`/`LOG.error` satırları aynen kalır (318, 323, 329, 336*, 339*, 342, 359, 369, 566 — *336/339 timeout/refused satırları info+exception'dır, hata görünürlüğü için aynen bırakılır, bu fix'te dokunulmaz).
- [x] **3.8 Testleri çalıştır, yeşil gör:** `mvn -o -Dtest=LSCEngineTest test` — beklenen: mevcut tüm testler + yeni test PASS.

### Görev 4 (A4): Takip işleri (bu branch'te kod değişikliği YOK)

- [x] **4.1** Aşağıdaki maddeler ayrı change-fix planları olarak açılmak üzere kayıt altına alındı (bu planla kapatılmaz):
  1. `CampaignRecordController.fetchRecords` → `CampaignRecordService.fetchAll` sorgu maliyeti + DBCP2 `AbandonedTrace` overhead'i (22.07 dump: catalina-exec-1 74 s, catalina-exec-4 59 s CPU).
  2. `DBEngineFileLogger` AOP'sinin her query'de dosya logu üretmesi.
  3. `ManagerConnectionDecorator.clearExpiredCallbacksFromListeners` periyodik tam taraması (dump'ta 12 RUNNABLE thread).
  4. `LoggerDecorator` → `ActionLogService.addActionLog` senkron DB insert mimarisi (argümanlı her log'da Hibernate save; batch/async kuyruk değerlendirmesi).
  5. logback FILE + CONSOLE çift senkron appender + `AsyncAppender` değerlendirmesi (log kaybı toleransı operasyonla netleşmeli); CONSOLE'un catalina.out büyütmesi.

### Görev 5: Kapanış

- [x] **5.1** Hedefli testlerin tamamı yeşil (aşağıdaki komut seti).
- [x] **5.2** `mvn -o -DskipTests package` başarılı (WAR derlemesi).
- [x] **5.3** History kaydı `docs/history/28.07.2026.md` dosyasına eklendi; plan adımları işaretlendi.
- [ ] **5.4** Commit'ler `gbpd-commit-discipline` ile hazırlandı (kullanıcı onayı sonrası); PR öncesi VPN'de `origin/develop` sync (`gbpd-sync-develop-branch`).
- [ ] **5.5** Deploy sonrası canlı doğrulama (aynı yük altında): `jstack` içinde `WebappClassLoaderBase.loadClass` bekleyeni 0; `lsc-executor-<ip>-N` sayısı ≤ maxLines; Elastic'te LSC INFO hacmi düşüşü.

## Hedefli Doğrulama Komutları

Önce `.codex/TOOL-PATHS.local.properties` okunur; `MAVEN_CMD`/`MAVEN_SETTINGS`/`MAVEN_REPO_LOCAL`/`JAVA_HOME` varsa uygulanır. Maven daima `-o` (offline) ile koşulur; eksik artifact indirilmez, raporlanır.

```
mvn -o -Dtest=RestOperationsFactoryCachingRegressionTest test
mvn -o -Dtest=CallOriginatorLscExecutorBoundRegressionTest test
mvn -o -Dtest=LoggerDecoratorAuditRegressionTest test
mvn -o -Dtest=LSCEngineTest test
mvn -o -DskipTests package
```

Frontend değişikliği yok → `npm.cmd run test:angular` bu plan için gerekli değil.

## Kapanış Kriteri

- Dört test sınıfı (3 yeni + LSCEngineTest genişletmesi) offline Maven ile yeşil.
- `RestOperationsFactory` çağrı başına yeni RestTemplate yaratmıyor; `build(-1,-1)` davranışı ve timeout semantiği birebir korunmuş.
- `lsc-executor` havuzları `max(1, maxLines)` üst sınırlı; cached semantiği (core 0 / 60 s / SynchronousQueue) korunmuş.
- LSC rutin akış satırları `audit`'e taşınmış: ActionLog kaydı (INFO seviyesi, argüman işleme) birebir korunmuş, logback'e üretimde gitmiyor; özet satırı ve warn/error'lar korunmuş.
- WAR paketleme başarılı; history kaydı eklenmiş; A4 maddeleri takip listesinde.
- Açık sorular cevaplanmış ve plana işlenmiş.

## Açık Sorular

Tümü 28.07.2026'da kullanıcı tarafından cevaplandı; plan bu cevaplara göre revize edildi.

1. **[A3 kapsam onayı — CEVAPLANDI]** Cevap: "ActionLog'lar kalmalı, operasyon inceliyor." → Tasarım revize edildi: seviye indirme yerine `LoggerDecorator.audit` — ActionLog kaydı INFO seviyesiyle birebir korunur, yalnız logback appender'ına debug yazılır (Görev 3'ün güncel hali).
2. **[A3 özet satırı — CEVAPLANDI]** Cevap: INFO kalsın. → Satır 216 dokunulmuyor.
3. **[A2 sınır değeri — CEVAPLANDI]** Cevap: çağrı akışını etkilemeyecek şekilde ajan seçsin. → `max(1, server.maxLines)` seçildi; gerekçe: `availableOriginateSlots` bütçesi in-flight LSC'yi zaten `maxLines` altında tuttuğu için normal akışta bu sınıra ulaşılamaz — çağrı akışı davranışı birebir korunur; sınır yalnız bugünkü gibi patolojik konvoy anında devreye girer ve `CallerRunsPolicy` çağrı kaybetmeden yavaşlatır.
4. **[Repo hijyeni — CEVAPLANDI]** Cevap: evet, ayrı işte temizle. → `docs/history/README.md` ve `docs/history/10.07.2026.md` conflict marker temizliği bu fix'in implementasyonu bittikten sonra ayrı bir commit olarak hazırlanacak (iki taraftaki meşru kayıtlar korunarak yalnız marker satırları kaldırılır).
