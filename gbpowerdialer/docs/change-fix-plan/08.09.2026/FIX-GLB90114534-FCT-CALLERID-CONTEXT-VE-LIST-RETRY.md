# FIX FCT Caller ID Context Kaybı, Boş Liste Yakması ve List-Retry Sınırı

## İş Etiketi

fix

Öncelik: normal

FCT routing üretimde kapalı (`Campaign.fctRoutingEnabled` pilot kampanyalarda açılacak), canlı arıza yok. Bu plan canlıya çıkış öncesi düzeltmeleri kapsar; hotfix değildir.

## Bağlam

`04.05.2026` FEAT-FCT-ROUTING (PR 23029, `cb60d1e7`) FCT routing temelini getirdi: `RouteDetailsCursor`, QSS `getRouteDetails`, `Campaign.fctRoutingEnabled`, campaign form checkbox'ı ve cause 34'te aynı çağrı içinde sıradaki caller ID ile yeniden originate. `07.08.2026` FEAT-FCT-KANAL-KAPASITE-GATE bunun üstüne kapasite gate'ini (`FctChannelRegistry`, `FctChannelGate`) ve monitor ekranını ekledi; `feature/GLB90114534-fct-kanal-kapasite-gate` branch'inde 63 test yeşil.

08.09.2026'da geliştirici PR 23029'un revert edilebilirliğini sordu. Bağımlılık analizi revert'i reddetti:

- `git merge-base --is-ancestor cb60d1e7 HEAD` → EVET; PR 23029 mevcut branch'in atası.
- Branch'te **26 dosya** PR 23029'un sembollerini kullanıyor (`RouteDetailsCursor`, `RouteDetailEntry`, `fctRoutingEnabled`, `getRouteDetails`, `routeDetailsCursor`); bunların **9'u** kapasite gate işiyle yeni yazılan dosyalar.
- PR 23029'dan sonra develop'a **155 commit** girmiş, ~50'si 23029'un dokunduğu dosyalarda (`ManagerConnectionDecorator`, `CallOriginator`, `Call`, `Campaign`, `campaignForm.html`, `Constants`, `CallerIdManager`). Azure'un `cb60d1` revert'inde çakışma vermesinin sebebi bu.
- Ek tuzak: develop'ta revert edip sonra bu branch'i merge etmek, git 23029 commit'lerini "zaten merge edilmiş ata" saydığı için temeli geri getirmez → develop derlenmez.

Revert yerine devralınan kodun gözden geçirilmesi kararlaştırıldı. Bu plan o incelemede çıkan üç sorunu ele alır.

**Üretim veri bağlamı** (31.08 + 01.09, `turkcell*`): ~180.600 originate/gün, cause 34 oranı ~%11,5 (~20.700 çağrı/gün), çağrı başına 6-8 congestion olayı, doomed çağrıların kanal tutması nedeniyle trunk zamanının **~%17'si** asla bağlanmayacak çağrılara gidiyor.

## Mevcut Davranış

### Kalem 1 — Caller ID seçim context'i FCT yolunda kayboluyor

`CallerIdManager.obtainCallerId` POOL kolunda QSS'e altı bilgi gönderiyor (`ResourceDistributionServiceFacade.java:56-82`):

| Alan | Değer |
|---|---|
| routingGroupId | caller ID pool |
| subjectId | `call.getCalledNumber()` |
| `unique` | `Campaign.retryCallerIdBehavior` → `Uniqueness` (ANY/UNIQUE/SAME) |
| attribute | `AT_<attempt-1>` |
| attribute | `CT_PD_<FRESH\|RETRY\|CALLBACK>` |
| utilizer | `UT_CAMPAIGN_<campaignId>` |

`CallerIdManager.obtainRouteDetailsCursor` (`:66-73`) ise yalnız `(routingGroupId, calledNumber)` gönderiyor (`Facade.java:109-127`).

Sonuç: **FCT routing açık kampanyalarda `retryCallerIdBehavior` sessizce yok sayılıyor**, attempt/callType context'i QSS'e gitmiyor ve provision history'de utilizer kaydı oluşmuyor.

