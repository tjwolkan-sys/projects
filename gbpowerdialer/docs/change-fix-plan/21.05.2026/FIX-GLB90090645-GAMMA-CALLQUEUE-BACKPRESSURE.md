# FIX-GLB90090645 Gamma CallQueue Backpressure Guard Change/Fix Planı

- İş Etiketi: fix
- Kimlik: GLB90090645
- Slug: gamma-callqueue-backpressure
- Tarih: 2026-05-21
- Öncelik: normal
- Plan Durumu: Uygulandı ve hedefli regression yeşil (2026-05-21)

## Bağlam

AMI bağlantı kaybı sırasında CallConsumer (Gamma) ile CallOriginator arasında senkron eksikliği nedeniyle `callQueue` sınırsız büyüyor; AMI restore olduğunda biriken contact'lar burst halinde Asterisk'e çıkıyor ve rc78/rc84 abandon üretiyor.

Somut örnek (DIYARBAKIR_UPSELLKONTRAT, 2026-05-20 12:18-12:19):
- 12:18:59 — AMI bağlantısı düştü (Asterisk-side veya network).
- 12:18:59 → 12:19:27 (~28 sn) — CallConsumer her tick `drainedCallCount=4` yapmaya devam etti çünkü `campaign.state == RUNNING` koşulu sağlanıyordu; CallOriginator durumundan habersizdi.
- callQueue 0 → concurrentLines üst sınırına kadar (~56 contact) büyüdü.
- 12:19:27 AMI restore — CallOriginator backlog'u 4-6 saniyede boşalttı.
- `ringingCount` 0 → 22 → 52 → 72 patlaması, simultaneous answer kümeleri → rc78 ve rc84 burst.

Aynı gün 5 kampanyada eş zamanlı ~2476 rc75 (AMI loss) gözlemlendi → altyapı katmanı olayı. Bu PR altyapı sorununu çözmüyor; **uygulama katmanında etki azaltma** ekliyor.

## İş Etiketi

fix

## Öncelik

