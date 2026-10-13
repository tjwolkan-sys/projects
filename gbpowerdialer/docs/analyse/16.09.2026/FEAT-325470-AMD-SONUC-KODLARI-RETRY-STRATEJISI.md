# FEAT AMD Türevli Sonuç Kodları (86/87/88) ve Retry Stratejisi Entegrasyonu

## Bağlam

Task `325470` (User Story, Sprint18-2026, parent `288124` "2026 Geliştirmeler"). Santral tarafında AMD (Answer Machine Detection) artık tek bir "makine" sonucu yerine ayrıştırılmış sonuç üretecek: meşgul tonu, cevapsız, kullanılmayan numara anonsu, ulaşılamıyor anonsu ("Plane") ve bilinmeyen ton. Dialer bugün bütün AMD sonuçlarını tek TRC (`pd_telephony_rc` id 71 / code 900) olarak alıyor ve aynı numaraya normal retry kuruyor.

İş talebi (kart açıklaması):

- **AMD busy** → normal busy (TRC id 11) ile aynı retry davranışı; Asterisk bize 11'i gönderecek.
- **AMD no answer** → no answer (TRC id 12) ile aynı; Asterisk bize 12'yi gönderecek.
- **Bad number** → yeni TRC **86** ("Kullanılmayan Numara"). Aynı numaraya retry **kurulmaz**; müşteri datasında başka numara varsa sıralamada sonraki numaraya retry kurulur, yoksa `complete_date` atılıp kayıt kapatılır.
- **Not Reached ("Plane")** → yeni TRC **87**, **Unknown Tone** → yeni TRC **88**: normal retry sürecine girer. Strateji `RETRY_RULES_MANAGES_CYCLE` ise Result Code Schedule List'teki satır (eklendiyse) kullanılır; diğer stratejilerde retry delay ile normal süreç devam eder.
- Yeni kodlar **Telephony Result Code List** ekranında ve **Result Code Schedule Form** → Telephony Result Code dropdown'unda görünmelidir.

Kart: `http://azuredevops/tfs/GLOBALCollection/MC.BulutSantral/_workitems/edit/325470/`

## İş Etiketi

`feat`

Branch: `feature/325470-amd-sonuc-kodlari-retry-stratejisi`. Önce develop `e56b3877` üzerinden açıldı; S6 kararıyla aynı gün `feature/325559-numara-basina-azami-arama-limiti` (`633de602`) tabanına taşındı.

## Etkilenen Alanlar

### Backend — engine (yüksek risk, hangup/result hot path)

- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/retry/RetryStrategy.java` — `createRetry` template'ine "bad number → numara geçersiz" kuralı (6 stratejinin tek chokepoint'i).
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java:117,1143,1163,1289` — `AMD_HANGUP_CAUSE_CODE = 71` **id** tabanlı üç kontrolün `code` tabanlı "AMD ailesi" kümesine genelleştirilmesi.
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionComposition.java` — `validateInternalTelephonyResultCodeMappings` zorunlu kod listesine yeni kodlar.
- `gbpowerdialer/src/main/java/com/powerdialer/util/Constants.java` — `TELEPHONY_RC_AMD`, `TELEPHONY_RC_AMD_BAD_NUMBER`, `TELEPHONY_RC_AMD_NOT_REACHED`, `TELEPHONY_RC_AMD_UNKNOWN_TONE` (code değerleri) ve `AMD_FAMILY_TELEPHONY_RC_CODES` kümesi.

### Backend — config ve persistence (S5 kararı: Seçenek B seçildi, bu bölüm UYGULANMAZ; kayıt için bırakıldı)

- `Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UNREACHABLE_NUMBERS` + `RESULT_FIELDS_ALLOWED_TYPES`, `ALLOWED_TECH_FIELDS`.
- `model/Campaign.java` (`colResultUnreachableNumbers`), `model/Call.java` (rawData'dan lazy hydration; `getDNCNumbers` deseni), `dto/CampaignFormDTO.java`, `util/CampaignPropertyCopier.java`, `service/CampaignDataTypeChecker.java`, `redis/event/interactionnotifier/dto/CampaignInDTO.java` + `mapper/CampaignInMapper.java`.
- `engine/db/CampaignDbOperations.java` — `createDefaultResultTable`, `addDefaultResultColumnsToSourceTable`, `updateCampaignListTableRow`, `insertIntoCampaignResultTable` (`dnc_numbers` yazımıyla aynı desen).

### Veri / migration

- `gbpowerdialer/src/main/resources/sql/migration/FEAT-325470-amd-telephony-result-codes.sql` (yeni): `pd_telephony_rc`'ye 3 satır, **elle** çalıştırılır (repoda Flyway/Liquibase yok, FEAT-321997 emsali). Uygulama restart'ı gerektirir (harita startup'ta yüklenir).
- Seçenek A'da: config DB `campaign` tablosuna binding kolonu + müşteri result tablolarına `unreachable_numbers` (per-customer onboarding; 325559 `close_reason` emsali).

### Frontend (AngularJS)

- **Kod değişikliği gerekmez** (bkz. F2): iki ekran da `/telephonyRc/list` üzerinden servlet-context haritasından beslenir.
- Seçenek A'da: `pages/campaignForm.html` result kolon binding select'i, `js/controllers/campaignFormCtrl.js` (`initializedResultColumns`, default atama), `js/constants.js`, 4 localization dosyası.

### Test

- `gbpowerdialer/src/test/java/tests/dialer/` — retry template karakterizasyon + feature testleri.
- `gbpowerdialer/src/test/java/tests/ami/` — AMD ailesi hangup karakterizasyon/regression.
- `gbpowerdialer/src/test/java/tests/angular/` — migration SQL ↔ `Constants` code contract testi.
- Seçenek A'da `src/test/angular/forms` Karma spec (binding alanı).

### Dokümantasyon

- `docs/architecture/DIALER-RUNTIME.md` (retry kuralı), `docs/architecture/ASTERISK-AMI-AGI.md` (`DialerHangup` cause contract'ı: `code`, id değil; AMD ailesi).

## Mevcut Davranış

### TRC çözümü ve harita anahtarı

- `DialerHangupEvent.hangupCause` → `ituToTelephonyResultCodeRelations.get(cause)` (`ManagerConnectionDecorator.resolveHangupTelephonyResultCode`). Harita anahtarı **`pd_telephony_rc.code`** kolonudur (`ManagerConnectionComposition.createItuToTelephonyResultCodeRelations`), **id değil**. Eşleşme yoksa WARN loglanır ve code 901 ("Call expiration timestamp has passed", id 73) fallback'i yazılır.
- Harita startup'ta bir kez yüklenir ve `Constants.TELEPHONY_RESULT_CODES` servlet-context attribute'una konur. `TelephonyResultCodeController.getAll` (list ekranı) ve `campaignFormCtrl.loadTelephonyRcs` → `resultCodeScheduleForm.html` `ng-options` (dropdown) aynı haritayı okur; sıralama `_.sortBy(name)`.
- `pd_telephony_rc` iki üretim DB'sinde de: `id` **IDENTITY** (sequence yok; entity'deki `@SequenceGenerator PD_TELEPHONY_RC_FCSEQ` fiilen etkisiz), 84 ITU satırı (code 0..127) + dahili 9xx satırları (900 AMD id 71, 901..911, 912 Ambiguous). **Code 86/87/88 ITU nedenleri olarak DOLU** (id 53 "Call having the requested call identity has been cleared", 54 "User not member of CUG", 55 "Incompatible destination").
- Bugün AMD tek koddur: cause 900 → id 71 "AMD (Answer machine detection)" / "Answering Machine". (Ayrıca id 83 / code 71 adlı ikinci bir "AMD" satırı var; dropdown'da iki kez "AMD" görünmesinin nedeni budur, bu iş kapsamı dışıdır.)

### Retry kararı

- `ManagerConnectionDecorator.manageRetries` (`:1454`): `retryScheduleEnabled && retryStrategy != null` ve URC final değilse `RetryStrategyFactory.createStrategy(...).createRetry(call)`; aksi hâlde `completeCall`.
- `RetryStrategy.createRetry` template'i: `(isCallAnswered() && !isResultCodeAllowsRetries()) || isOverallRetryLimitReached()` → complete; değilse `setRetryNumber()` + `setRetryDateAndIncrementRetryCounts()` + `updateRetryHistory()`. `isCallAnswered()` yalnız **code 16** (`TELEPHONY_RC_NORMAL_CLEARING`) için true'dur; **diğer her TRC (busy, no answer, AMD, unallocated dâhil) aynı numaraya retry'a girer.**
- `RetryRulesCycle` (manages cycle): `findResultCodeScheduleHavingTelephonyResultCode` enabled + `RETRIES`/`RETRIES_AND_CALLBACKS` kapsamlı satır arar (entity `equals` = id); bulursa o satırın `retryInterval`/advanced kuralı, bulamazsa `campaign.retryDelay`. Diğer 5 strateji `retryDelay` (+ macro interval).
- Numara halkası: `Campaign.getEnabledPhoneNumberColumns()` `colSort` sıralı; `Call.getNextNumberFromRawData()` halkada bir sonraki dolu numarayı verir; `advanceRetryNumberPastIneligible` DNC numaralarını atlar, aday kalmazsa `NoEligibleRetryNumberException` → complete. `ReachRetrySingleNumber` (varsayılan strateji, ekran görüntüsündeki "Reach retry limit for single number") **hiç numara değiştirmez** (`setRetryNumber = calledNumber`).
- "Kullanılmayan numara" bugün ITU cause 1 (Unallocated, id 1, "Dial Error") olarak gelir ve aynı numaraya normal retry alır.

### AMD'ye özel kontroller (id tabanlı)

`AMD_HANGUP_CAUSE_CODE = 71` (id) üç yerde: `shouldProcessCallResultOnHangup` (awaiting-URC'ye düşmeden finalize), `applyCustomerHangupBeforeAgentConnectionOverride` (TRC 84 override muafiyeti), `processCallResult` predictive NO_CONNECTION sınıflaması. Üçü de yalnız id 71'i tanır.

**Veri doğrulaması (Turkcell pd_cdr, son 3 gün, 16.09.2026):**

| telephony_rc | toplam | call_answer_time NULL |
|---|---|---|
| 71 (AMD) | 69.172 | 69.164 |
| 11 (User busy) | 57.486 | 57.486 |
| 13 (No answer from user) | 141.398 | 141.398 |
| 12 (No user responding) | 3 | 3 |
| 1 (Unallocated) | 10.520 | 10.520 |
| 84 (Customer hangup before agent) | 3.210 | 0 |

Dialplan AMD sonuçlarında `ClientPredictiveAnswer` göndermiyor (`answerDate` null kalıyor); AMD kontrolleri fiilen preview kampanyalar ve nadir kenar durumlar için çalışıyor. Bugün no-answer trafiği neredeyse tamamen **id 13 (code 19)**, id 12 (code 18) yok denecek kadar az.

## Beklenen Davranış

### Ekranlar

- Telephony Result Code List ve Result Code Schedule Form dropdown'unda 86/87/88 satırları görünür. Kod değişikliği yok; `pd_telephony_rc` satırları + uygulama restart'ı yeterlidir.

### Runtime (santral yeni cause'ları göndermeye başladığında)

| Santral sonucu | Beklenen TRC | Retry davranışı |
|---|---|---|
| AMD busy | id 11 (code 17) | Bugünkü busy ile aynı: manages-cycle'da "User busy" schedule satırı varsa o, yoksa retry delay. Kod değişikliği yok. |
| AMD no answer | id 13 (code 19); müşteri teyidi: 11 ve 13 bugün de geliyor (id 12 / code 18 gelirse aynı yoldan geçer) | Bugünkü no-answer ile aynı. Kod değişikliği yok. |
| Bad number | **id 86, code 913 (öneri)** | Aynı numaraya retry yok. Aranan numara kayıt için geçersiz sayılır; `colSort` sırasında sonraki uygun (DNC dışı, geçersiz olmayan, dolu) numara varsa ona retry; yoksa `complete_date` yazılıp kayıt kapanır. Retry zamanı stratejinin normal hesabıyla (S4). Tüm stratejilerde geçerli (S3). |
| Not Reached | **id 87, code 914 (öneri)** | Normal retry: manages-cycle'da 87 için schedule satırı varsa o, yoksa retry delay; diğer stratejilerde retry delay. Kod değişikliği yok. |
| Unknown Tone | **id 88, code 915 (öneri)** | 87 ile aynı. Kod değişikliği yok. |

- 900/913/914/915 "AMD ailesi" olarak tek kümede tanınır: preview dâhil awaiting-URC'ye düşmez, TRC 84 override'ı yemez, predictive'de NO_CONNECTION sayılır. Bu, bugünkü id 71 davranışının `code` tabanlı ve yeni kodları kapsayan hâlidir; 900 için davranış birebir korunur.
- Geçersiz numara için kalıcılık kolonu **eklenmez** (S5 = Seçenek B). Kural yalnız o karardaki aranan numarayı dışlar; iki numarası da 86 alan kayıtta halka ilk numaraya dönebilir ve döngü genel attempt limiti (ve 325559 numara başına limiti) ile sınırlıdır. Bilinçli kabul edilen trade-off.
- 86 kaynaklı kapanışta 325559'un `close_reason` binding'i varsa `all numbers unreachable` yazılır (S7); binding yoksa yazılmaz.
- 86 attempt sayımında bugünkü cause 1 gibi bir deneme sayılır (`TELEPHONY_RESULT_CODES_EXCLUDED_FROM_ATTEMPTS` yalnız 907'yi muaf tutar); değiştirilmez.
- `RING_VALID_RESULT_CODES` / `_V2` kümelerine yeni id'ler **eklenmez** (cause 1 ile aynı sınıf); predictive AR_ring etkisi ayrı ölçüm konusudur.

## Kod Okuma Bulguları

### F1 — `id` ≠ `code`: dış contract yalnız `code` ile çalışır

Kartta "Asterisk'ten 11/12 gönderilecek" ve "86/87/88 yeni sonuç kodu" ifadeleri `pd_telephony_rc.id` (ekrandaki Id kolonu) ile yazılmış. Dialer haritası ise `code` ile anahtarlanır. Santral hangup cause olarak **11** gönderirse tabloda code 11 yoktur → WARN + code 901 fallback (id 73, "Internal Error") → retry çalışır ama rapor yanlış olur ve manages-cycle'daki "User busy" satırı hiç eşleşmez. **86/87/88 gönderilirse** ITU id 53/54/55 satırlarına eşleşir; "Dial Error" olarak aynı numaraya retry'a girer ve bad-number kuralı hiç tetiklenmez. Bu yüzden yeni satırların `code` değeri dahili 9xx serisinden olmalı (912'den sonra 913/914/915) ve santral **code** göndermelidir. Doğrulama sinyali: test ortamı logunda `Hangup cause '...' is not mapped to a telephony result code` WARN'ı görülmemeli.

### F2 — İki ekran da veri güdümlü

`TelephonyResultCodeController.getAll` servlet-context haritasını döndürür; `resultCodeScheduleForm.html` `ng-options="item as item.name for item in resultCodeSchedule.telephonyRcList track by item.id"` ile filtresiz listeler; `campaignFormCtrl.loadTelephonyRcs` yalnız ada göre sıralar. Yeni satırlar restart sonrası otomatik görünür. Frontend'de kod değişikliği yoktur; yalnız migration satırları ile `Constants` code'larının uyumunu kilitleyen bir Java contract testi eklenecektir.

### F3 — 87/88 için engine değişikliği gerekmez; 86 için template kuralı gerekir

Template code 16 dışındaki her TRC'yi retry'a alır ve `RetryRulesCycle` TRC bazlı schedule satırını zaten arar. 87/88 için istenen davranış bugünkü kodun doğal sonucudur; karakterizasyon testiyle kilitlenir. 86 için "numara geçersiz" kuralı stratejiye özgü değil, numara geçerliliği kuralıdır; DNC dışlama gibi template seviyesinde (`RetryStrategy`) ve `advanceRetryNumberPastIneligible` predicate'ine üçüncü kaynak olarak eklenmelidir. `ReachRetrySingleNumber` numara değiştirmediği için orada da template kuralı devreye girecektir (S3).

### F4 — AMD kontrolleri id 71'e kilitli

Üç kontrol `getId() == 71` yapar. Yeni kodlar bu kontrollerden geçmez: preview kampanyada `callIsWaitingForUserResultCode` true iken 913/914/915 awaiting-URC cache'ine düşer (bkz. 12.08.2026 tcell preview vakası: agent ~1 saat kilitli kalıp 901/73 ile kapanıyordu). Kontroller `code` tabanlı `AMD_FAMILY_TELEPHONY_RC_CODES = {900, 913, 914, 915}` kümesine taşınmalı; 900 için davranış karakterizasyon testiyle korunmalıdır.

### F5 — Geçersiz numaranın kalıcılığı için mevcut kolon yok

`retry_history`/`cb_history`/`call_process` numara dizisini tutar ama numara başına **sonuç** tutmaz; `telephony_result_code` result kolonu yalnız son denemenin TRC'sidir. İki numaralı bir kayıtta n1→86→n2→86 dizisinde n1'in geçersiz olduğu bilinemez; halka n1'e döner ve kayıt genel attempt limitine kadar boşa aranır. Kalıcılık için `dnc_numbers` (`Call.getDNCNumbers`, `CampaignDbOperations:2253` yazımı, `CampaignDataTypeChecker:81` tip kontrolü) birebir emsaldir: `;` ayraçlı NVARCHAR, opsiyonel binding, `createDefaultResultTable` ile yeni tablolarda otomatik. Seçenek A budur; Seçenek B kalıcılık olmadan yalnız o anki aranan numarayı dışlar (sınırlı döngü kabul edilir).

### F6 — 325559 ile doğrudan örtüşme

`feature/325559-numara-basina-azami-arama-limiti` (aynı geliştirici, execution plan 97/97 tamam, PR henüz yok, develop ile 16.09'da merge'lenmiş) `RetryStrategy.createRetry`'a `isNumberIneligible(number)` predicate'i, `noEligibleNumberLeft()` guard'ı ve `applyExhaustedCloseReason()` (+ `Call.closeReason`, `col_result_close_reason`, `Constants.CLOSE_REASON_*`, `CampaignDbOperations.setClosedWithoutDialing`) ekliyor. 86 kuralı tam olarak bu predicate'e üçüncü kaynak ("unreachable") ve kapanışa bir neden metni olarak oturur. İki iş develop'a ayrı ayrı girerse `RetryStrategy.java`, `Call.java`, `CampaignDbOperations.java`, `Constants.java`, `campaignForm.html`, `campaignFormCtrl.js`, 4 localization ve doküman indekslerinde conflict kaçınılmazdır. Öneri: 325470'i 325559 üzerine kur (S6).

### F7 — `pd_telephony_rc.id` IDENTITY: 86/87/88 id'leri ancak `IDENTITY_INSERT` ile garanti edilir

Non-Turkcell DB'de 327734'ün "Ambiguous" (code 912) satırı **id 10083** almış (Turkcell'de 85). Identity atlaması nedeniyle düz `INSERT` ile 86/87/88 id'leri garanti edilmez; migration `SET IDENTITY_INSERT pd_telephony_rc ON` + açık id + `code`/`name` ile idempotent (`IF NOT EXISTS`) yazılmalı, her ortamda id/code doğrulanmalıdır. Yeni dialer kodu **yalnız `code`** ile karar vermelidir (id ortamlar arası güvenilir değil).

### F8 — Yan bulgu (kapsam dışı, ayrı iş önerilir)

`CallAttemptPolicy.TRC_AMBIGUOUS_RECORD = 85L` ve `UserPopulationEvaluator.SUCCESS_TELEPHONY_RESULT_CODES` içindeki `85L` **id** ile yazılmış. Non-Turkcell'de satır id 10083 olduğu için 327734'ün attempt sayımı ve agent sahipliği koruması o ortamda fiilen devre dışıdır; `pd_cdr.telephony_rc` raporları da ortamlar arası farklı id gösterir. Çözüm yönü: id yerine `code == 912` kontrolü veya Non-Turkcell satırının 85'e taşınması (DBA). Bu analiz için karar gerekmez; ayrı `fix` işi olarak açılması önerilir.

### F9 — Unmapped cause fallback'i 901

Bilinmeyen cause code 901 (id 73, "Internal Error") olarak yazılır ve normal retry alır. Santral yeni kodları dialer migration'ından **önce** göndermeye başlarsa çağrılar 73 ile raporlanır; sıralama migration + restart → dialplan geçişi olmalıdır.

## Riskler

- **Contract yanlış anlaşılması (en yüksek):** santral id (11/12/86/87/88) gönderirse davranış sessizce bozulur (F1). Test ortamında WARN log kontrolü ve `pd_cdr` dağılımı ile doğrulama zorunlu.
- **Hot path regresyonu:** `RetryStrategy.createRetry` ve `ManagerConnectionDecorator` hangup yolu her çağrıda çalışır; 900 ve DNC davranışı karakterizasyon testleriyle kilitlenmeden dokunulmaz.
- **Numara halkası sonsuz döngüsü:** 03.08.2026 vakasının koruma katmanı (`advanceRetryNumberPastIneligible` tur sınırı) yeni predicate ile de korunmalı; "tüm numaralar geçersiz/DNC" kombinasyonu testlenmeli.
- **Strateji 0 semantiği:** "single number" stratejisinde numara değiştirmek yeni davranıştır (S3); mevcut müşteriler için etki notu gerekir.
- **Migration/deploy sırası:** satırlar yoksa startup ERROR (validate) ve WARN fallback; `IDENTITY_INSERT` yetkisi DBA'da.
- **Predictive pacing:** yeni kodlar `RING_VALID` kümelerinde değil; AMD-busy/no-answer trafiği 900'den 17/18'e kayarsa 71 için ölçülmüş AR_ring dağılımı değişir (bilgi notu, bu işte aksiyon yok).
- **Conflict riski:** 325559 ile aynı dosyalar (F6).

## Performans Etkisi

- Hot path durumu: **evet** — hangup/result finalize (`ManagerConnectionDecorator`) ve retry kararı (`RetryStrategy.createRetry`).
- Call/event başına ek maliyet: AMD ailesi kontrolü `Set<Integer>.contains(code)` (O(1), 4 eleman); 86 kuralı yalnız `code == 913` olan çağrılarda çalışır ve mevcut DNC yürüyüşünün predicate'ine bir `Set.contains` ekler.
- IO/scan/lock/serialization/log etkisi: yeni DB sorgusu yok; Seçenek A'da `unreachable_numbers` mevcut UPDATE/INSERT cümlesine bir kolon ekler (ayrı round-trip yok) ve `rawData`'dan lazy okunur (fetch sorgusu zaten `SELECT *`). Yeni log yalnız 86 dalında tek INFO satırı.
- Azaltım/ölçüm notu: 900 davranışı değişmediği için mevcut AMD hacminde (günde ~23 bin/ortam) maliyet farkı beklenmez; hedefli testler + PRP'de `pd_cdr` dağılım kıyası.

## TDD Stratejisi

1. **Karakterizasyon (önce):**
   - `RetryStrategyAmdFamilyBaselineCharacterizationTest`: 6 stratejide code 900/17/18/19 → aynı numaraya retry; manages-cycle'da TRC eşli schedule satırı varsa interval'ı, yoksa `retryDelay`; code 16 + final URC → complete.
   - `ManagerConnectionDecoratorAmdHangupCharacterizationTest`: code 900 için awaiting-URC'ye düşmeme, 84 override muafiyeti ve NO_CONNECTION sınıflaması (preview + predictive senaryosu) — id 71 ile bugünkü davranış kilitlenir.
2. **Başarısız feature testleri:**
   - `RetryStrategyBadNumberNextNumberFeatureTest`: 913 + tek numara → complete (+ close reason, 325559 tabanında); 913 + iki numara → `colSort` sonraki numaraya retry, tarih stratejinin normal hesabı; 913 + sonraki numara DNC'de → onu da atlar; tüm numaralar geçersiz/DNC → complete, exception yok, tur sınırı korunur; 6 strateji için parametrik.
   - `UnreachableNumbersPersistenceFeatureTest` (Seçenek A): rawData hydration, `;` ayraçlı yazım, binding yokken sessiz degrade (session-only).
   - `AmdFamilyHangupFeatureTest`: 913/914/915 için 900 ile aynı üç davranış.
   - `TelephonyResultCodeMigrationContractTest`: migration SQL'deki (id, code) çiftleri ↔ `Constants` code sabitleri ↔ `validateInternalTelephonyResultCodeMappings` zorunlu listesi.
3. **Regression:** `RetryDncExhaustionInfiniteLoopRegressionTest` genişletmesi (unreachable + DNC kombinasyonu).
4. Frontend: kod değişmiyorsa Karma çalıştırılmaz; Seçenek A'da `campaignFormUnreachableNumbersBinding.spec.js` + `npm.cmd run test:angular`.
5. Hedefli komutlar: `mvn -o -Dtest=<Sınıf> test` (tool path'ler `.codex/TOOL-PATHS.local.properties`'ten), `mvn -o -DskipTests package`.

## Önerilen Çözüm Yönü

| Faz | İçerik | Risk |
|---|---|---|
| 0 | Taban kararı (S6) ve santral cause contract'ının yazılı teyidi (S1) | Yok |
| 1 | Karakterizasyon testleri (TDD 1) | Yok |
| 2 | Veri: migration SQL (3 satır, `IDENTITY_INSERT`, idempotent) + `Constants` code sabitleri + `validateInternalTelephonyResultCodeMappings` + contract testi | Düşük |
| 3 | Engine: AMD id-71 kontrollerinin `code` tabanlı aileye taşınması (davranış korunur) | Orta |
| 4 | Engine: `RetryStrategy` bad-number kuralı (ineligibility kaynağı + kapanış), 325559 predicate'ine entegre | **Yüksek** (hot path) |
| 5 | Seçenek A: `unreachable_numbers` binding (config/DTO/mapper/type checker/default tablo/UI/localization/migration) | Düşük (additive) |
| 6 | Doküman: `DIALER-RUNTIME.md`, `ASTERISK-AMI-AGI.md` (cause contract), history | Düşük |

Deploy sırası: (1) `pd_telephony_rc` migration her ortamda, (2) dialer deploy + restart (startup validate temiz), (3) santral dialplan geçişi, (4) `pd_cdr` dağılımı ve WARN log kontrolü.

## Kararlar (16.09.2026, geliştirici yanıtları)

| Soru | Karar |
|---|---|
| S1 | Santral `DialerHangup` cause olarak **code** gönderir. Busy (code 17 → id 11) ve no answer (code 19 → id 13) müşteri teyidiyle bugün zaten geliyor; yeni sonuçlar için santral **913 / 914 / 915** gönderecek (id 86 / 87 / 88). Kartta yazan 11/12/86/87/88 değerleri ekrandaki id kolonudur. |
| S2 | Satır metinleri öneri gibi: 86 `AMD Bad Number (unallocated number)` / `Wrong Number`; 87 `AMD Not Reached` / `No answer`; 88 `AMD Unknown Tone` / `Answering Machine`. |
| S3 | 86'da "sonraki numaraya geç" kuralı `REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER` dâhil 6 stratejide uygulanır. |
| S4 | 86 sonrası retry zamanı stratejinin normal hesabıdır (manages-cycle'da 86 için schedule satırı varsa interval oradan, yoksa retry delay). |
| S5 | Kalıcılık kolonu eklenmez (Seçenek B). |
| S6 | Branch 325559 tabanına taşındı; PR sırası 325559 → 325470. |
| S7 | 86 kaynaklı kapanışta `close_reason` yazılır: `all numbers unreachable` (yalnız binding varsa). |
| S8 | Non-Turkcell Ambiguous id 10083 uyumsuzluğu bu işin kapsamı dışı; DBA satırı 85'e düzeltecek. Yeni satırların id'leri her ortamda 86/87/88 olacak; dialer kodu yine de `code` ile karar verir. |

### Revizyon (05.10.2026): cause code 913/914/915 → 113/114/115

Santral ekibinin talebiyle yeni satırların `code` değerleri değişti: id 86 → code **113**, id 87 → **114**, id 88 → **115**. Id'ler, satır adları ve davranış aynı kaldı; yukarıdaki metinlerde geçen 913/914/915 ilk sürümün değerleridir. 113..115 Q.850'de atanmamış aralıktır, dört üretim/test DB'sinde boş olduğu doğrulandı ve `ASTERISK_ERROR_CODES` ile kesişmez. İlk sürüm PRP (`gbWebphone_test`) ve S2 test DB'sine uygulanmış olduğu için migration script'i aynı dosyada revize edildi: eski kodlu satırları `UPDATE` ile taşır, eksikse yeni kodla ekler, iki yönlü çakışma koruması taşır. Santral yeni değerleri (113/114/115) göndermelidir; 913/914/915 artık haritada yoktur ve 901 fallback'ine düşer.

Execution plan: `docs/execution-plan/16.09.2026/FEAT-325470-AMD-SONUC-KODLARI-RETRY-STRATEJISI.md` (Faz 6).

## Açık Sorular ve Netleştirme İhtiyacı (16.09.2026'da kapatıldı, kayıt için)

- **S1 — Cause contract (bloke edici):** Santral `DialerHangup` cause olarak **`pd_telephony_rc.code`** gönderecek şekilde mutabık mı? Yani busy → **17**, no-answer → **18** (id 12) ya da **19** (id 13; bugünkü no-answer trafiğinin tamamı ve ekran görüntüsündeki schedule satırı 13'tür — hangisi?), yeni kodlar → **913/914/915** (id 86/87/88). Id (11/12/86/87/88) gönderilirse F1'deki sessiz bozulma oluşur.
- **S2 — Yeni satır metinleri:** öneri — 86: `AMD Bad Number (unallocated number)` / açıklama `Wrong Number`; 87: `AMD Not Reached` / `No answer`; 88: `AMD Unknown Tone` / `Answering Machine`. Onay veya alternatif?
- **S3 — Strateji kapsamı:** 86'da "sonraki numaraya geç" kuralı `REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER` (varsayılan, ekran görüntüsündeki) dâhil **6 stratejide** de uygulansın mı? Öneri: evet (kural numara geçerliliğiyle ilgili, retry ritmiyle değil).
- **S4 — 86 sonrası retry zamanı:** öneri — stratejinin normal hesabı (manages-cycle'da 86 için schedule satırı varsa onun interval'ı, yoksa retry delay; anında istenirse interval 0 tanımlanır). Alternatif: her zaman anında (`retry_date = now`).
- **S5 — Geçersiz numaranın kalıcılığı:** **Seçenek A (öneri)** opsiyonel `unreachable_numbers` result kolonu (`dnc_numbers` deseni; binding boşsa session-only'ye düşer) mi, **Seçenek B** kalıcılık yok (çift-86 kayıtlarda genel limite kadar sınırlı döngü kabul) mu?
- **S6 — Taban branch:** 325470 develop yerine **325559 üzerine** kurulsun mu (öneri: evet; 325559 planı tamam, PR bekliyor; ortak predicate + `close_reason` yeniden kullanılır, conflict önlenir)? Evetse bu branch 325559'a rebase edilir ve PR sırası 325559 → 325470 olur.
- **S7 — Kapanış nedeni:** 86 kaynaklı kapanışta 325559'un `close_reason` kolonuna metin yazılsın mı? Öneri: yeni sabit `CLOSE_REASON_ALL_NUMBERS_UNREACHABLE = "all numbers unreachable"` (yalnız binding eşliyse yazılır).
- **S8 — F8 yan bulgusu:** Non-Turkcell "Ambiguous" id 10083 / hardcoded 85 uyumsuzluğu için ayrı `fix` işi açılsın mı?

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyor. Açık sorular 16.09.2026'da kapatıldı ve execution plan `docs/execution-plan/16.09.2026/FEAT-325470-AMD-SONUC-KODLARI-RETRY-STRATEJISI.md` altında oluşturuldu. Kod değişikliği, geliştirici execution plan'ı onayladıktan sonra plan üzerinden başlar.