Sistem kendi içinde çelişiyor: `CampaignService.merge:189-191` `retryCallerIdBehavior` değiştiğinde `resourceDistributionServiceFacade.deleteHistory(pool)` çağırıyor — yani ayarın anlamlı olduğunu varsayıyor.

QSS bu bilgiyi zaten **döndürüyor**: `RouteDetailEntry.type` (`CT_MANUAL → Manual`, `CT_PD_RETRY → Retry`, `CT_PD_CALLBACK → Callback`, `CT_PD_FRESH → Dialer First`; virgülle birleşik çok değerli) ve `attempts` (`AT_` attribute'larından, virgülle birleşik; route'ta hiç `AT_` yoksa 0..99 fallback'i). Ancak 04.05 kararıyla PD tarafında kullanılmıyor: *"type ve attempts alanları PowerDialer tarafında kullanılmaz, yalnız log/observability için tutulur."*

### Kalem 2 — Boş liste ile gRPC hatası ayırt edilemiyor; çağrı retry'sız yakılıyor

`Facade.getRouteDetails` iki farklı durumda da `Collections.emptyList()` dönüyor:

- gRPC channel kurulmamış (`:110-113`, Eureka kapalı)
- gRPC çağrısı exception attı (`:123-126`)

`obtainRouteDetailsCursor` boş listede `NoAvailablePoolCallerIdException` atıyor → `CallOriginator.handleNoAvailablePoolCallerId:567-570` → `cancelCall(call, Constants.TELEPHONE_RC_NO_CALLERID_AVAILABLE, false)`. Son parametre **`allowRetries = false`**.

QSS sözleşmesi "grup bulunamazsa boş liste döner (hata değil)" diyor. Yani kalıcı konfigürasyon eksikliği ile **geçici QSS/Eureka kesintisi aynı sonuca varıyor**: çağrı TRC 910 ile kalıcı kapanıyor, retry bile almıyor. Geçici bir kesintide o an originate edilen tüm çağrılar kaybedilir.

### Kalem 3 — In-call list-retry sınırsız, bir kolu testsiz

`ManagerConnectionDecorator.java:964`:

```java
if (hangupCause == Constants.HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE && tryFctRetryWithNextCallerId(call)) {
  LOG.info("FCT routing list retry continued, skipping standard hangup result for: " + call);
  return;
}
```

`tryFctRetryWithNextCallerId` (`:1057`) liste boyu kadar in-call retry yapabiliyor; PD tarafında cap yok (04.05 planı: *"Worst case latency: liste boyu × tek FCT cause 34 turnaround. PowerDialer tarafında ek hard cap konmaz."*).

Her tur şunları yapıyor: `campaign.removeCallInProgress` + koşullu `decrementRingingCalls` + `calls.invalidate` → `advanceToNextFctChannel` (gate varsa release + `markFull` + sıradaki uygun FCT rezervasyonu) → `originateCall`.

İki sorun:

1. **Kanal-zamanı çarpanı.** Prod'da çağrı başına 6-8 congestion olayı var. Bu gezme dialplan'daysa PD'nin list-retry'ı bunun üstüne bir dış döngü ekliyor (PD originate → dialplan 6-8 FCT gezer ~56 sn → cause 34 → PD sıradaki caller ID → dialplan yine gezer). Trunk zamanının %17'si zaten doomed çağrılara giderken bu çarpan ters yönde çalışıyor.
2. **Test boşluğu.** `originateCall` **true** dönen yol `ManagerConnectionDecoratorFctCause34CooldownTest` ile kapsanıyor; `false` (AMI exception → `handleExceptionDuringOrigination`) dönen yol kapsanmıyor. Kırılgan campaign counter'ları üzerinde hot path'te sıralı decrement/increment yapıldığı için bu kol testle sabitlenmeli.

Ek olarak: kapasite gate'i devreye girdikten sonra bu döngünün marjinal faydası azaldı. Gate dolu/cooldown FCT'ye gitmiyor; buna rağmen cause 34 geliyorsa kıtlık gate'in görmediği seviyede (trunk/SBC) demek — sıradaki FCT'nin de başarısız olma olasılığı yüksek, maliyeti ise kanal-zamanı.

## Beklenen Davranış

### Kalem 1

İki alt kalem; hangisinin uygulanacağı Açık Soru 1'e bağlı.

**1a — PD tarafı süzme (QSS bağımlılığı yok).** Cursor kurulurken entry'ler çağrının `callType`'ına göre süzülür. Eşleme: `FRESH → "Dialer First"`, `RETRY → "Retry"`, `CALLBACK → "Callback"`. `type` virgülle birleşik olduğu için token bazlı kontrol yapılır. `attempts` token listesi çağrının güncel attempt'ini içeriyorsa entry uygun sayılır (QSS `AT_` yoksa 0..99 döndürdüğü için bu koşul pratikte "her attempt" anlamına gelir).

**Fail-open zorunlu**: süzme sonucu liste boşalırsa filtresiz listeye dönülür ve WARN basılır. Çağrıyı caller ID bulamadığı için yakmak, yanlış caller ID kullanmaktan daha kötüdür.

**1a'nın sınırı açıkça kayda geçirilir**: bu, `SAME`/`UNIQUE` semantiğini **geri kazandırmaz**. SAME/UNIQUE, "bu numara için daha önce hangi caller ID kullanıldı" geçmişini gerektirir; o geçmiş QSS'in provision history'sinde, PD'de yok. 1a yalnız route'un o callType/attempt için tanımlı olup olmadığını süzer.

**1b — QSS request context'i (gerçek parite).** `RouteDetailsRequest`'e `uniqueness`, `attempt`, `callType`, `utilizer` alanları eklenir; seçimi eskisi gibi QSS yapar. `retryCallerIdBehavior` semantiğini gerçekten geri kazandıran tek yol budur. Proto değişikliği + QSS tarafı iş gerektirir.

### Kalem 2

`Facade.getRouteDetails` hata durumunu boş listeden ayırır (öneri: hata → `null`, grup yok → `emptyList`; alternatif küçük bir sonuç sarmalayıcı). `obtainRouteDetailsCursor` hata durumunda Açık Soru 2'de seçilen davranışı uygular. Grup gerçekten boşsa mevcut davranış (retry'sız iptal, TRC 910) **korunur** — o kalıcı bir konfigürasyon eksikliğidir.

