# FIX Expire job ve günlük cron'ların scheduler gecikmesi Change/Fix Planı

## Bağlam

Turkcell dialer'ında (PDP02) 08:45 expire job'ı (`CampaignResultJobService.runJob`) ve 07:30 mirror
job'ı 15.09.2026'dan sonra günlerce kaydı; 01.10 itibarıyla ortak scheduler kuyruğu ~75 saat geride.
REAKTIF (71) kampanyası 15.09'dan sonra yalnız iki gece koşusunda işlendi. RCA ve thread dump devir
dokümanı "Turkcell Expire Job RCA ve Fix Devri" (01.10.2026) üzerinden devralındı.

## İş Etiketi

fix

## Öncelik

hotfix

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/redis/RedisUtil.java` (`keys`)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/synchronization/UserSynchronizer.java` (`synchronizeUsers` zamanlaması)
- Testler: `gbpowerdialer/src/test/java/tests/redis/`

## Mevcut Davranış

- `applicationContext.xml` içindeki `<task:annotation-driven>` scheduler belirtmiyor ve context'te
  `TaskScheduler` bean'i yok; tüm `@Scheduled` metotlar Spring'in tek thread'li varsayılan
  scheduler'ında sırayla çalışıyor.
- `UserSynchronizer.synchronizeUsers` `fixedRate = 20000` ile çalışıyor ve her turda
  `RedisUtil.getLiveUserIds()` → `RedisUtil.keys("userData*")` çağırıyor.
- `keys()` Redisson `getKeysByPattern(pattern)` kullanıyor; Redisson 3.23.3'te bu overload
  `getKeysByPattern(pattern, 10)`'a delege ediyor (jar'dan `javap` ile doğrulandı). Tüm keyspace
  10'ar key'lik SCAN round-trip'leriyle taranıyor.
- Prod'da tur süresi 20 sn'yi aşınca (gündüz 21–49 sn) fixed-rate catch-up kuyruğun başını
  işgal ediyor; cron'lar ancak birikim kendi slotlarına gelince koşuyor ve kaçan günler düşüyor.
- Expire job yalnız durdurulmuş kampanyayı işlediği için (`stoppedDate is not null`) geç
  koşularda REAKTIF atlanıyor ve `pd_campaign_job_log`'a satır yazılmıyor.

## Beklenen Davranış

- `UserSynchronizer` turu yavaş olsa bile birikim oluşmaz; bir sonraki tur önceki turun
  bitişinden 20 sn sonra başlar ve arada vadesi gelen cron'lar sırasını alır.
- SCAN her round-trip'te 1000 key ister; round-trip sayısı ~100 kat azalır, dönen key kümesi
  değişmez.
- 07:30 mirror ve 08:45 expire job'ı deploy sonrası ilk sabahtan itibaren zamanında koşar.

## Kod Okuma Bulguları

- `RedisUtil.keys()` tek çağıranı `RedisUtil.getLiveUserIds()`; `getLiveUserIds()` tek çağıranı
  `UserSynchronizer.synchronizeUsers()`. Değişikliğin etki alanı yalnız `UserSynchronizer` turudur.
- Redisson 3.23.3 `RKeys.getKeysByPattern(String, int)` mevcut ve deprecated değil; `limit=0`
  (sınırsız) ve verilen `count` ile SCAN yürütüyor. Cluster modunda her master node ayrı taranır.
- `UserSynchronizer` başka bir yerden (XML `task:scheduled-tasks` vb.) zamanlanmıyor.
  `REMOTE_USERS_SYNCHRONIZATION_INTERVAL` sınıf içi `private` sabit.
- Spring 5.3.30'da `fixedDelay` için `initialDelay` varsayılanı ilk koşuyu açılışta hemen başlatır;
  açılış davranışı `fixedRate` ile aynı kalır.
- `REDUNDANT_USER_CATCH_LIMIT_BEFORE_LOGOUT` (2) ardışık tur sayısıyla çalışıyor; turlar ~20 sn arayla
  geleceği için redundant kullanıcı logout süresi tasarlanan değere (birikimsiz `fixedRate`) döner.
- Repo'da aynı desen mevcut: `AgentSizeConsistencyChecker` `@Scheduled(fixedDelay = 30_000)`.
- `IncallAgentsRefresher` Javadoc'undaki "UserSynchronizer 20 sn" notu değişiklikten sonra da doğru.
- Mevcut `tests/redis/LiveUserIdsScanContractTest` tek parametreli `getKeysByPattern` stub'ları
  kullanıyor; yeni imzaya göre güncellenmesi gerekiyor.
- MCP cross-check:
  - Azure DevOps: aktif PR'lardan yalnız #24951 (`feature/state-drift-snyc`) `RedisUtil.java`'ya
    dokunuyor; yeni metotlar ekliyor, `keys()`/`getLiveUserIds()` satırlarıyla çakışmıyor.
  - Test Redis (`properties-tr-test`, tek node): 1.081 key, `userData*` 1 key (`userData<id>`
    formatı doğrulandı). Test ortamı sorunu üretmiyor; eşik prod keyspace hacmine bağlı.
  - Turkcell prod Powerdialer deploy'ları 08.09, 10.09 ve 17.09; 15→16.09 arasında deploy yok.

