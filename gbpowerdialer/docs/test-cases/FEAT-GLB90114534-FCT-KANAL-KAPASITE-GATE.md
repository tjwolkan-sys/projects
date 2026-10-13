# FEAT-GLB90114534 — FCT Kanal Kapasite Gate · Test Case Seti

## Bağlam

FCT başına eş zamanlı kanal limiti QSS'te tanımlanır (`RouteDetailEntry.capacity`), PowerDialer'da sayılır (`FctChannelRegistry`) ve dolu FCT'ye originate atılmaz (`FctChannelGate`). Temel kural **bir numara = bir FCT** olduğu için kapasite anahtarı doğrudan `phoneNumber`'dır.

İlgili analiz/plan: `docs/analyse/07.08.2026/`, `docs/execution-plan/07.08.2026/`, `docs/change-fix-plan/08.09.2026/FIX-GLB90114534-FCT-CALLERID-CONTEXT-VE-LIST-RETRY.md`. Mimari: `docs/architecture/DIALER-RUNTIME.md` → "FCT Kanal Kapasite Gate'i". Sunum: `docs/presentation/09.09.2026/`.

Her case'in yanında otomatik test kapsamı işaretlidir: `[Otomatik: <test sınıfı>]` JUnit ile yeşil; `[Otomatik (Karma)]` AngularJS spec (`node_modules` blocker'ı nedeniyle henüz koşulmadı); `[Manuel]` QA/geliştirici tarafından el ile doğrulanmalı.

## Ortak test verisi / önkoşullar

- **K-FCT**: PROGRESSIVE/PREDICTIVE kampanya; `fctRoutingEnabled=true`, `callerIdBehaviour=POOL`, geçerli `routingGroupId`. Bu ikisi birlikte sağlanmazsa gate hiç devreye girmez (`isFctRoutingPath`).
- **K-PREVIEW**: aynı ayarlar + `previewCampaign=true`. Gate kapsamı **dışındadır** (`isGatePath`), FCT routing liste iterasyonu korunur.
- **K-KLASIK**: `fctRoutingEnabled=false`. Klasik `obtainCallerId` yolu; regresyon referansı.
- QSS yanıtı (`getRouteDetails`) örneği:
  | phoneNumber | type | attempts | capacity |
  |---|---|---|---|
  | 05550000401 | `Dialer First,Retry` | `0,1,2` | 10 |
  | 05550000402 | `Dialer First` | `0..99` | 4 |
  | 05550000403 | `Callback` | `0..99` | 0 |
- Varsayılan property'ler: `gate.enabled=true`, `ttl.ms=3900000`, `stale.warn.ms=600000`, `cooldown.ms=60000`, `headroom.percent=10`, `defer.limit=10`, `list.retry.max=0`, `event.log.size=200`, `snapshot.cache.ms=1000`.
- **effectiveCapacity** = `max(1, floor(capacity × (100 − headroom) / 100))`. `capacity ≤ 0` → gate pasif.
- **Registry zamanı testte enjekte edilebilir**: `clockMs` bir `LongSupplier`'dır; TTL/cooldown testleri gerçek beklemeye ihtiyaç duymaz.

---

## 1. Kapasite dolduğunda çağrı yakılmaz, ötelenir · [Otomatik: CallOriginatorFctGateTest, FctChannelGateTest]

Bu setin en kritik davranışı: **kapasite yokluğu bir hata değil, bir bekleme durumudur.**