### Kalem 3

`fct.channel.list.retry.max` property'si eklenir; `tryFctRetryWithNextCallerId` bu sınırı aşarsa `false` döner ve standart `processCallResult` zinciri (TRC 25 → `applyRetryStrategy`) çalışır. Sayaç dengesi `originateCall` hem `true` hem `false` döndüğünde testle sabitlenir. Varsayılan değer Açık Soru 3'e bağlı.

## Etkilenen Kod Alanları

- `com.powerdialer.rds.ResourceDistributionServiceFacade` — kalem 2 (hata ayrımı), kalem 1b (request alanları)
- `com.powerdialer.engine.asterisk.CallerIdManager` — kalem 1a (süzme), kalem 2 (hata davranışı)
- `com.powerdialer.engine.dialer.routing.RouteDetailsCursor` veya yeni bir süzme yardımcısı — kalem 1a
- `com.powerdialer.engine.dialer.routing.FctChannelGate` — kalem 3 (retry kapısı, property)
- `com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator` — kalem 3 (cap kontrolü)
- `gbpowerdialer/src/main/proto/resource-distribution-service.proto` — yalnız kalem 1b
- `gbpowerdialer/src/main/resources/application.properties` — kalem 3 property
- `gbpowerdialer/src/test/java/tests/**` — karakterizasyon + yeni testler

## Riskler

