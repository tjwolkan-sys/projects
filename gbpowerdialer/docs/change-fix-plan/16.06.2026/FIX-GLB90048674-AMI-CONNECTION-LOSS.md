# FIX-GLB90048674 AMI Connection Loss Root-Cause Change/Fix Planı

- İş Etiketi: fix
- Kimlik: GLB90048674
- Slug: ami-connection-loss-root-cause
- Tarih: 2026-06-16
- Öncelik: normal (üretim etkisi yüksek — kronik AMI connection loss)
- Branch: `ami-route-cause-fix` (develop'tan)
- Plan Durumu: Üç faz da uygulandı, spec+kalite+holistic review'lerden geçti, hedefli regresyon yeşil — **pre-prod doğrulamasına hazır** (2026-06-16). Deploy/pilot gözlemi bekliyor.

## Bağlam

gbpowerdialer üretimde sık sık **AMI connection loss** yaşıyor; aynı altyapıyı kullanan referans proje **gbwebphone hiç yaşamıyor**. Kütüphane aynı (asterisk-java `2.0.3.gb*`); fark **AMI bağlantı topolojisi ve event işleme thread modeli**nde.

Bu olayın downstream semptomu zaten `docs/change-fix-plan/21.05.2026/FIX-GLB90090645-GAMMA-CALLQUEUE-BACKPRESSURE.md` planında belgelendi: AMI loss + restore → `callQueue` burst → rc78/rc84/rc75 (bir günde 5 kampanyada ~2476 rc75). O plan açıkça *"Bu PR altyapı sorununu çözmüyor; uygulama katmanında etki azaltma ekliyor"* notunu düştü. **Bu plan, o ertelenen altyapı kök nedenini kapatır.**

Kök neden (koddan birinci elden doğrulandı): gbpowerdialer sunucu başına **tek bir AMI bağlantısını** hem TÜM event'leri dinlemek hem de Originate/Hangup göndermek için kullanıyor; bu tek bağlantının event handler'ları ve originate response callback'i **AMI reader thread'i üzerinde senkron DB/CDR I/O** yapıyor. Predictive yükte reader thread tıkanıyor; ping/komut cevabını zamanında okuyamayan bağlantı keepalive tarafından "ölü" sayılıp düşürülüyor ve/veya Asterisk yavaş tüketiciyi atıyor.

gbwebphone aynı tuzağa düşmüyor: (a) event ve command bağlantısını **ayırmış** (command bağlantısı `eventMask="off"`), (b) ağır işi `ExecutorService`'e devrediyor (`com/webphone/voip/ManagerConnectionWrapper.java:58`, `MediaManagerConnectionWrapper.java:103-107`, `AsteriskCommandSender.java:79-85`), (c) event süresi 200ms'yi geçince WARN logluyor (`ManagerConnectionWrapper.java:48,152-159`), (d) event hacmi düşük.

## İş Etiketi

fix

## Öncelik

normal (üretim etkisi yüksek; acil hotfix değil — kontrollü, fazlı ilerleyecek)

## Etkilenen Alanlar

Yüksek riskli AMI lifecycle. İlgili skill'ler: `gbpd-ami-event-flow`, `gbpd-call-flow-performance-guardrails`, `gbpd-no-workaround-root-cause-engineering`, `gbpd-safe-change`, `gbpd-characterization-tests`, `gbpd-tdd-workflow`, `gbpd-runtime-state-audit`, `gbpd-commit-discipline`, `gbpd-agent-history`.

- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java` (1637 satır)
  - Faz 1: `onManagerEvent` (`:425-445`) + originate response callback işini decorator başına tek-thread bounded "event worker"a devret; `createConnection` (`:231-241`) + `logoff` (`:417-419`) executor lifecycle.
  - Faz 2: originate (`sendActionUsingCallback` → `managerConnection.sendAction`, `:362-366`) ve hangup (`hangupCall`, `~:1261-1270`) ayrı **command** bağlantısına; mevcut bağlantı event-only.
  - Faz 3: per-event DB maliyeti — `saveCdrOnAnswer`/`findPersistedCdr` (`getByInteractionId` SELECT `:595-600`), `insertCallResultToDB` batch/async.
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionComposition.java` (409 satır)
  - Faz 2: command bağlantısının kurulması/kapatılması (`createConnectionAddToOpenedConnections:333-349`, `preDestroy:205-208`).
- `gbpowerdialer/src/main/java/com/powerdialer/util/Constants.java`
  - Faz 1: event worker kuyruk kapasitesi sabiti (kill-switch eklenmez — karar #2).
- Testler (YENİ, `gbpowerdialer/src/test/java/tests/ami/`):
  - Faz 1: `AmiEventWorkerOffloadRegressionTest`, `AmiEventWorkerOrderingRegressionTest`.
  - Faz 2: `AmiCommandConnectionSplitRegressionTest`.
  - Faz 3: `CdrPersistRoundTripReductionRegressionTest`, `CdrBatchOrderingNoLossRegressionTest`.

## Mevcut Davranış

`onManagerEvent` (AMI reader thread) `instanceof` zinciriyle handler'lara dağıtıyor (`:425-445`) ve doğrudan senkron iş yapıyor:
- `handleCallCreation` → `dbOperations.insertCallResultToDB(call)` (`:474`).
- `handleCallAnswer` → `insertCallResultToDB` (`:517`) + `saveCdrOnAnswer` (`:519` → `getByInteractionId` SELECT `:599` + `cdrService.saveOrUpdate` `:568`).
- `handleAgentAnswer` → `saveCdrOnAnswer` (`:557`).
- `handleDialerHangup` → `processCallResult` (`~:895-1030`: `insertCallResultToDB` + `cdrService.saveOrUpdate`).
- Her event başında `LOG.info("... Event received: " + event)` (`:427`).

Originate response callback: `sendActionUsingCallback` (`:362-366`) `managerConnection.sendAction(originateAction, callbackWrapper)`; `SendActionCallbackWrapper.onResponse` hata yolunda `handleOriginationFail` → `processCallResult` → DB. Bu callback de **aynı bağlantının reader/dispatch thread'inde** çalışıyor.

Originate/Hangup **aynı `managerConnection`** üzerinden gidiyor (`addEventListener(this)` `:234` + `sendAction(...)` `:365,~:1265`).

Concurrency: `calls` Guava Cache (thread-safe) ve `Campaign` sayaçları (atomic) bugün **CallOriginator (CPS thread) origination** ile **reader thread event işleme** arasında zaten paylaşılıyor; **event'ler arası eşzamanlılık YOK** (reader tek thread). Bu invariant korunmalı.

Connection loss tespiti: `CallOriginator.run()` CPS loop'u (`:122-137`) `getManagerConnectionState() != CONNECTED` → `handleConnectionLoss()` (`:526-593`). `setNewConnectionDecoratorIfExists()` (`~:650`) decorator değişimini ele alıyor → **decorator REPLACE edilebiliyor.**

## Beklenen Davranış

- **Faz 1 sonrası:** AMI reader thread'i (ve originate response dispatch'i) bloklanmaz; tüm event/callback işi decorator başına **tek thread'li, bounded** bir worker'da, **geldiği sırayla** işlenir. DB yavaşlasa bile reader serbest kaldığı için ping/komut cevapları okunur → **bağlantı düşmez.** Davranış (sayaç geçişleri, CDR sırası, cache durumları) bugünküyle birebir aynı kalır.
- **Faz 2 sonrası:** Originate/Hangup/transfer komutları **event almayan ayrı command bağlantısı** üzerinden gider; event'ler listen-only bağlantıda kalır. Komut yolu event yükünden tamamen izole (gbwebphone modeli).
- **Faz 3 sonrası:** Answer/hangup event başına gereksiz `getByInteractionId` SELECT ve tekrar eden upsert maliyeti düşer; yazımlar sıra-koruyan batch/async akışa alınır → worker daha hızlı, backlog daha küçük.
- Hiçbir fazda AMI event adları, `ORIGINID`/`PARENTID` korelasyonu, result code mapping, Redis channel adları veya CDR/result yazım **sırası** değişmez.

## Kök Neden / Değişiklik Gerekçesi

asterisk-java'da her bağlantının tek reader thread'i socket'i okur, event'leri **senkron** dinleyiciye dağıtır, ping ve komut cevaplarını da aynı akıştan okur. gbpowerdialer bu thread üzerinde event başına DB/CDR yazınca predictive yükte saturate olur → ping/komut cevabı okunamaz → bağlantı düşer. `gbpd-call-flow-performance-guardrails` zaten *"Pahalı iş gerekiyorsa event handler dışına, bounded async boundary'ye taşı"* diyor — bu plan tam olarak budur (workaround değil, owner boundary'de kalıcı çözüm).

## Kod Okuma Bulguları

- Originate (`originateCall:264-290`) **CPS thread'inde** (`CallOriginator:384`); event işleme **reader thread'inde**. `calls`/sayaçlar bugün de iki thread arası paylaşımlı; tek thread'li worker bu modeli BOZMAZ. **Çok-thread'li havuz event'ler arası YENİ eşzamanlılık → sayaç drift'i → KULLANILMAYACAK.**
- Originate response callback de reader/dispatch thread'inde DB yazıyor → Faz 1'de **aynı worker'a** verilmeli.
- `openedConnections` çok yerden iterate ediliyor: cross-decorator transfer lookup (`findCallByParentIdAcrossDecorators ~:1595`, `resolveTransferCall ~:1574`), AGI `getOriginatedCall` (`Composition:390-399` → `DialWithAbandonTimeoutScript:103`), `CallResource`, `WebphoneSubscriber`, `UserLoginLogoutHandler:154`, `StatisticSynchronizer:42`. **Faz 2 bunları bozmamalı** (caches/worker decorator'da tek; sadece komutun gittiği socket değişir).
- Komut göndericiler: `originateCall` ← `CallOriginator:384`; `hangupCall` ← `UserLoginLogoutHandler:183`, `CampaignStopJob:124`.
- `getManagerConnectionState` polling `CallOriginator:127` → Faz 2'de command bağlantısı authoritative.
- Constructor'da `scheduleService` var (`:140-154`) ama shutdown görülmedi → Faz 1 executor'ı `logoff()`/replace yolunda kapatılmalı.
- DB: `CampaignDbOperations.insertCallResultToDB` UPSERT, non-transactional (`:1696-1706`); `CDRService.saveOrUpdate` `@Transactional` (`:34-36`), `getByInteractionId` `@Transactional(readOnly)` (`:40-42`). Hepsi blocking.
- Test stili (`tests/ami/*RegressionTest`): JUnit4 + `MockitoJUnitRunner`, gerçek decorator + setter enjeksiyon, private handler `ReflectionTestUtils.invokeMethod`, cache'ler `decorator.getCalls()` getter. **Mevcut testler private handler'ı doğrudan çağırdığı için Faz 1'den sonra da geçerli** → düşük test churn. Worker testte **deterministik/senkron executor** enjekte edilerek kontrol edilir.

## Tasarım

### Kritik Veri Bütünlüğü Kısıtı (tüm fazlar — geliştirici şartı, 2026-06-16)

DB'ye giden insert/update **sırası korunmalı** ve **hiçbir DB yazımı (insert/update/CDR) kaybedilmemeli.** Aksi halde geç gelen bir yazım eski state'i geri yazıp son durumu kaybettirebilir. Bu yüzden tüm fazlar şu garantilere uyar:
- İşleme **tek thread'li** → sıra garantisi, eşzamanlı yazım yok.
- Kuyruk **asla task düşürmez** (drop YOK, CallerRuns YOK — CallerRuns reader'ı worker'la eşzamanlı çalıştırıp sırayı/atomikliği bozardı).
- Kapanışta kuyruk **drain edilir** (`shutdown()` + `awaitTermination`, `shutdownNow()` DEĞİL) → bekleyen yazımlar uçmaz.
- Faz 3 batch/async da aynı sıra + kayıpsızlık + garantili flush kuralına uyar.

### Faz 1 — Reader thread'ini serbest bırak (decorator başına tek-thread bounded event worker)
- `ManagerConnectionDecorator`'a **enjekte edilebilir** (testte deterministik) `ExecutorService eventWorker`: `new ThreadPoolExecutor(1, 1, 0L, MS, new LinkedBlockingQueue<>(CAP), namedDaemonFactory("ami-event-worker-"+ip), blockingRejectedHandler)`. Tek thread → sıralama korunur; bounded → AGENTS "bounded async".
- **Overflow (karar #1): bounded + blocking-put.** Custom `RejectedExecutionHandler` → `executor.getQueue().put(r)` ile **bloklar, düşürmez**. Hiç task/DB yazımı kaybolmaz, sıra korunur. CallerRuns KULLANILMAZ. Kapasite büyük (öneri ~50.000, `Constants` sabiti) + kuyruk derinliği WARN logu. Yalnız **sürekli aşırı yükte** reader put'ta bloklanır (= bugünkü davranış, ama kayıpsız/sıralı); Faz 3 bunu pratikte ortadan kaldırır.
- `onManagerEvent`: ucuz `instanceof` route'u worker'a sarılır (`eventWorker.execute(() -> route(event))`); reader normalde anında döner. `LOG.info(... Event received ...)` worker içine taşınır.
- Originate response callback'in ağır kısmı (`handleOriginationFail`/`processCallResult`) **aynı eventWorker'a** submit edilir (event↔callback sırası bugünkü gibi serileşmiş kalır).
- gbwebphone'daki gibi 200ms eşikli süre logu (worker içinde).
- Lifecycle: worker `createConnection`'da kurulur; `logoff()` ve decorator-replace yolunda **`shutdown()` + `awaitTermination`** ile **kuyruk drain edilerek** kapatılır (bekleyen DB yazımları kaybolmaz; `shutdownNow()` KULLANILMAZ). Reconnect'te decorator (ve worker) yaşar.
- Kill-switch eklenmez (karar #2); geri dönüş revert/redeploy ile.

### Faz 2 — Event/command bağlantı ayrımı (gbwebphone modeli — koşulsuz, karar #3)
- Decorator iki `AutonomousManagerConnection` tutar: `eventConnection` (listener, `eventMask=null`, sendAction YOK) ve `commandConnection` (`eventMask="off"`, listener YOK, originate/hangup buradan).
- `sendActionUsingCallback` ve `hangupCall` `commandConnection`'a yönlenir; callback yine eventWorker'a düşer (tek worker serileştirir).
- `getManagerConnectionState()`: **event bağlantısı authoritative** (`CallOriginator:127`) — recovery/connection-loss state semantiği DEĞİŞMEZ. (Uygulamada bilinçli olarak event-authoritative seçildi; planın ilk taslağındaki "command authoritative" notu bu daha düşük-riskli karar lehine güncellendi. `commandConnection` düşerse originate `sendAction` exception alır ve mevcut `handleExceptionDuringOrigination` çağrıyı temiz recycle eder.)
- Composition iki bağlantıyı kurar/kapatır; `openedConnections` ve cross-decorator/AGI lookup'ları değişmez.

### Faz 3 — Per-event DB maliyetini azalt (agresif batch/async — karar #4)
- CDR id bilindikten sonra gereksiz `getByInteractionId` SELECT eliminasyonu.
- `insertCallResultToDB` / CDR yazımlarını **batch/async** akışa al (worker arkasında ayrı bounded, sıra-koruyan flush).
- **Kritik kısıt:** batch/async per-call yazım **sırasını** korur, **hiçbir yazımı kaybetmez**, kapanış/flush garantilidir (eski state son durumu ezemez). Batch flush hata/timeout'ta retry; kayıpsızlık testle kilitlenir.

## Riskler

- **Event sırası / sayaç drift'i:** En kritik. Çözüm: **tek-thread** worker → serileştirilmiş, sıralı işleme; bugünkü semantik birebir korunur. Karakterizasyon testleriyle kilitlenir.
- **Veri kaybı / eski state ezilmesi (geliştirici kritik şartı):** blocking-put (drop yok) + tek thread (sıra) + drain-on-shutdown. Faz 3 batch/async aynı garantilere tabi.
- **Worker overload:** DB sürekli yetişemezse kuyruk büyür. Mitigasyon: büyük bounded queue + **blocking-put (drop YOK, sıra korunur)** + kuyruk derinliği WARN + Faz 3. Yalnız sürekli aşırı yükte reader bloklanır (bugünkü davranış, ama kayıpsız/sıralı).
- **Executor leak:** Decorator replace/logoff'ta shutdown unutulursa thread leak. Mitigasyon: lifecycle adımı + test.
- **Faz 2 invazivliği:** İki bağlantı, iki login/logoff, state semantiği. Daha riskli → Faz 1 commit'inden SONRA ayrı commit (koşulsuz yapılacak); cross-decorator/AGI lookup regression testi.
- **AGENTS uyumu:** Yeni kütüphane YOK (JDK `java.util.concurrent` + mevcut asterisk-java) ✅; yeni static mutable state YOK (instance-level) ✅; AMI/event/result/Redis contract değişmez ✅; hot path `Performans Etkisi` var ✅; "refactor+fix karıştırma" → her faz ayrı commit ✅.
- **Doğrulama sınırı:** Predictive yük lokalde load-test edilemez; asıl kanıt prod'da kopma frekansının düşmesi. Lokalde hedefli JUnit + offline Maven (artifact eksikse blocker raporlanır).

## TDD Stratejisi

`gbpd-tdd-workflow` + `gbpd-characterization-tests`. Önce mevcut davranışı kilitle, sonra refactor. Testler `tests/ami/` altında mevcut stille; `eventWorker` testte **senkron/deterministik executor** enjekte edilir (sleep yok).

- **Faz 1**: karakterizasyon (mevcut senkron davranış kilidi) + `AmiEventWorkerOffloadRegressionTest` (çağıran thread bloklanmaz; handler yine çağrılır) + `AmiEventWorkerOrderingRegressionTest` (FIFO sıra) + overflow blocking-put (dolu-queue deterministik executor; drop yok).
- **Faz 2**: `AmiCommandConnectionSplitRegressionTest` (originate/hangup command bağlantısından; event'ler event bağlantısından; cross-decorator/AGI lookup korunur).
- **Faz 3**: `CdrPersistRoundTripReductionRegressionTest` (gereksiz SELECT yok) + `CdrBatchOrderingNoLossRegressionTest` (sıra + kayıpsızlık + flush).
- Regresyon: `ConnectionLossBridgedCallRecoveryRegressionTest`, `SaveInitialCdrHangupResultRegressionTest`, `AwaitingUserResultCacheTransitionRegressionTest`, `TransferReleaseAgentRegressionTest`, ilgili Gamma/manager testleri.

## Açık Sorular

- [x] **#1 Overflow politikası:** **Bounded + blocking-put** (drop YOK, sıra korunur, CallerRuns DEĞİL). Geliştirici kritik şartı: hiçbir DB yazımı kaybedilmeyecek ve DB yazım sırası korunacak. Kapasite ~50.000 (`Constants` sabiti).
- [x] **#2 Kill-switch:** Hayır. Config yüzeyi açılmaz; geri dönüş revert/redeploy.
- [x] **#3 Faz 2 koşulu:** Koşulsuz uygulanacak (Faz 1 prod'u çözse de). Faz 1 commit'inden sonra ayrı commit.
- [x] **#4 Faz 3 derinliği:** Agresif batch/async upsert — sıra + kayıpsızlık + garantili flush kısıtıyla.
- [x] **#5 Pilot/rollout:** Faz 1 deploy sonrası en çok rc75 alan kampanyada min. 1 iş günü gözlem (varsayılan; geliştirici override edebilir).
- [x] **#6 Kütüphane teyidi:** Davranışsal kabulle ilerlenir; tasarım her durumda doğru. Gerekirse jar sonra teyit edilir.

## Execution Plan

Geliştirici plan onayından sonra faz faz, her faz kendi commit'i ve testleriyle.

**Hazırlık**
- [x] Sözleşmeleri (AGENTS.md, change-fix-plan README, skill'ler) ve AMI akışını oku.
- [x] develop'tan `ami-route-cause-fix` branch'i aç (working tree temiz).
- [x] Bu change-fix-plan dosyasını oluştur ve açık soruları cevapla.
- [x] **Geliştirici plan onayı** (kod değişikliğinden önce zorunlu).

**Faz 1 — event worker offload (4 commit: `29a905ed` test, `0626674a` refactor worker, `3d9f64f0` drain edges, `21c7a7d3` exception+timeout)**
- [x] Karakterizasyon testlerini yaz/genişlet (mevcut senkron davranış kilitli).
- [x] `AmiEventWorkerOffloadRegressionTest` + `AmiEventWorkerOrderingRegressionTest` + `AmiEventWorkerNoLossRegressionTest` + `AmiEventWorkerHandlerExceptionRegressionTest` + `AmiPreDestroyDrainRegressionTest` + `DecoratorReplaceWorkerDrainRegressionTest`.
- [x] `eventWorker` alanı + enjeksiyon seam + blocking-put rejected-handler + lifecycle (`createConnection`/`logoff`/replace → `shutdown()`+`awaitTermination` drain).
- [x] `onManagerEvent` ve originate-fail callback'i worker'a devret; 200ms eşik logu + kuyruk derinliği logu; handler exception'ında worker'ı koru+logla.
- [x] Testleri yeşile çevir; regresyon koş. (Final holistic review: READY TO SHIP.)

**Faz 2 — event/command bağlantı ayrımı (commit `7622b23f`: `refactor(ami): originate/hangup'ı event'siz command bağlantısına ayır`)**
- [x] `AmiCommandConnectionSplitRegressionTest` (5 test) failing→yeşil.
- [x] Decorator'da `eventConnection`/`commandConnection`; Composition kurulum/kapanış; state event-authoritative (recovery değişmedi).
- [x] originate/hangup → command bağlantısı; cross-decorator/AGI lookup + recovery regresyonu yeşil.

**Faz 3 — per-event DB maliyeti (commit `5e93936c`: `perf(ami): answer/hangup CDR round-trip'ini azalt`)**
- [x] (A) Gereksiz `getByInteractionId` SELECT eliminasyonu (one-shot `Call.markCdrPersistLookupAttempted`); çağrı başına ≤1 SELECT. `CdrPersistRoundTripReductionRegressionTest` failing→yeşil.
- [~] (B) Agresif batch/async **bilinçli olarak ertelendi**: `saveOrUpdate` `@Transactional` ve 6+ çağıran tarafından senkron flush/görünürlüğe bağımlı; batch/defer paylaşımlı contract'ı bozar, coalescing ara state kaybeder → kritik no-loss/order şartını ihlal eder. Güvenli gelecek tasarımı Tamamlanma Notu'nda önerildi. Yalnız READ elendi; hiçbir yazım sırası/sayısı değişmedi.

**Kapanış**
- [x] `docs/architecture/ASTERISK-AMI-AGI.md` güncellendi (yeni event-işleme thread modeli + bağlantı topolojisi).
- [x] `gbpd-agent-history` ile `docs/history/16.06.2026.md` + indeks.
- [x] `gbpd-commit-discipline` (her faz spec+kalite review'i + diff kontrolü; secret/ilgisiz değişiklik yok).
- [ ] **Pre-prod doğrulaması** (üç fazın bütünü) ve sonrası prod'da kopma frekansı izleme (pilot) — geliştiricide.

## Performans Etkisi

- **Hot path durumu:** AMI event başına + originate response başına (yüksek frekans).
- **Faz 1:** Reader thread'inden senkron DB/CDR/log I/O **kaldırılır** → reader'da net **negatif maliyet** (bağlantı sağlığı korunur). Eklenen: event başına 1 `execute()` (O(1)) + 1 tek-thread worker; bellek: bounded queue (küçük event nesneleri). **Hot path, ek maliyet azaltıldı.**
- **Faz 2:** Komut yolu event yükünden izole; command bağlantısı reader'ı boşta. Ek maliyet: sunucu başına 1 ek soket (gbwebphone ile aynı, kanıtlı).
- **Faz 3:** Event başına DB round-trip azaltılır + yazımlar batch/async → worker latency düşer, backlog küçülür. Kısıt: sıra + kayıpsızlık + garantili flush korunur.
- **Doğrulama:** Lokalde load-test mümkün değil → **üretim doğrulaması (kopma frekansı) deploy sonrası beklemede.**

## Doğrulama

`.codex/TOOL-PATHS.local.properties` okunacak; Maven offline + `properties-tr-dev` profili. Artifact eksikse indirme denenmez, blocker raporlanır.

Hedefli komut deseni:

```text
mvn -o -P properties-tr-dev -Dtest=AmiEventWorkerOffloadRegressionTest,AmiEventWorkerOrderingRegressionTest test
mvn -o -P properties-tr-dev -Dtest=ConnectionLossBridgedCallRecoveryRegressionTest,SaveInitialCdrHangupResultRegressionTest,AwaitingUserResultCacheTransitionRegressionTest,TransferReleaseAgentRegressionTest test
```

## Kapanış Kriteri

- [x] Açık sorular cevaplandı ve plana işlendi.
- [ ] Her fazın yeni TDD testleri yeşil.
- [ ] İlgili AMI/manager regresyon testleri yeşil.
- [ ] Mimari doküman güncellendi.
- [ ] History kaydı + indeks eklendi.
- [ ] Final yanıtta kısa Test Özeti sunuldu.
- [ ] (Post-deploy) Pilot kampanyada kopma frekansı düşüşü gözlendi.

## Tamamlanma Notu

Uygulama tamamlandı (2026-06-16), branch `ami-route-cause-fix` (develop'tan), **push edilmedi**. Subagent-driven, her faz TDD + iki aşamalı review (spec + kod kalitesi) + tüm değişiklik için holistic final review.

**Commit'ler (develop `697a2626` → HEAD `5e93936c`):**
- `29a905ed` test(ami): onManagerEvent dispatch karakterizasyonu
- `0626674a` refactor(ami): event/callback işini reader thread'inden bounded single-thread worker'a al
- `3d9f64f0` fix(ami): worker'ı graceful shutdown ve decorator-replace yollarında drain et (kayıpsızlık)
- `21c7a7d3` fix(ami): event worker'ı handler exception'ında koru+logla; replace-path drain timeout'unu kısalt
- `7622b23f` refactor(ami): originate/hangup'ı event'siz command bağlantısına ayır
- `5e93936c` perf(ami): answer/hangup CDR round-trip'ini azalt

**Üretim kodu değişiklikleri:** `ManagerConnectionDecorator.java` (eventWorker + iki bağlantı + lifecycle/drain + exception guard + CDR SELECT gate), `ManagerConnectionComposition.java` (preDestroy → decorator.logoff drain), `CallOriginator.java` (replace-path worker drain), `Constants.java` (worker kuyruk kapasitesi + timeout sabitleri), `Call.java` (transient `cdrPersistLookupAttempted` flag).

**Invariant'lar (kanıtlı):** tek-thread worker (sıra) · blocking-put (drop yok) · drain-on-shutdown/replace/preDestroy (kayıp yok) · handler exception'ında worker hayatta + app log · event-handler/processCallResult/sayaç/CDR sırası/result code/Redis/recovery semantiği değişmedi · komut yolu event'siz `commandConnection` ile izole.

**Test Özeti (offline Maven, `properties-tr-dev`, JDK 1.8):**
- Yeni testler: `AmiEventDispatchCharacterizationRegressionTest`(5), `AmiEventWorkerOffloadRegressionTest`(1), `AmiEventWorkerOrderingRegressionTest`(1), `AmiEventWorkerNoLossRegressionTest`(2), `AmiEventWorkerHandlerExceptionRegressionTest`(1), `AmiPreDestroyDrainRegressionTest`(1), `DecoratorReplaceWorkerDrainRegressionTest`(2), `AmiCommandConnectionSplitRegressionTest`(5), `CdrPersistRoundTripReductionRegressionTest`(2).
- Final HEAD'de hedefli blast-radius regresyonu (AMI + dialer + recovery + CallOriginatorTest dâhil 14 sınıf): **76/76 yeşil**. Ek collateral: 61/61 yeşil.
- TDD red→green her fazda doğrulandı.

**Pre-existing test gürültüsü (BİZİM DEĞİL):** Full suite'te yalnız 4 sınıf fail eder — `ACDEngineTest`, `DefaultFindMatchingUserStrategyTest` (zaman/`statusSetDate` bağımlı "most idle" sıralaması), `CallProducerTest` (develop'ın `ff71b1b1` CDR commit'ine ait test-NPE), `StopStateTest` (`Bug #38994 - Unit tests fail at 00:00 UTC` ile belgeli zaman-flaky + PowerMock static-mock kirlenmesi). Branch'imiz bu 4 sınıfın ne kendisine ne üretim koduna dokunur (git diff ile kanıtlı); izolasyonda da aynen fail ederler → develop'ta da var. Pre-prod'da bu 4 fail yanıltıcı değildir.

**Yapılmayan (B):** Faz 3'te agresif batch/async DB **bilinçli ertelendi** — `CDRService.saveOrUpdate` paylaşımlı `@Transactional` contract'ı + per-campaign dinamik SQL nedeniyle kritik no-loss/order şartı kanıtlanabilir korunamadığı için. Önerilen güvenli gelecek tasarımı: tek worker FIFO arkasında, yalnız worker'a özel `saveOrUpdateBatch(List<CDR>)` + garantili drain-on-shutdown, **yalnız farklı çağrıların final yazımlarını** batch'le (tek çağrının ara state'lerini asla coalesce etme); öncesinde tüm `getByInteractionId`/result-table reader'larının davranış denetimi.

**Operasyonel izleme notu:** Recovery state event bağlantısında olduğu için, yalnız `commandConnection` düşerse (iki ayrı socket olduğundan nadir) loss-recovery yoluna girilmez; originate temiz fail+recycle eder ve autonomous reconnect iyileştirir. Deploy sonrası birincil metrik (AMI disconnect frekansı) yanında **origination-fail RC spike**'ı da izlenmeli.

**Bekleyen:** pre-prod doğrulaması (üç fazın bütünü) ve prod pilot gözlemi — geliştiricide. Predictive yük lokalde load-test edilemez; kök-neden fix'inin asıl kanıtı prod'da kopma frekansının düşmesidir.
