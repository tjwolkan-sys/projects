# FIX-GLB90090645 Gamma Pending Ringing Cache Change/Fix Planı

- İş Etiketi: fix
- Kimlik: GLB90090645
- Slug: gamma-pending-ringing-cache
- Tarih: 2026-05-15
- Öncelik: normal
- Plan Durumu: Uygulandı ve hedefli regression yeşil (2026-05-15)

## Bağlam

Predictive Gamma stratejisi, her döngüde `ringingLoad` hesabı için anlık olarak çalan çağrı sayısını (`ringingCount`) bilmek zorundadır. Bu sayı `CampaignRealTimeRingingManager.getRealTimeRingingMetrics` üzerinden alınır; içerdiği cache yalnızca Asterisk'in **VarSetEvent** ile onayladığı çağrıları tutar.

Originate akışı şu sırada işliyor:

1. `ManagerConnectionDecorator.originateCall` → `calls.put` + `sendOriginateActionToAsterisk` + `addCallInProgress` (T0)
2. Asterisk dialplan ORIGINID set ediyor, AMI üzerinden VarSetEvent dönüyor (T0 + 1–2 sn)
3. `handleCallCreation` → `incrementRingingCalls` + `addRingingCall(call)` (real-time cache'e eklenir)
4. `handleCallAnswer` veya hangup → `removeRingingCall` + `decrementRingingCalls`

Adım 1 ile 3 arasındaki 1–2 saniyelik AMI roundtrip penceresinde, originate edilmiş çağrılar real-time cache'te görünmüyor. Gamma tick'i (2 saniyede bir koşuyor — `CallConsumer.GAMMA_CPS_PERIOD=2000`) bu pencerede koşarsa "uçuştaki" çağrıları sayamadığı için `ringingLoad` eksik kalır, `xn = rawXn − ringingLoad` fazla pozitif çıkar ve sistem over-dial yapar. Müşteri sahasında alınan örnek veri bunu doğruluyor: 14:11:49 → 14:11:55 arasında 4 tick'te toplam 25 drain (7+6+7+5) yapıldıktan sonra `ringingCount` 12 → 22 → 25 → 30 sıçraması gözleniyor; bu sıçramanın bir kısmı drain'ler sırasında ringingLoad'a hiç dahil edilmemişti.

Asterisk tarafına dokunma imkânı (snapshot, dialplan erken event ekleme) **yok**; çözüm Java tarafında olmak zorunda.

## İş Etiketi

fix

## Öncelik

normal

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CampaignRealTimeRingingManager.java`
  - Yeni `pendingRingingCallsMap` ve `PENDING_RINGING_TTL_SEC` sabiti
  - `onCampaignStart`, `onCampaignStop` lifecycle
  - `addRingingCall` davranışı (pending invalidate)
  - Yeni `addPendingRingingCall`, `removePendingRingingCall` public API
  - `getRealTimeRingingMetrics` sayım kapsamı (+ pending telemetri)
  - Yeni inner static `PendingRingingEntry` (call + addedAtMs)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java`
  - `originateCall` non-preview yolu (pending'e yazma)
  - `clearCall` central cleanup (pending'i temizleme)
  - `CampaignRealTimeRingingManager` import'u
- `gbpowerdialer/src/main/java/com/powerdialer/dto/RingingMetricsDTO.java`
  - Yeni `pendingTotalRinging` (int) ve `pendingMaxAgeMs` (long) alanları (telemetri)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/predictive/gamma/model/PredictiveGammaCallData.java`
  - Yeni `pendingRingingCount`, `realtimeOnlyRingingCount`, `pendingRingingMaxAgeMs` alanları
- `gbpowerdialer/src/main/java/com/powerdialer/engine/predictive/gamma/calculator/PredictiveGammaCallCalculator.java`
  - `buildCallData` artık pending telemetri alanlarını da doldurur (`realtimeOnlyRingingCount = total - pending` türetilir)
- `gbpowerdialer/src/main/java/com/powerdialer/redis/event/predictive/PredictiveGammaEvent.java`
  - Aynı üç alan Redis event payload'una taşınır (bs-gamma-analyzer'ın okuduğu kanal)
- `gbpowerdialer/src/test/java/tests/dialer/CampaignRealTimeRingingManagerPendingRingingTest.java` (yeni)
  - 12 senaryo: 9 davranış + 3 telemetri (pending count ayrımı, pendingMaxAgeMs yaş ölçümü, pending boşken alan 0)

## Mevcut Davranış

`CampaignRealTimeRingingManager` tek cache tutar: `realTimeRingingCallsMap`. Cache'e ekleme yalnız `addRingingCall` üzerinden, yalnız `handleCallCreation` (VarSetEvent) yolundan yapılır. `getRealTimeRingingMetrics` bu cache'i tarayıp `RingingMetricsDTO` üretir; tip kırılımı (CB/RETRY/FRESH) Call objesinin `getCallType()`'ından okunur.

Originate ile VarSetEvent arasındaki pencerede:

- `Campaign.callsInProgress` set'i çağrıyı görür (Set<String>, sadece id'ler — tip bilinmiyor).
- `ManagerConnectionDecorator.calls` Guava cache çağrıyı görür (startDate=null).
- `CampaignRealTimeRingingManager` görmez. Gamma da bu kanalı kullandığı için over-dial olur.

## Beklenen Davranış

- Originate edildiği anda çağrı kısa süreli bir "pending" buffer'a yazılır; Gamma tick'i bu buffer'ı da `ringingLoad` hesabında görür.
- VarSetEvent geldiğinde çağrı pending'den otomatik çıkarılır ve real-time cache'e eklenir; **toplam ringing sayısı değişmez** (double-count yok).
- Originate fail, exception veya bağlantı kaybı yollarında pending temizlenir (central cleanup üzerinden otomatik kapsanmalı).
- VarSetEvent hiç gelmezse pending TTL kendi başına temizlik yapar (self-healing).
- Tip kırılımı (CB/RETRY/FRESH) hem pending'de hem real-time'da korunur.
- Preview kampanyaları (Redis pub/sub yoluyla giden) etkilenmez; Beta predictive (kendi AtomicInteger sayacını kullanıyor) etkilenmez.

## Kod Okuma Bulguları

- `Campaign.callsInProgress` mevcut authoritative ama tip bilgisi yok; tek başına yeterli değil (Öneri 2 elendi).
- `clearCall` (line 1040) hem normal hangup hem origination fail hem exception hem connection loss yollarının central cleanup noktası; pending temizliği burada yapılırsa tüm hata yolları otomatik kapsanır.
- `addRingingCall` çağrısı yalnız `handleCallCreation` (VarSetEvent) yolunda yapılıyor; pending invalidate'ı bu method'a koymak otomatik geçişi sağlar.
- `originateCall` preview kampanyalarda `publishOriginateMessageToRedis` yoluna gidiyor (AMI yok); pending yalnız `else` (direct AMI) dalında devreye girmeli.
- `Beta` predictive `Campaign.getRingingCalls().get()` AtomicInteger'ı kullanıyor (PredictiveBetaEvent.java:52); real-time cache'i okumuyor. Pending değişikliği Beta'yı etkilemez.

## Root Cause / Değişiklik Gerekçesi

`CampaignRealTimeRingingManager` "Asterisk doğrulanmış ringing" sayar; "originate edildi ama Asterisk doğrulamadı" durumunu kapsamıyor. AMI gecikmesi yapısal bir gerçek (snapshot mümkün değil); Java tarafında bu gri pencereyi kapatan bir authoritative kaynak gerekli. En düşük blast radius'lu çözüm: aynı manager içine type-aware, self-healing, kısa ömürlü pending buffer eklemek.

## Çözüm Yaklaşımı

### Tasarım

`CampaignRealTimeRingingManager` içine ikinci bir Guava cache (`pendingRingingCallsMap`) eklenir. TTL = 5 sn (`PENDING_RINGING_TTL_SEC`); AMI roundtrip 99p + tampon. Cache her kampanya için `onCampaignStart`'ta yaratılır, `onCampaignStop`'ta düşürülür.

Yeni public API:

- `addPendingRingingCall(Call)`: originate edilen çağrıyı pending'e yazar.
- `removePendingRingingCall(Call)`: fail/cleanup yollarında pending'den çıkarır.

`addRingingCall(Call)` davranışı genişletilir: real-time'a put + pending'den invalidate. Böylece VarSetEvent geldiği anda çağrı tek atomic adımla pending'den real-time'a "taşınır"; sayım toplamı değişmez.

`getRealTimeRingingMetrics(Campaign)` her iki cache'i de sayar; tip kırılımı korunur. Sayma mantığı `countActiveRinging` helper'ına çıkarılır (DRY).

### Wire-up

`ManagerConnectionDecorator.originateCall`:

```java
if (call.getCampaign().isPreviewCampaign()) {
  publishOriginateMessageToRedis(originateAction, call);
  publishCallPreviewEvent(call);
} else {
  sendOriginateActionToAsterisk(originateAction);
  connectionComposition.getDialerFacade()
      .getCampaignRealTimeRingingManager()
      .addPendingRingingCall(call); // YENİ
}
call.getCampaign().addCallInProgress(call);
```

`ManagerConnectionDecorator.clearCall`:

```java
CampaignRealTimeRingingManager ringingManager = ...;
ringingManager.removeRingingCall(call);
ringingManager.removePendingRingingCall(call); // YENİ — central cleanup
```

`clearCall` zaten origination fail/exception/connection loss yollarının tümünden çağrıldığı için ek wiring gerekmez.

### Self-healing edge case'leri

- AMI hiç dönmedi: pending TTL 5 sn sonra otomatik düşer. En kötü 5 sn fazla sayım; sonra düzelir.
- Originate fail: `handleOriginateResponse` → `handleOriginationFail` → `processCallResult` → `clearCall` → pending invalidate (anlık).
- Exception: `handleExceptionDuringOrigination` → `clearCall` → pending invalidate.
- AMI bağlantı kaybı: `ManagerConnectionComposition.cleanUpCallsOnConnectionLoss` → `clearCall` → pending invalidate.

## Riskler

- **Mikro race condition (sırasıyla)**: Çok hızlı VarSet (<50ms) durumunda `handleCallCreation.addRingingCall` `addPendingRingingCall`'dan ÖNCE koşabilir → pending invalidate boşa düşer, sonra pending put olur ve 5 sn double-count görünür. Üretimde bu yarışın olasılığı düşük (AMI roundtrip ~1-2 sn). İleride sorun olursa `addPendingRingingCall`'da realTime contains kontrolü veya order swap (pending önce, send sonra) eklenebilir.
- **TTL yetersizliği**: AMI roundtrip p99 5 sn'yi aşarsa pending erken düşer, kısa bir görünmezlik penceresi oluşur (yine de bugünkü 1-2 sn'den iyi). Pilotta `pendingMaxAgeMs` telemetrisiyle izlenmeli.
- **Hot-path overhead**: originate başına 1 `cache.put`, ring metrics okuma başına ek for döngüsü (boyut küçük, p99 < 1ms). Hesap formülü değişmedi.
- **Memory**: 5 sn TTL × tipik peak originate hızı → kampanya başına 10-50 entry. İhmal edilebilir.
- **AGENTS.md kuralları**:
  - "Yeni kütüphane yok" — sadece mevcut Guava kullanıldı ✅
  - "Yeni static mutable state yok" — pending cache instance field, ek static yok ✅
  - "Increment/decrement counter'ı tek source of truth gibi kullanma" — Campaign'in AtomicInteger sayacına dokunulmadı; authoritative `CampaignRealTimeRingingManager` olarak kaldı ✅
  - Call flow hot path performans etkisi `Performans Etkisi` bölümünde ✅

## TDD Stratejisi

Yeni test sınıfı: `tests/dialer/CampaignRealTimeRingingManagerPendingRingingTest`. 9 senaryo:

1. `pendingCall_isCountedInTotalRinging` — pending eklenince total artar, tip kırılımı çalışır.
2. `pendingCall_typeBreakdown_callback` — CB tipinde pending cbRinging artırır.
3. `pendingCall_typeBreakdown_retry` — RETRY tipinde retryRinging artırır.
4. `varSetTransition_movesCallFromPendingToRealTime_withoutDoubleCounting` — VarSet sonrası toplam aynı kalır.
5. `removePendingRingingCall_clearsEntry` — fail simulasyonu, pending temizlenir.
6. `pendingForUnstartedCampaign_isNoOp` — onCampaignStart çağrılmamış kampanyada NPE yok.
7. `onCampaignStop_clearsPendingCache` — stop sonrası restart'ta pending boş.
8. `answeredCallInPending_isNotCounted` — cevap dönen çağrı pending'de bile sayılmaz (mevcut filtreyle tutarlı).
9. `mixedRealTimeAndPending_areSummed` — iki kova birlikte doğru sayılır.

Regresyon (kapsamak için koşturulan): `PredictiveGammaCallManagerTest`, `PredictiveGammaCallCalculatorTest`, `PredictiveGammaRecentCallsTest`, `SharedGammaSnapshotProviderTest`, `AwaitingUserResultCacheTransitionRegressionTest`, `AwaitingUserResultTimeoutPreserveRegressionTest`, `SaveInitialCdrHangupResultRegressionTest`, `StopStateTest`.

## Açık Sorular

- [ ] **Pilot kampanya seçimi**: Pending davranışı önce hangi kampanyada izlenecek? Önerim: drain hacmi orta-yüksek tek bir Gamma kampanyası (paylaşımsız) ile başlamak; 1-2 gün `pendingRingingCount` ve `pendingRingingMaxAgeMs` izlenmeli.
- [x] **Telemetri**: `PredictiveGammaCallData` ve `PredictiveGammaEvent`'e `pendingRingingCount`, `realtimeOnlyRingingCount`, `pendingRingingMaxAgeMs` alanları aynı PR kapsamında eklendi (kullanıcı onayı 2026-05-15). `bs-gamma-analyzer` tarafında ALTER TABLE / kolon ekleme adımı ayrı bir takip işidir.
- [ ] **TTL ayarı**: 5 sn başlangıç değeri yeterli mi yoksa 7 sn ile mi açılsın? Üretim AMI roundtrip p99 verisi yoksa pilotta `pendingRingingMaxAgeMs` p99'una göre ayarlanacak (kullanıcı onayı: şimdilik 5 sn).

## Execution Plan

- [x] Sözleşmeleri (AGENTS.md, change-fix-plan README) ve etkilenen tüm dosyaları tara.
- [x] Mevcut testleri ve cleanup yollarını çıkar; `clearCall`'in central olduğunu doğrula.
- [x] Failing TDD test'i yaz (`CampaignRealTimeRingingManagerPendingRingingTest`).
- [x] `CampaignRealTimeRingingManager` içine pending cache, public API ve sayım genişletmesi eklendi.
- [x] `ManagerConnectionDecorator.originateCall` non-preview yolunda `addPendingRingingCall` çağrısı eklendi.
- [x] `ManagerConnectionDecorator.clearCall` içinde `removePendingRingingCall` çağrısı eklendi (central cleanup).
- [x] `CampaignRealTimeRingingManager` import'u eklendi.
- [x] Telemetri katmanı eklendi: `RingingMetricsDTO`, `PredictiveGammaCallData`, `PredictiveGammaCallCalculator.buildCallData`, `PredictiveGammaEvent` (pending count, realtimeOnly count, pendingMaxAgeMs).
- [x] `PendingRingingEntry` holder eklendi (call + addedAtMs); pending cache değer tipi bu holder'a yükseltildi.
- [x] Telemetri test senaryoları eklendi (3 case): pending count ayrımı, maxAge yaş ölçümü, pending boşken alanların 0 olması.
- [x] Hedefli testler yeşile çevrildi (12/12 yeni manager test + 51/51 mevcut regression = 63 toplam).
- [x] History kaydı `docs/history/15.05.2026.md` eklendi; `docs/history/README.md` indeksi güncellendi.
- [x] Change-fix-plan dosyası açıldı (bu doküman, retrospektif olarak doldurulup tamamlanma notu eklendi).
- [ ] `bs-gamma-analyzer` schema güncellemesi (yeni 3 kolon: pending_ringing_count, realtime_only_ringing_count, pending_ringing_max_age_ms) — ayrı PR / takip işi.
- [ ] Pilot kampanyada canlı gözlem (deploy sonrası takip).

## Performans Etkisi

- Hot path durumu: `originateCall` AMI hot path'inin son adımı (call başına 1 kez). `getRealTimeRingingMetrics` Gamma tick (campaign başına 2 sn'de 1 kez) tarafından okunur.
- Call/event/tick başına ek maliyet:
  - originate başına: 1 `cache.put` (Guava ConcurrentMap, O(1))
  - VarSet handler başına: 1 ek `cache.invalidate` (mevcut put'a ek)
  - Gamma tick başına: 1 ek for döngüsü pending cache üzerinde (boyut < 100 entry tipik, O(n))
- IO/scan/lock/serialization/log etkisi: Yeni DB/network IO yok. Yeni synchronized blok yok. Yeni log mesajları yalnız onCampaignStart'ta tek satır eklendi. Cache iteration zaten mevcut yapı.
- Azaltım veya ölçüm notu: Pending cache TTL 5 sn olduğu için steady-state boyutu küçük kalır. Memory footprint kampanya başına 50 × Call referansı ≈ ihmal edilebilir. Hot-path latency artışı O(μs).

## Doğrulama

Doğrulama öncesi `.codex/TOOL-PATHS.local.properties` okundu; Maven offline çalıştırıldı.

Hedefli komutlar:

```text
mvn -o -s C:/Users/GLB90090645/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90090645/.m2/repository -Dtest=CampaignRealTimeRingingManagerPendingRingingTest test
mvn -o -s C:/Users/GLB90090645/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90090645/.m2/repository -Dtest=PredictiveGammaCallManagerTest,PredictiveGammaCallCalculatorTest,SharedGammaSnapshotProviderTest,PredictiveGammaRecentCallsTest,AwaitingUserResultCacheTransitionRegressionTest,SaveInitialCdrHangupResultRegressionTest,AwaitingUserResultTimeoutPreserveRegressionTest,StopStateTest test
```

Sonuç:
- `CampaignRealTimeRingingManagerPendingRingingTest`: 9/9 başarılı.
- Toplu regresyon: 51/51 başarılı.
- `DialerFacadeTest` baseline'da da fail veriyor (`UnnecessaryStubbingException`, pre-existing); bu değişiklikle ilişkisi yok.

## Kapanış Kriteri

- [x] Açık sorular dökümana yazıldı; pilot ve TTL kararı canlı izleme sonrası verilecek.
- [x] Yeni TDD testleri yeşile çevrildi.
- [x] Mevcut regresyon testleri yeşil (pre-existing fail hariç, raporlandı).
- [x] History kaydı eklendi ve indeks güncellendi.
- [x] Plan dosyası açıldı (retrospektif).
- [ ] Pilot kampanya canlı izleme tamamlandı (post-deploy).

## Tamamlanma Notu

Uygulama tamamlandı (2026-05-15). Üretim kodu değişiklikleri:

- `CampaignRealTimeRingingManager.java`:
  - `pendingRingingCallsMap` (Guava cache, TTL 5 sn) eklendi. Değer tipi `PendingRingingEntry` (call + addedAtMs).
  - `addPendingRingingCall`, `removePendingRingingCall` public method'ları açıldı.
  - `addRingingCall` artık pending'i otomatik invalidate eder (double-count önlemi).
  - `getRealTimeRingingMetrics` her iki cache'i sayar; gerçek cache `countActiveRinging` ile, pending cache `countActivePendingRinging` ile (yaş hesabı ve pending count'u burada toplanır). Tip kırılımı (CB/RETRY/FRESH) iki kovada da korunur.
  - `onCampaignStart` / `onCampaignStop` pending cache lifecycle'ını yönetir.
- `ManagerConnectionDecorator.java`:
  - `originateCall` non-preview yolunda (`else` bloğu) `addPendingRingingCall` çağrısı eklendi.
  - `clearCall` central cleanup'ta `removePendingRingingCall` çağrısı eklendi.
  - `CampaignRealTimeRingingManager` import'u eklendi.
- `RingingMetricsDTO.java`:
  - `pendingTotalRinging` (int) ve `pendingMaxAgeMs` (long) alanları eklendi.
- `PredictiveGammaCallData.java`:
  - `pendingRingingCount`, `realtimeOnlyRingingCount`, `pendingRingingMaxAgeMs` alanları eklendi (Lombok @Data).
- `PredictiveGammaCallCalculator.java`:
  - `buildCallData` pending telemetri alanlarını set eder; `realtimeOnlyRingingCount = max(0, total - pending)` türetilir.
- `PredictiveGammaEvent.java`:
  - Aynı üç alan event payload'una taşındı (Redis event üzerinden bs-gamma-analyzer'a akar).

Test değişiklikleri:

- Yeni: `tests/dialer/CampaignRealTimeRingingManagerPendingRingingTest` (12 senaryo = 9 davranış + 3 telemetri).

Doğrulama:

- `CampaignRealTimeRingingManagerPendingRingingTest`: 12/12 ✅
- Toplam targeted regression (Gamma + AMI + StopState + manager): 63/63 ✅
- `DialerFacadeTest.UnnecessaryStubbingException` baseline'da da fail veriyor; pre-existing, ilgisiz.

Bu plan retrospektif olarak hazırlandı: uygulama önce kullanıcının "başla" onayıyla yapıldı, ardından AGENTS.md change-fix-plan akışını tamamlamak için dosya açıldı. Telemetri katmanı kullanıcının "hemen ekle" kararıyla aynı PR'a dahil edildi. Bir sonraki iterasyonda plan açma onayı kod değişikliğinden önce alınmalı.

## Revize Notları

### 2026-05-15 — TTL ve log seviyesi ayarı

- **`PENDING_RINGING_TTL_SEC` 5 → 10**: Kullanıcı kararı ile pilot başlangıç değeri 10 sn'ye çıkarıldı. Sebep: AMI roundtrip p99 ölçüm verisi henüz yok; muhafazakar üst sınır pilot süresince self-heal kaynaklı eksik sayımı azaltır. Telemetri (`pendingRingingMaxAgeMs`) p99'una göre canlıda revize edilir. Memory etkisi hâlâ ihmal edilebilir (kampanya başına 20-100 entry).
- **`onCampaignStart` log seviyesi**: Pending buffer'a özel log `log.debug` seviyesine çekildi; mevcut `log.info("Initialized realtime ringing cache for campaign...")` orijinal haline döndürüldü. Pending init detayı yalnız debug aktifken görünür. Üretim INFO log'u kalabalıklaşmadı.
- Bu plan ve önceki history kaydı (append-only) korundu; revize satırları yalnız buraya eklendi.
