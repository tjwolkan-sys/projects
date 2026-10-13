# FEAT-321997 — Agent Callback Reset Stratejisi · Test Case Seti

## Bağlam

Callback to Appointer için iki sıfırlama stratejisi: **Reset by Timer** (legacy) ve **Reset by Attempt Count** (yeni). ATTEMPT_COUNT'ta sahip agent uygun değilse callback ötelenir + deneme sayılır; eşikte campaign-wide'a (havuza) devredilir. İlgili analiz/plan: `docs/analyse/25.06.2026/FEAT-321997-...md` ve `docs/execution-plan/25.06.2026/FEAT-321997-...md`.

Her case'in yanında otomatik test kapsamı işaretlidir: `[Otomatik: <test sınıfı>]` JUnit ile yeşil; `[Otomatik (Karma)]` AngularJS spec (ortam blocker'ı nedeniyle henüz koşulmadı); `[Manuel]` QA tarafından el ile doğrulanmalı.

## Ortak test verisi / önkoşullar

- **K-ATTEMPT**: PROGRESSIVE/PREDICTIVE kampanya; `allowCallback=true`, `callbackToAppointer=true`, `appointerResetStrategy=ATTEMPT_COUNT`, `agentCbRescheduleDelayMin=20`, `agentCbConvertAfterAttempts=3`; result tablosunda `callback_attempts` (INT) kolonu var ve `colResultCallbackAttempts`'e eşli; günlük schedule 09:00–18:00 + timezone tanımlı.
- **K-TIMER**: aynı kampanya, `appointerResetStrategy=TIMER`, `appointerReset=true`, `appointerResetTimeout=3600`.
- **K-WEBPHONE**: `allowOverwriteByWebphone=true`, callback'ler `ASSIGN_TO_SAME_USER`.
- Agent statüleri: **Available**, **Pause/Mola**, **ACW/wrapup**, **In-Call/meşgul**, **Logout/offline**.
- "Genel Max Attempts" = telefon retry üst limiti (`attempts`/`retryOveralAttempt`); agent-callback deneme sayacından (`callback_attempts`) ayrıdır.

---

## A. Konfigürasyon / UI (Campaign Form → General)

### TC-A1 — Reset-strategy gate · [Otomatik (Karma): campaignFormResetStrategy.spec.js]
- Given: `callbackToAppointer` ve `allowOverwriteByWebphone` ikisi de kapalı.
- Then: Reset Strategy radio'ları ve reschedule/convert/timeout alanları **disabled**.
- When: biri açılır → Then: alanlar **aktifleşir**.

### TC-A2 — Karşılıklı freeze: Timer · [Otomatik (Karma)]
- Given: gate aktif, **Reset by Timer** seçili.
- Then: `Appointer Reset Timeout (s)` **enabled**; `Reschedule Delay` + `Convert After Attempts` **freeze**.

### TC-A3 — Karşılıklı freeze: Attempt · [Otomatik (Karma)]
- Given: **Reset by Attempt Count** seçili.
- Then: `Reschedule Delay` + `Convert After Attempts` **enabled**; `Appointer Reset Timeout` **freeze**.

### TC-A4 — callback_attempts kolonu zorunlu (hard block) · [Manuel]
- Given: ATTEMPT seçili; `colResultCallbackAttempts` boş.
- Then: reset-strategy bloğunda **kırmızı uyarı**.
- When: Kaydet → Then: form `$invalid`, **kaydetme engellenir**, hatalı alana odaklanır.
- When: kolon eşlenir → Then: uyarı kaybolur, kayıt başarılı.

### TC-A5 — Schedule bitiş saati uyarısı (soft) · [Manuel]
- Given: ATTEMPT seçili; kampanyada hiç schedule satırı yok.
- Then: **sarı uyarı** ("günlük bitiş saati tanımlayın"), ama **kaydetmeyi engellemez**.
- When: schedule eklenir → Then: uyarı kaybolur.

### TC-A6 — Default değerler · [Manuel]
- Given: yeni kampanya. Then: `appointerResetStrategy=Timer(0)`, `agentCbRescheduleDelayMin=20`, `agentCbConvertAfterAttempts=3`.

### TC-A7 — Localization · [Manuel]
- TR ve EN dilinde tüm yeni etiket/uyarılar anlamlı görünür (ham key görünmez).

### TC-A8 — Kaydet/Yükle round-trip · [Otomatik: CampaignsResourceTest]
- ATTEMPT + 20/3 + kolon kaydet → yeniden aç → tüm değerler korunmuş.

