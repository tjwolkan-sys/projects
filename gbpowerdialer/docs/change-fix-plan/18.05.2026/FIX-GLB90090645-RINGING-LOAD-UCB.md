# FIX-GLB90090645 Gamma Ringing Load UCB Change/Fix Planı

- İş Etiketi: fix
- Kimlik: GLB90090645
- Slug: ringing-load-ucb
- Tarih: 2026-05-18
- Öncelik: normal
- Plan Durumu: Uygulandı ve hedefli regression yeşil (2026-05-18)

## Bağlam

Predictive Gamma stratejisinin merkez formülü:

```text
xn = (effectiveAvailable + (incall / (avgTalk + avgAcw)) × avgRing × CF) × abandonPenalty − ringingLoad
ringingLoad = cb.AR × ringingCb + retry.AR × ringingRetry + fresh.AR × ringingFresh
```

Mevcut `ringingLoad`, üç çağrı tipinin (cb/retry/fresh) **beklenen değer** (mean) tahmini. Beklenen değer doğru, ama ağırlıklı düşük AR + büyük ringing havuzu (örn. SUPERONLINE fresh blok, retry burst) senaryolarında **eş zamanlı cevap kümeleri** (binomial dağılımın varyansı) Asterisk tarafında rc78/abandon olarak geri dönüyor:

- 18.05.2026 SUPERONLINE örneği: retry AR ~0.007, fresh AR ~0.01 aralığında, anlık ringing 250–400. Beklenen değer cevap = 1.7–4 cevap. Ama binom σ = √(N × p × (1−p)) ≈ 1.3–2 → tek tick'te 6–8 eş zamanlı cevap olağan, ringingLoad bunu görmüyor.
- Pending ringing buffer (Faz 10) AMI gecikmesini kapadı ama "havuz büyükken cevap kümelenmesi" sorununu çözmedi; rc78 % oranı saatler içinde 0.5 → 1.2 aralığında salınmaya devam ediyor.

Çözüm yönü olarak değerlendirilen seçenekler ve kullanıcı kararı:

1. **AR floor (min AR sabitleme)** — Reddedildi. Sebep: müşteri segmentleri farklı; tek floor değeri SUPERONLINE'a uyarken kurumsal kampanyaları over-throttle ediyor.
2. **Wilson interval / EWMA / Pool cap / Capacity ratio** — Reddedildi. Sebep: karmaşıklık artıyor, mevcut formülün yorumu kayboluyor; UCB tek başına problemin %80'ini çözüyor.
3. **Binomial UCB (Upper Confidence Bound) sadece ringingLoad'a** — **Onaylandı**.

Hardcoded `k = 1.645` (tek-kuyruk %95 normal aproksimasyonu) ile başla; per-campaign tunable hale getirme Faz 2.2'ye bırakıldı. Budget kanadı (rawXn) gözlem tabanlı (`availableAgents` Asterisk anlık değer) olduğu için UCB uygulanmadı; sadece tahmin (`ringingLoad`) UCB'ye taşındı.

## İş Etiketi

fix

## Öncelik