- **Given**: K-FCT; cursor'daki tek entry `capacity=4` (effective 3) ve üzerinde 3 aktif rezervasyon var.
- **When**: yeni bir çağrı `processOrigination`'a girer.
- **Then**:
  - `reserveForOrigination` **false** döner; AMI `OriginateAction` **gönderilmez**.
  - Çağrı iptal **edilmez**: `cleanupCallBeforeDeferredOrigination` ile attempt geri alınır (`CallAttemptPolicy.restoreForDeferredOriginationIfNeeded`), `callUpToDateKeeper` unregister edilir, varsa agent `addToIdleUsers` ile idle havuzuna döner.
  - Çağrı kampanya kuyruğunun **başına** konur (`offerFirst`), kuyruk yoksa `returnCallToDb`.
  - `fctChannelDeferCount` 1 artar; WARN satırı basılır.
  - Kampanya sayaçları dengede kalır (originated/inProgress sızıntısı olmaz).
- **When**: aynı çağrı 11. kez ötelenir (`deferCount > defer.limit=10`).
- **Then**: `TELEPHONE_RC_NO_CALLERID_AVAILABLE` ile `allowRetries=false` iptal; defer sayacı sıfırlanır. **Çağrı sonsuza kadar kuyrukta dönmez.**
- **When**: rezervasyon boşalır ve çağrı yeniden denenir.
- **Then**: originate başarılı; `resetFctChannelDeferCount` çağrılır (sayaç sıfırlanır, bir sonraki sıkışıklıkta baştan sayar).

> **Manuel ek kontrol**: ötelenen çağrının veritabanındaki `attempts` değeri artmamış olmalı. Öteleme bir deneme değildir.

---

## 2. Eş zamanlılık: son kanal iki çağrıya satılamaz · [Otomatik: FctChannelRegistryConcurrencyTest]