normal

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallConsumer.java`
  - `pollCallFromProducerToConsumer()` Gamma branch'ine backpressure guard:
    `callQueue.size() >= cps` ise drain (`drainWithBudget`) ve starvation credit update atlanır;
    event hâlâ `drainedCallCount=0` ile yayımlanır.
- `gbpowerdialer/src/test/java/com/powerdialer/engine/dialer/CallConsumerGammaBackpressureGuardTest.java` (YENİ)
  - 8 senaryo: guard tetiklenme, regression (drain serbest), birikim sınırı, telemetri davranışı, starvation skip, Beta etkilenmeme.

## Mevcut Davranış

`CallConsumer.run()` `campaign.state == RUNNING` kontrolü yapıyor ama AMI bağlantı durumunu sorgulamıyor. `CallOriginator.run()` her saniye AMI state'i kontrol edip ölü ise `handleConnectionLoss()` çağırıyor (originate yok). İki bileşen birbirinden habersiz: consumer doldurmaya devam ederken originator boşaltamıyor → callQueue concurrentLines'a kadar şişiyor.

CPS (örn. 7) per server per second uygulanır → restore'da multi-server kampanyada `K × CPS / saniye` hızında backlog boşalır → kısa süreli yüksek originate yoğunluğu → Asterisk simultaneous answer kümesi → rc78/rc84.

## Beklenen Davranış

- Sağlıklı state'te tick anında callQueue genelde 0; guard tetiklenmez, mevcut davranış değişmez.
- AMI ölü iken (veya CallOriginator yavaş çekiyorsa) callQueue CPS'e dayandığı an consumer drain etmez. Mevcut backlog (en fazla CPS adet) boşalsın diye bekler.
- AMI restore → originator hızla CPS hızında boşaltır → guard otomatik release olur → consumer normal akışa döner. Burst yok.
- Çoklu sunucu: hiç sayım/state takibi gerekmez. Bir server ölü diğer canlı → kalan originator çekmeye devam → queue boş kalır → guard tetiklenmez. Tüm server'lar ölü → queue dolar → guard tetiklenir. Otomatik doğru davranış.
- Telemetri: guard tetiklendiği tick'lerde `drainedCallCount=0` AND `queueSize >= cps` deseni event payload'unda görünür. Analyzer türev kolonla "backpressure aktif" durumunu sınıflandırır. Java event'ine yeni alan eklenmez.

## Kod Okuma Bulguları

- `CallConsumer.callQueue` BlockingDeque, `callQueueSoftLimit = max(1, concurrentLines)` üst sınırla `remainingSoftCapacity` üzerinden zaten bağlı; ama bu sınır agent sayısının katları olduğu için (~30) AMI loss penceresinde ciddi backlog'a izin veriyor.
- `drainWithBudget` her tick `min(cps, remainingSoftCapacity)` ile sınırlı. Yani consumer EKLEME hızı sabit CPS — ek mantık gerekmez.
- `CallOriginator.callQueues` listesi tüm originator'lar tarafından paylaşılan tek queue üzerinden çalışır. Multi-server'da originator'lar yarış ederek queue'dan çeker.
- `calculateDialBudget` formülü çalıştırır (Holt state, window stats); guard tetiklendiğinde de çağrılır → state tutarlılığı korunur.

## Root Cause / Değişiklik Gerekçesi

CallConsumer ve CallOriginator arasında AMI-state senkronu yok. Consumer her ne kadar `state == RUNNING` kontrol etse de bu kampanya yaşam döngüsü state'idir, bağlantı state'i değildir. Sonuç: AMI ölü iken consumer doldurur, originator boşaltamaz, queue patlama riskli.

Çözüm yaklaşımı için iki seçenek değerlendirildi:

1. **AMI status guard** (CallConsumer'a AMI bağlantı state'ini sor): plumbing gerekli (CampaignContext → DialerFacade → server pool query), multi-server agregasyonu zorunlu.
2. **Queue threshold guard** (callQueue.size() >= cps): yerel kontrol, sıfır plumbing, multi-server otomatik handle.

Kullanıcı tercihi (2026-05-21) ile **#2** seçildi. Daha temiz, daha geniş kapsayıcı (AMI loss + originator stall + asterisk overload — hepsi aynı symptom).

## Riskler

- **False trip riski**: Sağlıklı state'te queue tick anında geçici olarak CPS'e ulaşabilir mi? Hesap:
  - Consumer 2sn'de CPS=7 ekler → callQueue tick sonrası max 7
  - Originator delayOrigination ile 1sn içinde 7'yi boşaltır
  - Sonraki consumer tick'i 1sn sonra → queue genelde 0
  - JVM scheduling jitter <50ms → marjinal
  - Sonuç: false trip riski **çok düşük**. Olursa o tick 1 atlanır (3.5 contact eksik dial), bir sonraki tick normal akış.
- **Production loss**: Sadece AMI loss + restore senaryolarında devreye girer. Sağlıklı operasyonda hiç tetiklenmez → production etkisi sıfır.
- **Starvation credit etkisi**: Guard tetiklendiğinde `updateStarvationCredit` atlanır. Shared kampanyaların yapay kredi biriktirmesinin önüne geçilir (bu "starvation" değil, fiziksel blokaj). Tek bir starvation credit periyodu kaçırılırsa shared boost dağıtımı minimum etkilenir.
- **Telemetri yorumlama**: `drainedCallCount=0 AND queueSize >= cps` deseni operatör için manuel okuma gerektirir. AGENTS.md/GAMMA-FORMULA.md'de not edilmeli; analyzer tarafında türev kolon `drain_backpressure` eklenecek (ayrı PR).
- **AGENTS.md kuralları**:
  - "Yeni kütüphane yok" — sadece mevcut concurrent collection API ✅
  - "Yeni static mutable state yok" — sadece local boolean flag ✅
  - "Call flow hot path performans etkisi" — tick başına 1 ek `callQueue.size() >= cps` karşılaştırma ✅

## TDD Stratejisi

Yeni test sınıfı: `tests/dialer/CallConsumerGammaBackpressureGuardTest`. 8 senaryo:

1. `backpressureGuard_skipsDrain_whenCallQueueAtCps` — queue=CPS, guard trip, drain atlandı, dataQueue dokunulmadı.
2. `backpressureGuard_skipsDrain_whenCallQueueAboveCps` — queue>CPS, guard trip.
3. `backpressureGuard_allowsDrain_whenCallQueueBelowCps` — queue<CPS, normal drain (regression).
4. `backpressureGuard_allowsDrain_whenCallQueueIsEmpty` — queue=0, regression.
5. `backpressureGuard_callQueueDoesNotGrowAboveCps_overMultipleTicks` — 10 ardışık tick (AMI loss simulation), originator boşaltmıyor → callQueue CPS'i geçmez.
6. `backpressureGuard_stillDispatchesEvent_withDrainZero` — guard trip durumunda dispatchEvent çağrılır, drainedCallCount=0 yazılır.
7. `backpressureGuard_doesNotInvokeStarvationCredit` — guard trip → starvation credit update atlanır.
8. `backpressureGuard_doesNotApplyToBetaCampaign` — Beta branch'inde guard yok (sadece Gamma).

Regresyon (kapsamak için koşturulan): `CallConsumerGammaBudgetTest`, `PredictiveGammaCallCalculatorRingingLoadUcbTest`, `PredictiveGammaCallCalculatorTest`, `PredictiveGammaCallManagerTest`, `PredictiveGammaRecentCallsTest`, `SharedGammaSnapshotProviderTest`, `CampaignRealTimeRingingManagerPendingRingingTest`, `AwaitingUserResult*Test`, `SaveInitialCdrHangupResultRegressionTest`, `StopStateTest`.

## Açık Sorular

- [x] Eşik değeri: `>= cps` mi `>= cps+1` mi? Karar: **`>= cps`**. Sağlıklı state'te tick anında queue=0, false trip riski yok; semantik temiz ("CPS'e dayandı → sorun").
- [x] Yeni telemetri alanı gerekli mi? Karar: **Hayır**. Mevcut `drainedCallCount` + `queueSize` kombinasyonu backpressure'ı uniquely identifies. Analyzer tarafında türev kolon (`drain_backpressure = drained==0 AND queue_size >= cps`) eklenecek.
- [x] Multi-server senaryo: AMI status takibi gerekli mi? Karar: **Hayır**. Queue threshold yöntemi multi-server'ı otomatik handle eder (bir server canlı → queue boş → guard pasif; tüm server ölü → queue dolu → guard aktif).
- [ ] Pilot kampanya: önce hangi kampanyada izleyelim? Önerim: SUPERONLINE_NT (en yüksek rc75 maruziyeti + en hassas profil). Min 1 iş günü gözlem.
- [ ] `bs-gamma-analyzer` türev kolon ve görselleştirme: ayrı PR olarak takip işi.

## Execution Plan

- [x] Sözleşmeleri (AGENTS.md, change-fix-plan README) ve CallConsumer/CallOriginator akışını tara.
- [x] Mevcut testleri ve guard'ın aklınında yer alabileceği yerleri çıkar; `drainWithBudget` öncesi tek noktada karar verilebileceğini doğrula.
- [x] Failing TDD test'i yaz (`CallConsumerGammaBackpressureGuardTest`, 8 case).
- [x] `CallConsumer.pollCallFromProducerToConsumer()` Gamma branch'ine `backpressureActive` flag + koşullu drain/starvation update + telemetri.
- [x] Hedefli testler yeşile çevrildi (8/8 yeni + 71/71 mevcut regression = 79/79).
- [x] Change-fix-plan dosyası açıldı (bu doküman).
- [x] History kaydı `docs/history/21.05.2026.md` eklendi; `docs/history/README.md` indeksi güncellendi.
- [ ] Pilot kampanyada canlı gözlem (deploy sonrası takip).
- [ ] `bs-gamma-analyzer` `drain_backpressure` türev kolonu + görselleştirme (ayrı PR).

## Performans Etkisi

- Hot path durumu: `CallConsumer.pollCallFromProducerToConsumer()` Gamma tick (campaign başına 2 sn'de 1 kez) tarafından çağrılır.
- Call/event/tick başına ek maliyet:
  - tick başına: 1 ek `callQueue.size()` çağrısı + 1 karşılaştırma. O(1).
  - Guard tetiklendiğinde `drainWithBudget` (potansiyel O(N) iterasyon) atlanır → **negatif maliyet** (daha hızlı).
- IO/scan/lock/serialization/log etkisi: Yeni IO yok. `BlockingDeque.size()` lock-free implementasyon. Log yok (mevcut "drained > 0" log atlanır, drained=0 olduğu için zaten yazılmazdı).
- Azaltım veya ölçüm notu: Tick latency artışı ölçülemez seviyede. Guard tetiklendiğinde gerçekten daha hızlı çalışır (drain iterasyonu yok).

## Doğrulama

`.codex/TOOL-PATHS.local.properties` okundu; Maven offline + `properties-tr-dev` profili + JDK 1.8.0_202 ile çalıştırıldı.

Hedefli komutlar:

```text
mvn -o -P properties-tr-dev -Dtest=CallConsumerGammaBackpressureGuardTest test
mvn -o -P properties-tr-dev -Dtest=CallConsumerGammaBackpressureGuardTest,CallConsumerGammaBudgetTest,PredictiveGammaCallCalculatorRingingLoadUcbTest,PredictiveGammaCallCalculatorTest,PredictiveGammaCallManagerTest,PredictiveGammaRecentCallsTest,SharedGammaSnapshotProviderTest,CampaignRealTimeRingingManagerPendingRingingTest,AwaitingUserResultCacheTransitionRegressionTest,AwaitingUserResultTimeoutPreserveRegressionTest,SaveInitialCdrHangupResultRegressionTest,StopStateTest test
```

Sonuç:
- `CallConsumerGammaBackpressureGuardTest`: 8/8 başarılı.
- Toplu hedefli regresyon: **79/79 başarılı** (8 yeni + 71 mevcut Gamma/AMI/manager regression).

## Kapanış Kriteri

- [x] Açık sorular cevaplandı (eşik, telemetri, multi-server).
- [x] Yeni TDD testleri yeşile çevrildi (8/8).
- [x] Mevcut regresyon testleri yeşil (71/71).
- [x] Final yanıtta kısa Test Özeti sunuldu (79/79).
- [x] History kaydı eklendi ve indeks güncellendi.
- [ ] Pilot kampanya canlı izleme tamamlandı (post-deploy).
- [ ] `bs-gamma-analyzer` türev kolon eklendi (takip işi).

## Tamamlanma Notu

Uygulama tamamlandı (2026-05-21). Üretim kodu değişiklikleri:

- `CallConsumer.java`:
  - `pollCallFromProducerToConsumer()` Gamma branch'ine `backpressureActive` boolean (`callQueue.size() >= cps`) eklendi.
  - `backpressureActive == true` ise `drainWithBudget` ve `updateStarvationCredit` atlanır (drainSize=0 olarak akış devam eder).
  - `dispatchEvent` her durumda yayımlanır (telemetri sürekliliği); `drainedCallCount=0` ve `queueSize` doğru değerlerle.

Test değişiklikleri:

- Yeni: `tests/dialer/CallConsumerGammaBackpressureGuardTest` (8 senaryo).

Doğrulama:

- `CallConsumerGammaBackpressureGuardTest`: 8/8 ✅
- Toplam targeted regression: 79/79 ✅

Plan kullanıcı onayı ("tamam dikkatli şekilde ozaman başlayalım, yaptıklarından emin ol" — 2026-05-21) ile feature branch `feature/GammaImprovements-part11-AddUCBForRingLoad` üzerinde tamamlandı. Analyzer türev kolon (`drain_backpressure`) ayrı PR olarak işaretlendi.