1. **Kalem 1a fazla süzerse çağrı caller ID bulamaz.** Azaltım: fail-open + WARN + monitor ekranında görünürlük. Süzme asla çağrıyı yakmamalı.
2. **`type` serbest string.** QSS değerleri değişirse eşleme kırılgan. Azaltım: bilinmeyen değerde entry'yi **eleme, geçir** (fail-open yönü) ve WARN.
3. **Kalem 1b proto uyumsuzluğu.** Alan adı/numarası QSS ile birebir olmazsa değer sessizce default gelir. Azaltım: QSS ekibiyle canonical proto teyidi (13.08'de `capacity` için aynı yöntem işledi).
4. **Kalem 2'de fallback seçilirse** gRPC hatası sessizce stratejiyi değiştirir (FCT yolu → eski POOL yolu). Azaltım: WARN + monitor banner'ı.
5. **Kalem 3'te retry kapatılırsa** cause 34 çağrıları retry penceresine düşer; TRC 25 raporlaması artar. Bu gerçek kayıp değil **muhasebe değişikliğidir** (eskiden aynı çağrı içinde yeniden deneniyordu, şimdi retry olarak sayılacak). PO'ya bildirilmeli (Açık Soru 4).
6. **Hot path maliyeti**: kalem 1a originate başına O(liste) token karşılaştırması; kalem 3 hangup başına O(1). Yeni IO/DB/lock yok.
7. **Kırılgan counter'lar**: kalem 3 `removeCallInProgress`/`decrementRingingCalls` sıralamasına dokunuyor. Azaltım: her iki `originateCall` sonucu için test; `gbpd-runtime-state-audit` kapsamında writer/reader kontrolü.

## Performans Etkisi

- **Hot path durumu**: Evet — `CallOriginator.pollCallsFromConsumerAndOriginate` (cursor kurulumu) ve `ManagerConnectionDecorator.handleDialerHangup`.
- **Kalem 1a**: originate başına entry sayısı × virgül token karşılaştırması. Liste kısa (numara başına bir kayıt, grup boyu kadar). Yeni gRPC/DB/IO yok, `getRouteDetails` çağrı sayısı değişmez.
- **Kalem 1b**: RPC sayısı değişmez; request payload'ına 4 küçük alan eklenir.
- **Kalem 2**: yalnız hata yolunda ek dal; normal akışta ölçülebilir maliyet yok.
- **Kalem 3**: cap kontrolü O(1). Retry kapatılırsa hangup başına yapılan iş **azalır** ve doomed çağrıların kanal tutma süresi düşer — net kazanç trunk kapasitesinde.
- Log: kalem 1a'da süzme sonucu boşalınca WARN (nadir), kalem 3'te cap aşımında INFO. Per-event verbose payload log eklenmez.

## TDD Stratejisi

### Karakterizasyon (önce mevcut davranışı kilitle)

- [ ] `FctRoutingGateDisabledCharacterizationTest` (mevcut) korunur.
- [ ] `ManagerConnectionDecoratorFctRoutingHangupTest` (mevcut) korunur; kalem 3 property'si "sınırsız" değerindeyken davranışın birebir aynı kaldığı doğrulanır.
- [ ] `CallerIdManagerRouteDetailsCharacterizationTest` (yeni): süzme eklenmeden önce cursor'ın QSS sırasını birebir koruduğu ve boş listede `NoAvailablePoolCallerIdException` attığı kilitlenir.

### Yeni davranış

- [ ] `ResourceDistributionServiceFacadeRouteDetailsErrorTest`: gRPC exception ve channel-null durumları "grup yok"tan ayrı sinyal döner; başarılı çağrı davranışı değişmez.
- [ ] `CallerIdManagerRouteDetailsErrorHandlingTest`: hata sinyalinde Açık Soru 2'de seçilen davranış; gerçek boş grupta mevcut davranış korunur.
- [ ] `CallerIdManagerRouteDetailsFilterTest` (kalem 1a): callType eşlemesi (FRESH/RETRY/CALLBACK), virgüllü `type` token'ı, bilinmeyen `type` değerinde entry'nin elenmemesi, `attempts` token kontrolü, süzme boşalınca filtresiz listeye fail-open dönüş.
- [ ] `ManagerConnectionDecoratorFctListRetryCapTest` (kalem 3): cap 0'da retry yok → standart `processCallResult`; cap 1'de tek tur sonra standart akış; `originateCall=false` yolunda campaign counter dengesi.
- [ ] Kalem 1b seçilirse: `ResourceDistributionServiceFacadeRouteDetailsRequestTest` — request alanlarının (`uniqueness`, `attempt`, `callType`, `utilizer`) doğru doldurulduğu.

## Uygulama Kontrol Listesi

Uygulama sırası bağımlılığa göre belirlendi: kalem 2 ve 3 bağımsız, dar ve açık soruya en az bağımlı; kalem 1 hem karar hem (1b seçilirse) QSS bekliyor. İş etkisi sıralaması ise 1 > 2 > 3.

### Kalem 2 — Boş liste / gRPC hatası ayrımı

- [x] **Adım 1**: Mevcut `ResourceDistributionServiceFacadeRouteDetailsTest` karakterizasyon görevini gördü; iki testi yeni sözleşmeye göre güncellendi, boş grup için ayrı test eklendi.
- [x] **Adım 2**: `Facade.getRouteDetails` `RouteDetailsUnavailableException` fırlatıyor; boş liste yalnız gerçek boş yanıtta.
- [x] **Adım 3**: `CallOriginator.handleRouteDetailsUnavailable` → `cancelCall(..., allowRetries=true)`.
- [x] **Adım 4**: `ResourceDistributionServiceFacadeRouteDetailsTest` (5) + `CallerIdManagerRouteDetailsTest` (5) yeşil.

### Kalem 3 — List-retry sınırı ve test boşluğu

- [x] **Adım 5**: `fct.channel.list.retry.max = 0` eklendi (gate açıkken in-call retry yok).
- [x] **Adım 6**: `FctChannelGate.isListRetryAllowed(int)` eklendi; `tryFctRetryWithNextCallerId` sınır aşımında `releaseAndMarkFull` + `false`.
- [x] **Adım 7**: `originateCall=false` kolu için `getCallsInProgressCount()` denge testi yazıldı.
- [x] **Adım 8**: `ManagerConnectionDecoratorFctListRetryCapTest` (5) yeşil — limit 0, limit 1, limit aşımı, kill switch, başarısız re-originate.
- [x] **Adım 8.1** (plan üstü): log satırı kısaltıldı, dead `abbreviateRouteDetailValue` yardımcısı ve sabiti kaldırıldı.

### Kalem 1 — Caller ID seçim context'i

- [x] **Adım 9**: Açık Soru 1 cevaplandı — **1a uygulandı**, 1b QSS gündemine bırakıldı.
- [x] **Adım 10**: `RouteDetailsFilter.expectedTypeLabel` ile eşleme tek noktada.
- [x] **Adım 11**: `obtainRouteDetailsCursor` süzülmüş listeyle cursor kuruyor; üç katmanlı fail-open + WARN.
- [ ] **Adım 12** (1b): proto request alanları — **kapsam dışı bırakıldı**, QSS ekibiyle ayrı gündem.
- [ ] **Adım 13** (1b): facade request doldurma — 1b ile birlikte.
- [x] **Adım 14**: `RouteDetailsFilterTest` (12) + `CallerIdManagerRouteDetailsTest` yeşil.

### Kapanış

- [x] **Adım 15**: Hedefli set (29) + tam FCT regresyon seti → **118 test, 0 failure, 0 error**.
- [x] **Adım 16**: `mvn -o -DskipTests package` → BUILD SUCCESS.
- [x] **Adım 17**: `docs/architecture/DIALER-RUNTIME.md` FCT bölümü güncellendi.
- [x] **Adım 18**: History kaydı eklendi (`docs/history/09.09.2026.md`).

## Doğrulama

```text
mvn -o -Dtest=CallerIdManagerRouteDetailsCharacterizationTest,ResourceDistributionServiceFacadeRouteDetailsErrorTest,CallerIdManagerRouteDetailsErrorHandlingTest,CallerIdManagerRouteDetailsFilterTest,ManagerConnectionDecoratorFctListRetryCapTest test
```

FCT regresyon seti (mevcut davranışın korunduğunu doğrular):

```text
mvn -o -Dtest=FctChannelRegistryTest,FctChannelRegistryConcurrencyTest,FctChannelRegistryCampaignCleanupTest,FctChannelGateTest,FctRoutingGateDisabledCharacterizationTest,CallOriginatorFctGateTest,ManagerConnectionDecoratorFctCause34CooldownTest,ManagerConnectionDecoratorFctRoutingHangupTest,RouteDetailsCursorTest,ResourceDistributionServiceFacadeRouteDetailsTest,CampaignFctRoutingFormMappingRegressionTest test
```

Paketleme:

```text
mvn -o -DskipTests package
```

Maven komutları `.codex/TOOL-PATHS.local.properties` üzerinden offline çalıştırılır.

## Açık Sorular

Tümü 09.09.2026'da geliştirici tarafından onaylandı ("tüm önerilerini onaylıyorum"); uygulama bu cevaplarla yürütüldü.

- [x] **1. Kalem 1 kapsamı**: **1a şimdi uygulandı**, 1b ayrı gündem olarak QSS ekibine bırakıldı. 1a'nın `SAME`/`UNIQUE` semantiğini geri kazandırmadığı `RouteDetailsFilter` javadoc'una ve aşağıdaki uygulama notuna yazıldı.
- [x] **2. Kalem 2 hata davranışı**: **(b)** — `allowRetries=true` ile iptal. Fallback seçilmedi; stratejiyi sessizce değiştirmesi ve o çağrı için kapasite gate'ini devre dışı bırakması nedeniyle.
- [x] **3. Kalem 3 varsayılanı**: **evet, kapalı** — `fct.channel.list.retry.max = 0`. Gate kill switch ile kapatıldığında sınır uygulanmaz (04.05 davranışı birebir geri gelir).
- [x] **4. Raporlama etkisi**: PO ile paylaşılacak (geliştirici sorumluluğunda). Kalem 3 ile aynı çağrı içinde denenen turlar artık retry olarak sayılacağı için TRC 25 raporlaması artar; bu muhasebe değişikliğidir, gerçek kayıp değil.
- [x] **5. Prod teyidi**: Log satırı kodda mevcut (PR 23029'dan geliyor) ama 5 alanlı ve `type` uzun değerler taşıyordu. Geliştirici "kısa ve öz olsun, log dosyası şişmesin" dedi; satır kalem 3 kapsamında kısaltıldı, arama token'ı (`FCT routing list retry`) korundu. Prod'da satırın olup olmadığı teyidi hâlâ operasyon tarafında.

Plan durumu: **Uygulandı ve doğrulandı.**

## Uygulama Sonucu (09.09.2026)

Üç kalem sırayla uygulandı; 118 hedefli/regresyon testi yeşil, `mvn -o -DskipTests package` BUILD SUCCESS.

### Kalem 2 — uygulandı

- `RouteDetailsUnavailableException` (YENİ, `com.powerdialer.exceptions`) eklendi; javadoc'unda kalıcı (boş liste) ve geçici (erişim hatası) ayrımı yazılı.
- `ResourceDistributionServiceFacade.getRouteDetails` artık channel-null ve gRPC exception durumlarında bu exception'ı **fırlatıyor**; boş liste yalnız QSS gerçekten boş liste döndürdüğünde oluşuyor. Facade artık loglamıyor — log tek noktada, çağrı bağlamıyla birlikte basılıyor (kesinti sırasında originate başına çift satır oluşmuyor).
- **Plan üstü ek**: `"Got route details: size=..."` satırı INFO'dan guard'lı DEBUG'a indirildi. Bu satır her FCT originate'inde basılıyordu (günlük ~180.000 satır) ve geliştiricinin log şişmesi endişesiyle aynı kapsamdaydı. Develop'taki `perf(loglar)` commit'i de aynı yaklaşımı izliyor.
- `CallerIdManager.obtainRouteDetailsCursor` exception'ı yukarı taşıyor; `CallOriginator` yeni `handleRouteDetailsUnavailable` ile `cancelCall(..., allowRetries=true)` uyguluyor.

### Kalem 3 — uygulandı

- `fct.channel.list.retry.max = 0` property'si eklendi; `FctChannelGate.isListRetryAllowed(int)` kill switch kapalıyken sınırı uygulamıyor.
- `Call.fctListRetryCount` (transient) sayacı eklendi; `tryFctRetryWithNextCallerId` sınırı aşarsa `gate.releaseAndMarkFull(call)` çağırıp `false` dönüyor — **"bu FCT dolu" bilgisi retry yapılmasa da korunuyor**, ardından standart `processCallResult` (TRC 25 → retry) çalışıyor.
- Log satırı kısaltıldı: `FCT routing list retry: <callId> <index>/<size> <callerId>`. `type` ve `attempts` payload'ı çıkarıldı (QSS'te virgüllü çok değerli, attempts fallback'i 0..99).
- `abbreviateRouteDetailValue` yardımcısı ve `ROUTE_DETAIL_LOG_MAX_LENGTH` sabiti **kaldırıldı** (yalnız kaldırılan log payload'ı için vardı — dead code); ilgili test metodu da kaldırıldı.

### Kalem 1a — uygulandı

- `RouteDetailsFilter` (YENİ, `engine.dialer.routing`) eklendi: callType → QSS etiketi eşlemesi (`FRESH → Dialer First`, `RETRY → Retry`, `CALLBACK → Callback`), virgüllü çok değerli `type`/`attempts` için token bazlı kontrol, QSS sıralamasının korunması.
- **Fail-open üç katmanlı**: boş alan değeri entry'yi elemez; değerde hiç *bilinen* etiket/sayısal token yoksa entry elenmez (QSS sözlüğü değişirse); süzme tüm listeyi elerse filtresiz listeye dönülür ve WARN basılır.
- `CallerIdManager.obtainRouteDetailsCursor` cursor'ı süzülmüş listeyle kuruyor; attempt olarak `call.getAttempts()` kullanılıyor (originate öncesi artırıldığı için QSS'in `AT_` indeksiyle aynı değer).
- **Sınır kayda geçirildi**: bu süzme `retryCallerIdBehavior` (UNIQUE/SAME) semantiğini geri kazandırmaz; o semantik QSS provision history'sini gerektirir. Tam parite kalem 1b ile gelir.

### Uygulama sırasında çıkan yan bulgu

`Campaign.hasCallsInProgress()` (`:2133`) `originatedCalls.size() > 0` okuyor; `addCallInProgress`/`removeCallInProgress` ise ayrı bir `callsInProgress` set'ini yönetiyor (`:220`, `:1839-1847`). İki farklı koleksiyon olduğu için PR 23029'dan gelen `ManagerConnectionDecoratorFctRoutingHangupTest`'teki `hasCallsInProgress() == false` beklentisi **vacuous** (koleksiyon hiç dolmadığı için her koşulda geçer). Yeni testte doğru sayaç (`getCallsInProgressCount()`) kullanıldı. Mevcut testin düzeltilmesi ayrı bir tidy-up olarak değerlendirilebilir.

## Kapanış Kriteri

- [ ] Açık Sorular bölümünde cevaplanmamış madde yok.
- [ ] Seçilen kalemlerin tüm adımları işaretlendi.
- [ ] Hedefli testler ve FCT regresyon seti BUILD SUCCESS, 0 failure / 0 error.
- [ ] `mvn -o -DskipTests package` başarılı.
- [ ] `Performans Etkisi` bölümü gerçek patch'e göre güncel.
- [ ] Mimari doküman güncellendi.
- [ ] History kaydı eklendi.
- [ ] Final yanıtta kısa Test Özeti sunuldu.

## Riskler ve Geri Dönüş

- **Kalem 1a fazla süzüyor**: fail-open sayesinde çağrı yakılmaz; geri dönüş süzmeyi devre dışı bırakan property veya commit revert'i (bu branch'te izole commit).
- **Kalem 1b proto uyumsuzluğu**: değer default gelir, davranış eski hâline döner (fail-safe). Geri dönüş proto alanlarını kaldırmak.
- **Kalem 2 fallback beklenmedik davranış üretiyor**: seçilen davranış tek noktada; geri dönüş eski `NoAvailablePoolCallerIdException` yoluna dönmek.
- **Kalem 3 cause 34 sonuçlarını kötüleştiriyor**: `fct.channel.list.retry.max` değerini eski (sınırsız) davranışa çekmek yeterli; kod geri alınmaz.
- **Tam geri dönüş**: üç kalem de ayrı commit'lerde tutulur; `fct.channel.gate.enabled=false` ile kapasite gate'i, `fct.channel.list.retry.max` ile retry davranışı bağımsız olarak eski hâline döndürülebilir.
