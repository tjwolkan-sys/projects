# FEAT FCT Kanal Kapasite Gate ve Canlı Monitor Uygulama Planı

## Analiz Referansı

`docs/analyse/07.08.2026/FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE.md`

## Plan Özeti

QSS `getRouteDetails` yanıtındaki her `RouteDetailEntry` yeni bir `capacity` alanı taşır. PowerDialer, numara bazlı (bir numara = bir FCT) kanal doluluğunu yeni `FctChannelRegistry` bileşeninde tutar; `processOrigination` başında rezervasyon alır, alamazsa çağrıyı yakmadan kuyruğa geri koyar. `cause 34` hangup'ında ilgili numara release edilip cooldown'a alınır. Rezervasyonlar `callId` set'i olarak tutulduğu için sayısal drift oluşmaz; TTL prune kaçan release'leri self-healing temizler ve bunu sağlık sinyali olarak raporlar. Registry'nin iç durumu `GET /fctChannel/snapshot` ile dışa verilir ve AngularJS `Options` menüsü altındaki yeni "FCT Kanal Monitörü" ekranında canlı izlenir. Gate `capacity` gelmediğinde veya kill switch kapalıyken tamamen pasiftir; bu durumda 04.05.2026 FCT routing davranışı birebir korunur.

## Amaç

FCT kapasitesi dolu numaraya originate atmayı önleyerek üretimdeki `cause 34 → TRC 25` kaybını reaktif kurtarma yerine proaktif olarak engellemek; kapasite doluluğunu, cooldown'ları ve release kaçaklarını operasyonun canlı izleyebileceği bir ekranla gözlemlenebilir kılmak.

## Kapsam Dışı