## Root Cause / Değişiklik Gerekçesi

Ortak tek scheduler thread'inde `fixedRate` ile çalışan `UserSynchronizer`, Redisson'un varsayılan
SCAN COUNT 10 değeri nedeniyle keyspace büyüdükçe 20 sn periyodunu aşıyor; fixed-rate catch-up
birikimi kuyruğun başını kalıcı olarak işgal edip günlük cron'ları geciktiriyor. Owner boundary
`UserSynchronizer` zamanlaması ve `RedisUtil.keys()` sayfa boyutudur; cron ifadeleri ve expire job
mantığı doğru çalışıyor.

## Riskler

| Risk | Azaltım |
|---|---|
| `fixedDelay` ile yavaş dönemlerde senkron turları seyrekleşir | Turlar zaten birikmeli koşuyordu; count 1000 ile tur süresi kısalır, aralık ~20 sn'ye döner |
| SCAN COUNT 1000 her çağrıda Redis'te daha uzun iş yapar | Çağrı başına ~1000 bucket; `KEYS` gibi tüm keyspace'i tek komutta taramaz, toplam iş değişmez |
| Keyspace büyümeye devam ederse ileride tur yine uzayabilir | `fixedDelay` birikimi engellediği için cron gecikmesi en fazla bir tur süresiyle sınırlı kalır |

## Kapsam Dışı

- Scheduler izolasyonu (`<task:scheduler>` havuzu): bugün sıralı çalışan işleri paralelleştirir ve
  thread-safety taraması ister; bu hotfix için gerekli değil.
- `UserSynchronizer` süre logu ve aktif kullanıcıyı keyspace taramadan tutan indeks (PR #24951'deki
  online users set yaklaşımı) ayrı işlerdir.
- Cron ifadeleri ve sunucu tarafı ayarlar değişmez.
- PROAKTIF (72) kaydının prod `pd_campaign_job`'dan çıkmış olması veri/ayar konusudur.

## TDD Stratejisi

- `tests/redis/LiveUserIdsScanContractTest`: stub ve verify'lar `getKeysByPattern("userData*", 1000)`
  imzasına çekilir; mevcut üretim kodunda kırmızı olması beklenir.
- `tests/redis/UserSynchronizerFixedDelayScheduleContractTest` (yeni): `synchronizeUsers` üzerindeki
  `@Scheduled` annotation'ının `fixedDelay = 20000` ve `fixedRate` tanımsız olduğunu kilitler.
- Mevcut `com.powerdialer.engine.synchronization.UserSynchronizerTest` regression olarak koşar.

## Açık Sorular

Yok

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [x] `LiveUserIdsScanContractTest` yeni imzaya çekildi ve kırmızı görüldü.
- [x] `UserSynchronizerFixedDelayScheduleContractTest` yazıldı ve kırmızı görüldü.
- [x] `RedisUtil.keys()` `getKeysByPattern(pattern, 1000)` kullanacak şekilde değiştirildi.
- [x] `UserSynchronizer` `fixedRate` → `fixedDelay` yapıldı.
- [x] Hedefli testler yeşile döndü.
- [x] `tests.redis`, `UserSynchronizerTest` ve `IncallAgents*` regression koşusu yeşil.
- [x] History kaydı eklendi.

## Performans Etkisi

- Hot path durumu: Call/event hot path değil; 20 sn'lik periyodik reconciler ve ortak scheduler
  thread'i.
- Call/event/tick başına ek maliyet: Yok. Tur başına Redis round-trip sayısı ~100 kat azalır.
- IO/scan/lock/serialization/log etkisi: SCAN çağrısı başına iş ~10 → ~1000 bucket; toplam tarama işi
  aynı. Yeni lock, log veya serialization yok.
- Azaltım veya ölçüm notu: Deploy sonrası `pd_mirror_job_log` 07:30 ve `pd_campaign_job_log` 08:45
  satırları izlenir. `UserSynchronizer` her turda log basmadığı için tur aralığı loglardan doğrudan
  ölçülemez; cron'ların zamanında koşması dolaylı kanıttır.

## Doğrulama

```bash
mvn -o -Dtest=LiveUserIdsScanContractTest,UserSynchronizerFixedDelayScheduleContractTest test
```

```bash
mvn -o -Dtest='tests.redis.**,UserSynchronizerTest,*IncallAgents*' -Dsurefire.failIfNoSpecifiedTests=false test
```

Deploy sonrası: ilk sabah 07:30 mirror ve 08:45 expire koşuları zamanında; REAKTIF 08:45'te
durdurulmuşsa `execution_status = 1`.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] Planlanan testler çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Tamamlandı (01.10.2026). Kod tarafı bitti; commit ve deploy yapılmadı.

- RED: `LiveUserIdsScanContractTest.liveUserIdsAreResolvedFromScanBasedKeyLookup` (`expected:<2> but was:<0>`) ve `UserSynchronizerFixedDelayScheduleContractTest` (`expected:<20000> but was:<-1>`).
- GREEN: hedefli 4/4; regression 41/41 (`tests.redis.**`, `UserSynchronizerTest`, `IncallAgentsDebouncedRefreshRegressionTest`).
- Prod doğrulaması deploy sonrası ilk sabah yapılacak (bkz. Doğrulama).