normal

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/predictive/gamma/calculator/PredictiveGammaCallCalculator.java`
  - Yeni `DEFAULT_RINGING_LOAD_CONFIDENCE_K = 1.645` sabiti.
  - `CalculationContext` içine `ringingLoadMean`, `ringingLoadSafe`, `ringingLoadConfidenceK` alanları.
  - `computeXn` artık tip başına mean ve UCB ayrı hesaplar; aktif formülde `ringingLoad = ringingLoadSafe` kullanılır.
  - Yeni private helper: `ucbRingingLoad(double ar, int ringingCount, double k)`.
  - `buildCallData` yeni telemetri alanlarını set eder.
- `gbpowerdialer/src/main/java/com/powerdialer/engine/predictive/gamma/model/PredictiveGammaCallData.java`
  - Yeni `ringingLoadMean`, `ringingLoadSafe`, `ringingLoadConfidenceK` (Double) alanları.
- `gbpowerdialer/src/main/java/com/powerdialer/redis/event/predictive/PredictiveGammaEvent.java`
  - Aynı üç alan event payload'una eklendi ve constructor'da data'dan kopyalandı (bs-gamma-analyzer'ın okuduğu kanal).
- `gbpowerdialer/src/test/java/tests/predictive/gamma/PredictiveGammaCallCalculatorRingingLoadUcbTest.java` (YENİ)
  - 7 senaryo: boş havuz, retry-only, mixed types, düşük AR (SUPERONLINE benzeri), yüksek AR (callback), AR=1 / AR=0 kenar durumları, xn ile entegrasyon.

## Mevcut Davranış

`computeXn` içinde tek satır mean hesaplaması:

```java
double ringingLoad = (cbAR * cb) + (retryAR * retry) + (freshAR * fresh);
double xn = max(0, rawXn - ringingLoad);
```

Bu, beklenen cevap sayısı doğru ama varyansı yok sayar. Büyük havuz + düşük AR'da σ ≈ √(Np(1−p)) mean'in %50'sini aşabilir; eş zamanlı cevaplar rawXn'in kapsamadığı ek yükü Asterisk'e bırakır → rc78/abandon.

## Beklenen Davranış

- Her tipin (cb/retry/fresh) ringingLoad katkısı `N × AR + k × √(N × AR × (1−AR))` formülü ile hesaplanır.
- `k` şimdilik sabit 1.645 (Faz 2.2'de Campaign tunable yapılacak).
- Aktif formül `ringingLoad = ringingLoadSafe` kullanır; `ringingLoadMean` salt telemetri.
- AR=0, AR=1 veya ringingCount≤0 kenar durumlarında UCB ek yük üretmez (variance=0 → mean'e düşer; havuz boşsa 0).
- Telemetri (PredictiveGammaCallData → PredictiveGammaEvent → Redis → analyzer DB) `ringingLoadMean` ve `ringingLoadSafe`'i ayrı kolonlarda taşır; canlıda fark ölçülebilir.

## Kod Okuma Bulguları

- `ringingLoad` formülü tek noktada (`computeXn`) yaşıyor; eş çağrı eden ikinci bir kanal yok.
- `RingingMetricsDTO` tip kırılımı (`getCbRinging`, `getRetryRinging`, `getFreshRinging`) zaten ayrı; UCB tip başına uygulanabiliyor.
- `availableAgents` (rawXn'in dominant girdisi) Asterisk anlık sayım → gözlem; UCB tahminlere uygulanır, gözlemlere değil. Bütçe kanadına UCB uygulanmadı.
- Pending ringing telemetrisi (Faz 10) zaten event'te taşınıyor; UCB onunla ortogonal.
- Test sınıfı `PredictiveGammaCallCalculatorTest` mevcut; UCB için bağımsız bir test sınıfı eklemek daha tekil kapsam veriyor.

## Root Cause / Değişiklik Gerekçesi

Mean tahmini, binomial dağılımın varyansını göz ardı ediyor; düşük AR + büyük havuz birleşiminde mean küçük kalsa bile σ kümelenmeyle tek tick'te 2–3× mean kadar cevap üretebiliyor. Bu varyans payı Asterisk'e rc78/abandon olarak yansıyor. UCB, ringingLoad'a tek mekanizmayla istatistiksel güvenlik katmanı ekler; düşük AR'da etkisi büyük, yüksek AR'da varyans küçük olduğu için etkisi otomatik azalır (self-scaling).

## Çözüm Yaklaşımı

### Tasarım

`computeXn` içinde tek `cb*ringing + retry*ringing + fresh*ringing` satırı yerine:

```java
double cbMean    = cbAR * cb;
double retryMean = retryAR * retry;
double freshMean = freshAR * fresh;
ctx.ringingLoadMean = cbMean + retryMean + freshMean;