- QSS tarafındaki kapasite tanımı ekranı/verisi (geliştirme QSS ekibinde).
- Registry'nin Redis'e taşınması, restart'a dayanıklılık ve multi-node paylaşımı.
- Kampanya bazlı kapasite kotası (fair-share/quota) modeli.
- `route()`, `obtainCallerId`, `applyRetryStrategy`, `RetryStrategyFactory` ve predictive alpha/beta/gamma iç mantığı.
- Preview kampanyalarda gate uygulanması (originate'i webphone attığı için bu fazda dışarıda).
- `Campaign` tablosunda yeni kolon veya yeni DB migration (mevcut `fct_routing_enabled` flag'i yeniden kullanılır).
- `deleteProvisionHistory` / `onCallComplete` semantiğinin değiştirilmesi.
- Tüm QSS FCT envanterinin monitor'de gösterilmesi (yalnız registry'nin gördüğü numaralar listelenir).

## Performans Etkisi

- **Hot path durumu**: Evet — `CallOriginator.pollCallsFromConsumerAndOriginate`, `CallOriginator.processOrigination`, `ManagerConnectionDecorator.handleDialerHangup`.
- **Originate başına ek maliyet**:
  - Cursor seçiminde entry başına O(1) cooldown map lookup.
  - `processOrigination` başında per-numara kısa `synchronized` blok: O(effectiveCapacity) TTL prune + set ekleme. Global lock yok.
  - `getRouteDetails` gRPC çağrı sayısı **değişmez**.
- **Hangup başına ek maliyet**: release O(kapasite) map işlemi; `cause 34`'te ek O(1) cooldown yazımı. Yeni DB query veya blocking network çağrısı yok.
- **Event log**: olay başına O(1) push + bounded pop (varsayılan 200). `PRUNE`/`SKIP` için 5 dk pencereli timestamp deque'i yazımda budanır.
- **Snapshot endpoint**: hot path dışı, HTTP thread'inde. O(numara × kapasite) kopya üretimi, per-numara kısa lock, varsayılan 1 sn snapshot cache'i ile poll yükü sınırlı.
- **IO/scan/lock/serialization/log**: yeni DB query, tam koleksiyon taraması, geniş `synchronized` blok veya per-event verbose payload log'u eklenmez. Tekrarlayan skip log'u DEBUG seviyesinde tutulur.
- **Azaltım/ölçüm**: monitor ekranı ölçüm aracının kendisidir (doluluk, prune, skip). Headroom, cooldown, TTL ve defer limiti property'lerle ayarlanır; `fct.channel.gate.enabled=false` tek hamlede eski davranışa döner.

## TDD Planı

### Karakterizasyon (önce kilitle)

- [x] `tests/dialer/routing/FctRoutingGateDisabledCharacterizationTest` (3 test): `capacity=0` ve `fct.channel.gate.enabled=false` durumlarında FCT routing akışı bugünküyle birebir aynı — liste sırasındaki ilk entry uygulanır, cursor ilerletilmez, `cause 34`'te sıradaki entry'ye geçilir ve cooldown akışı kısmaz. Bu test aynı zamanda rollback yolunun (kill switch) davranış kanıtıdır.
- [x] ~~`DeferOriginationAttemptRestoreCharacterizationTest`~~ — **yazılmadı, gerekmedi**: mevcut `tests/dialer/PauseCampaignOriginatorRegressionTest` bu davranışı (attempt geri alma, keeper unregister, kuyruk başına koyma, originate atmama) zaten kilitliyor ve gate defer'i aynı primitive'i kullanıyor. Regresyon koşusuna dahil edildi ve yeşil.

### Yeni davranış

- [x] `tests/dialer/routing/FctChannelRegistryTest` (11 test): kapasite sınırı, `capacity<=0` fail-open, gate kill switch, idempotent + owner kontrollü release, aynı `callId` çift acquire, cooldown başlangıç/bitiş, TTL prune + `PRUNE` event'i + KPI sayacı, TTL altındaki uzun çağrının prune EDİLMEMESİ, headroom (`effectiveCapacity` en az 1), snapshot alanları.
- [x] `tests/dialer/routing/FctChannelRegistryConcurrencyTest` (1 test): 24 thread eşzamanlı `tryAcquire`, kapasite 5 → tam olarak 5 başarı.
- [x] `tests/dialer/routing/FctChannelRegistryCampaignCleanupTest` (4 test): `releaseAllForCampaign` yalnız hedef kampanyanın rezervasyonlarını düşürür; null campaignId no-op; idle numara retention sonrası düşer; canlı rezervasyonu olan numara düşmez.
- [x] ~~`FctChannelRegistrySnapshotTest`~~ — ayrı sınıf yazılmadı; snapshot kapsamı `FctChannelRegistryTest` ve `FctChannelMonitorControllerTest` içinde doğrulandı.
- [x] `tests/dialer/routing/FctChannelGateTest` (9 test) — **plan eki**: gate karar mantığı ayrı test edildi (ilk uygun entry, dolu/cooldown atlama, hepsi uygunsuz, preview muafiyeti, FCT routing kapalı muafiyeti, cause 34 release+cooldown+sıradaki, liste tükenmesinde cooldown, idempotent release).
- [x] `tests/dialer/originator/CallOriginatorFctGateTest` (3 test): rezervasyon `processOrigination` başında ve pause kontrolünden sonra alınır; hepsi dolu ise `originateCall` çağrılmaz, defer edilir, attempt artmaz; kapasite varsa originate edilir; dolu FCT atlanıp sıradaki rezerve edilir.
- [x] `tests/ami/ManagerConnectionDecoratorFctCause34CooldownTest` (2 test): `cause 34`'te numara release + `markFull`, sıradaki uygun entry rezerve edilip yeni Originate; uygun entry yoksa standart `processCallResult` ve yine cooldown.
- [x] ~~`FctChannelReleaseWiringTest`~~ — ayrı sınıf yazılmadı; release semantiği `FctChannelRegistryTest` + `FctChannelGateTest`, wiring'in kendisi cause 34 testinde uçtan uca doğrulandı.
- [x] `tests/controller/FctChannelMonitorControllerTest` (5 test): snapshot JSON şeması, `@PreAuthorize` yetkisi, `/fctChannel` path eşleşmesi, boş registry, cache penceresi.
- [x] `tests/angular/fct/FctChannelMonitorContractTest` (7 test): route, WRO sırası, Options menü girdisi, `track by` kullanımı, reduced-motion/overflow/focus-visible, factory `skipErrorRedirect`, dört dilde anahtarlar.
- [ ] `src/test/angular/fct/fctChannelMonitor.spec.js` (18 spec yazıldı): polling aralığı, Duraklat, Auto Refresh, arama + tip filtresi, DURUM türetme, cooldown geri sayımı, lamba hesabı (≤10 ve >10 ölçekleme), yaş chip'leri/overflow, dinamik tip seçenekleri, feed etiketleri ve kısa callId, KPI, snapshot hatası, stale rezervasyon, feed satır limiti. **Koşulamadı — `node_modules` blocker'ı (Adım 32).**

## Uygulama Kontrol Listesi

### Faz A — Proto ve konfigürasyon

- [x] **Adım 1**: `src/main/proto/resource-distribution-service.proto` içinde `RouteDetailEntry`'ye `int32 capacity = 4;` eklendi ve `attempts` QSS canonical tipine (`string`) hizalandı.
- [x] **Adım 2**: `mvn -o test-compile` ile stub regenerate edildi; `RouteDetailEntry.getCapacity()` üretildi (BUILD SUCCESS, 794 production + 189 test source).
- [x] **Adım 3**: `application.properties` içine literal varsayılanlar eklendi (`previewTimeoutOriginationDelaySec` stiliyle, profil filtresi kullanılmadı):
  - `fct.channel.gate.enabled = true`
  - `fct.channel.ttl.ms = 3900000` — **plan sapması**: analizdeki 60 sn değeri üretimde yanlış olurdu. `Constants.CALL_EXPIRE_TIMEOUT = 3600000` dialer'ın "bu çağrı artık yaşayamaz" eşiği; TTL bunun altında kalırsa konuşma hâlindeki uzun çağrının kanalı prune ile erken serbest bırakılır ve tam olarak engellemeye çalıştığımız over-dial oluşur. TTL = `CALL_EXPIRE_TIMEOUT + 5 dk` seçildi.
  - `fct.channel.stale.warn.ms = 600000` — **plan eki**: TTL uzun olduğu için "kaçmış release adayı" uyarısı ayrı ve kısa bir eşikten gelir; monitor ekranı bu eşiği geçen rezervasyon yaşını kırmızı gösterir (prune etmez).
  - `fct.channel.cooldown.ms = 60000`
  - `fct.channel.headroom.percent = 10`
  - `fct.channel.meta.retention.ms = 1800000`
  - `fct.channel.event.log.size = 200`
  - `fct.channel.defer.limit = 10`
  - `fct.channel.snapshot.cache.ms = 1000`

### Faz B — Registry

- [x] **Adım 4**: Karakterizasyon testleri yazıldı ve yeşil geçti (TDD Planı ilk iki madde; ikinci madde mevcut `PauseCampaignOriginatorRegressionTest` ile karşılandı).
- [x] **Adım 5**: `com.powerdialer.engine.dialer.routing.FctChannelRegistry` `@Component` olarak eklendi (`AgentDialLockRegistry` kalıbı, JDK koleksiyonları, yeni kütüphane yok):
  - Per-numara state: metadata (`type`, `attempts`, `capacity`, `lastSeenMs`), `LinkedHashMap<callId, Reservation{reservedAtMs, campaignId}>`, `cooldownUntilMs`, `lastEventMs`.
  - Bounded event log (`ArrayDeque`, `fct.channel.event.log.size`) ve `PRUNE`/`SKIP` için 5 dk pencereli timestamp deque'leri.
  - API: `tryAcquire(number, type, attempts, capacity, callId, campaignId)`, `release(number, callId)`, `markFull(number)`, `releaseAllForCampaign(campaignId)`, `isInCooldown(number)`, `occupancy(number)`, `snapshot()`.
  - Kapasite kontrolü + ekleme aynı per-numara `synchronized` bloğunda; TTL prune acquire ve snapshot yollarında, prune edilen her rezervasyon için tek `PRUNE` event'i.
  - `capacity <= 0` veya gate kapalı → `tryAcquire` her zaman `true` (fail-open), rezervasyon yine kaydedilir (monitor doğru göstersin).
  - Startup'ta tek `registry cold start` WARN'u.
- [x] **Adım 6**: `FctChannelRegistryTest` (11), `FctChannelRegistryConcurrencyTest` (1), `FctChannelRegistryCampaignCleanupTest` (4) yazıldı ve yeşil geçti. Snapshot doğrulaması ayrı sınıf yerine `FctChannelRegistryTest.shouldExposeCapacityAndInFlightInSnapshot` + `FctChannelMonitorControllerTest` içinde kapsandı (aynı kapsam, daha az kurulum tekrarı).

### Faz C — Dialer entegrasyonu

- [x] **Adım 7**: `ManagerConnectionComposition`'a `@Autowired FctChannelGate` + `getFctChannelGate()` eklendi (`getAgentDialLockRegistry` kalıbı). **Sapma**: `CallOriginator` constructor'ı değiştirilmedi; gate'e `connectionDecorator.getConnectionComposition().getFctChannelGate()` üzerinden erişiliyor. Böylece `DialerFacade`'deki iki `new CallOriginator(...)` çağrısı ve mevcut testlerin kurulumu bozulmadı; gate null ise (Spring dışı testler) akış bugünkü davranışa düşüyor.
- [x] **Adım 8**: Ek cursor metodu **gerekmedi**: ilerletme mantığı `FctChannelGate` içinde mevcut `current()`/`next()` API'siyle yürüyor. `RouteDetailsCursor` değişmedi (kırılgan legacy yüzey büyütülmedi).
- [x] **Adım 9**: **Sapma — bilinçli sadeleştirme**: cursor kurulurken ayrı cooldown filtresi eklenmedi. Rezervasyon adımı (Adım 10) cooldown'daki entry'yi zaten atlıyor ve caller ID'yi güncelliyor; iki ayrı karar noktası yerine tek karar noktası tutuldu. Sonuç davranış aynı, dokunulan kod yolu daha az.
- [x] **Adım 10**: `CallOriginator.processOrigination` başında (pause kontrolünden sonra, `callQueue.add`'den önce) `reserveFctChannel(call)` ile rezervasyon alınıyor; cursor pozisyonundan itibaren `tryAcquire` denenip başarısızsa ilerletiliyor, başarıda `call.setCallerId(...)` güncelleniyor.
- [x] **Adım 11**: Gate defer yolu eklendi: `returnCallToPausedCampaignQueue` → `returnCallToCampaignQueue` olarak genelleştirildi, `deferOriginationUntilFctChannelAvailable(call)` mevcut `cleanupCallBeforeDeferredOrigination` + requeue primitive'ini kullanıyor. Defer sayacı `Call.incrementFctChannelDeferCount()` (transient) ile tutuluyor; `fct.channel.defer.limit` aşılırsa `cancelCall(..., TELEPHONE_RC_NO_CALLERID_AVAILABLE, false)` mevcut davranışına düşülüyor.
- [x] **Adım 12**: `ManagerConnectionDecorator.tryFctRetryWithNextCallerId` güncellendi: `advanceToNextFctChannel` → gate varsa `reserveNextAfterNoCircuit` (release + `markFull` + sıradaki uygun entry için `tryAcquire`), gate yoksa eski cursor davranışı. Uygun entry yoksa `false` dönüp standart `processCallResult` zinciri çalışıyor; hangup'ı veren FCT bu durumda da cooldown'a alınıyor.
- [x] **Adım 13**: Release noktaları bağlandı — `releaseTelephonySlotIfNeeded`, `CallCleaner.clearExpiredCall`, `ManagerConnectionDecorator.handlePreviewTimeout`, `handleExceptionDuringOrigination`. **Plan eki**: release ayrıca `CallCleaner.completeCall` içine bağlandı; bu funnel `RetryStrategy`, `UserLoginLogoutHandler`, `CampaignStopJob.clearCallAndSaveResultToDb`, `CallOriginator.cancelCall` ve `manageRetries` yollarını da kapsadığı için forceful stop için ayrı bir dokunuş gerekmedi (release idempotent olduğu için çoklu kapsama zararsız).
- [x] **Adım 14**: `CampaignStopJob.lastCleanUpChecks` içine `releaseAllForCampaign(campaignId)` safety net'i eklendi. Start/pause/reset/reload yollarına **dokunulmadı**.
- [x] **Adım 15**: Gate koşulu `FctChannelGate.isGatePath(call)` içinde tek noktada tanımlandı: `fctRoutingEnabled && callerIdBehaviour == POOL && !isPreviewCampaign`. Kill switch registry seviyesinde (`gateEnabled`) uygulanıyor, böylece kapalıyken de doluluk izlenmeye devam ediyor. `CallOriginator.isFctRoutingPath` (cursor kurulum koşulu) preview'ı dışlamıyor: preview kampanyalarda FCT routing korunuyor, yalnız rezervasyon uygulanmıyor.
- [x] **Adım 16**: `CallOriginatorFctGateTest` (3), `ManagerConnectionDecoratorFctCause34CooldownTest` (2), `FctChannelGateTest` (9) yazıldı ve yeşil geçti. **Sapma**: `FctChannelReleaseWiringTest` ayrı sınıf olarak yazılmadı — release yolları tek satırlık `releaseFctChannel(call)` çağrıları ve gerçek doğrulama değeri idempotent release semantiğinde olduğu için bu `FctChannelRegistryTest.shouldTreatReleaseAsIdempotentAndOwnerScoped` + `FctChannelGateTest.shouldReleaseReservationForCall` ile kapsandı. Wiring'in kendisi cause 34 testinde uçtan uca çalışıyor.

### Faz D — Monitor API

- [x] **Adım 17**: DTO'lar eklendi (`com.powerdialer.dto`): `FctRegistrySnapshotDTO`, `FctChannelEntryDTO`, `FctInFlightDTO`, `FctChannelEventDTO`, `FctChannelKpiDTO`. Şemaya plan üstüne `staleWarnMs`, `FctChannelEventDTO.id` (AngularJS `track by` anahtarı) ve `kpi.rejectedLast5Min` eklendi. JSON şeması:

```json
{
  "serverTimeMs": 1723012345678,
  "ttlMs": 60000,
  "cooldownMs": 60000,
  "headroomPercent": 10,
  "gateEnabled": true,
  "entries": [
    {
      "phoneNumber": "+90 555 123 45 67",
      "type": "MOBILE",
      "attempts": "1240",
      "capacity": 10,
      "effectiveCapacity": 9,
      "inFlight": [ { "callId": "dl-...4211", "sinceMs": 1723012345678 } ],
      "cooldownUntilMs": 0,
      "lastEventMs": 1723012345678
    }
  ],
  "recentEvents": [
    { "ts": 1723012345678, "kind": "ACQUIRE", "phoneNumber": "...", "callId": "...", "detail": "" }
  ],
  "kpi": { "prunedLast5Min": 12, "skippedLast5Min": 34 }
}
```

- [x] **Adım 18**: `com.powerdialer.controller.FctChannelMonitorController` eklendi: `@RestController @RequestMapping("/fctChannel")`, `GET /snapshot`, `@PreAuthorize("hasAuthority('" + Constants.PERMISSION_DIALER_ADMINISTRATION + "')")` (`ServiceStatusesController` kalıbı). `fct.channel.snapshot.cache.ms` süresince snapshot cache'leniyor.
- [x] **Adım 19**: `WEB-INF/security.xml` içine `<sec:intercept-url pattern="/fctChannel/**"/>` eklendi (`/serviceStatuses/**` satırının hemen altına).
- [x] **Adım 20**: `FctChannelMonitorControllerTest` (5) yazıldı ve yeşil geçti — DTO şeması, yetki anotasyonu, path eşleşmesi, boş registry ve cache davranışı.

### Faz E — Monitor ekranı (AngularJS)

- [x] **Adım 21**: `js/services/fctChannelFactory.js` eklendi: `$http.get('fctChannel/snapshot', {skipErrorRedirect: true})` — polling hatası `/error` route'una yönlendirmiyor.
- [x] **Adım 22**: `js/controllers/fctChannelMonitorCtrl.js` eklendi:
  - `$interval` polling (1s / 2s / 5s), Auto Refresh toggle, Duraklat/Devam.
  - Arama (numara) + tip filtresi (dinamik, snapshot'taki distinct `type` değerlerinden) + tablo/kart görünüm toggle'ı; state sayfa içinde tutulur, `localStorage` kullanılmaz.
  - Türetmeler: doluluk yüzdesi, DURUM (`COOLDOWN mm:ss` / `FULL` / `HIGH USAGE` / `MEDIUM` / `HEALTHY` / `IDLE`), lamba dizisi (`LAMP_MAX = 10`; kapasite > 10 ise her lamba `ceil(capacity/10)` kanal), en yaşlı 3 rezervasyon yaş chip'i + `+N`.
  - `serverTimeMs` ile local saat farkı düzeltilerek 1 sn'lik yaş/cooldown sayaçları; **sapma**: kırmızı "kaçmış release adayı" eşiği TTL'in %80'i yerine `staleWarnMs` (10 dk) — TTL 65 dk olduğu için %80 (52 dk) operasyonel olarak işe yaramazdı.
  - 60 saniyelik doluluk geçmişi (sparkline verisi) ve alt KPI'lar (TTL, ortalama in-flight yaş, prune/skip son 5 dk).
- [x] **Adım 23**: `pages/fctChannelMonitor.html` eklendi — `images/FctChannelRegistry.png` düzeni: header + LIVE pill + Auto Refresh switch + aralık seçici + Duraklat + saat/tarih; 5 hücreli aggregate şerit + SVG sparkline (amber çizgi + degrade dolgu, 100/50/0% ekseni); FCT numara paneli (arama, dinamik tip filtresi, tablo/kart toggle, kolonlar NUMARA / KAPASİTE / KANALLAR (LAMBALAR) / IN-FLIGHT / DOLULUK / DURUM / SON GÜNCELLEME, alt legend + toplam numara); sağda sticky CANLI AKTİVİTE FEED; altta 4 KPI hücresi. Stil sayfa içi `<style>` bloğunda CSS değişkenleriyle; `prefers-reduced-motion: reduce` tüm animasyonları kapatıyor; `:focus-visible` görünür; 1050px altında feed alta iniyor ve tablo yatay scroll alıyor. Ek olarak gate kapalıyken ve snapshot hatasında bilgi banner'ları eklendi.
- [x] **Adım 24**: Yanıp sönme önlendi: tüm `ng-repeat`'ler `track by` ile (`entry.phoneNumber`, `chip.callId`, `$index`, `event.id`, `type`); filtre sonucu her poll'da controller içinde hesaplanıp alana yazılıyor, template fonksiyon çağırmıyor.
- [x] **Adım 25**: `app.js` route'u eklendi: `.when('/fctChannelMonitor', { templateUrl: 'pages/fctChannelMonitor.html', controller: 'fctChannelMonitorCtrl as fctMonitor' })`.
- [x] **Adım 26**: `index.jsp` Options dropdown'una girdi eklendi (`ROLE_SUPERADMIN`); üst `<li>`'nin `ng-class` aktiflik listesine `isActive('/fctChannelMonitor')` eklendi.
- [x] **Adım 27**: `WEB-INF/wro.xml` `global` grubuna `/js/services/fctChannelFactory.js` ve `/js/controllers/fctChannelMonitorCtrl.js` eklendi (app.js'ten sonra, factory controller'dan önce).
- [x] **Adım 28**: `localization_{tr,en,ru,ua}.js` dosyalarına 45 anahtar eklendi. Teknik terimler (ACQUIRE, RELEASE, IN-FLIGHT, cooldown, FULL, HIGH USAGE, MEDIUM, HEALTHY, IDLE, Auto Refresh, TTL, prune, skip) İngilizce bırakıldı; başlık PNG'deki gibi "FCT Channel Monitor".
- [x] **Adım 29**: `tests/angular/fct/FctChannelMonitorContractTest` (7 test) yazıldı ve yeşil geçti. `src/test/angular/fct/fctChannelMonitor.spec.js` (18 spec) yazıldı; `karma.conf.js` dosya listesine yeni JS'ler eklendi. **Blocker**: `node_modules` kurulu olmadığı için Karma koşulamadı (aşağıya bakın).

### Faz F — Kapanış

- [x] **Adım 30**: Hedefli Java testleri çalıştırıldı: **61 test, 0 failure, 0 error** (BUILD SUCCESS).
- [x] **Adım 31**: `mvn -o -DskipTests package` → BUILD SUCCESS, `gbpowerdialer.war` üretildi.
- [ ] **Adım 32**: `npm run test:angular` **çalıştırılamadı — blocker**: `gbpowerdialer/node_modules` yok, `karma` binary'si bulunamıyor. `NPM_CMD` artık dolu (`C:/Program Files/nodejs/npm.cmd`, npm 10.9.2) ama Karma bağımlılıkları hiç kurulmamış. Ajan dependency indirmeyi denemez; geliştirici bir kez `npm install` çalıştırdıktan sonra `npm run test:angular -- --spec=fctChannelMonitor.spec.js` koşulmalıdır. Spec dosyası ve `karma.conf.js` girdileri hazır.
- [x] **Adım 33**: `Performans Etkisi` bölümü gerçek patch'e göre doğrulandı (aşağıdaki not).
- [x] **Adım 34**: `docs/architecture/DIALER-RUNTIME.md` ve `docs/architecture/API-AND-UI.md` güncellendi.
- [x] **Adım 35**: `docs/history/07.08.2026.md` dosyasına append-only kayıt eklendi.

### Performans Etkisi Doğrulaması (gerçek patch)

- Originate başına eklenen iş: `processOrigination` başında bir per-numara `synchronized` blok; blok içinde O(kapasite) TTL prune + `LinkedHashMap.put`. Yeni gRPC/DB/IO yok, `getRouteDetails` çağrı sayısı değişmedi.
- Hangup başına: `releaseTelephonySlotIfNeeded` içinde bir `LinkedHashMap.remove`; cause 34'te ek olarak `markFull` (O(1)) ve sıradaki entry için `tryAcquire`.
- Cursor kurulumunda ek iş **yok** (Adım 9 sadeleştirmesi sayesinde).
- Event log: olay başına `ArrayDeque.addLast` + bounded `removeFirst`; KPI pencereleri yalnız PRUNE/SKIP/REJECT olaylarında yazılıyor.
- Snapshot: hot path dışı; per-numara kısa kilit + kopya, 1 sn cache.
- Log: gate defer'i ve TTL prune WARN (nadir), `markFull`/`SKIP` kayıtları event log'una gidiyor, per-event log satırı basılmıyor.

## Doğrulama

Java hedefli testler:

```text
mvn -o -Dtest=FctRoutingGateDisabledCharacterizationTest,FctChannelRegistryTest,FctChannelRegistryConcurrencyTest,FctChannelRegistryCampaignCleanupTest,FctChannelGateTest,CallOriginatorFctGateTest,ManagerConnectionDecoratorFctCause34CooldownTest,FctChannelMonitorControllerTest,FctChannelMonitorContractTest test
```

Regresyon (mevcut FCT routing + originate/release yolları):

```text
mvn -o -Dtest=RouteDetailsCursorTest,ResourceDistributionServiceFacadeRouteDetailsTest,ManagerConnectionDecoratorFctRoutingHangupTest,PauseCampaignOriginatorRegressionTest,InitialCdrOriginationRegressionTest,SaveInitialCdrHangupResultRegressionTest,ConnectionLossBridgedCallRecoveryRegressionTest,CallOriginatorTest,CampaignRoutesAndBundleContractTest test
```

Gerçekleşen doğrulama (07.08.2026):

1. `mvn -o test-compile` → **BUILD SUCCESS** (794 production + 189 test source; proto stub regenerate edildi).
2. Yeni davranış testleri → **BUILD SUCCESS, 38 test / 0 failure / 0 error**.
3. Regresyon koşusu → **BUILD SUCCESS, 57 test / 0 failure / 0 error**.
4. Birleşik koşu (yeni + regresyon + contract) → **BUILD SUCCESS, 61 test / 0 failure / 0 error**.
5. `mvn -o -DskipTests package` → **BUILD SUCCESS**, `target/gbpowerdialer.war` üretildi.
6. Karma → **blocker**: `node_modules` yok.

Proto stub ve derleme:

```text
mvn -o test-compile
mvn -o -DskipTests package
```

AngularJS:

```text
npm.cmd run test:angular -- --spec=fctChannelMonitor.spec.js
```

Maven komutları `.codex/TOOL-PATHS.local.properties` içindeki `MAVEN_CMD`, `MAVEN_SETTINGS`, `MAVEN_REPO_LOCAL`, `JAVA_HOME` ile offline çalıştırılır. Eksik artifact varsa indirme denenmez, geliştiriciye raporlanır.

Manuel doğrulama (geliştirici ortamı):

1. Tomcat'te uygulama açılır, `Options → FCT Kanal Monitörü` ekranına gidilir; snapshot boş registry ile hatasız render eder.
2. Pilot kampanya (`fctRoutingEnabled = true`, POOL) çalıştırılır; ekranda numaraların lambaları dolup boşalır, feed'de `ACQUIRE`/`RELEASE` akar, hiçbir liste yanıp sönmez.
3. `cause 34` senaryosu izlenir: satır flash → kırmızı lambalar → `COOLDOWN mm:ss` geri sayımı → feed'de `MARK FULL` ve sonraki denemelerde `SKIP` → süre bitince normale dönüş.
4. `fct.channel.gate.enabled=false` ile davranışın 04.05.2026 akışına döndüğü doğrulanır.

## Açık Sorular

Tümü 07.08.2026 tarihinde geliştirici tarafından cevaplandı; uygulama bu cevaplarla yürütüldü.

- [x] **1. QSS proto alanı**: `int32 capacity = 4;` ve `string attempts = 3;` onaylandı. **13.08.2026: QSS geliştirmesi tamamlandı ve sözleşme teyit edildi** — `capacity` field 4, numara başına eşzamanlı çıkış çağrısı limiti, `qss_route.capacity` NULL ise 0 (limit tanımsız) ve limiti QSS değil tüketen dialer uygular. Detaylı sözleşme analiz dosyasındaki Açık Soru 1 altında. Bu teyidin gerektirdiği düzeltmeler aşağıdaki 13.08.2026 bölümünde.
- [x] **2. Defer üst limiti**: 10 tick, sonra mevcut `TELEPHONE_RC_NO_CALLERID_AVAILABLE` akışı — onaylandı.
- [x] **3. Headroom varsayılanı**: pilotta `%10` — onaylandı.
- [x] **4. PD dışı tüketici**: Bu fazda cevaplanmadı; tasarım bunu cooldown ve headroom ile absorbe ediyor. Cevap netleştiğinde headroom yüzdesi yeniden değerlendirilebilir.
- [x] **5. Preview kampanyalar**: gate dışında tutulması onaylandı (FCT routing preview'da korunuyor).
- [x] **6. Monitor yetkisi**: menü `ROLE_SUPERADMIN`, endpoint `DialerAdministration` — uygulandı.
- [x] **7. Numara kapsamı**: yalnız registry'nin gördüğü numaralar; idle numaralar `fct.channel.meta.retention.ms` (30 dk) boyunca listede kalıyor.
- [x] **8. Tema**: `images/FctChannelRegistry.png` (açık tema) esas alındı; koyu glassmorphic tema uygulanmadı. Renkler CSS değişkeni olarak tanımlı, tema kararı değişirse tek noktadan dönülür.

### Faz 2 açık soruları (30.09.2026'da cevaplandı)

- [x] **9. Sabit hat kaynağı** → **A′: mevcut `Campaign.callerId` kolonu yeniden kullanılır.** Prompt'un ilk önerisi ("Campaign alanı") olduğu gibi uygulanamazdı: `campaignForm.html:1567` alanı yalnız `callerIdBehaviour == 0 || == 1` (FIXED/DYNAMIC) iken render ediyor, FCT routing ise yalnız POOL (`== 3`) kampanyalarda çalışıyor. Entity'de `@NotNull` de yok. Yani alan FCT kampanyalarında UI'da hiç görünmüyor ve pratikte boş; fallback sessizce no-op olurdu. Karar: aynı kolon kullanılır ama `ng-if` POOL + `fctRoutingEnabled` için de açılır ve **ayrı bir i18n etiketi** (`campaign.fctFixedLineCallerId`) kullanılır — mevcut `campaign.callerId` anahtarı FIXED/DYNAMIC anlamını taşıdığı için karışmasın. Boş = fallback kapalı (güvenli varsayılan). Birden fazla sabit hat ihtiyacı doğrularsa ayrı iş olarak `fctFallbackCallerId` listesi açılır; bu fazda değil.
- [x] **10. `fct.fixed.line.retry.max` varsayılanı** → **1.** A′ tek numara verdiği için >1 anlamsız.
- [x] **11. Sabit hat registry'de görünsün mü?** → **Hayır.** Rezervasyon tutulmaz, numara için registry girdisi açılmaz; yalnız `FALLBACK_FIXED_LINE` feed olayı üretilir. `hasNextCallerId` sabit hattı sayarken registry'ye bakmaz.
- [x] **12. `originationDate` ilk bacaktan korunsun mu?** → **Evet; ve bu bir raporlama değişikliği değil, bugfix.** Doğrulandı: alanın tek yazarı `CallOriginator.processOrigination`, re-originate ise `originateCall`'ı doğrudan çağırıyor. Eski `setOriginationDate(null)` satırından sonra alan bir daha doldurulmuyordu; `Call.getDurationFromOrigination()` null `originationDate` gördüğünde hiç güncellenmemiş alanı döndürüyor. Ara bacakta standart sonuç akışı çalışmadığı için getter hiç çağrılmıyor ve alan **0'da donuyordu** → `Campaign.incrementTotalCallDuration(0)` ve payload'da `durationFromOrigination = 0`. Yani süre "son bacaktan" değil, hiç ölçülmüyordu. PO bilgilendirmesi gerekmez.
- [x] **13. Gate kapalıyken `HAS_NEXT` gönderilmemesi** → **Evet.** Kill switch iki tarafı birden 04.05.2026 davranışına döndürür; sabit hat fallback'i de kapanır. Değişkenin yokluğu dialplan'da eski davranışa denk geldiği için `0` göndermek yerine hiç göndermemek tercih edildi (`FctChannelGate.isHasNextContractActive`).
- [x] **14. `fct.channel.list.retry.max`** → **0 → 1.** Zorunlu karar: guard `advanceToNextFctChannel`'dan ÖNCE olduğu için 0 iken Faz 2'nin tamamı ölü koddu. 08.09'daki "kanal-zamanı maliyeti kesin" gerekçesi Asterisk `h` guard'ıyla hafifledi (ara bacak yan etkisiz). Ayrıca sabit hat fallback'i bu guard'ın **dışına** alındı: sabit hat FCT kapasitesiyle ilgili değil, bütçesi ayrı (`fct.fixed.line.retry.max`), bu yüzden `list.retry.max = 0` iken bile çalışır.
- [x] **15. `HAS_NEXT` ile gerçek karar ayrışması** → **Tek yönlü tehlike kabul edildi ve testle kilitlendi.** `HAS_NEXT=1` + re-originate yok = Asterisk `h` extension'ı Disconnect/ODBC/hangup_data adımlarını atladığı için o bacağın hangup kaydı hiç oluşmaz; tersi yalnızca eski davranıştır. Bu yüzden şüphede `0` üretilir ve `hasNextCallerId` gerçek karar yoluyla aynı yardımcılardan türer. `FctChannelGateHasNextTest.hasNextCallerIdShouldMatchActualReOriginateDecision` yedi senaryoda eşdeğerliği doğrular.

Plan durumu: **Faz A–F ve Faz 2 uygulandı, doğrulandı; yalnız Karma koşusu blocker'lı.**

## Kapanış Kriteri

- [x] Faz A–E adımlarının tamamı işaretlendi (Adım 32 blocker olarak açık bırakıldı).
- [x] Hedefli Java testleri BUILD SUCCESS, **61 test / 0 failure / 0 error**.
- [x] Mevcut FCT routing regresyon testleri hâlâ yeşil (`RouteDetailsCursorTest`, `ResourceDistributionServiceFacadeRouteDetailsTest`, `ManagerConnectionDecoratorFctRoutingHangupTest`), ayrıca `PauseCampaignOriginatorRegressionTest`, `InitialCdrOriginationRegressionTest`, `SaveInitialCdrHangupResultRegressionTest`, `ConnectionLossBridgedCallRecoveryRegressionTest`, `CallOriginatorTest`, `CampaignRoutesAndBundleContractTest` yeşil.
- [x] `mvn -o test-compile` ve `mvn -o -DskipTests package` başarılı.
- [ ] AngularJS Karma koşusu: **blocker** — `node_modules` kurulu değil (Adım 32).
- [x] `Performans Etkisi` bölümü gerçek patch'e göre güncel.
- [x] `Açık Sorular` bölümünde cevaplanmamış madde yok.
- [x] Mimari dokümanlar güncellendi.
- [x] History kaydı eklendi.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## QSS Sözleşmesi Teyidi Sonrası Düzeltmeler (13.08.2026)

QSS geliştirmesi tamamlandı ve `type`/`attempts` alanlarının **virgülle birleşik çok değerli** olduğu netleşti. Bu, 07.08.2026'da yazılan ekranda iki gerçek kusur ortaya çıkardı; ikisi de düzeltildi:

- [x] **Tip filtresi ve badge token bazlı yapıldı**: `type` tek değer varsayıldığı için filtre tam string eşleşmesi yapıyordu ve `Manual,Retry,Callback` gelen numara hiçbir filtreye takılmıyordu. Artık `splitTokens` ile token'lara ayrılıyor; filtre seçenekleri token bazında toplanıyor (`Dialer First`, `Manual`, `Retry`, `Callback` ayrı seçenekler), eşleşme `typeTokens.indexOf(filter) >= 0` ile yapılıyor ve her token ayrı badge olarak render ediliyor.
- [x] **`attempts` özetlendi**: route'ta hiç `AT_` attribute'u yokken QSS 0..99 fallback'i döndüğü için kart görünümünde ~290 karakterlik dizi basılıyordu. Artık `summarizeTokens` ile `0..99 (100)` biçiminde özetleniyor, tam değer `title` attribute'unda tooltip olarak kalıyor.
- [x] **Hot path log kısaltması**: `ManagerConnectionDecorator` cause 34 list-retry log satırı `attempts` alanını ham basıyordu — 0..99 fallback'i ile her list-retry'da ~290 karakterlik payload demek. `abbreviateRouteDetailValue` ile 32 karakterde kesilip gerçek uzunluk `(289 chars)` biçiminde eklenecek şekilde düzeltildi (AGENTS.md "hot path'te verbose payload log eklenmez" kuralı).
- [x] **Capacity teşhis banner'ı**: registry'nin bildiği tüm numaralarda `capacity = 0` ise gate sessizce pasif demektir (QSS alanı gelmiyor, `qss_route.capacity` NULL veya proto alan uyumsuzluğu). Bu durum artık ekranda `fct.monitor.capacity.missing` banner'ı ile görünür — proto uyumsuzluğunun tek görünür işareti buydu.
- [x] Testler genişletildi: `FctChannelMonitorContractTest`'e token badge + attempts özeti + capacity banner sözleşmesi, `ManagerConnectionDecoratorFctCause34CooldownTest`'e log kısaltma testi, Karma spec'ine 8 yeni "QSS alan sözleşmesi" senaryosu (token ayrıştırma, token bazlı filtre, 0..99 özeti, kısa liste, capacity yok/var, capacity 0'da lamba davranışı) eklendi.
- [x] Doğrulama: **63 test / 0 failure / 0 error**; dokunulan 9 JS dosyası `node --check` ile syntax doğrulandı (Karma hâlâ `node_modules` blocker'lı).

## Ekranda Ham Metin Görünmesi — Kök Neden ve Düzeltme (31.08.2026)

Geliştirici FCT Kanal Monitörü ekranında tüm metinlerin ham hâlde göründüğünü bildirdi. Teşhis, eksik localization anahtarı **olmadığını** gösterdi: template'te kullanılan 45 i18n anahtarının tamamı `localization_tr.js` ve `localization_en.js` içinde tanımlıydı. Gerçek kök neden JS yükleme yolundaydı.

- [x] **Kök neden — `pages/sources.jsp` eksikliği**: `index.jsp` yalnız `project.stage == production` iken WRO bundle'ı yükler; diğer tüm stage'lerde (geliştirici ortamı, `MAVEN_PROFILE=properties-tr-dev`) `pages/sources.jsp` include edilir ve JS dosyaları **tek tek** oradan yüklenir. 07.08.2026 uygulamasında yalnız `wro.xml` güncellenmişti; `fctChannelFactory.js` ve `fctChannelMonitorCtrl.js` `sources.jsp`'ye eklenmemişti. Sonuç: dev ortamında controller hiç yüklenmiyor, `$controller` çağrısı patlıyor ve ngView template'i DOM'a koyduktan sonra `link()` çalışmadığı için sayfa ham `{{...}}` ifadeleriyle render ediliyor. İki script tag'i mevcut `?v=${appConfig['project.version']}` konvansiyonuyla eklendi (factory controller'dan önce).
- [x] **İkincil ve tekrarlayan neden — localization cache-busting yokluğu**: `index.jsp` localization dosyalarını `?v=` parametresi olmadan yüklüyordu; oysa WRO bundle (`:92`), `global.css` (`:11`) ve `sources.jsp`'deki her dosya sürüm parametresi kullanıyor. Bu, yeni eklenen her i18n anahtarının tarayıcı cache'i nedeniyle görünmemesine yol açan yapısal bir tuzaktı (bu iş özelinde de semptomu maskeliyordu). Dört localization script tag'ine `?v=${appConfig['project.version']}` eklendi.
- [x] **Testle kalıcı koruma**:
  - `sourcesJspShouldLoadMonitorScriptsForNonProductionStage`: dev yükleme yolunda iki dosyanın varlığı, sırası ve cache-busting parametresi doğrulanır — `wro.xml`'i güncelleyip `sources.jsp`'yi atlamak artık build'de yakalanır.
  - `localizationScriptsShouldBeCacheBusted`: localization dosyalarının sürüm parametresiyle yüklendiği doğrulanır.
  - `localizationFilesShouldDefineEveryKeyUsedInTemplate`: elle tutulan anahtar listesi kaldırıldı; anahtarlar **template'ten regex ile türetilip** `localization_tr.js` ve `localization_en.js` içinde aranıyor. Ekrana yeni metin eklenip localization atlandığında test kendiliğinden kırılır. Kapsam geliştirici kararıyla Türkçe + İngilizce'dir (RU/UA dosyalarındaki mevcut çeviriler korundu ama zorunlu tutulmuyor).
- [x] **Doğrulama**: 63 hedefli test yeşil (contract testi 7 → 10'a çıktı); `mvn -o -DskipTests package` BUILD SUCCESS; paketlenen `target/gbpowerdialer` içinde `sources.jsp` iki script'i, `index.jsp` cache-busting'i ve `localization_{tr,en}.js` içinde 46 FCT anahtarı doğrulandı.

**Not — mevcut yapısal risk**: `wro.xml` ve `pages/sources.jsp` aynı JS listesini iki ayrı yerde tutuyor ve tarihsel olarak birbirinden sapmış durumda (örn. `js/util/tooltip.js` yalnız `sources.jsp`'de). Yeni ekran ekleyen her iş iki dosyayı birlikte güncellemek zorunda; bu iş kapsamında ilgili contract testi eklendi ancak listelerin tek kaynaktan üretilmesi ayrı bir refactor konusudur.

## Campaign Form FCT Routing Flag'i Kaydedilmiyordu (04.09.2026)

Geliştirici UI'da "FCT Yönlendirme" checkbox'ını açıp kaydettiğinde değerin yansımadığını bildirdi. Bu, 04.05.2026 FEAT-FCT-ROUTING işinin UI adımından (Adım 10) kalan bir defect.

- [x] **Kök neden — `CampaignFormDTO` içinde alan yok**: Campaign form akışı DTO tabanlı (`CampaignController` → `GenericController<Campaign, CampaignFormDTO, CampaignListDTO>`). Entity'de (`Campaign.java:215, 2106-2113`), template'te (`campaignForm.html:1604-1611`), factory default'unda (`campaignFactory.js:86`) ve dört localization dosyasında alan vardı; **yalnız `CampaignFormDTO`'da yoktu**. Dozer konfigürasyonu boş (`dozer-global-configuration.xml`) yani eşleme isim bazlı implicit çalışıyor — DTO'da karşılığı olmayan alan sessizce düşüyor.
- [x] **Etkinin gerçek boyutu tahminden büyük**: `CampaignService.merge:171` DTO'yu `mapper.map(item, Campaign.class)` ile **sıfırdan yeni** bir `Campaign` nesnesine map edip `:214`'te DB'ye merge ediyor. Yani DTO'da bulunmayan alan her form save'inde Java default'uyla yazılıyor. `fctRoutingEnabled` primitive `boolean` olduğu için sonuç **her kayıtta `false`**: checkbox hiç kalıcı olmuyordu ve flag DB'den elle açılsa bile ilk form save'inde sessizce kapanıyordu. Aynı desenin bilinen örneği plan öncesinde de kodda mevcut: `expireFilterEnabled` DTO'da olmadığı için `:184`'te açıkça persisted değerden geri yükleniyor.
- [x] **Düzeltme**: `CampaignFormDTO`'ya `private boolean fctRoutingEnabled` alanı + `isFctRoutingEnabled()`/`setFctRoutingEnabled()` eklendi (entity ile birebir aynı tip; primitive olduğu için null belirsizliği ve Dozer primitive edge-case'i doğmuyor). Tek dosya değişikliği; save (POST/PUT), form yükleme (GET) ve `CampaignService.clone` yolları aynı anda düzeldi.
- [x] **Zincirin kalanı doğrulandı, başka kopukluk yok**: `campaignFactory.js:86` yalnız yeni kampanya initializer'ı (yüklenen kampanyayı ezmiyor); `CALLER_ID_BEHAVIOUR` sıralamasında POOL=3 olduğu için template'in `ng-if="callerIdBehaviour == 3"` kontrolü doğru; `ng-disabled` koşulu pool + permission ile sınırlı.
- [x] **Regresyon testi**: `tests/campaign/CampaignFctRoutingFormMappingRegressionTest` (5 test) — DTO→entity (enabled ve disabled), entity→DTO, entity→DTO→entity round-trip ve AngularJS `ng-model`'ının beklediği JSON alan adı (`fctRoutingEnabled`). Test DTO alanı olmadan **derlenmez**, dolayısıyla hem alanın varlığını hem iki yönlü eşlemeyi kilitler.
- [x] **Doğrulama**: 49 test yeşil (yeni test + `CampaignsResourceTest` + FCT suite + angular contract).

**Not — yapısal risk**: Bu sınıf hata `CampaignFormDTO`'ya alan eklemeyi unutan her yeni campaign alanında tekrarlanır ve **sessizdir** (hata vermez, değeri default'a yazar). Kalıcı çözüm, `Campaign` entity'sindeki persist edilebilir alanların DTO'da karşılığı olduğunu doğrulayan bir contract testi olabilir; bu ayrı bir iş olarak değerlendirilmeli.

## Geliştiriciye Kalan Dış Adımlar

1. ~~**QSS proto teyidi**~~ — 13.08.2026'da tamamlandı; `capacity` field 4 ve null→0 semantiği doğrulandı.
2. **Karma koşusu**: bir kez `npm install`, ardından `npm run test:angular` (26 spec).
3. **Pilot**: `fct.channel.gate.enabled=true` (varsayılan) ile FCT routing açık bir kampanyada izlenmesi; monitor ekranındaki `Prune Edilen (Son 5 dk)` sıfır kalmalı, kalmıyorsa bir release yolu eksik demektir.
4. **DB migration gerekmiyor**: mevcut `pd_campaign.fct_routing_enabled` kolonu yeniden kullanıldı.

## Riskler ve Geri Dönüş

- **Kapasite yanlış tanımlı → self-throttle**: Dialer kendini sessizce kısar. Geri dönüş: `fct.channel.gate.enabled=false` veya `headroom.percent` düşürme. Monitor ekranı ve defer WARN'ları erken uyarı verir.
- **Defer starvation**: Tüm FCT'ler uzun süre dolu kalırsa kampanya ilerlemez. Azaltım: defer limiti + WARN. Geri dönüş: limiti düşürmek veya gate'i kapatmak.
- **Release kaçağı**: Bir yol atlanırsa numara dolu görünür. Azaltım: TTL prune + `PRUNE` event'i + KPI. Geri dönüş: TTL'i kısaltmak.
- **Concurrency hatası**: Kapasite kontrolü + ekleme atomik değilse son kanal iki çağrıya satılır. Azaltım: per-numara `synchronized` ve hedefli concurrency testi.
- **Counter drift**: Rezervasyonlar `callId` set'i olduğu için yapısal olarak drift oluşmaz; çift acquire/release no-op'tur.
- **Proto uyumsuzluğu**: Alan adı/numarası QSS ile eşleşmezse gate pasif kalır (fail-open, üretim davranışı bozulmaz). Doğrulama Adım 2'de yapılır.
- **Restart penceresi**: Registry boş, Asterisk'te çağrı var → geçici over-dial. Azaltım: cooldown + headroom + cold start WARN'u.
- **Monitor polling yükü**: Snapshot cache'i ve `DialerAdministration` yetkisi sınırlar. Geri dönüş: `snapshot.cache.ms` artırmak veya minimum poll aralığını 5 sn'ye çekmek.
- **AngularJS render regresyonu**: `track by` eksikse ekran yanıp söner ve animasyonlar sıfırlanır. Contract testi bunu korur.
- **WRO bundle sırası**: Factory controller'dan sonra yüklenirse ekran açılışta patlar. Contract testi sırayı doğrular.
- **security.xml girdisi eksikse**: Endpoint erişilemez veya korumasız kalır. Adım 19 zorunludur; controller testi yetkiyi doğrular.
- **Tam geri dönüş**: `fct.channel.gate.enabled=false` + menü girdisinin kaldırılması ile davranış ve yüzey 04.05.2026 durumuna döner; DB migration olmadığı için geri alma maliyeti yoktur.

## Faz 2 — cause 34 re-originate, `HAS_NEXT` ve sabit hat fallback (30.09.2026)

Asterisk tarafı tamamlandı: `[outbound-predictive-fct]` context'inin `h` extension'ı şu guard'ı kullanıyor:

```text
GotoIf($["${HAS_NEXT}" = "1" & ("${DIALSTATUS}" = "CONGESTION" | "${DIALSTATUS}" = "CHANUNAVAIL")]?intermediate)
```

`HAS_NEXT=1` gelen ve circuit reddiyle düşen bacakta Asterisk yalnız `DialerHangup` UserEvent'ini gönderir; `Disconnect`, ODBC hangup update, hangup_data.log, EXEC hook ve qos atlanır. Asterisk listeyi bilmez; her Originate tek caller ID taşır.

### Sözleşme ve tek yönlü risk

`HAS_NEXT` "bu bacak cause 34 ile düşerse dialer aynı `Call`'ı yeni bir caller ID ile tekrar originate edecek mi?" sorusunun cevabıdır. Risk simetrik değildir: `HAS_NEXT=1` gönderip re-originate **etmemek**, Asterisk tarafındaki hangup kaydının hiç oluşmamasına yol açar; tersi (0 gönderip yine de re-originate etmek) yalnızca eski davranıştır. Bu yüzden `hasNextCallerId` şüphede `false` döner ve gerçek karar yoluyla aynı yardımcılardan (`isListRetryAllowed`, cursor'daki kalan uygun aday, `hasFixedLineFallback`) türetilir.

### Uygulanan adımlar

- [x] `ManagerConnectionDecorator.originateCall`: `generateCall` sonrası `applyHasNextVariable` ile `Variable: HAS_NEXT=0|1`. Gate yok, gate yolu dışı veya kill switch kapalıysa değişken **hiç eklenmez** (`FctChannelGate.isHasNextContractActive`). Preview `isGatePath` gereği kapsam dışıdır; builder sınıflarına dokunulmadı.
- [x] `FctChannelGate`: `hasNextCallerId`, `hasNextFctCandidate`, `hasFixedLineFallback`, `nextFixedLineCallerId`, `isHasNextContractActive` eklendi. `hasNextFctCandidate` kapasite doluluğunu **bilinçli olarak değerlendirmez** (originate ile hangup arasında değişir, rezervasyon denemesi olmadan kesin bilinemez); cooldown ise numara bazında kalıcı sinyaldir.
- [x] `tryFctRetryWithNextCallerId` yeniden yapılandırıldı: sınır aşıldığında da, cursor tükendiğinde de `tryFixedLineFallback`'e düşülür; fallback yoksa mevcut TRC 25 yolu değişmeden çalışır. Ortak temizlik `prepareCallForReOrigination`'a çıkarıldı.
- [x] `call.setOriginationDate(null)` **kaldırıldı** (Açık Soru 12 — bugfix).
- [x] `Call.fixedLineAttemptCount` (`transient volatile`) + increment/reset/getter; reset `processOrigination` başında `resetFctChannelDeferCount` yanında.
- [x] `FctChannelEventKind`: `REORIGINATE`, `FALLBACK_FIXED_LINE`. Registry'ye `recordReoriginate` / `recordFixedLineFallback` public feed metotları eklendi; `recordEvent` imzası değişmedi, KPI penceresi beslenmedi.
- [x] `application.properties`: `fct.fixed.line.retry.max = 1`; `fct.channel.list.retry.max` 0 → 1 (gerekçesiyle).
- [x] UI: `campaignForm.html`'e POOL + `fctRoutingEnabled` için sabit hat alanı (ayrı i18n etiketi, aynı `callerId` kolonu); `fctChannelMonitorCtrl.js` feed etiketi/ikonu/sınıfı; dört dil dosyasına `campaign.fctFixedLineCallerId`.

### Plan sapmaları ve gerekçeleri

1. **`RouteDetailsCursor`'a `remaining()` eklendi** (prompt §8'de "dokunulmaz" listesindeydi). Zorunluydu: `hasNextFctCandidate`'ın cooldown kontrolü kalan entry'leri görmeyi gerektiriyor, cursor'da tahrip etmeden okuma yolu yoktu (`next()` ilerletiyor). Atlanması hâlinde `HAS_NEXT` aşırı iyimser olur — Açık Soru 15'teki tek yönlü tehlikeli yön. Eklenen metot salt-okunur, cursor'ı ilerletmez, mevcut hiçbir metodu değiştirmez; `RouteDetailsCursorTest` ile ayrıca kilitlendi.
2. **Yeni event kind'leri için i18n anahtarı eklenmedi.** `fctChannelMonitorCtrl.js`'deki feed etiketleri altı mevcut kind için de hardcoded İngilizce; sözleşme testi anahtarları **template'ten** türettiği için JS'te duran etiketler kapsam dışı. İki yeni kind aynı desene eklendi; kullanılmayan "ölü" anahtar üretilmedi. Gerçek yeni kullanıcı metni olan kampanya formu alanı dört dilde localize edildi.
3. **Prompt §2'deki `abbreviateRouteDetailValue` referansı geçersizdi**; helper 09.09.2026'da ölü kod olarak kaldırılmıştı (`5cfa5bfd`). Kural korundu: yeni log satırları yalnız callId, liste konumu/bütçe ve caller ID içeriyor, route payload'ı basılmıyor.

### Kapsam dışı bırakılanlar (bilinçli)

- `handleCallCreation.insertCallResultToDB` re-originate bacağında tekrar çalışıyor (VarSet başına bir kez, aynı satıra upsert). Değiştirilmedi; karakterizasyon testinin javadoc'una bilinçli bırakıldığı yazıldı. Ayrı iş.
- `CallAttemptPolicy.incrementForOrigination` yalnız `CallOriginator.pollCallsFromConsumerAndOriginate`'te; `originateCall`/`tryFctRetryWithNextCallerId` çağırmıyor. Testle kilitlendi.
- Dialplan, `FctChannelRegistry` mevcut API semantiği, QSS proto, preview kampanyalar.

### Faz 2 doğrulaması

```text
mvn -o -Dtest=<Faz 2 hedefli set: 12 sinif> test   -> 78 test, 0 failure, 0 error
mvn -o -Dtest=<regresyon seti: 9 sinif> test       -> 87 test, 0 failure, 0 error
mvn -o -Dtest=RouteDetailsCursorTest test          -> 7 test (remaining() dahil)
mvn -o -DskipTests package                         -> BUILD SUCCESS (gbpowerdialer.war)
node --check <5 dokunulan JS dosyasi>              -> hepsi OK
```

Karma koşulamadı: `node_modules` blocker'ı sürüyor (Adım 32).

## Faz 3 — FCT numarasının trunk'ı (02.10.2026)

Asterisk tarafındaki ihtiyaç: çağrının hangi trunk'tan çıkacağı numaraya göre değişiyor (örn. `10080` bir trunk, kampanyanın sabit hattı başka bir trunk). Bu eşleme dialplan'da elle tutuluyordu ve havuza yeni numara eklendiğinde sessizce bozuluyordu.

Karar: bilgi numaranın tanımıyla aynı yerde dursun. QSS `RouteDetailEntry`'ye `trunk` alanı ekledi; PD bunu yorumlamadan `Variable: DIALER_TRUNK` olarak iletiyor, dialplan boş değeri varsayılan trunk sayıyor.

### QSS sözleşme değişikliği ve PD'ye yansıması

Yeni canonical proto iki değişiklik getirdi:

1. **`RouteDetailEntry.trunk = 5`** (beklenen, additive).
2. **`RouteDetailsRequest`**: `routingGroupId` → `groupId` ve **`calledNumber` kaldırıldı.** PD bu alanı `ebb5188d` (PR 23029) ile kendi eklemişti; QSS sözleşmesi grup bazlı olduğu için (grubun default rule'una bağlı enabled route'lar) alan hiç okunmuyordu. Kaldırılması davranışı değiştirmez — çağrı tipine/denemeye göre süzmenin PD tarafında (`RouteDetailsFilter`) yapılmasının sebebi zaten buydu. `ResourceDistributionServiceFacade.getRouteDetails` artık yalnız `groupId` alıyor.

> **QSS'e öneri**: yeni proto'da 2 numaralı alan `reserved` değil. İleride 2 numarası başka bir amaçla kullanılırsa, eski bir istemcinin gönderdiği `calledNumber` sessizce o alana düşer. `reserved 2;` eklenmesi tek satırlık bir koruma.

### Uygulanan adımlar

- [x] `resource-distribution-service.proto`: QSS canonical sürümü birebir alındı.
- [x] `ResourceDistributionServiceFacade.getRouteDetails(Long groupId)` — `calledNumber` parametresi kaldırıldı, `setGroupId` kullanılıyor; `CallerIdManager.obtainRouteDetailsCursor` çağrısı güncellendi.
- [x] `Call.fctTrunk` (`transient volatile`) + getter/setter.
- [x] `FctChannelGate.tryAcquire`: trunk **caller ID'nin hemen yanında** set edilir. İkisi aynı QSS entry'sinin alanları; ayrışmaları (numara A, trunk B) çağrıyı yanlış trunk'tan çıkarır.
- [x] `FctChannelGate.safeTrunk`: AMI format guard'ı (`^[A-Za-z0-9_.-]{1,64}$`). Geçersiz değerde trunk düşürülür + WARN; çağrı varsayılan trunk'a gider, kaybedilmez.
- [x] `ManagerConnectionDecorator.applyTrunkVariable`: `Variable: DIALER_TRUNK`. Boş/null trunk'ta değişken **hiç eklenmez** (dialplan'da set edilmemiş değişken zaten boş string'e denk; AMI satırı gereksiz uzamaz).
- [x] `tryFixedLineFallback`: `call.setFctTrunk(null)` — sabit hat QSS'te tanımlı değil, bayat trunk taşınırsa çağrı yanlış trunk'tan çıkar.

### Tasarım kararı — trunk kill switch'e bağlı DEĞİL

`HAS_NEXT` yalnız `isHasNextContractActive` (gate yolu **+** kill switch açık) iken gönderiliyor. `DIALER_TRUNK` ise yalnız `isGatePath` koşuluna bağlı.

Gerekçe: `fct.channel.gate.enabled=false` kapasite saymayı durdurur ama caller ID yine QSS entry'sinden seçilir. Trunk'ı o anda göndermeyi kesmek, kill switch'i açmanın **bütün FCT çağrılarını varsayılan trunk'a kaydırması** demek olurdu — kapasite gate'ini geri almak için basılan düğme, çağrıların yanlış trunk'tan çıkmasına yol açardı. Kill switch kapasite gate'ini geri alır, çağrının doğru trunk'tan çıkmasını değil. `ManagerConnectionDecoratorTrunkVariableTest.shouldStillSendTrunkWhenCapacityGateIsDisabled` bunu kilitler.

### Faz 3 doğrulaması

```text
mvn -o test-compile                                     -> BUILD SUCCESS
mvn -o -Dtest=<FCT hedefli set: 16 sinif> test          -> 104 test, 0 failure, 0 error
mvn -o -Dtest=tests.** test                             -> 1123 test (1108 -> 1123), 1 failure + 1 error
mvn -o -DskipTests package                              -> BUILD SUCCESS (gbpowerdialer.war)
```

Geniş koşudaki iki hata merge sonrası ölçülen baseline'ın aynısı (`LogbackAsyncAppenderConfigContractTest`, `UnmappedHangupCauseFallbackRegressionTest`). Yeni kırık yok.

### Faz 3 kapsamı dışında

- Dialplan tarafındaki `DIALER_TRUNK` tüketimi ve varsayılan trunk dalı (Asterisk ekibi).
- `RouteDetailsFilter`'ın `attempts` süzmesi: üretimde her çağrıda fail-open'a düşüyor (`Route details filter emptied the list`). QSS'ten `attempts` alanının anlamı (kota mı, indeks mi) netleşmeden dokunulmuyor; ayrı iş.

## Faz 3.1 — Trunk'ın monitor ekranında görünür olması (07.10.2026)

### Neden

Faz 3 sonrası PRP testinde `DIALER_TRUNK`'ın gerçekten dolu gidip gitmediği **PD loglarından doğrulanamadı**: boş trunk ile geçerli trunk aynı logu üretiyor (geçersiz değer olmadığı için WARN da yok) ve `VarSetEvent` yalnız `manager.conf` `channelvars` listesindeki değişkenleri taşıyor. Doğrulama için AMI debug ya da Asterisk tarafında konfigürasyon değişikliği gerekiyordu. Trunk zaten registry'den geçtiği için monitor snapshot'ına eklemek bu bağımlılığı kaldırıyor.

### Uygulanan adımlar

1. `FctChannelEntryDTO.trunk` alanı eklendi (snapshot JSON'una otomatik çıkar).
2. `FctChannelRegistry.tryAcquire` trunk taşıyan 7 parametreli sürüme kavuştu; mevcut 6 parametreli imza ona delege eden overload olarak korundu (~45 test çağrı noktası değişmedi). `ChannelState.trunk` her rezervasyonda yeniden yazılır, `toEntryDto` snapshot'a taşır.
3. `FctChannelGate.tryAcquire` `safeTrunk(...)` sonucunu **bir kez** hesaplayıp hem `registry.tryAcquire`'a hem `call.setFctTrunk`'a veriyor. Sözleşme: ekranın gösterdiği değer AMI'ye giden değerin aynısıdır; format kapısına takılan trunk ekranda da "varsayılan" görünür.
4. `fctChannelMonitor.html`: numara kolonundan sonra TRUNK kolonu; değer boşsa `fct.monitor.trunk.default` rozeti + `fct.monitor.trunk.default.hint` tooltip'i (boş hücre, "veri yok" ile "varsayılan trunk"u ayırt ettirmezdi). Kart görünümüne de aynı anahtarlarla meta satırı eklendi.
5. Üç i18n anahtarı dört dile de eklendi (`tr`, `en`, `ru`, `ua`).

Controller tarafında değişiklik gerekmedi: `decorateEntry` snapshot alanlarını yerinde zenginleştiriyor, `entry.trunk` doğrudan geçiyor.

### Faz 3.1 doğrulaması

```text
node --check localization_{tr,en,ru,ua}.js                 -> OK (4/4)
mvn -o -Dtest=FctChannelRegistryTest,FctChannelGateTrunkTest,
    FctChannelMonitorContractTest,FctChannelMonitorControllerTest test
                                                           -> 38 test, 0 failure, 0 error
mvn -o -Dtest=Fct*Test,ManagerConnectionDecorator*Test,
    RouteDetails*Test test                                 -> 119 test, 0 failure, 0 error
```

Yeni testler: `FctChannelRegistryTest` +3 (snapshot trunk taşır / trunk yoksa boş kalır / sonraki rezervasyonda tazelenir), `FctChannelGateTrunkTest` +2 (ekran ile AMI değeri ayrışmaz; reddedilen trunk ekrana da çıkmaz), `FctChannelMonitorContractTest` +1 (kolon + varsayılan rozeti + tooltip).