### TC-A9 — IVR / Permission · [Manuel]
- `type=OUTBOUND_IVR` veya `EditCampaign` yetkisi yoksa reset-strategy alanları disabled.

---

## B. ATTEMPT_COUNT — Çalışma zamanı (çekirdek)

### TC-B1 — Owner AVAILABLE → dial · [Otomatik: CallbackAssignmentRequiresAvailableAgentTest]
- Given: callback due, owner Available + idle. Then: callback owner'a originate edilir; `callback_attempts` artmaz.

### TC-B2 — Owner online-meşgul → +1 attempt · [Otomatik: CallOriginatorAppointedCallbackResetTest, AppointedCallbackRescheduleSqlRegressionTest]
- Given: owner In-Call (online ama available değil), `callback_attempts=0`.
- Then: dial yapılmaz; `callback_attempts=1`; `callback_date` +20 dk; **genel Max Attempts değişmez (0)**.

### TC-B3 — Owner molada (Pause/ACW) → +1 attempt · [Otomatik: CallbackAssignmentRequiresAvailableAgentTest]
- Given: owner Pause/ACW. Then: dial yapılmaz; `callback_attempts +1`; reschedule +delay.

### TC-B4 — Owner offline (logout) → fetch + +1 attempt · [Otomatik: CallProducerTest.attemptModeFetchesCallbacksWithoutOwnerGate]
- Given: owner logout; callback due.
- Then: callback **fetch edilir** (owner online filtresi uygulanmaz); dial yapılmaz; `callback_attempts +1`; reschedule.

### TC-B5 — Eşikte havuza devir · [Otomatik: AppointedCallbackRescheduleSqlRegressionTest]
- Given: `callback_attempts=2`, eşik=3, owner uygun değil.
- When: yeni deneme (`2+1>=3`). Then: `user_id=NULL` (campaign-wide), `callback_date=now`, `callback_attempts=0`; callback CALLBACK tipinde kalır.

### TC-B6 — Havuzdan dial → Max Attempts devreye girer · [Manuel]
- Given: callback campaign-wide. When: havuzdaki ilk available agent'a originate. Then: gerçek arama yapılır ve **genel Max Attempts** artar.

### TC-B7 — Attempt sayacı kalıcılığı · [Otomatik: CallCallbackAttemptsHydrationTest]
- Given: `callback_attempts=1`, ardından JVM restart / kampanya stop-start / agent logout.
- Then: sayaç 1'den devam eder (result-table kolonu), sıfırlanmaz.

### TC-B8 — Webphone ASSIGN_TO_SAME_USER · [Otomatik: CallbackAssignmentRequiresAvailableAgentTest]
- Given: K-WEBPHONE, owner mola. Then: dial yapılmaz; uniform attempt mekanizması appointer ile aynı işler.

### TC-B9 — Owner ara dönüş · [Manuel]
- Given: `callback_attempts=1`, reschedule kurulu; owner due anında Available.
- Then: callback owner'a dial edilir (kaldığı sayaçtan), havuza gitmez.

---

## C. Snap-to-window (arama penceresi)

### TC-C1 — Pencere içi reschedule · [Otomatik: CallRescheduledCallbackDateSnapTest]
- now+delay pencere içindeyse → `callback_date = now+delay`.

### TC-C2 — Pencere dışı reschedule → snap · [Otomatik: CallRescheduledCallbackDateSnapTest]
- Given: pencere 09:00–18:00; 17:55'te +20dk=18:15 (dışarıda).
- Then: `callback_date` sonraki pencere açılışına (ertesi gün 09:00) snap edilir; pencere sınırında churn olmaz.

### TC-C3 — Timezone OFF · [Otomatik: CallRescheduledCallbackDateSnapTest]
- Timezone OFF → snap yok, `callback_date = now+delay`.

### TC-C4 — Overnight pencere (örn. 22:00–02:00) · [Manuel]
- Gece-aşırı pencere için snap doğru hesaplanır (off-by-one yok).

---

## D. Senaryo + kenar durumlar

### TC-D1 — Kullanıcı senaryosu: owner hiç dönmez · [Manuel/entegrasyon]
- Callback 18:45'e kurulu; owner 18:45 meşgul → 19:05; owner 19:00 logout ve hiç dönmez.
- Then: 19:05 (attempt 2), 19:25 (attempt 3 → **havuza**). ~40 dk içinde campaign-wide; ertesi günü beklemez.