ctx.ringingLoadConfidenceK = DEFAULT_RINGING_LOAD_CONFIDENCE_K;
double cbUcb    = ucbRingingLoad(cbAR, cb, k);
double retryUcb = ucbRingingLoad(retryAR, retry, k);
double freshUcb = ucbRingingLoad(freshAR, fresh, k);
ctx.ringingLoadSafe = cbUcb + retryUcb + freshUcb;

ctx.ringingLoad = ctx.ringingLoadSafe;
```

`ucbRingingLoad` private helper:

```java
private double ucbRingingLoad(double answerRate, int ringingCount, double k) {
    if (ringingCount <= 0) return 0.0;
    if (answerRate <= 0.0) return 0.0;
    double ar = Math.min(1.0, answerRate);
    double n = ringingCount;
    double mean = n * ar;
    double variance = n * ar * (1.0 - ar);
    if (variance <= 0.0) return mean;
    return mean + k * Math.sqrt(variance);
}
```

### Telemetri

- `PredictiveGammaCallData`'ya 3 alan (Double): `ringingLoadMean`, `ringingLoadSafe`, `ringingLoadConfidenceK`.
- `PredictiveGammaEvent` constructor'ı bu alanları data'dan kopyalar.
- Redis event üzerinden `bs-gamma-analyzer`'a akar; analyzer schema güncellemesi (ALTER TABLE) takip işidir.

### Kapsam dışı (deliberately)

- Per-campaign `gammaRingingLoadConfidenceK` kolonu (Campaign tablosu) → Faz 2.2.
- Pool cap / capacity ratio / EWMA / Wilson → kullanıcı kararı ile elendi.
- Budget kanadına UCB → uygulanmadı (gözlem temelli).

## Riskler

- **Over-throttle**: Yüksek AR + küçük havuz kombinasyonunda σ küçük; etki ihmal edilebilir (mean ≈ safe). Düşük AR + büyük havuzda etki belirgin (istenen davranış). Pilotta `ringingLoadMean` vs `ringingLoadSafe` farkı izlenmeli; safe sürekli mean'in 3×'ünden büyükse k aşırı agresif demektir.
- **k = 1.645 evrensel mi**: Tüm kampanyalar için aynı k. SUPERONLINE-benzeri kampanyalarda yeterli, kurumsal kampanyalarda fazla muhafazakar olabilir. Faz 2.2'de per-campaign tunable hale gelecek. Şimdilik kullanıcı kararı: tek sabit değer.
- **rc78 oranı düşmezse**: UCB sadece bir mekanizma; AR floor + EWMA + capacity ratio gibi tamamlayıcı yaklaşımlar Faz 12+ için açık. Pilot sonrası karar verilecek.
- **AGENTS.md kuralları**:
  - "Yeni kütüphane yok" — yalnız `Math.sqrt`, `Math.min` ✅
  - "Yeni static mutable state yok" — `DEFAULT_RINGING_LOAD_CONFIDENCE_K` immutable static ✅
  - "Hot path performans etkisi minimal" — tick başına 3 `Math.sqrt` ekstra ✅

## TDD Stratejisi

Yeni test sınıfı: `tests/predictive/gamma/PredictiveGammaCallCalculatorRingingLoadUcbTest`. 7 senaryo:

1. `ringingLoad_emptyPool_ucbContributesZero` — havuz boşsa hem mean hem safe 0.
2. `ringingLoad_retryOnly_ucbAddsVarianceMargin` — sadece retry havuzu varken safe > mean ve fark = k×σ.
3. `ringingLoad_mixedTypes_sumsPerTypeUcb` — cb + retry + fresh için tip başına UCB toplanır.
4. `ringingLoad_lowAr_marginalUcb` — SUPERONLINE benzeri AR≈0.007, havuz büyük → safe belirgin şekilde mean'in üstüne çıkar (asıl hedeflenen senaryo).
5. `ringingLoad_highAr_significantUcb` — callback AR=0.80, küçük havuz → safe mean'e yakın (varyans küçük).
6. `ringingLoad_arOneOrZero_noVarianceContribution` — AR=1 veya AR=0 → variance=0 → safe = mean (kenar durum).
7. `xn_safeRingingLoadIsAppliedInSubtraction` — xn formülünde `rawXn − safe` kullanıldığını doğrular.

Regresyon (kapsamak için koşturulan): `PredictiveGammaCallManagerTest`, `PredictiveGammaCallCalculatorTest`, `PredictiveGammaRecentCallsTest`, `SharedGammaSnapshotProviderTest`, `CampaignRealTimeRingingManagerPendingRingingTest`, `AwaitingUserResult*Test`, `SaveInitialCdrHangupResultRegressionTest`, `StopStateTest`.

## Açık Sorular

- [x] `k` değeri: 1.645 (kullanıcı onayı 2026-05-18, hardcoded). Per-campaign tunable Faz 2.2.
- [x] Budget kanadına UCB gerek var mı: Yok; budget kanadı gözleme dayanıyor (kullanıcı onayı 2026-05-18).
- [ ] Pilot kampanya: ilk hangi kampanyada izleyelim? Önerim: SUPERONLINE (UCB'nin en yüksek etki göstereceği profile uyuyor) veya retry oranı yüksek bir kurumsal kampanya.
- [ ] Pilot süresi: kaç saat gözlem yeterli? Önerim: en az 1 işgünü, peak ve off-peak saatleri kapsamalı.
- [ ] `bs-gamma-analyzer` schema güncellemesi (3 kolon): ne zaman? Ayrı PR / takip işi.

## Execution Plan

- [x] Sözleşmeleri (AGENTS.md, change-fix-plan README) ve etkilenen tüm dosyaları tara.
- [x] Mevcut testleri ve `computeXn` davranışını çıkar; ringingLoad katmanının tek nokta olduğunu doğrula.
- [x] Failing TDD test'i yaz (`PredictiveGammaCallCalculatorRingingLoadUcbTest`, 7 case).
- [x] `PredictiveGammaCallCalculator` içine `DEFAULT_RINGING_LOAD_CONFIDENCE_K`, `CalculationContext` alanları, `ucbRingingLoad` helper ve `computeXn` UCB akışı eklendi.
- [x] `buildCallData` yeni telemetri alanlarını set eder.
- [x] `PredictiveGammaCallData` ve `PredictiveGammaEvent` modellerine 3 telemetri alanı eklendi.
- [x] Hedefli testler yeşile çevrildi (7/7 UCB + 64/64 mevcut regression = 71/71).
- [x] Change-fix-plan dosyası açıldı (bu doküman).
- [x] History kaydı `docs/history/18.05.2026.md` eklendi; `docs/history/README.md` indeksi güncellendi.
- [ ] `gbpredictivecalculateviews` mirror (DTO + entity + @Column annotation, ayrı PR / takip işi).
- [ ] `bs-gamma-analyzer` mirror (DB schema + sekme görselleştirme, ayrı PR / takip işi).
- [ ] Pilot kampanyada canlı gözlem (deploy sonrası takip).

## Performans Etkisi

- Hot path durumu: `computeXn` Gamma tick (campaign başına 2 sn'de 1 kez) tarafından çağrılır.
- Call/event/tick başına ek maliyet:
  - tick başına: 3 ek `Math.sqrt` çağrısı + 3 ek çarpma/toplama (O(1)).
  - mean ile UCB ayrı hesaplandığı için lokal değişken sayısı arttı; bytecode etkisi ihmal edilebilir.
- IO/scan/lock/serialization/log etkisi: Yeni IO yok. Yeni log yok. Mevcut log satırı `xn` içeriyor; safe/mean ayrımı sadece telemetri event'inde görünür.
- Azaltım veya ölçüm notu: Tick latency artışı sub-μs düzeyinde. `Math.sqrt` JIT ile intrinsic; ölçülebilir etki beklenmiyor.

## Doğrulama

`.codex/TOOL-PATHS.local.properties` okundu; Maven offline + `properties-tr-dev` profili ile çalıştırıldı.

Hedefli komutlar:

```text
mvn -o -P properties-tr-dev -s C:/Users/GLB90090645/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90090645/.m2/repository -Dtest=PredictiveGammaCallCalculatorRingingLoadUcbTest test
mvn -o -P properties-tr-dev -s C:/Users/GLB90090645/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90090645/.m2/repository -Dtest=PredictiveGammaCallCalculatorRingingLoadUcbTest,PredictiveGammaCallManagerTest,PredictiveGammaCallCalculatorTest,PredictiveGammaRecentCallsTest,SharedGammaSnapshotProviderTest,CampaignRealTimeRingingManagerPendingRingingTest,AwaitingUserResultCacheTransitionRegressionTest,AwaitingUserResultTimeoutPreserveRegressionTest,SaveInitialCdrHangupResultRegressionTest,StopStateTest test
```

Sonuç:
- `PredictiveGammaCallCalculatorRingingLoadUcbTest`: 7/7 başarılı.
- Toplu regresyon: 71/71 başarılı.
- `DialerFacadeTest.UnnecessaryStubbingException` baseline'da da fail veriyor; pre-existing, bu değişiklikle ilgisi yok.

## Kapanış Kriteri

- [x] Açık sorular dökümana yazıldı; pilot ve takip işleri ayrı PR olarak işaretlendi.
- [x] Yeni TDD testleri yeşile çevrildi (7/7).
- [x] Mevcut regresyon testleri yeşil (pre-existing fail hariç, raporlandı).
- [x] Final yanıtta kısa Test Özeti sunuldu (71/71).
- [x] History kaydı eklendi ve indeks güncellendi.
- [ ] `gbpredictivecalculateviews` ve `bs-gamma-analyzer` mirror (takip işleri).
- [ ] Pilot kampanya canlı izleme tamamlandı (post-deploy).

## Tamamlanma Notu

Uygulama tamamlandı (2026-05-18). Üretim kodu değişiklikleri:

- `PredictiveGammaCallCalculator.java`:
  - `DEFAULT_RINGING_LOAD_CONFIDENCE_K = 1.645` sabit static (package-private; testten okunuyor).
  - `CalculationContext` içine `ringingLoadMean`, `ringingLoadSafe`, `ringingLoadConfidenceK` alanları.
  - `computeXn` tip başına mean ve UCB hesaplar; aktif formülde `ringingLoad = ringingLoadSafe` kullanılır.
  - `ucbRingingLoad(double answerRate, int ringingCount, double k)` private helper; kenar durumları (n≤0, ar≤0, variance=0) güvenli işler.
  - `buildCallData` yeni üç alanı set eder.
- `PredictiveGammaCallData.java`:
  - Yeni alanlar (Lombok @Data): `Double ringingLoadMean`, `Double ringingLoadSafe`, `Double ringingLoadConfidenceK`.
- `PredictiveGammaEvent.java`:
  - Aynı üç alan event payload'una eklendi ve constructor'da data'dan kopyalandı.

Test değişiklikleri:

- Yeni: `tests/predictive/gamma/PredictiveGammaCallCalculatorRingingLoadUcbTest` (7 senaryo).

Doğrulama:

- `PredictiveGammaCallCalculatorRingingLoadUcbTest`: 7/7 ✅
- Toplam targeted regression (Gamma + AMI + Manager + StopState): 71/71 ✅
- `DialerFacadeTest.UnnecessaryStubbingException` baseline'da da fail veriyor; pre-existing, ilgisiz.

Plan kullanıcı onayı (2026-05-18 "başla geliştirmeye, dikkatli ol hem testleri hem dökümanları güncelle dikkatlice") ile feature branch `feature/GammaImprovements-part11-AddUCBForRingLoad` üzerinde tamamlandı. `gbpredictivecalculateviews` DTO/entity mirror'ı ve `bs-gamma-analyzer` schema + UI mirror'ı ayrı takip işi olarak işaretlendi.
