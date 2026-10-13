# FEAT FCT Kanal Kapasite Gate ve Canlı Monitor

## Bağlam

`04.05.2026` tarihli `FEAT-GLB90114534-FCT-ROUTING` işi, FCT (Fixed Cellular Terminal) kaynaklı `cause 34 → TRC 25` başarısızlığını reaktif olarak çözdü: hangup geldikten sonra QSS'in sıraladığı listede bir sonraki caller ID ile aynı çağrı için yeniden Originate atılıyor. Bu iş, aynı problemi **proaktif** hâle getirir: FCT'lerin eşzamanlı kanal kapasitesi bilindiğine göre, kapasitesi dolu olan FCT'ye hiç gitmemek.

Kapasite tanımı QSS tarafında tutulur ve `getRouteDetails` yanıtındaki her `RouteDetailEntry` ile PowerDialer'a taşınır. Sayım ise PowerDialer'da yapılır; QSS dial/hangup event'i görmediği için o an kaç kanalın kullanıldığını bilemez. Bu bölüşüm geliştiriciyle netleştirilmiştir:

- **Kapasite tanımı (statik)**: QSS'te. Yeni alan `RouteDetailEntry.capacity`.
- **Doluluk sayımı (dinamik)**: PowerDialer'da, in-memory ve TTL ile self-healing.
- **Bir numara = bir FCT**: kapasite anahtarı doğrudan `phoneNumber`. Ek `fctId` alanı gerekmez.

İkinci kapsam, bu iç durumun operasyon tarafından izlenebilmesidir: AngularJS arayüzündeki `Options` menüsü altına, registry'nin anlık durumunu (kanal rezervasyonları, kapasite, doluluk, cooldown, TTL prune) gösteren canlı bir monitor ekranı eklenir.

## İş Etiketi

feat

Öncelik: normal

## Etkilenen Alanlar

### Backend

- `gbpowerdialer/src/main/proto/resource-distribution-service.proto` — `RouteDetailEntry` içine `capacity` alanı; `attempts` alanının QSS canonical tipiyle hizalanması.
- `com.powerdialer.engine.dialer.routing.FctChannelRegistry` (**yeni**) — numara bazlı kanal rezervasyon registry'si, cooldown, TTL prune, bounded event log, snapshot üretimi.
- `com.powerdialer.engine.dialer.routing.RouteDetailsCursor` — cooldown/kapasite nedeniyle entry atlama için ilerletme desteği.
- `com.powerdialer.engine.dialer.CallOriginator` — cursor kurulurken cooldown'daki entry'yi atlama; `processOrigination` başında rezervasyon; rezervasyon alınamazsa defer.
- `com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator` — `tryFctRetryWithNextCallerId` içinde release + `markFull`; `releaseTelephonySlotIfNeeded`, `handlePreviewTimeout`, `handleExceptionDuringOrigination` yollarında release.
- `com.powerdialer.engine.CallCleaner` — `clearExpiredCall` yolunda release.
- `com.powerdialer.engine.dialer.CampaignStopJob` — forceful stop'ta release; `lastCleanUpChecks` içinde kampanya bazlı safety-net süpürme.
- `com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition` — decorator'ın registry'ye erişimi için getter (`getAgentDialLockRegistry` kalıbı).
- `com.powerdialer.controller.FctChannelMonitorController` (**yeni**) — `GET /fctChannel/snapshot`.
- `com.powerdialer.dto` — snapshot DTO'ları.
- `gbpowerdialer/src/main/resources/application.properties` — TTL, cooldown, headroom, event log boyutu, gate kill switch.
- `gbpowerdialer/src/main/webapp/WEB-INF/security.xml` — `/fctChannel/**` intercept-url.

### Frontend