### TC-D2 — Hiç online agent yok · [Manuel]
- Tüm kampanya offline; callback'ler due → fetch + attempt + eşikte havuza (`user_id=NULL`); bir agent online/available olana kadar havuzda bekler, sonra dial.

### TC-D3 — After-hours flood (asıl problem) · [Manuel/entegrasyon]
- 18:00 sonrası çok sayıda callback aynı anda due; owner'ların çoğu offline/mola.
- Then: her callback attempt+reschedule ile bireysel işlenir; eşikte havuza düşenler available (appointer olmayan) agent'lara dağıtılır → **avail agent boşta kalmaz, ringing düşmez** (starvation çözülür).

### TC-D4 — Multi-node lost-update · [Otomatik (guard): AppointedCallbackRescheduleSqlRegressionTest]
- Aynı callback iki node'da işlenirse `AND callback_attempts=expectedOld` guard'ı ile çift sayım olmaz (biri no-op).

### TC-D5 — Convert sonrası cevapsız · [Manuel]
- Havuza düşen callback gerçek dial'de cevapsız kalırsa → normal retry/result akışına girer (callback mekanizması bitmiştir).

---

## E. TIMER (legacy) regresyon — DEĞİŞMEMELİ

### TC-E1 — TIMER + owner available → dial · [Manuel]

### TC-E2 — TIMER + owner unavailable → return-to-DB · [Otomatik: CallOriginatorAppointedCallbackResetTest.timerStrategyUsesLegacyReturnToDbWithoutReschedule]
- Eski davranış; reschedule/convert ÇAĞRILMAZ.

### TC-E3 — appointerReset ON + timeout → time-escape · [Manuel]
- Timeout dolunca callback herhangi agent'a açılır (eski); fetch SQL'i `callback_date <= now-resetTimeout` kolu üretir.

### TC-E4 — TIMER fetch owner gate · [Otomatik: CallProducerTest.timerModeAppliesOwnerGate]
- TIMER'da owner aktif-kullanıcı filtresi uygulanır (offline owner callback'i çekilmez — legacy korunur).

---

## F. Sağlıklı-akış regresyon — KIRILMAMALI

### TC-F1 — Fresh data dial · [Manuel] — fresh çağrı seçimi/dial'i değişmemiş.
### TC-F2 — Retry dial · [Manuel] — retry akışı + Max Attempts semantiği değişmemiş.
### TC-F3 — Generic/grup callback · [Manuel] — `user_id NULL` veya `ASSIGN_TO_USER_GROUP` callback herhangi available agent'a; attempt mekanizması devreye girmez.
### TC-F4 — callbackToAppointer=false kampanya · [Manuel] — reset-strategy hiçbir şeyi etkilemez; callback'ler normal akışta.
### TC-F5 — CampaignInMapper / Redis · [Otomatik: CampaignInMapperTest] — yeni alanlar event'e eklenir, `appointerReset`/`appointerResetTimeout` korunur.

---

## G. Migration / deploy

### TC-G1 — Config DB ALTER uygulanmadan · [Manuel]
- Yeni kolonlar nullable; mevcut kampanyalar `appointerResetStrategy=NULL` → TIMER (legacy); sorun çıkmaz.

### TC-G2 — Yanlış tip callback_attempts kolonu · [Manuel]
- `colResultCallbackAttempts` NVARCHAR kolona eşlenip start → `campaign.start.wrong.types` ile reddedilir (CampaignDataTypeChecker).

### TC-G3 — ATTEMPT + result-table kolonu yok (API bypass) · [Manuel]
- `colResultCallbackAttempts` boş + API ile kaydedilirse → degraded: yalnız reschedule (convert olmaz). UI bunu engeller (TC-A4).

### TC-G4 — Mevcut kampanya korunması · [Manuel]
- `appointerReset=FALSE` eski kampanyalar dokunulmadan TIMER (legacy) davranışında.

---

## Otomatik kapsam özeti

- **JUnit (offline Maven, 67/67 yeşil):** B (runtime), C (snap), Req-1 fetch (CallProducerTest), multi-node guard, availability (CallbackAssignmentRequiresAvailableAgentTest), Redis/DTO contract, TIMER legacy yönlendirme.
- **Karma (henüz koşulmadı — `node_modules` blocker):** A (UI freeze/validation) — `campaignFormResetStrategy.spec.js`; geliştirici `npm ci` sonrası koşmalı.
- **Manuel/QA odak:** A (UI), D (entegrasyon senaryoları, after-hours flood), E3/F (legacy + healthy-path el ile), G (migration/deploy).