- **Given**: `capacity=2` (effective 1), numara üzerinde rezervasyon yok.
- **When**: N thread aynı numara için eş zamanlı `tryAcquire` çağırır (farklı `callId`'lerle).
- **Then**: tam olarak **1** çağrı `true` döner; `occupancy(number) == 1`; diğerleri için `REJECT` olayı kaydedilir.
- **Gerekçe (kod)**: kapasite kontrolü ve rezervasyon kaydı aynı **per-numara** `synchronized (state)` bloğundadır. Kilit numara bazlıdır; farklı numaralar birbirini bloklamaz — hot path'te global kilit yoktur.
- **When**: aynı `callId` ile `tryAcquire` iki kez çağrılır.
- **Then**: ikinci çağrı kapasiteye **karşı sayılmaz** (`inFlight.containsKey(callId)` kontrolü) ve `true` döner; `ACQUIRE` olayı bir kez kaydedilir. Rezervasyon idempotenttir.

---

## 3. Headroom hesabı ve sınır değerleri · [Otomatik: FctChannelRegistryTest]

- **Given**: `headroom.percent=10`.
- **Then**: `capacity=10 → effective 9`; `capacity=4 → 3`; `capacity=1 → 1` (floor 0 iken `max(1, …)` devreye girer, tanımlı kapasite asla 0'a düşmez); `capacity=0 → 0` (gate pasif).
- **When**: `capacity=10` üzerinde 9 rezervasyon varken 10. çağrı gelir.
- **Then**: `REJECT`, detay `"kapasite dolu 9/9"`.
- **When**: `headroom.percent` 0 yapılır.
- **Then**: `capacity=10 → effective 10`; headroom devre dışı kalır ama gate çalışmaya devam eder.
- **Sınır**: `headroom.percent` 0–100 aralığına clamp'lenir (`Math.max(0, Math.min(100, …))`); negatif veya 100 üstü değer NPE/negatif kapasite üretmez.

---

## 4. Gate pasif hâlleri — fail-open · [Otomatik: FctRoutingGateDisabledCharacterizationTest, FctChannelGateTest]

Geri dönüş yolunun çalıştığını kanıtlayan karakterizasyon seti.

- **Given**: `fct.channel.gate.enabled=false`.
- **When**: kapasitesi dolu bir numara için çağrı originate edilir.
- **Then**: originate **atılır** (gate kısmaz); `isInCooldown` her zaman `false`; `isListRetryAllowed` her zaman `true` → cause 34 sonrası **04.05 FCT routing davranışı birebir geri gelir**.
- **Ama**: rezervasyon **yine kaydedilir** → monitor ekranı gerçek doluluğu gate açılmadan önce de gösterir. Bu, devreye alma sırasının 1. adımıdır (önce gözlem, sonra kısıtlama).
- **Given**: `capacity=0` (QSS'te `qss_route.capacity` NULL).
- **Then**: `isGateActive` false; sınırsız kabul edilir; kayıt tutulmaya devam eder.
- **Given**: K-PREVIEW.
- **Then**: `isGatePath` false → rezervasyon alınmaz (originate'i webphone atar), fakat FCT routing liste iterasyonu korunur.
- **Given**: `FctChannelGate` bean'i yok (Spring dışında kurulan testler / eski composition).
- **Then**: `reserveFctChannel` true döner, akış bugünkü gibi devam eder. Gate'in yokluğu çağrıyı düşürmez.
- **Given**: `phoneNumber` veya `callId` boş.
- **Then**: `tryAcquire` true döner (no-op), `release` no-op. Boş anahtar registry'yi kirletmez.

---

## 5. cause 34: bilgi saklanır, çağrı zorlanmaz · [Otomatik: ManagerConnectionDecoratorFctListRetryCapTest, ManagerConnectionDecoratorFctCause34CooldownTest]

- **Given**: K-FCT, `list.retry.max=0` (varsayılan), çağrı 05550000402 ile originate edilmiş.
- **When**: `DialerHangupEvent` `hangupCause=34` ile gelir.
- **Then**:
  - `isListRetryAllowed(0)` **false** → aynı çağrı içinde **yeniden Originate atılmaz**.
  - Buna rağmen `releaseAndMarkFull` çalışır: rezervasyon bırakılır **ve** numara `cooldown.ms=60000` süresince cooldown'a alınır (`MARK_FULL` olayı). "Bu FCT dolu" bilgisi diğer çağrılar için korunur.
  - Standart `processCallResult` zinciri çalışır: TRC 25 → retry strategy. **Çağrı kaybolmaz.**
  - `fctListRetryCount` artmaz (retry yapılmadı).
- **When**: cooldown içindeyken başka bir çağrı aynı numarayı denemek ister.
- **Then**: `tryAcquire` false, `SKIP` olayı, detay `"cooldown kalan <n>sn"`. Cursor sıradaki entry'ye geçer.
- **Given**: `list.retry.max=1` (retry açık senaryo).
- **When**: cause 34 gelir ve cursor'da uygun bir sonraki entry var.
- **Then**: `reserveNextAfterNoCircuit` → önceki numara release + cooldown, sıradaki uygun numara rezerve edilir, `fctListRetryCount` 1 olur, tek satır log basılır:
  `FCT routing list retry: <callId> <index>/<size> <callerId>` ve yeni `originateCall` çağrılır. `processCallResult` **çalışmaz**.
- **When**: ikinci bir cause 34 gelir (`fctListRetryCount=1`, sınır 1).
- **Then**: `isListRetryAllowed(1)` false → `releaseAndMarkFull` + standart sonuç akışı.
- **When**: cursor'da uygun entry kalmaz (`reserveNextAfterNoCircuit` false).
- **Then**: standart sonuç akışına düşülür; sonsuz döngü oluşmaz.

> **Manuel/prod kontrolü**: canlı loglarda `"FCT routing list retry"` satırının **varlığı**. Bu satır yoksa çağrı başına gözlenen 6-8 congestion olayı PowerDialer'dan değil dialplan'dan geliyor demektir; teşhisin yönü buna bağlıdır.

---

## 6. Release bütünlüğü: her çıkış yolundan bırakılır · [Otomatik: FctChannelGateTest, FctChannelRegistryTest]

Kanal sızıntısının tek gerçek kaynağı eksik bir release yoludur. Her yol ayrı doğrulanmalıdır.

- **Given**: numara üzerinde bir rezervasyon var.
- **When / Then** — her biri için `occupancy` 0'a düşmeli ve `RELEASE` olayı kaydedilmeli:
  1. Normal hangup → `releaseTelephonySlotIfNeeded`
  2. Çağrı tamamlandı → `CallCleaner.completeCall`
  3. Süresi geçti → `CallCleaner.clearExpiredCall`
  4. Preview timeout → `handlePreviewTimeout`
  5. Originate atılamadı (exception) → `handleExceptionDuringOrigination` — rezervasyon originate **öncesi** alındığı için bu yol zorunludur
  6. cause 34 → `releaseAndMarkFull`
- **When**: aynı çağrı için `release` iki kez çağrılır.
- **Then**: ikinci çağrı **no-op**; ikinci `RELEASE` olayı üretilmez. Idempotenttir (`markTelephonySlotReleased` guard'ının önünde durması sorun değildir).
- **When**: A çağrısının tuttuğu numara için B çağrısının `callId`'siyle `release` çağrılır.
- **Then**: A'nın rezervasyonu **bozulmaz** (owner kontrolü: `(numara, callId)`).

> **Manuel/prod kontrolü**: monitor ekranındaki KPI'da `pruned` sayısı sürekli artıyorsa bir release yolu eksiktir. Bu, tasarımın kendi teşhis sinyalidir.

---

## 7. TTL self-healing: kaçan release temizlenir, uzun konuşma korunur · [Otomatik: FctChannelRegistryTest]

- **Given**: `ttl.ms=3900000` (= `Constants.CALL_EXPIRE_TIMEOUT` 1 saat + 5 dk); enjekte edilmiş saat.
- **When**: bir rezervasyon alınır ve release **hiç** gelmez; saat TTL kadar ilerletilir.
- **Then**: `pruneExpired` rezervasyonu siler, `PRUNE` olayı kaydedilir, KPI `pruned` penceresi artar, kapasite yeniden kullanılabilir olur.
- **When**: saat TTL'in **altında** bir süre ilerletilir (örn. 50 dakika — konuşma hâlindeki uzun bir çağrı).
- **Then**: rezervasyon **korunur**. Bu case bilinçli bir plan sapmasının regresyon kilidi: spec'teki 60 saniyelik TTL canlı çağrıların kanalını erken bırakacaktı.
- **When**: saat `stale.warn.ms=600000` kadar ilerler ama TTL'e ulaşmaz.
- **Then**: kayıt prune **edilmez** ama snapshot'ta **stale** işaretlenir → operatör "bu kayıt 10 dakikadır hareketsiz" bilgisini prune'dan önce görür.
- **Sınır**: `occupancy` ve `tryAcquire` okumadan önce prune eder; süresi geçmiş rezervasyon asla kapasiteye karşı sayılmaz.

---

## 8. Kampanya yaşam döngüsü ve restart · [Otomatik: FctChannelRegistryCampaignCleanupTest] + [Manuel]

- **Given**: iki kampanya (A, B) aynı numarayı kullanıyor, her birinin rezervasyonu var.
- **When**: A kampanyası START / PAUSE / PAUSING / RESET / RELOAD edilir (manuel veya scheduler ile).
- **Then**: registry'ye **hiç dokunulmaz**; uçuştaki çağrılar kanalını tutmaya devam eder. Gerekçe: rezervasyonun sahibi **çağrıdır**, kampanya değildir. Scheduler yolu ayrı bir kod yolu kullanmaz.
- **When**: A kampanyası graceful STOP edilir.
- **Then**: her çağrı normal hangup yolundan kendi kanalını bırakır.
- **When**: A kampanyası forceful STOP edilir ve `CampaignStopJob.lastCleanUpChecks` çalışır.
- **Then**: `releaseAllForCampaign(A)` yalnız **A'ya ait** rezervasyonları siler (`RELEASE` + `"campaign cleanup"` detayı); **B'nin rezervasyonları korunur**. `campaignId=null` ile çağrı no-op'tur.
- **When**: uygulama restart/deploy edilir. · **[Manuel]**
- **Then**: registry boşalır (JVM içi state); kısa süre kapasite boş görünür. Startup'ta **bir kez** cold start WARN'u basılır. Bu pencereyi `headroom.percent` ve cause 34 cooldown'u absorbe eder — kabul edilmiş bir tasarım tavizidir.
- **Yatay ölçek notu** · **[Manuel]**: registry JVM içidir. Aynı server/kampanya için ikinci bir node çalışırsa kapasite sayımı node başına ayrışır. `docs/architecture/DIALER-RUNTIME.md` → "Yatay Ölçek Riski" ile aynı kısıt.

---

## 9. QSS: geçici erişim hatası ile kalıcı route yokluğu ayrımı · [Otomatik: CallerIdManagerRouteDetailsTest, ResourceDistributionServiceFacadeRouteDetailsTest, CallOriginatorFctGateTest]

Bu ayrım olmadan kısa bir QSS/Eureka kesintisi, o an originate edilen tüm çağrıları kalıcı olarak kaybettiriyordu.

- **Given**: gRPC channel yapılandırılmamış (`blockingStub == null`).
- **When**: `getRouteDetails` çağrılır.
- **Then**: `RouteDetailsUnavailableException` fırlatılır (boş liste **dönmez**).
- **Given**: gRPC çağrısı exception atıyor (QSS/Eureka erişilemez).
- **Then**: aynı exception; mesaj `routingGroupId` içerir; orijinal hata `cause` olarak korunur.
- **When**: `CallOriginator` bu exception'ı yakalar.
- **Then**: `handleRouteDetailsUnavailable` → ERROR log (tek noktada, çağrı bağlamıyla) + `cancelCall(TELEPHONE_RC_NO_CALLERID_AVAILABLE, allowRetries=**true**)`. Çağrı retry havuzunda kalır.
- **Given**: QSS erişilebilir ama routing grubu bulunamadı → **boş liste** döner (QSS sözleşmesine göre bu bir hata değildir).
- **Then**: `NoAvailablePoolCallerIdException` → `cancelCall(…, allowRetries=**false**)`. Konfigürasyon eksikliği tekrar denemekle düzelmez.
- **Log regresyonu**: facade başarı yolunda **INFO basmaz**; `"Got route details"` yalnız `LOG.isDebugEnabled()` guard'ı altında DEBUG'dır. Bu satır günde ~180.000 kez basılıyordu.

---

## 10. RouteDetailsFilter: context süzmesi ve üç katmanlı fail-open · [Otomatik: RouteDetailsFilterTest, CallerIdManagerRouteDetailsTest]

- **Given**: QSS yanıtı yukarıdaki ortak tabloyla aynı.
- **When**: `callType=FRESH`, `attempts=0`.
- **Then**: `Dialer First` içeren ve `attempts` token'ı `0` olan entry'ler kalır (401 ve 402); `Callback` entry'si (403) elenir. **QSS sıralaması korunur** — öncelik mantığı QSS'te kalır.
- **When**: `callType=RETRY`, `attempts=1`.
- **Then**: yalnız `Retry` + `1` eşleşen entry (401) kalır.
- **When**: `callType=CALLBACK`.
- **Then**: `Callback` etiketli entry (403) kalır. Eşleme: `FRESH → Dialer First`, `RETRY → Retry`, `CALLBACK → Callback`.
- **Fail-open katman 1** — `type` veya `attempts` **boş**: entry elenmez.
- **Fail-open katman 2** — değerde hiç **bilinen** etiket yok (örn. QSS sözlüğü değişti, `type="Foo,Bar"`): entry elenmez. Aynı mantık `attempts` için: hiç sayısal token yoksa elenmez.
- **Fail-open katman 3** — süzme **tüm listeyi** eler: filtresiz listeye dönülür + WARN (`"Route details filter emptied the list, falling back to unfiltered"`).
- **When**: `callType` bilinmeyen bir değer (`expectedTypeLabel → null`).
- **Then**: süzme hiç uygulanmaz, liste olduğu gibi döner.
- **Then (tümü için)**: süzme **hiçbir koşulda** çağrıyı caller ID'siz bırakmaz.
- **Bilinen sınır** — test edilmesi gereken değil, **kabul edilmiş** boşluk: bu süzme `retryCallerIdBehavior` (UNIQUE/SAME) semantiğini geri kazandırmaz. O semantik QSS provision history'sini gerektirir; tam parite `RouteDetailsRequest`'e context alanlarının eklenmesiyle gelir (kalem 1b, QSS gündeminde). Bu davranış için **regresyon testi yazılmamalı**; aksi hâlde eksik davranış doğru davranış olarak kilitlenir.

---

## Ek kontroller (10'un dışında, kapsam tamlığı için)

### E1 — `fctRoutingEnabled` kaydetme round-trip · [Otomatik: CampaignFctRoutingFormMappingRegressionTest]
- UI'da checkbox işaretlenip kaydedilir → yeniden açıldığında **işaretli** kalır.
- Gerekçe: `CampaignFormDTO`'da alan yokken Dozer `mapper.map(item, Campaign.class)` her kaydetmede `false` yazıyordu. Test entity↔DTO alan paritesini korur.

### E2 — FCT Channel Monitor ekranı · [Otomatik: FctChannelMonitorControllerTest, FctChannelMonitorContractTest] + [Otomatik (Karma): fctChannelMonitor.spec.js]
- `GET /fctChannel/snapshot` → numara listesi, doluluk, cooldown kalan süre, stale işareti, olay log'u (son 200), 5 dakikalık KPI (`pruned` / `skipped` / `rejected`).
- 1 saniyelik sunucu cache'i (`snapshot.cache.ms`) — 1 saniye içindeki ikinci istek aynı nesneyi döner.
- `DialerAdministration` yetkisi yoksa **403**.
- Ekran salt-okunur; registry'ye yazmaz.

### E3 — Lokalizasyon · [Manuel]
- TR ve EN dillerinde monitor ekranındaki tüm etiketler anlamlı görünür, **ham key görünmez**.
- Regresyon notu: bu hatanın kaynağı eksik key değil, `pages/sources.jsp`'de JS dosyalarının yüklenmemesiydi (dev stage WRO kullanmaz). Yeni bir FCT JS dosyası eklenirse `sources.jsp` de güncellenmelidir.

### E4 — Performans / hot path · [Manuel]
- `tryAcquire` / `release` hot path'tedir. Kilit **numara bazlıdır**, global değildir; olay log'u ve KPI pencereleri **bounded**'dır (200 kayıt / 5000 örnek).
- Doğrulama: gate açık ve kapalı hâllerde originate gecikmesi karşılaştırılır; anlamlı fark görülmemelidir.

---

## Bilinen açık maddeler

- **Karma koşumu**: `node_modules` eksik olduğu için 26 spec yazıldı ama hiç koşulmadı. Geliştirici tarafında `npm install` + `npm run test:angular` gerekiyor.
- **Vacuous assertion**: PR 23029'dan gelen `ManagerConnectionDecoratorFctRoutingHangupTest`'te `hasCallsInProgress() == false` beklentisi her koşulda geçer — `hasCallsInProgress()` `originatedCalls`'a bakarken `addCallInProgress` ayrı bir `callsInProgress` set'ini yönetiyor. Düzeltilmesi tidy-up olarak açık.
- **TRC 25 raporlama kayması**: gate açıldığında dolu FCT'ye hiç gitmeyen çağrılar TRC 25 yerine defer/retry olarak görünecek. Rapor karşılaştırmalarında bu kırılma PO ile paylaşılmalı.