- `gbpowerdialer/src/main/webapp/pages/fctChannelMonitor.html` (**yeni**) — monitor template'i ve sayfa içi stil bloğu.
- `gbpowerdialer/src/main/webapp/js/controllers/fctChannelMonitorCtrl.js` (**yeni**).
- `gbpowerdialer/src/main/webapp/js/services/fctChannelFactory.js` (**yeni**).
- `gbpowerdialer/src/main/webapp/js/app.js` — `/fctChannelMonitor` route'u.
- `gbpowerdialer/src/main/webapp/index.jsp` — `Options` dropdown'una menü girdisi.
- `gbpowerdialer/src/main/webapp/WEB-INF/wro.xml` — yeni iki JS dosyası.
- `gbpowerdialer/src/main/webapp/js/localization/localization_{tr,en,ru,ua}.js` — ekran anahtarları.

### Test

- `gbpowerdialer/src/test/java/tests/dialer/routing/**`, `tests/ami/**`, `tests/controller/**`, `tests/angular/fct/**`.
- `gbpowerdialer/src/test/angular/fct/fctChannelMonitor.spec.js`.

## Mevcut Davranış

- `CallOriginator.pollCallsFromConsumerAndOriginate` (`CallOriginator.java:196-202`) FCT path'inde `callerIdManager.obtainRouteDetailsCursor(call)` ile sıralı listeyi alır, **koşulsuz** ilk entry'nin numarasını caller ID yapar.
- Kapasite kavramı yok: dolu FCT'ye originate atılır, Asterisk `cause 34` döner, `ManagerConnectionDecorator.handleDialerHangup` (`:959`) `tryFctRetryWithNextCallerId` ile bir sonraki entry'yi dener; liste tükenince standart `processCallResult → applyRetryStrategy` çalışır.
- `processOrigination` (`CallOriginator.java:496-515`) originate öncesi tek noktadır ve iki yerden çağrılır: LSC kapalı akış (`:206`) ve LSC OK dalı (`:457`).
- Originate bütçesi server seviyesinde `min(maxLines − inFlightCallQueue, cpsLimit) − underLSC` ile hesaplanır (`:225-235`); kampanya seviyesinde `canOriginateMoreCalls()` (`Campaign.java:2178`) `concurrentLines` sınırını uygular. FCT/numara seviyesinde hiçbir sınır yoktur.
- `NoAvailablePoolCallerIdException` durumunda çağrı `cancelCall(..., TELEPHONE_RC_NO_CALLERID_AVAILABLE, false)` ile iptal edilir (`:567-570`) — yani müşteri hiç aranmaz.
- Kampanya PAUSED iken originate erteleme yolu mevcuttur: `deferOriginationUntilCampaignResumes` → `cleanupCallBeforeDeferredOrigination` (keeper unregister, `CallAttemptPolicy.restoreForDeferredOriginationIfNeeded` ile attempt geri alma, agent'ı idle havuzuna döndürme) + `returnCallToPausedCampaignQueue` (kuyruğun başına koyma) (`:522-550`).
- Telefonluk kaynak bırakma için idempotent tek-shot primitive vardır: `releaseTelephonySlotIfNeeded` + `Call.markTelephonySlotReleased()` (`ManagerConnectionDecorator.java:1495-1503`, `Call.java:794`). Bu funnel `processCallResult` (`:1219`), awaiting-user-result (`:975`) ve recoverable connection loss timeout (`:1677`) yollarını kapsar; `CallCleaner.clearExpiredCall`, `CampaignStopJob.clearCallAndSaveResultToDb` ve `handlePreviewTimeout` bu funnel'ı **kullanmaz**.
- Registry benzeri bileşen kalıbı repoda mevcuttur: `AgentDialLockRegistry` (`engine/asterisk/fastagi/AgentDialLockRegistry.java:24-58`) — `@Component`, `ConcurrentMap`, `putIfAbsent` ile atomik acquire, owner kontrollü idempotent release, `ManagerConnectionComposition.getAgentDialLockRegistry()` ile decorator'a açılıyor. `CampaignRealTimeRingingManager` ise TTL cache + add/remove çifti kalıbını gösteriyor.
- `getRouteDetails` proto'sunda kapasite alanı yok; `RouteDetailEntry.type` ve `attempts` yalnız log amaçlı kullanılıyor (`ManagerConnectionDecorator.java:1074-1078`).
- `Options` menüsü `index.jsp:37-53` içindedir; salt-okuma monitor ekranı için en yakın örnek `serviceStatusesList` route'u + `ServiceStatusesController` (`@PreAuthorize(hasAuthority('DialerAdministration'))`) + `security.xml:39` `/serviceStatuses/**` girdisidir.

## Beklenen Davranış

### Gate

- `Campaign.fctRoutingEnabled = true`, `callerIdBehaviour = POOL` ve kampanya preview değil ise gate devrededir.
- Cursor kurulurken cooldown'da olan entry'ler atlanır; kalan ilk entry caller ID olur.
- `processOrigination` başında (kampanya pause kontrolünden sonra, `callQueue.add` ve `originateCall`'dan önce) rezervasyon alınır: cursor pozisyonundan itibaren `tryAcquire(numara, capacity, callId)` denenir, başarısızsa cursor ilerletilir.
- Rezervasyon alınırsa akış bugünkü gibi devam eder (`callQueue.add` → `originateCall` → `saveInitialCDR` → dial event).
- Hiçbir entry rezerve edilemezse çağrı **iptal edilmez**: mevcut defer primitive'i ile attempt geri alınır, agent idle havuzuna döner, çağrı kuyruğun başına konur ve bir sonraki tick'te yeniden denenir. Defer üst limiti aşılırsa mevcut `TELEPHONE_RC_NO_CALLERID_AVAILABLE` davranışına düşülür.
- `capacity <= 0` (QSS alanı göndermiyor veya tanımsız) veya global kill switch kapalı ise gate o entry için devre dışıdır; davranış bugünküyle birebir aynı kalır (fail-open).
- Kapasitenin bir kısmı headroom olarak ayrılabilir: `effectiveCapacity = max(1, floor(capacity × (100 − headroomPercent) / 100))`.

### Cooldown

- `cause 34` hangup'ında ilgili numara için rezervasyon bırakılır ve numara `cooldownMs` süresince cooldown'a alınır (`markFull`); bu süre boyunca hem cursor seçiminde hem rezervasyonda atlanır.
- Cooldown, kapasite sayımının göremediği trafiği (PowerDialer dışı tüketiciler, restart sonrası bilinmeyen doluluk) dolaylı olarak telafi eder.

### Release

- Release owner kontrollü (`(numara, callId)`) ve idempotenttir; birden fazla noktadan çağrılması no-op'tur.
- Bağlanacak noktalar: `releaseTelephonySlotIfNeeded`, `CallCleaner.clearExpiredCall`, `CampaignStopJob.clearCallAndSaveResultToDb`, `handlePreviewTimeout`, `handleExceptionDuringOrigination`, `tryFctRetryWithNextCallerId`.
- Kampanya bazlı safety net: `CampaignStopJob.lastCleanUpChecks` içinde `releaseAllForCampaign(campaignId)`.
- Hiç sinyal gelmezse TTL prune rezervasyonu düşürür ve bunu `PRUNE` event'i olarak kaydeder (release kaçağının sağlık sinyali).

### Kampanya lifecycle davranışı

Rezervasyonun sahibi çağrıdır, kampanya değildir. Kampanya state geçişleri rezervasyon yaratmaz ve silmez.

| Aksiyon | Registry davranışı |
|---|---|
| START (manuel/scheduler/automation) | Hiçbir şey. **Temizleme yapılmaz** — aynı FCT'de başka kampanyaların canlı çağrıları olabilir |
| PAUSE | Rezervasyonlar korunur; in-flight çağrılar kesilmez |
| Pause anında originate (`CallOriginator.java:497-500`) | Rezervasyon alınmışsa release |
| STOP_REGULAR / stop condition / scheduler stop | Çağrılar normal biter, standart release çalışır |
| STOP_FORCEFUL | Açık release gerekir (`releaseTelephonySlotIfNeeded` bu yolda çağrılmıyor) |
| RESTART | Regular gibi; ek iş yok |
| RESET (manuel + scheduler) | **Dokunulmaz.** Reset istatistik sıfırlamasıdır; FCT'de gerçekten konuşan çağrı vardır |
| RELOAD | İş yok |
| Kampanya edit (pool / retry behavior) | Dokunulmaz; release `call.getCallerId()` üzerinden çalışır |
| `fctRoutingEnabled` runtime'da kapatıldı | Yeni originate gate'siz; mevcut rezervasyonlar release/TTL ile düşer |
| Stop job son süpürmesi | `releaseAllForCampaign(campaignId)` safety net |

Scheduler ve automation, manuel aksiyonlarla aynı funnel'ı kullanıyor (`ScheduledCampaignRunner.java:183,193,203`, `AutomaticCampaignExecutor.java:71,73,83` → `campaignService.action(...)` → `CampaignStateTemplate`), bu yüzden tetikleyici bazlı ayrı davranış tanımlanmaz.

### Deploy / restart davranışı

- Registry JVM içi olduğu için restart'ta boşalır; Asterisk'teki çağrılar devam eder. Kısa süre PD kapasiteyi boş sanar → cause 34 artabilir; cooldown bunu ilk hangup'ta absorbe eder.
- Startup'ta bir kez `registry cold start` WARN'u basılır (operasyonun bu pencereyi tanıması için).
- Headroom yüzdesi bu pencereyi ve PD dışı trafiği aynı anda yumuşatır.
- Kalıcılık (Redis'e taşıma) bu iş kapsamında **değildir**; multi-node tartışmasıyla birlikte ayrı ele alınır.

### Monitor ekranı

- `Options → FCT Kanal Monitörü` menüsünden erişilir; ekran salt-okunurdur.
- `GET /fctChannel/snapshot` seçilen aralıkta (1s / 2s / 5s) çekilir; Auto Refresh ve Duraklat kontrolleri vardır.
- Ekran, `images/FctChannelRegistry.png` görselindeki açık (light) tema düzenini birebir izler: header + LIVE pill, 5 hücreli aggregate şerit (Toplam Kapasite, In-Flight, Doluluk Oranı, Cooldown'da, 60 saniyelik doluluk sparkline'ı), FCT numara listesi (tablo/kart görünüm toggle'ı, arama, tip filtresi), sağda canlı aktivite feed'i, altta 4 KPI hücresi (TTL Süresi, Ortalama In-Flight Yaş, Prune Edilen (Son 5 dk), Skip Edilen (Son 5 dk)).
- Lamba semantiği: lamba = kanal slotu. Dolu slot doluluk bandına göre renklenir, boş slot sönük gri, cooldown'da tüm lambalar kırmızı.
- DURUM türetme: cooldown → `COOLDOWN mm:ss`; %100 → `FULL`; ≥%80 → `HIGH USAGE`; ≥%50 → `MEDIUM`; >0 → `HEALTHY`; 0 → `IDLE`.
- Feed olay tipleri: `ACQUIRE`, `RELEASE`, `REJECT`, `SKIP (COOLDOWN)`, `MARK FULL (CAUSE 34)`, `PRUNE (TTL EXPIRED)`.
- Periyodik render'da liste ve lambalar yeniden kurulmaz; AngularJS `track by` ile mevcut DOM node'ları yerinde güncellenir (yanıp sönme olmaz, CSS animasyonları sıfırlanmaz).
- Yaş sayaçları ve cooldown geri sayımları poll aralığından bağımsız olarak 1 saniyede bir yerinde güncellenir (sunucu `serverTimeMs` referansı ile local saat farkı düzeltilir).
- `prefers-reduced-motion: reduce` tüm animasyonları kapatır.

## Kod Okuma Bulguları

- `processOrigination`'ın yalnız iki çağrı noktası olması (`:206`, `:457`), rezervasyon için tek ve doğru enjeksiyon noktası yaratıyor: LSC sonrası, AMI send öncesi. Rezervasyonu daha erken (cursor seçiminde) almak, LSC async işlemi ve TIMEOUT süresince kanalı boşa tutardı.
- `cleanupCallBeforeDeferredOrigination` + `returnCallToPausedCampaignQueue` ikilisi, "rezervasyon alınamadı → çağrıyı yakma" ihtiyacını **hazır** karşılıyor: attempt snapshot'tan geri alınıyor (`CallAttemptPolicy.restoreForDeferredOriginationIfNeeded`), agent idle'a dönüyor, çağrı kuyruğun başına gidiyor. Yeni bir mekanizma icat edilmesi gerekmiyor; yalnız isimlendirme genelleştirilecek.
- `AgentDialLockRegistry`, önerilen registry'nin birebir kalıbı: kapasitesi 1 olan hâli. Yeni bir mimari kavram eklenmiyor.
- `attempts` alanında contract drift var: repo proto'su `int32 attempts = 3;`, geliştiricinin paylaştığı QSS proto'su `string attempts = 3;`. Aynı field number'da wire type değişimi olduğu için PD tarafı değeri unknown field'a düşürür, parse hatası fırlatmaz; alan yalnız log'da kullanıldığı için blast radius küçüktür ama hizalanmalıdır.
- `RouteDetailEntry.type` serbest string (QSS tarafında Manual/Retry/Callback/Dialer First gibi değerler taşıyor). PNG'deki `MOBILE`/`FIXED` badge'leri örnektir; UI tip filtresi snapshot'taki distinct değerlerden **dinamik** kurulmalıdır, sabit enum varsayılmamalıdır.
- Monitor endpoint'i için `security.xml` explicit `intercept-url` listesi kullanıyor (`:32-57`); yeni path prefix'i eklenmeden endpoint korumasız/erişilemez kalır. `/serviceStatuses/**` satırı örnek alınacak.
- Snapshot'ta müşteri verisi yok: yalnız kendi FCT numaraları, `callId` (interaction id) ve zaman damgaları taşınır. `calledNumber` **taşınmaz**.
- PowerDialer yalnız `getRouteDetails` yanıtında gördüğü numaraları bilir; monitor ekranı "registry'nin bildiği numaralar"ı gösterir. Trafiği duran numaraların ekrandan hemen kaybolmaması için metadata retention'ı (varsayılan 30 dk) rezervasyon TTL'inden (60 sn) uzun tutulur.

## Riskler

1. **Yanlış kapasite tanımı → self-throttle**: Kapasite gerçekten olduğundan küçük tanımlanırsa dialer kendini kısar ve bu sessizce olur. Azaltım: global kill switch (`fct.channel.gate.enabled`), `capacity<=0` fail-open, defer WARN'ları ve monitor ekranındaki doluluk göstergesi.
2. **Defer starvation**: Tüm uygun FCT'ler uzun süre dolu kalırsa çağrı kuyruk başında sürekli dönebilir ve o kampanya ilerlemez. Azaltım: defer üst limiti (property) ve limit aşımında mevcut no-callerid davranışına düşme; ayrıca `logOriginateStarvationIfNeeded` benzeri WARN.
3. **Release kaçağı → kalıcı kapasite kaybı**: Bir release noktası atlanırsa numara dolu görünür. Azaltım: TTL prune + `PRUNE` event'i + monitor KPI'ı (Prune Edilen, Son 5 dk) — kaçak sessiz kalmaz.
4. **Counter drift yerine yapısal sayım**: Rezervasyon serbest sayaç değil, `callId` set'i olarak tutulur; aynı `callId` iki kez acquire edilse bile doluluk şişmez, iki kez release edilse bile eksilmez.
5. **Concurrency**: Aynı numaraya birden fazla `CallOriginator` thread'i (server başına bir thread) eşzamanlı erişir. Kontrol + ekleme aynı per-numara kritik bölümünde yapılmazsa son kanal iki çağrıya satılır. Azaltım: per-numara `synchronized`, global lock yok; hedefli concurrency testi.
6. **Hot path maliyeti**: Originate ve hangup yollarına eklenen iş O(kapasite) prune + O(1) map erişimidir; yeni IO, DB query, tam koleksiyon taraması veya geniş lock eklenmez. Event log push'u O(1) ve bounded'dır.
7. **Monitor polling yükü**: 1 saniyelik aralıkla çoklu admin oturumu registry lock'larına dokunur. Azaltım: snapshot per-numara kısa lock ile kopya üretir, endpoint kısa süreli (varsayılan 1 sn) cache'lenebilir, yetki `DialerAdministration` ile sınırlıdır.
8. **Restart penceresi**: Registry boş, Asterisk'te çağrı var → geçici over-dial. Azaltım: cooldown + headroom + cold start WARN'u.
9. **PD dışı tüketiciler**: Aynı FCT'yi manual dial veya başka uygulama kullanıyorsa PD'nin sayımı eksik kalır. Azaltım: cooldown ve headroom. Bu, tasarımın bilinçli kabul ettiği sınırdır ve doğrulanması gereken bir açık sorudur.
10. **Preview kampanyalar**: Preview'da originate'i webphone atar ve `previewTimeout` boyunca beklenir; rezervasyonu bu süre boyunca tutmak kapasite israfıdır. Bu fazda preview kampanyalar gate dışında tutulur.
11. **Proto field uyumsuzluğu**: `capacity` alanının adı veya field number'ı QSS ile birebir aynı olmazsa değer sessizce 0 gelir ve gate hiç aktive olmaz. Doğrulama zorunlu.
12. **AngularJS render regresyonu**: `track by` kullanılmazsa periyodik render tüm listeyi yeniden kurar; lamba animasyonları sıfırlanır ve ekran yanıp söner. Contract/Karma testleriyle korunur.
13. **Görsel/spec farkı**: Yazılı spec koyu glassmorphic tema, `images/FctChannelRegistry.png` ise açık flat tema tarif ediyor. PNG esas alınmıştır; renkler CSS değişkenleriyle tanımlanacağı için tema kararı değişirse tek noktadan dönülebilir.

## TDD Stratejisi

### Karakterizasyon (önce mevcut davranışı kilitle)

1. `tests/dialer/routing/FctRoutingGateDisabledCharacterizationTest` — `capacity=0` veya gate kill switch kapalı iken FCT routing akışının bugünküyle birebir aynı olduğunu (cursor ilk entry, `processOrigination` side effect zinciri, cause 34 → sıradaki entry) kilitler.
2. `tests/dialer/originator/DeferOriginationAttemptRestoreCharacterizationTest` — kampanya PAUSED defer yolunun attempt'i geri aldığını, keeper'dan çıkardığını ve çağrıyı kuyruğun başına koyduğunu kilitler (gate defer'i bu davranışı yeniden kullanacağı için).

### Yeni davranış

3. `tests/dialer/routing/FctChannelRegistryTest` — acquire/kapasite sınırı, `capacity<=0` fail-open, idempotent release, owner kontrolü, cooldown süresi, cooldown bitişi, TTL prune + `PRUNE` event'i, metadata retention, headroom hesabı.
4. `tests/dialer/routing/FctChannelRegistryConcurrencyTest` — N thread, kapasite C → tam olarak C acquire başarılı.
5. `tests/dialer/routing/FctChannelRegistryCampaignCleanupTest` — `releaseAllForCampaign` yalnız o kampanyanın rezervasyonlarını düşürür.
6. `tests/dialer/originator/CallOriginatorFctGateTest` — rezervasyon `processOrigination` başında alınır; dolu entry atlanır; hepsi dolu ise originate atılmaz, defer edilir, attempt artmaz; defer limiti aşılınca mevcut no-callerid akışına düşer.
7. `tests/ami/ManagerConnectionDecoratorFctCause34CooldownTest` — cause 34'te release + `markFull` çağrılır, sıradaki uygun entry rezerve edilip yeni Originate atılır, uygun entry yoksa standart `processCallResult` çalışır.
8. `tests/dialer/routing/FctChannelReleaseWiringTest` — expired call, forceful stop, preview timeout ve originate exception yollarında release'in çağrıldığı doğrulanır.
9. `tests/controller/FctChannelMonitorControllerTest` — snapshot DTO şeması (alan adları ve tipleri), yetki kontrolü, boş registry davranışı, `calledNumber` gibi müşteri verisinin taşınmadığı.
10. `tests/angular/fct/FctChannelMonitorContractTest` — `app.js` route'u, `wro.xml` sırası (app.js → factory → controller), template dosyasının varlığı, `index.jsp` menü girdisi, dört localization dosyasında anahtarların bulunması, template'te `track by` kullanımı.
11. `src/test/angular/fct/fctChannelMonitor.spec.js` (Karma/Jasmine) — polling aralığı değişimi, duraklat, arama + tip filtresi birlikte çalışması, DURUM türetme kuralları, cooldown geri sayımı.

### Test çalıştırma

- Java: `mvn -o -Dtest=<TestClass> test` (`.codex/TOOL-PATHS.local.properties` üzerinden offline).
- AngularJS: `npm.cmd run test:angular` — `NPM_CMD` lokal değeri yoksa blocker olarak raporlanır (04.05.2026 planındaki durum).

## Performans Etkisi

- **Hot path durumu**: Evet — `pollCallsFromConsumerAndOriginate`, `processOrigination` ve `handleDialerHangup`.
- **Originate başına**: cursor seçiminde entry başına O(1) cooldown lookup; `processOrigination` başında per-numara kısa `synchronized` blok içinde O(effectiveCapacity) TTL prune + set ekleme. Yeni gRPC, DB query, koleksiyon taraması veya global lock yok. `getRouteDetails` çağrı sayısı değişmiyor.
- **Hangup başına**: release O(kapasite) map işlemi; cause 34'te ek olarak O(1) cooldown yazımı. Yeni IO yok.
- **Event log**: olay başına O(1) push + bounded pop (varsayılan 200 kayıt). Ayrı bir 5 dk pencereli timestamp deque'i yalnız `PRUNE`/`SKIP` için tutulur ve yazımda budanır.
- **Snapshot endpoint**: hot path dışı (HTTP thread). Maliyet O(numara × kapasite); per-numara kısa lock ile kopya üretir, kısa süreli cache ile poll yükü sınırlanır.
- **Log hacmi**: gate defer'i ve `markFull` INFO/WARN; aynı numara için tekrarlayan skip DEBUG seviyesinde tutulur, per-event verbose payload log'u eklenmez.
- **Azaltım/ölçüm**: monitor ekranı zaten ölçüm aracıdır (doluluk, prune, skip). Pilotta headroom ve cooldown süreleri property üzerinden ayarlanır.

## Önerilen Çözüm Yönü

1. Proto'ya `capacity` alanı eklenir, `attempts` QSS canonical tipine hizalanır, `mvn -o test-compile` ile stub regenerate edilir.
2. `FctChannelRegistry` `@Component` olarak eklenir (`AgentDialLockRegistry` kalıbı): per-numara state (metadata + rezervasyon set'i + cooldown), TTL prune, bounded event log, snapshot üretimi. Yeni kütüphane kullanılmaz; JDK `ConcurrentHashMap`/`LinkedHashMap`/`ArrayDeque` yeterlidir.
3. Gate `processOrigination` başında tek noktadan devreye girer; cursor seçiminde yalnız cooldown filtresi uygulanır.
4. Rezervasyon alınamazsa mevcut defer primitive'i genelleştirilerek kullanılır; attempt ve agent israfı olmaz.
5. Release, altı mevcut yaşam döngüsü noktasına bağlanır; idempotent olduğu için fazladan çağrı zararsızdır. TTL prune backstop olarak kalır.
6. Kampanya lifecycle'ında yalnız iki dokunuş yapılır: forceful stop release'i ve stop job'daki kampanya bazlı safety-net süpürme. Start/pause/reset/reload registry'ye dokunmaz.
7. Monitor endpoint'i `/fctChannel/snapshot` olarak eklenir (`security.xml` girdisi + `@PreAuthorize`), DTO şeması UI spec'iyle birebir uyumlu tutulur.
8. AngularJS ekranı repo standardına göre eklenir (route + controller + factory + template + localization + WRO) ve `track by` ile yerinde güncellenir; görsel `images/FctChannelRegistry.png` esas alınır.
9. Tüm davranış property'lerle ayarlanabilir ve kill switch ile kapatılabilir; varsayılanlar üretim davranışını değiştirmeyecek şekilde seçilir (gate `capacity` gelmeden pasiftir).

## Açık Sorular ve Netleştirme İhtiyacı

1. **QSS proto alanı** — KAPALI (13.08.2026, geliştirici teyidi). QSS tarafındaki geliştirme tamamlandı ve sözleşme şöyle netleşti:
   - `getRouteDetails` istenen `groupId` için enabled + silinmemiş CALLERID routing grubunu bulur, grubun default rule'una bağlı enabled route'ları (CALLERID numaraları) toplar ve **numara başına bir kayıt** döner. Grup bulunamazsa **boş liste** döner (hata değil) — PowerDialer'ın boş listede `NoAvailablePoolCallerIdException` ile mevcut no-callerId akışına düşmesi bu semantikle uyumludur.
   - `phoneNumber`: route'un arbitrary value'su (CALLERID numarası).
   - `type`: route attribute'larından çevrilen çağrı tipleri, **virgülle birleşik çok değerli** — `CT_MANUAL → Manual`, `CT_PD_RETRY → Retry`, `CT_PD_CALLBACK → Callback`, `CT_PD_FRESH → Dialer First`.
   - `attempts`: `AT_` prefix'li attribute'lardan gelen değerler, **virgülle birleşik**; route'ta hiç `AT_` yoksa fallback olarak **0..99** doldurulur (~290 karakter).
   - `capacity` (field 4): numara başına eşzamanlı çıkış çağrısı limiti. `qss_route.capacity` NULL ise **0** döner = limit tanımsız/limitsiz. **Limiti QSS uygulamaz, tüketen dialer uygular** — bu, PowerDialer tarafındaki fail-open tasarımının birebir karşılığıdır.
   - Aynı mantığın REST yüzeyi (`RoutingConfigurationManager.getRouteDetails`) `[ { "<numara>": { "Type": "...", "Attempts": "...", "Capacity": "0" } } ]` map listesi döner; `Capacity` orada string'dir ve grup/route yoksa 204 NO_CONTENT verir. Dialer bu yüzeyi kullanmaz.

   Bu teyit iki UI davranışını değiştirdi (13.08.2026 düzeltmeleri): `type` tek değer varsayılamaz (token bazlı badge + filtre) ve `attempts` ham hâliyle gösterilemez/loglanamaz (özet + log kısaltma). Ayrıntı execution plan içinde.
2. **Defer üst limiti** — Tüm uygun FCT'ler dolu iken çağrı kaç tick kuyrukta beklesin ve limit aşılırsa ne olsun? Önerim: 10 tick, sonrasında mevcut `TELEPHONE_RC_NO_CALLERID_AVAILABLE` davranışı.
3. **Headroom varsayılanı** — `fct.channel.headroom.percent` kaç olsun? Önerim: pilotta 10, sonra ölçüme göre 0.
4. **PD dışı tüketici** — Aynı FCT numaralarını PowerDialer dışında kullanan sistem var mı (manual dial vb.)? Varsa cooldown ve headroom tek telafi mekanizmasıdır.
5. **Preview kampanyalar** — Bu fazda gate dışında tutulmasını öneriyorum; onaylıyor musunuz?
6. **Monitor yetkisi** — Menü `ROLE_SUPERADMIN`, endpoint `DialerAdministration` authority'si (serviceStatuses ile aynı kalıp) uygun mu?
7. **Numara kapsamı** — Monitor yalnız registry'nin gördüğü numaraları gösterecek (idle numaralar metadata retention süresi kadar listede kalır). Tüm QSS envanterinin görünmesi isteniyorsa ek bir QSS çağrısı (`getTargets`) gerekir; bu fazda kapsam dışı bırakıyorum.
8. **Tema** — `images/FctChannelRegistry.png` (açık tema) esas alınacak, yazılı spec'teki koyu glassmorphic tema uygulanmayacak; onaylıyor musunuz?

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyor. Execution plan `docs/execution-plan/07.08.2026/FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE.md` altında oluşturulmuştur. Yukarıdaki açık sorulardan en az 1, 2, 5 ve 8 numaralı maddeler cevaplanmadan uygulama başlatılmaz.
