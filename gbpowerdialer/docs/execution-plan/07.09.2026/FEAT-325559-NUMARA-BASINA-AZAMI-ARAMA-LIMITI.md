# FEAT Numara Başına Azami Arama Limiti Uygulama Planı

## Analiz Referansı

`docs/analyse/07.09.2026/FEAT-325559-NUMARA-BASINA-AZAMI-ARAMA-LIMITI.md`

Kararlar K1-K18, riskler R1-R11, bulgular F1-F5 / D1-D5 / B1 o dosyada. Bu plan onları tekrar etmez, uygular.

## Onaylanan Plan Özeti

Kampanyaya opsiyonel `maxAttemptPerNumber` parametresi eklenir ve mevcut **6 retry stratejisinin tamamına** ortak `RetryStrategy` template'i üzerinden entegre edilir. Yeni `RETRY_STRATEGY` enum değeri eklenmez. Numara başına sayaç için yeni sayaç kolonu eklenmez; sayım `call_process` kursoru ile `retry_history` + `cb_history` üzerinden yeniden kurulur. Kapanış nedeni `col_result_close_reason` binding'i üzerinden müşteri result tablosuna yazılır. Alan boş bırakıldığında bugünkü davranış birebir korunur.

5 faz: (1) karakterizasyon, (2) config + contract, (3) engine, (4) producer, (5) doküman.

## Amaç

Talebin kabul kriterleri 1-8'i ve "başarılı kapanış / callback limit kontrolü netleştirmesi"ni, üretimdeki çağrı akışını bozmadan karşılamak.

## Kapsam Dışı

- **B1 "+1" sızıntısı** (mevcut kayıt tavanının 11'e sızması) — K9 ile bilinçli kapsam dışı. Kök neden araştırılmaz, ayrı `fix` açılmaz.
- **Konfigürasyon gözlemleri** (camp 54'te `retry_delay = 0`, saat kısıtı olmaması, strateji 2'de Result Code Schedule List'in etkisiz olması) — mevcut durum kaydı, kod işi değil.
- **`col_result_attempts` opsiyonelliğinin latent riski** (B3 ailesi) — Denizbank'ta elendi, bu iş kapsamında düzeltilmiyor.
- **Yeni "max cycle" alanı** — talepte tek yeni parametre tanımlı; terminoloji notu analizde.
- **gbwebphone** — K7.
- **Yeni TRC id / CDR satırı** — K11.
- `FEAT-321997` migration script'indeki `campaign` / `pd_campaign` tablo adı hatası (D5) — ayrı iş.

## Performans Etkisi

- **Hot path durumu:** EVET. `RetryStrategy.createRetry` her hangup'ta, `processRetryNumberWithDNC` her retry planlamasında, producer fetch'i tick başına çalışıyor.
- **Call/event/tick başına ek maliyet:** Retry planlama başına numara→sayı haritası **bir kez** kurulur: `call_process` + `retry_history` + `cb_history` üzerinde tek geçiş → O(geçmiş uzunluğu). Numara adayları için tek `getNumberColumnsInRawData()` çağrısı. Bugünkü `ReachRetryPerNumber` yürüyüşü her adımda `retryHistory`'yi yeniden stream'liyor (O(numara × geçmiş)); yeni hâl **daha az** iş yapar.
- **IO/scan/lock/serialization/log etkisi:** Yeni DB query yok. Yeni blocking çağrı yok. Yeni `synchronized` blok yok. Fetch SQL'ine per-number koşulu **eklenmez** (non-sargable olur; 27.07.2026 FKS_CAPRAZ timeout vakası). Kapanış nedeni mevcut `insertCallResultToDB` UPDATE'ine eklenir → ek round-trip yok. Producer elemesi batch `uniqueKey IN (...)` tek UPDATE. Yeni log yalnız throttle'lı WARN (fallback ve erken kapatma).
- **Azaltım veya ölçüm notu:** Sayım haritası strateji instance'ında memoize edilir; strateji nesnesi her retry için yeniden üretildiği için (`RetryStrategyFactory.createStrategy`, `CallOriginator.java:744`) instance-level cache güvenli, static state gerekmez. Faz 3 sonunda `RetryStrategy` içinde retry başına `retryHistory`/`callProcessHistory`/`cbHistory` erişim sayısı sayılarak eski hâlle karşılaştırılır.

## TDD Planı

### Faz 1 — Karakterizasyon (davranış değişmeden) — **TAMAMLANDI 08.09.2026**

- [x] `RetryStrategySixStrategiesBaselineCharacterizationTest` — 6 stratejinin `isOverallRetryLimitReached()` + `setRetryNumber()` çıktısı; `countAllCallTypes` açık/kapalı; `macroMaxAttempt` tetiklemesi. **18 test.**
- [x] `RetryStrategySuccessfulCloseMatrixCharacterizationTest` — `createRetry:43` matrisi: TRC 16 + URC yok / final / final değil / DNC identifier ve TRC 16 dışı. **7 test.**
- [x] `CallHistoryColumnsLockstepCharacterizationTest` — `updateRetryHistory()` ile `"R"` adımının, `addCallbackHistoryEntry()` ile `"CB"` adımının birlikte yazıldığı; ilk adımın her zaman `"F"` olduğu; fresh aramanın `retryHistory`'de **bulunmadığı**; ayraç farkları (`;` vs `,`) ve hydration. **13 test.**
- [x] `CallProducerFreshNumberSelectionCharacterizationTest` — `extractNumberToCallFromPhoneNumbers` colSort'a göre ilk dolu kolonu seçiyor, geçmişe bakmıyor; tüm kolonlar NULL iken `IllegalStateException` yutuluyor ve `Call` üretilmiyor (D1 mevcut davranışı). **13 test.**

**Faz 1'de doğrulanan ek bulgular:**

- **F6 (yeni):** `RoundRobinPerCycle` tek dolu numaralı kayıtta `isRetryCycleReached()` her zaman `true` döner — per-cycle gecikme mantığının sınır durumu, testle kilitlendi.
- **D1 daha geniş:** Sızıntı callback numarası **dolu** olsa bile gerçekleşiyor. `CallProducer.java:615` `ObjectUtils.firstNonNull(...)` varargs olduğu için `extractNumberToCallFromPhoneNumbers` **her zaman** değerlendiriliyor; telefon kolonları boşsa callback'i olan kayıt da düşüyor. Faz 4'te bu da giderilecek.
- **Strateji 5 doğrulandı:** result code schedule tanımlı değilse numara **hiç dönmüyor** (aynı numarada kalıyor); advanced `NEXT` kuralı tanımlıysa halkada ilerliyor ve `processRetryNumberWithDNC` yürüyüşü uygulanıyor — yani Faz 3'teki ortak predicate bu stratejiyi de etkileyecek.

### Faz 2 — Config + contract — **TAMAMLANDI 08.09.2026**

- [x] `CampaignMaxAttemptPerNumberFieldContractTest` — kolon adı map'leri, `@Min(1)` sınırı, null kabulü, entity/DTO isim eşliği (Dozer), `ALLOWED_TECH_FIELDS` üyeliği, kapanış nedeni metinleri ve 255 karakter/tırnak uygunluğu. **13 test.**
- [x] `CampaignInMapperTest` genişletme — `maxAttemptPerNumber` wire contract'ta; mevcut alanlar korunuyor. **15 test (mevcut + yeni assertion).**
- [x] `CampaignDataTypeCheckerCloseReasonBindingTest` — `CLOSE_REASON` tip doğrulaması, sayısal tipin reddi, özel kolon adı, K18 otomatik binding, kolon yokken start'ın engellenmemesi, açık binding'in ezilmemesi, mevcut `cb_history`/`call_process` otomatik binding'lerinin bozulmaması. **7 test.**
- [x] `campaignFormMaxAttemptPerNumber.spec.js` — `constants` mirror, K2 freeze matrisi (strateji 0-5), `retryScheduleEnabled`/yetki freeze'i, kapanış nedeni select'i, `restrictByType` davranışı. **12 spec — KOŞULAMADI, bkz. blocker.**

**Faz 2'de eklenen ek karar:**

- **K18 uygulandı:** `close_reason` binding'i hem backend (`CampaignDataTypeChecker.applyDefaultHistoryColumnMappings`) hem frontend (`campaignFormCtrl.setDefaultValuesForUndefinedResultTableColumns`) tarafında otomatik atanıyor. Operatörün elle seçmesi gerekmez; DDL çalıştıktan sonra binding kendiliğinden oluşur. Açıkça seçilmiş bir binding **ezilmez** (testle korundu).

### Faz 3 — Engine (asıl davranış) — **TAMAMLANDI 08.09.2026**

Plandaki 10 test maddesi 5 sınıfta konsolide edildi; kapsam birebir korundu (eşleme aşağıda).

- [x] `MaxAttemptPerNumberCounterTest` — dizi yeniden kurulumu: `F`/`R`/`CB` kursoru ↔ `retry_history`/`cb_history` eşlemesi, bağımsız kursorlar, tutarsız uzunlukta fallback (4 varyant), `F` adımının colSort'a göre ilk dolu kolona atfı, kolon dışı callback numarasının kendi sayacı (K16), muhafazakârlık yönü. **15 test.**
- [x] `MaxAttemptPerNumberFeatureOffRegressionTest` — alan `null`/`0`/negatif iken atlama, erken kapatma, kapanış nedeni ve callback dalı yürüyüşü HİÇ devreye girmiyor; DNC-only semantiği korunuyor. Ayrıca **K2/R2**: strateji 1 alanı yok sayıyor (iki katmanlı sigorta) + karşıt kontrol olarak aynı fixture'ın round-robin'de atlama ürettiği. *(planlanan `ReachRetryPerNumberFieldIgnoredRegressionTest` bu sınıfta.)* **9 test.**
- [x] `MaxAttemptPerNumberSkipsExhaustedNumberTest` — Kriter 1, 3, 4, 5; **"limitteki numaraya bir daha arama atılmaz"** invariantı ve 4 turluk simülasyon. **6 test.**
- [x] `MaxAttemptPerNumberEarlyRecordTerminationTest` — Kriter 2 (4 dolu × 10 = 40'ta kapanış, overall 60 dolmamışken) + **5 stratejinin tamamında** erken kapatma (`RetryRulesCycleEarlyTerminationTest` ve `ReachRetrySingleNumberPerNumberLimitTest` kapsamı burada, F3/F4/K15) + **R11** başarılı kapanışta neden yazılmaması (`MaxAttemptPerNumberDoesNotOverrideSuccessfulCloseTest` kapsamı). **9 test.**
- [x] `MaxAttemptPerNumberCloseReasonPersistenceTest` — `close_reason` SQL contract'ı: binding + neden varken yazılıyor, binding yokken kolon adı sorguya **hiç girmiyor** (R12), neden yokken/boşken yazılmıyor, **TRC korunuyor** (D3), callback nedeni, tek tırnak kaçışı. **8 test.**
- [x] `RetryDncExhaustionInfiniteLoopRegressionTest` genişletildi — fuse deseni per-number senaryosuna taşındı (2 yeni test). **Mevcut 6 test silinmedi, değiştirilmedi.**

**Faz 3'te alınan ek kararlar:**

- **Ortak guard `createRetry` içinde uygulandı**, 6 stratejinin `isOverallRetryLimitReached()` override'ına ayrı ayrı eklenmedi. Gerekçe: tek nokta, strateji atlanması yapısal olarak imkânsız (F3/F4 endişesi böylece kapandı) ve en riskli metotta diff minimal. Kontrol sırası korundu: başarılı kapanış → genel limit → numara başına.
- **Arama dizisi kurulumu `Call`'a kondu** (`getDialedNumberSequence`, `getFirstDialedNumber`), politika `RetryStrategy`'de kaldı. `call_process`/`retry_history`/`cb_history` `Call`'ın sahip olduğu veri; owner boundary'de çözüldü.
- **Fallback log seviyesi WARN değil DEBUG.** Gerekçe: fallback bir hata değil, `call_process` kolonu olmayan eski kayıtların BEKLENEN yolu; her retry kararında WARN basmak hot path'te gürültü üretirdi. Throttle için static map gerekirdi ki AGENTS.md yeni static mutable state yasaklıyor. Görünürlük gerekirse aggregate sayaç monitoring'e aittir, per-call WARN'a değil.
- **`insertIntoCampaignResultTable` (INSERT yolu) bilinçli kapsam dışı.** Numara başına limit kaynaklı kapanış, satırda önceden geçmiş olmasını gerektirir; geçmiş varsa satır result tablosunda zaten mevcuttur ve UPDATE yolu çalışır. INSERT'e dokunmak, kolon/değer listelerini iki ayrı geçişte kuran bu metotta gereksiz risk olurdu.
- **`StringUtils.isNotBlank`** kullanıldı (`isNotEmpty` değil): boşluklu neden absent sayılır, komşu `cb_history` guard'ıyla aynı stil.

### Faz 4 — Producer — **TAMAMLANDI 08.09.2026**

Planlanan 4 test maddesi, Faz 1'de yazılan `CallProducerFreshNumberSelectionCharacterizationTest`'in yeni contract'a taşınmasıyla + 1 yeni SQL contract sınıfıyla karşılandı.

- [x] `CallProducerFreshNumberSelectionCharacterizationTest` **yeni contract'a taşındı ve genişletildi** (13 → 22 test). Kapsananlar: Kriter 4 (limitli kolon atlanıyor), Kriter 8 (yalnız yeni eklenen numara aranıyor), Kriter 6/7 (callback numarası limit altı/üstü), limitli retry numarası, D1 fix (null dönüş + DB'de kapatma + neden), reason ayrımı (3 neden), feature-off korunumu, batch çağrı sayısı. Korunan invariantlar (colSort, boş/disabled kolon, `calledNumber` öncelik sırası) aynen duruyor.
- [x] `CampaignDbOperationsClosedWithoutDialingSqlContractTest` — `setClosedWithoutDialing` SQL contract'ı: `complete_date` + `is_disabled` + koşullu `close_reason`, **TRC yazılmaması** (K11), **user alanlarına dokunulmaması**, ayrı tablolarda önce eksik satır INSERT'i, tek tabloda INSERT atlanması, boş/null key listesinde DB çağrısı olmaması, 1000'lik chunk, tırnak kaçışı. **11 test.**

**Faz 4'te alınan ek kararlar:**

- **`extractNumberToCallFromPhoneNumbers` silindi** (ölü kod). Yerine `resolveCalledNumber` + `selectFreshNumber` geldi. `gbpd-dead-code-audit` gereği referanslar tarandı: üretimde tek çağrı noktası kalmamıştı, yalnız Faz 1 testi reflection ile çağırıyordu; test yeni metoda taşındı. `ObjectUtils` import'u da kullanılmadığı için kaldırıldı.
- **`ObjectUtils.firstNonNull` kaldırıldı, argümanlar tembel değerlendiriliyor.** Faz 1'de bulunan geniş D1 hali (callback numarası dolu olsa bile kolonlar boşsa kayıt düşüyordu) bu sayede giderildi. Test `recordWithCallbackNumberAndEmptyColumnsNowProducesCall` bunu kanıtlıyor.
- **`setClosedWithoutDialing` ayrı tablolarda önce eksik result satırlarını ekliyor.** `setFetchedDataFlagForCalls` ile aynı desen. Aksi halde UPDATE 0 satır etkiler ve kayıt her producer tick'inde yeniden SELECT edilmeye devam ederdi — D1'in gerçek semptomu buydu.
- **`processAndQueueFetchedCalls`'a ayrı filtre eklenmedi.** Eleme `createCallsFromRawData` içinde, Call kurulurken yapılıyor; callback/retry/fresh üç yol da oradan geçtiği için Kriter 7 doğal olarak kapsanıyor. İkinci bir batch UPDATE ve ikinci bir tarama gereksiz olurdu. DNC/expire filtreleri kendi elemelerini sonrasında yapmaya devam ediyor.

## Uygulama Kontrol Listesi

### Faz 1 — Karakterizasyon — **TAMAMLANDI**

- [x] Faz 1 testleri `gbpowerdialer/src/test/java/tests/dialer/` altına yazıldı ve **yeşil** koştu (mevcut davranış kilitlendi). Üretim kodu **değişmedi**.

### Faz 2 — Config + contract (additive, feature off) — **TAMAMLANDI**

- [x] `Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES` → `CLOSE_REASON("close_reason")`.
- [x] `Constants.RESULT_FIELDS_ALLOWED_TYPES` → `CLOSE_REASON` için NVARCHAR ailesi (`RETRY_HISTORY` şablonu).
- [x] `Constants.ALLOWED_TECH_FIELDS` → `maxAttemptPerNumber`.
- [x] `Constants` → kapanış nedeni metin sabitleri (K14): `CLOSE_REASON_NUMBER_LIMIT_REACHED`, `CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK`, `CLOSE_REASON_NO_CALLABLE_NUMBER`.
- [x] `Campaign.java` → `maxAttemptPerNumber` (`Integer`, `@Min(1)`, `@Column(name = "max_attempt_per_number")`) ve `colResultCloseReason` (`String`, `@Column(name = "col_result_close_reason")`).
- [x] `CampaignFormDTO.java` → aynı isimli iki alan (Dozer isim-eşli, testle doğrulandı).
- [x] `CampaignDataTypeChecker.java` → `CLOSE_REASON` `checkColumn` satırı + `applyDefaultColumnMapping` ile otomatik binding (K18).
- [x] `CampaignDbOperations.java` → `createDefaultResultTable` ve `addDefaultResultColumnsToSourceTable` içinde `close_reason` (`ncharType` = `NVARCHAR(255)`).
- [x] `CampaignInDTO.java` + `CampaignInMapper.mapRetryFields` → `maxAttemptPerNumber`. Mevcut alanlar silinmedi/yeniden adlandırılmadı.
- [x] `ValidationMessages{,_tr_TR,_ru_RU,_ua_UA}.properties` → `campaign.max_attempt_per_number.min`. **4 dosya, 4 ekleme, 0 silme**; non-ASCII değerler dosya konvansiyonuna uygun `\uXXXX` escape ile yazıldı.
- [x] `campaignForm.html` → Retry Rules tab'ına kendi satırında `Max Attempt Per Phone Number` input; `ng-required` **yok**; `retryStrategy == 1` iken `ng-disabled` + açıklama `help-block` (K2).
- [x] `campaignForm.html` → `colResultCloseReason` result-kolon `<select>`'i, `restrictByType` filtresiyle; `ng-required` **yok** (K17/R9).
- [x] `constants.js` → client mirror'a `CLOSE_REASON` + allowed types.
- [x] `campaignFormCtrl.js` → `initializedResultColumns` listesine `colResultCloseReason`.
- [x] `campaignFormCtrl.js` → `setDefaultValueForResultColumn('close_reason')` (K18).
- [x] `campaignFactory.js` → `maxAttemptPerNumber` default **yazılmadı** (K17; `_createNew` dosyasına dokunulmadı).
- [x] `localization_{tr,en,ru,ua}.js` → `campaign.maxAttemptPerNumber`, `campaign.maxAttemptPerNumber.frozenForStrategy`, `campaign.colResultCloseReason`. 4 dosyada da 3 key mevcut (doğrulandı).
- [x] `wro.xml` → yeni bundle dosyası eklenmediği için değişiklik yok (doğrulandı).
- [x] `resources/sql/migration/FEAT-325559-max-attempt-per-number.sql` → iki bölümlü: **BÖLÜM 1** PD ana DB (`dbo.pd_campaign` 2 kolon, `COL_LENGTH(...) IS NULL` guard), **BÖLÜM 2** kampanya DB'leri (tüm result tablolarına dinamik `close_reason`, opsiyonel `call_process`/`cb_history`, kapasite raporu, üreten-ama-çalıştırmayan `ALTER COLUMN` script'i, doğrulama sorgusu). Rollback/kill-switch notu dâhil. Tablo adı sabitlenmez.
- [x] Faz 2 testleri yeşil; feature off olduğu için Faz 1 karakterizasyonu **hâlâ yeşil** (163/163).

### Faz 3 — Engine — **TAMAMLANDI**

- [x] `Call.getDialedNumberSequence()` + `Call.getFirstDialedNumber()` — dizi yeniden kurulumu owner boundary'de (`Call` bu üç kolonun sahibi). Tutarsızlıkta güvenli fallback + DEBUG log.
- [x] `Call.getCloseReason()` / `setCloseReason()` — kapanış nedeni taşıyıcısı.
- [x] `RetryStrategy.isPerNumberLimitActive()` — alan null/`<=0` veya kampanya stratejisi 1 ise **feature off**.
- [x] `RetryStrategy.getDialCountsByNumber()` — numara→sayı haritası, **karar başına bir kez** memoize (instance-level; `createRetry` girişinde sıfırlanır).
- [x] `RetryStrategy.isNumberAttemptLimitReached(String)` ve `isNumberIneligible(String)` = `DNC || limitReached`.
- [x] `RetryStrategy.processRetryNumberWithDNC()` → `advanceRetryNumberPastIneligible(this::isNumberIneligible)`. Feature off iken birebir eski DNC-only davranışa düşer.
- [x] `ReachRetryPerNumber.isPerNumberLimitActive()` → `false` (K2 ikinci katman sigorta); kendi `retryOveralAttempt` semantiği korundu.
- [x] `RetryStrategy.noEligibleNumberLeft()` — ortak guard, `createRetry` içinde tek noktada uygulandı; **6 stratejinin tamamı** kapsanıyor (F3/F4/K15).
- [x] `RetryStrategy.createRetry` → kontrol sırası: başarılı kapanış → genel limit → numara başına. Kapanış nedeni **yalnız limit yolunda** set edilir (R11). Callback dalına, **yalnız feature aktifken**, eligibility yürüyüşü eklendi (Kriter 7'nin engine tarafı; feature off iken bu dalın bugünkü davranışı korunur).
- [x] `RetryStrategy.applyExhaustedCloseReason()` — callback ise `max attempt reached on callback`, dolu numara yoksa `no callable number available`, aksi halde `closing for reached number limit`.
- [x] `CampaignDbOperations.updateCampaignListTableRow` → kapanış nedeninin **koşullu** yazımı (binding dolu **ve** neden blank değilse), tek tırnak kaçışıyla.
- [x] Faz 3 testleri yeşil; Faz 1 karakterizasyonu feature-off senaryosunda **hâlâ yeşil** (266/266).

### Faz 4 — Producer — **TAMAMLANDI**

- [x] `CallProducer.resolveCalledNumber(Call)` → tembel değerlendirilen öncelik sırası (callback > retry > kolonlar) + limit-farkında uygunluk kontrolü. Aday kalmazsa `null` döner; istisna yutulmaz.
- [x] `CallProducer.selectFreshNumber(Call)` → colSort'a göre ilk dolu **ve limitini doldurmamış** kolon. Feature off iken birebir eski davranış.
- [x] `CallProducer.perNumberDialCounts(Call)` → feature off iken hiç hesaplanmaz; açıkken kayıt başına bir kez.
- [x] `CallProducer.extractNumberToCallFromPhoneNumbers` **silindi** (ölü kod), `ObjectUtils` import'u kaldırıldı.
- [x] `CallProducer.createCallsFromRawData` → aranamayan kayıtların key'leri **neden bazında** toplanıyor, `callsUniqueKeys`'e ekleniyor (aynı tick'te tekrar denenmesin) ve batch olarak kapatılıyor.
- [x] `CallProducer.resolveUncallableReason(Call)` → callback / limit dolu / geçerli numara yok ayrımı.
- [x] `CallProducer.closeUncallableRecords(...)` → neden başına **tek** batch DB çağrısı, `try/catch` ile izole, throttle'sız tek WARN (nadir olay, kayıt başına değil batch başına).
- [x] `CampaignDbOperations.setClosedWithoutDialing(...)` → `complete_date` + `is_disabled` + koşullu `close_reason`; **TRC yazılmaz** (K11), **user alanlarına dokunulmaz**; ayrı tablolarda önce `insertMissingResultRows`.
- [x] `processAndQueueFetchedCalls`'a ayrı filtre **eklenmedi** (gerekçe yukarıda).
- [x] Faz 4 testleri yeşil; Faz 1-3 regression'ı **hâlâ yeşil** (293/293).

### Faz 5 — Doküman — **TAMAMLANDI 08.09.2026**

- [x] `docs/architecture/DIALER-RUNTIME.md` → yeni `Retry ve Numara Seçimi` bölümü: `createRetry` chokepoint'i ve kontrol sırası (başarılı kapanış → genel limit → numara başına), numara başına limitin atlama/erken kapatma/muafiyet/sayaç kaynağı davranışı, producer tarafındaki limit-farkında seçim ve kapatılan sızıntı. `Change Contract`'a 3 yeni madde.
- [x] `docs/architecture/DATA-PERSISTENCE-AND-DB.md` → `Arama geçmişi kolonları ve numara başına sayaç` (üç kolonun tablosu, kilitli adım invariantı, fresh'in `retry_history`'de olmaması, muhafazakârlık, fallback) ve `Kapanış nedeni (close_reason)` alt bölümleri. `Change Contract`'a 3 yeni madde.
- [x] Onboarding checklist → `DATA-PERSISTENCE-AND-DB.md` içinde `Kampanya Onboarding Kontrol Listesi` bölümü: 6 adımlı liste (config DB DDL, result tablosu DDL, otomatik binding, **kolon kapasitesi kontrol sorgusu**, `call_process` eşlemesi, paylaşımlı result tablosu semantiği) + kill-switch notu.
- [x] `docs/history/07.09.2026.md` append-only kayıt.

Mimari doküman diff'i: **85 ekleme, 0 silme** — mevcut içerik korundu, yeni doküman açılmadı (bu nedenle `docs/README.md` indeksi güncellenmedi).

### Genel

- [x] Onaylanan planın tüm adımları bu plana işlendi; plandan sapılan noktalar (test sınıfı konsolidasyonu, guard'ın tek noktada uygulanması, `processAndQueueFetchedCalls` filtresinin gereksiz kalması, INSERT yolunun kapsam dışı bırakılması) gerekçeleriyle yazıldı.
- [x] Hot path performans etkisi doğrulandı: sayaç haritası karar başına bir kez memoize ediliyor, producer'da feature off iken hiç hesaplanmıyor; yeni DB query/blocking çağrı/`synchronized` blok yok, fetch SQL'ine per-number koşulu eklenmedi, kapanış nedeni mevcut UPDATE'e eklendi (ek round-trip yok), aranamayan kayıt kapatma neden başına tek batch.
- [x] Yeni static mutable state eklenmedi (fallback log'u bu yüzden DEBUG, throttle map'i yok).
- [x] Yeni kütüphane/artifact eklenmedi; `ObjectUtils` bağımlılığı kullanımdan kalktı.
- [x] Yeni dosyalarda secret/gerçek IP/credential yok. Test verisinde `number_1` / `0555_manual_entry` gibi placeholder'lar kullanıldı; migration script'inde parola/IP yok. **İstisna:** analiz dosyasındaki `pd_cdr` ölçüm çıktısı gerçek müşteri numarası içermiyor (yalnız `contact_id` + `dial_count` dağılımı yazıldı).

## Doğrulama

`.codex/TOOL-PATHS.local.properties` okunur; Maven **offline** (`-o`) çalıştırılır, dependency indirilmez.

Faz bazlı hedefli komutlar:

```text
mvn -o -DskipTests compile
mvn -o -Dtest=RetryStrategySixStrategiesBaselineCharacterizationTest,RetryStrategySuccessfulCloseMatrixCharacterizationTest,CallHistoryColumnsLockstepCharacterizationTest,CallProducerFreshNumberSelectionCharacterizationTest test
mvn -o -Dtest=CampaignMaxAttemptPerNumberFieldContractTest,CampaignDataTypeCheckerCloseReasonBindingTest,CampaignInMapperTest test
mvn -o -Dtest=MaxAttemptPerNumberCounterTest,MaxAttemptPerNumberFeatureOffRegressionTest,ReachRetryPerNumberFieldIgnoredRegressionTest,MaxAttemptPerNumberSkipsExhaustedNumberTest,MaxAttemptPerNumberEarlyRecordTerminationTest test
mvn -o -Dtest=RetryRulesCycleEarlyTerminationTest,ReachRetrySingleNumberPerNumberLimitTest,MaxAttemptPerNumberDoesNotOverrideSuccessfulCloseTest,MaxAttemptPerNumberCloseReasonPersistenceTest test
mvn -o -Dtest=RetryLimitTest,RetryStrategyFactoryTest,RetryStrategyTemplateTest,RoundRobinNextCycleTest,RoundRobinNextNumberTest,RoundRobinPerCycleTest,RetryDncExhaustionInfiniteLoopRegressionTest test
mvn -o -Dtest=CallProducerLimitAwareFreshNumberSelectionTest,CallProducerNoCallableNumberClosesRecordTest,CallProducerAllNumbersExhaustedFilterTest,CallProducerCallbackLimitReachedFilterTest,CallProducerTest,CallOriginatorTest test
npm.cmd run test:angular
```

Frontend değiştiği için plan **yalnız Maven testleriyle kapatılamaz**; `npm.cmd run test:angular` koşulmalı, environment blocker varsa açık yazılır (`node_modules` yoksa offline kuralı gereği indirilmez, geliştiriciye raporlanır).

### Faz 1 sonuçları (08.09.2026)

Çalıştırılan komutlar (offline JDK 1.8.0_202 + Maven 3.9.14, `-o -s settings.xml -Dmaven.repo.local=...`):

```text
mvn -o ... -f gbpowerdialer/pom.xml -DskipTests compile
mvn -o ... -Dtest=RetryStrategySixStrategiesBaselineCharacterizationTest test
mvn -o ... -Dtest=RetryStrategySuccessfulCloseMatrixCharacterizationTest,CallHistoryColumnsLockstepCharacterizationTest test
mvn -o ... -Dtest=CallProducerFreshNumberSelectionCharacterizationTest test
mvn -o ... -Dtest=<4 yeni sinif>,RetryLimitTest,RetryStrategyFactoryTest,RetryStrategyTemplateTest,
                  RoundRobinNextCycleTest,RoundRobinNextNumberTest,RoundRobinPerCycleTest,
                  RetryDncExhaustionInfiniteLoopRegressionTest,
                  ReachRetryPerNumberDncRetryCharacterizationTest,CallProducerTest test
```

Sonuç:

- `compile`: **BUILD SUCCESS** (790 main kaynak).
- Yeni 4 karakterizasyon sınıfı: **51/51 PASS** (18 + 7 + 13 + 13).
- Birleşik hedefli koşu (yeni 4 + mevcut 9 ilgili sınıf): **96/96 PASS, BUILD SUCCESS**.
- Mevcut retry testleri (`RetryLimitTest`, `RetryStrategyFactoryTest`, `RetryStrategyTemplateTest`, `RoundRobinNextCycleTest`, `RoundRobinNextNumberTest`, `RoundRobinPerCycleTest`, `RetryDncExhaustionInfiniteLoopRegressionTest`, `ReachRetryPerNumberDncRetryCharacterizationTest`, `CallProducerTest`): hepsi **PASS**, hiçbiri değiştirilmedi veya silinmedi.
- `npm.cmd run test:angular`: **Faz 1'de KOŞULMADI** — bu fazda frontend dosyası değişmedi. Faz 2'de zorunlu.

### Faz 2 sonuçları (08.09.2026)

```text
mvn -o ... -DskipTests compile
mvn -o ... -Dtest=CampaignMaxAttemptPerNumberFieldContractTest,CampaignDataTypeCheckerCloseReasonBindingTest,CampaignInMapperTest test
mvn -o ... -Dtest=<Faz1 4 sinif>,<Faz2 3 sinif>,CampaignsResourceTest,RetryLimitTest,RetryStrategyFactoryTest,
                  RetryStrategyTemplateTest,RoundRobinNextCycleTest,RoundRobinNextNumberTest,RoundRobinPerCycleTest,
                  RetryDncExhaustionInfiniteLoopRegressionTest,ReachRetryPerNumberDncRetryCharacterizationTest,
                  CallProducerTest,CallOriginatorTest test
node --check <degisen 6 js dosyasi + yeni spec>
npm.cmd run test:angular
```

Sonuç:

- `compile`: **BUILD SUCCESS** (790 main kaynak).
- Faz 2 Java testleri: **35/35 PASS** (13 + 7 + 15).
- Birleşik Faz 1 + Faz 2 regression (18 sınıf): **163/163 PASS, BUILD SUCCESS**. Faz 1 karakterizasyonunun tamamı yeşil kaldı → feature off davranışı değişmedi.
- Mevcut testler (`CampaignsResourceTest`, `CallOriginatorTest`, `CallProducerTest`, 6 retry sınıfı, 2 karakterizasyon sınıfı): **PASS**, hiçbiri değiştirilmedi veya silinmedi. `CampaignInMapperTest` ve `MockFactory`'ye yalnız **yeni assertion/stub eklendi**, mevcut assertion'lar korundu.
- `node --check`: değişen 6 JS dosyası + yeni spec → **hepsi OK**.
- **`npm.cmd run test:angular`: KOŞULAMADI (environment blocker).** `gbpowerdialer/node_modules` yarı bozuk: 145 paket dizininin **73'ü tamamen boş** ve bunlar arasında Karma'nın ihtiyaç duyduğu **tüm** paketler var (`karma`, `jasmine-core`, `angular-mocks`, `karma-jasmine`, `karma-chrome-launcher`); `node_modules/.bin` de boş, bu yüzden `karma` binary'si bulunamıyor. Offline kuralı gereği indirme denenmedi. Geliştirici VPN'de `npm ci` çalıştırdıktan sonra `npm.cmd run test:angular` tekrar koşulmalıdır.

**Blocker yerine yapılan telafi doğrulamaları** (spec'in kırılganlığı buradan kaynaklandığı için):

- Spec'teki `ng-disabled` ifadeleri `campaignForm.html` ile **byte-for-byte** karşılaştırıldı ve eşleştiği doğrulandı (yeni alan, kapanış nedeni select'i ve karşılaştırma için `macroMaxAttempt`).
- HTML'de kullanılan 3 yeni i18n key'inin 4 localization dosyasının **hepsinde** bulunduğu doğrulandı.
- `wro.xml`'in değişiklik gerektirmediği doğrulandı.

### Faz 3 sonuçları (08.09.2026)

```text
mvn -o ... -DskipTests compile
mvn -o ... -Dtest=<Faz1 4 sinif>,RetryLimitTest,RetryStrategyFactoryTest,RetryStrategyTemplateTest,
                  RoundRobinNextCycleTest,RoundRobinNextNumberTest,RoundRobinPerCycleTest,
                  RetryDncExhaustionInfiniteLoopRegressionTest,
                  ReachRetryPerNumberDncRetryCharacterizationTest test          # feature-off kontrolu
mvn -o ... -Dtest=MaxAttemptPerNumberCounterTest test
mvn -o ... -Dtest=MaxAttemptPerNumberSkipsExhaustedNumberTest test
mvn -o ... -Dtest=MaxAttemptPerNumberEarlyRecordTerminationTest test
mvn -o ... -Dtest=MaxAttemptPerNumberFeatureOffRegressionTest test
mvn -o ... -Dtest=MaxAttemptPerNumberCloseReasonPersistenceTest,RetryDncExhaustionInfiniteLoopRegressionTest test
mvn -o ... -Dtest=<26 sinif: Faz 1 + Faz 2 + Faz 3 + mevcut ilgili testler> test
```

Sonuç:

- `compile`: **BUILD SUCCESS**.
- **Feature-off kontrolü (kod değişikliğinden hemen sonra, yeni test yazılmadan): 87/87 PASS.** Faz 1 karakterizasyonunun tamamı yeşil kaldı → engine değişikliği alan boşken davranış-nötr.
- Faz 3 yeni testleri: **47/47 PASS** (15 + 9 + 6 + 9 + 8).
- `RetryDncExhaustionInfiniteLoopRegressionTest`: **8/8 PASS** (mevcut 6 + yeni 2).
- **Birleşik Faz 1 + 2 + 3 regression (26 sınıf): 266/266 PASS, BUILD SUCCESS.** Kapsananlar: `CampaignsResourceTest`, `CallOriginatorTest`, `CallProducerTest`, `WebphoneSubscriberTest` (48 test), 6 retry sınıfı, `ReachRetryPerNumberTest`, `ReachRetrySingleNumberTest`, `CampaignInMapperTest`.
- `npm.cmd run test:angular`: Faz 3'te frontend dosyası değişmedi; Faz 2 blocker'ı geçerli.

**Uygulama sırasında bulunan ve giderilen iki hata (kendi kodumda):**

1. `Call.getDialedNumberSequence()` ilk sürümünde "iz boş → fresh-only" kısayolu, `retry_history` dolu ama `call_process` boş olan **eski kayıtlarda aramaları EKSİK sayıyordu** (limit hiç tetiklenmezdi). Kısayol yalnız üç kolonun da boş olduğu duruma daraltıldı; diğer durum tutarsızlık olarak fallback'e düşüyor. `MaxAttemptPerNumberCounterTest.emptyTrailWithPopulatedRetryHistoryFallsBackInsteadOfUndercounting` bunu kilitler.
2. `close_reason` yazımında `StringUtils.isNotEmpty` boşluklu metni geçiriyordu; `isNotBlank`'e çevrildi.

**Kapsam dışı olduğu doğrulanan mevcut RED test:** `CampaignResultJobServiceTest` — 4 failure (`expire_date` date-only WHERE / `CAST(... AS datetime)` kurulumu). **Pristine `HEAD` (03c0f7cc) üzerinde geçici worktree ile koşuldu ve aynı 4 failure birebir aynı mesajlarla alındı** → önceden RED, bu iş kapsamı dışı. Bu işin dokunduğu hiçbir metodu kullanmıyor (`grep` ile doğrulandı) ve dosyaları bu iş kapsamında değiştirilmedi.

### Faz 4 sonuçları (08.09.2026)

```text
mvn -o ... -DskipTests compile
mvn -o ... -Dtest=CallProducerFreshNumberSelectionCharacterizationTest test
mvn -o ... -Dtest=CampaignDbOperationsClosedWithoutDialingSqlContractTest test
mvn -o ... -Dtest=<30 sinif: Faz 1 + 2 + 3 + 4 + mevcut ilgili testler> test
```

Sonuç:

- `compile`: **BUILD SUCCESS**.
- `CallProducerFreshNumberSelectionCharacterizationTest`: **22/22 PASS** (13 → 22, yeni contract).
- `CampaignDbOperationsClosedWithoutDialingSqlContractTest`: **11/11 PASS**.
- **Birleşik Faz 1+2+3+4 regression (30 sınıf): 293/293 PASS, BUILD SUCCESS.**
- `npm.cmd run test:angular`: Faz 4'te frontend dosyası değişmedi; Faz 2 blocker'ı geçerli.

**D1'in gerçek semptomu düzeltildi ve analizdeki tanım güncellendi.** Analizde "kayıt `is_fetched` işaretli kalıp `complete_date` NULL olduğu için ne aranıyor ne kapanıyor" yazıyordu. Faz 4'te kod yeniden okunduğunda görüldü ki `setFetchedDataFlagForCalls` yalnız **oluşturulan** Call'lara uygulanıyor; düşürülen kayıt o listeye hiç girmediği için **`is_fetched` de işaretlenmiyor**. Yani kayıt her producer tick'inde yeniden SELECT edilip yeniden WARN'lanıyordu — "takılı kayıt" değil, **sonsuz yeniden-fetch döngüsü**. Bu yüzden `setClosedWithoutDialing` ayrı tablolarda önce eksik result satırını ekliyor; aksi halde UPDATE 0 satır etkiler ve döngü sürerdi.

### Faz 5 sonuçları (08.09.2026)

Dokümantasyon-only faz; Maven/NPM testi çalıştırılmadı (AGENTS.md: kod davranışı değişmedi). Doğrulama diff kontrolüyle yapıldı: `docs/architecture/` altında **85 ekleme, 0 silme**.

### Kapanış doğrulaması (Faz 1-5 birlikte)

```text
mvn -o ... -DskipTests compile
mvn -o ... -Dtest=<30 sinif> test
```

- `compile`: **BUILD SUCCESS**.
- Birleşik regression (30 sınıf): **293/293 PASS, BUILD SUCCESS**.
- `npm.cmd run test:angular`: **KOŞULAMADI** — `node_modules` blocker'ı (bkz. Faz 2 sonuçları). 12 spec bekliyor.

### Kabul kriterlerinin karşılanma durumu

| Kriter | Durum | Kanıtlayan test |
|---|---|---|
| 1 — limit dolan numara atlanır | ✅ | `MaxAttemptPerNumberSkipsExhaustedNumberTest` |
| 2 — tüm numaralar dolunca kayıt kapanır | ✅ | `MaxAttemptPerNumberEarlyRecordTerminationTest` |
| 3 — dengeli geçmiş, 11. arama asla çıkmaz | ✅ | `MaxAttemptPerNumberSkipsExhaustedNumberTest` |
| 4 — limitli ilk numara hiç denenmeden atlanır | ✅ | `CallProducerFreshNumberSelectionCharacterizationTest` + skip testi |
| 5 — sonuç kodundan bağımsız sayaç | ✅ | `MaxAttemptPerNumberSkipsExhaustedNumberTest` |
| 6 — callback limit altındaysa aranır | ✅ | `CallProducerFreshNumberSelectionCharacterizationTest` |
| 7 — callback limit dolduysa aranmaz, kayıt kapanır | ✅ | `CallProducerFreshNumberSelectionCharacterizationTest` |
| 8 — yeni eklenen numara aranır, eskiler atlanır | ✅ | `CallProducerFreshNumberSelectionCharacterizationTest` |
| Başarılı kapanış netleştirmesi | ✅ | `RetryStrategySuccessfulCloseMatrixCharacterizationTest`, `MaxAttemptPerNumberEarlyRecordTerminationTest` |
| Tüm numaralar NULL → statüyle kapanır | ✅ | `CallProducerFreshNumberSelectionCharacterizationTest` |

### Deploy öncesi kalan işler (kod dışı)

1. **DBA:** `sql/migration/FEAT-325559-max-attempt-per-number.sql` iki bölüm hâlinde çalıştırılır. **BÖLÜM 1** PD ana DB'de bir kez (`pd_campaign` 2 kolon). **BÖLÜM 2** her kampanya DB'sinde bir kez; tablo adı sabitlenmediği için tüm result tabloları dinamik keşfedilir. Bölümler tek dosyada olduğu için istenirse iki ayrı script olarak bölünebilir.
2. **Onboarding:** BÖLÜM 2'nin `2.3` raporu boş dönmüyorsa `2.4`'ün ürettiği `ALTER COLUMN ... NVARCHAR(MAX)` ifadeleri DBA tarafından bakım penceresinde çalıştırılır (script bunları bilinçli olarak EXEC etmez). Genel limit yükseltilmeden önce zorunlu. `2.5` doğrulama sorgusuyla kapanır.
3. **Frontend testi:** geliştirici VPN'de `npm ci` sonrası `npm.cmd run test:angular` koşar.
4. **İş birimi (M1):** `Overall Attempts` yeniden kurgulanır. 10'da kalırsa yeni alan hiç tetiklenmez; 60'a çıkarılırsa çok numaralı kontaklarda toplam hacim artar. Kampanya yöneticisinin bilinçli kararı gerekir.

## Kapanış Kriteri

- [x] Planlanan tüm Java testleri çalıştırıldı ve **başarılı** oldu (293/293).
- [x] Feature off (`maxAttemptPerNumber = null`) iken davranış Faz 1 karakterizasyonuyla birebir aynı — `MaxAttemptPerNumberFeatureOffRegressionTest` (null/0/negatif, DNC-only semantiği, callback dalı) + Faz 1'in 4 sınıfı yeşil.
- [x] Strateji 1 + alan dolu senaryosunda davranış bugünküyle birebir aynı — `reachRetryPerNumberIgnoresMaxAttemptPerNumberField` ve karşıt kontrol `sameFixtureDoesSkipUnderRoundRobinStrategy`.
- [x] "Limitteki numaraya bir daha arama atılmaz" invariantı testle kanıtlandı — `numberAtExactLimitNeverReceivesAnotherDial`, `exhaustedNumberIsSkippedInEverySubsequentCycle`.
- [x] Başarılı kapanışta kapanış nedeni yazılmadığı ve TRC'nin değişmediği testle kanıtlandı — `successfulCloseDoesNotWriteCloseReason`, `telephonyResultCodeIsPreservedWhileCloseReasonIsWritten`, `telephonyResultCodeIsNeverWritten`.
- [x] `npm.cmd run test:angular` **koşulamadı; blocker açık yazıldı** (Faz 2 sonuçları). 12 spec bekliyor.
- [x] Migration script'i yazıldı: `sql/migration/FEAT-325559-max-attempt-per-number.sql` (idempotent guard'lı, kapasite ön kontrol sorgusu ve rollback/kill-switch notu dâhil). DBA'ya iletilecek adım `Faz 5 sonuçları → Deploy öncesi kalan işler` altında.
- [x] Mimari dokümanlar ve history güncellendi.
- [x] Final yanıtta kısa Test Özeti sunuldu.

**Plan durumu: `Faz 1-5 tamamlandı`.** Java tarafı doğrulanmış ve yeşil; kapanış tek blocker'a bağlı: `npm ci` sonrası AngularJS spec'lerinin koşulması.

## Riskler ve Geri Dönüş

Riskler analiz dosyasındaki R1-R11'dir. Faz bazlı geri dönüş:

| Faz | Geri dönüş |
|---|---|
| 2 | Additive; kolonlar nullable, alan boş → davranış değişmez. Geri alma = commit revert; DDL kolonları zararsız kalır. |
| 3 | **En riskli faz.** Feature-off regression testi yeşil kalmadığı sürece ilerlenmez. Üretimde sorun çıkarsa kampanyada `maxAttemptPerNumber` boşaltılarak davranış anında eski hâle döner — **kod deploy'u gerekmez.** Bu, feature'ın birincil kill-switch'idir. |
| 4 | Producer elemesi yalnız `maxAttemptPerNumber` dolu kampanyalarda çalışır; aynı kill-switch geçerli. D1 düzeltmesi feature'dan bağımsız olduğu için ayrı commit'te tutulur ve gerekirse tek başına geri alınır. |

Faz 3 ve 4 ayrı commit'lerde tutulur; `gbpd-commit-discipline` ile diff/secret/ilgisiz değişiklik kontrolü yapılır.

## Açık Sorular

Yok. Analizdeki A1-A4 kapandı (K14-K17), K18 uygulama detayı olarak eklendi.

## Tamamlanma Notu

**08.09.2026 — Faz 1-5 tamamlandı.** Java tarafı yeşil (293/293), dokümanlar güncel, migration script'i hazır. Kapanış `npm ci` sonrası AngularJS spec'lerinin koşulmasına bağlı.

Dokunulan üretim dosyaları (11): `Constants.java`, `Campaign.java`, `Call.java`, `CampaignFormDTO.java`, `CampaignDataTypeChecker.java`, `CampaignDbOperations.java`, `CampaignInDTO.java`, `CampaignInMapper.java`, `RetryStrategy.java`, `ReachRetryPerNumber.java`, `CallProducer.java`. Ek olarak 4 `ValidationMessages*`, 4 `localization_*`, `constants.js`, `campaignFormCtrl.js`, `campaignForm.html` ve yeni migration script'i.

Yeni test sınıfları (9): `RetryStrategySixStrategiesBaselineCharacterizationTest`, `RetryStrategySuccessfulCloseMatrixCharacterizationTest`, `CallHistoryColumnsLockstepCharacterizationTest`, `CallProducerFreshNumberSelectionCharacterizationTest`, `MaxAttemptPerNumberCounterTest`, `MaxAttemptPerNumberFeatureOffRegressionTest`, `MaxAttemptPerNumberSkipsExhaustedNumberTest`, `MaxAttemptPerNumberEarlyRecordTerminationTest`, `MaxAttemptPerNumberCloseReasonPersistenceTest`, `CampaignMaxAttemptPerNumberFieldContractTest`, `CampaignDataTypeCheckerCloseReasonBindingTest`, `CampaignDbOperationsClosedWithoutDialingSqlContractTest` ve `campaignFormMaxAttemptPerNumber.spec.js`. Genişletilen mevcut testler: `RetryDncExhaustionInfiniteLoopRegressionTest`, `CampaignInMapperTest`, `MockFactory`. **Hiçbir mevcut test silinmedi.**

Uygulama sırasında kendi kodunda bulunup giderilen üç hata: (1) `getDialedNumberSequence` boş-iz kısayolunun eski kayıtlarda aramaları eksik sayması, (2) `close_reason` yazımında `isNotEmpty` yerine `isNotBlank` gerekmesi, (3) analiz dosyasına gerçek müşteri telefon numarasının yazılmış olması (placeholder'a çevrildi).

Kapsam dışı bırakılan ve gerekçesi yazılan maddeler: B1 "+1" sızıntısı (K7), `insertIntoCampaignResultTable` INSERT yolu, `processAndQueueFetchedCalls` ayrı filtresi, `col_result_attempts` opsiyonelliğinin latent riski, FEAT-321997 script'indeki tablo adı hatası (D5).

> **Sonradan eklendi (08.09.2026):** Faz 1-5 kapandıktan sonra bağımsız kod incelemesi uçtan uca bir hata buldu (numara başına sayaç, iki katmanda aynı diziyi farklı anlamlarla okuyordu) ve bu hata **Faz 6**'da giderildi. Yukarıdaki Faz 1-5 içeriği tarihsel kayıt olarak korunmuştur; **güncel durum için `Faz 6` bölümüne bakılır.**

## Faz 6 — Bağımsız Kod İncelemesi Düzeltmesi (08.09.2026)

Faz 1-5 kapandıktan sonra `feature/325559-numara-basina-azami-arama-limiti` dalı üzerinde bağımsız bir kod incelemesi yapıldı ve **uçtan uca bir hata** bulundu. Bu faz o hatanın giderilmesidir; Faz 1-5 içeriği korunmuştur.

### Bulgu — aynı sayaç, iki katmanda farklı anlam

Numara başına limit motorun iki katmanında uygulanıyor ve iki katman `Call.getDialedNumberSequence()` dizisini **paylaşıyordu**. Ancak iki katman zaman ekseninin farklı noktalarında çalışır:

| Katman | Çalışma anı | İzin son girdisi |
|---|---|---|
| `RetryStrategy.createRetry` | hangup **SONRASI** | fiilen yapılmış arama |
| `CallProducer.resolveCalledNumber` | arama **ÖNCESİ** | henüz yapılmamış **plan** |

`RetryStrategy.updateRetryHistory` planı yazar (`retry_history` + `R` adımı + `retry_date`), `ManagerConnectionDecorator.manageRetries` hemen ardından persist eder. Satır producer'a tam bu yüzden (`retry_date`/`callback_date` dolu olduğu için) gelir. Dolayısıyla producer aynı diziyi okuduğunda **bekleyen planı yapılmış arama sayıyordu**; numaranın son hakkı yanıyor ve fiili limit `maxAttemptPerNumber - 1` oluyordu.

Ölçüm (yeni `MaxAttemptPerNumberEndToEndDialLoopTest` ile, düzeltme öncesi):

| Ayar | Beklenen arama | Fiilî arama |
|---|---|---|
| `maxAttemptPerNumber = 3` | 3 | 2 |
| `maxAttemptPerNumber = 40` | 40 | 36 |
| `maxAttemptPerNumber = 1` | 1 | **0** (numara hiç aranmıyor) |

Hata tek katmanlı testlerde görünmüyordu: her katman kendi içinde tutarlıydı, sapma yalnız producer → dial → strateji → persist **round-trip'inde** ortaya çıkıyor.

### Düzeltmeler

- **F1 — İki okumanın ayrıştırılması.** `Call.getCompletedDialSequence()` (`Call.java:865`) eklendi: arama öncesi okuma. Satırda bekleyen plan varsa (`hasPendingDialPlan()`, `Call.java:910` — `retry_date` veya `callback_date` dolu) izin son girdisi **sayılmaz**; üç geçmiş kolonu da boşsa (hiç işlem görmemiş kayıt) dizi **boş** döner (aksi halde `limit = 1` iken ilk kolon hiç aranmazdı). `CallProducer.perNumberDialCounts` (`CallProducer.java:761`) artık bu diziyi kullanır. `getDialedNumberSequence()` **değişmedi**; strateji katmanı hangup sonrası okumasını aynen sürdürür.
- **F2 — Limitli planın kaydı kapatması.** `CallProducer.fallBackToEligibleNumber` (`CallProducer.java:693`) eklendi: planlanan retry/callback numarası limitini doldurmuşsa kayıt **kapatılmaz**, kalan numaraların kotası kontrol edilip ilk uygun numaraya düşülür (kabul kriteri 3). Eski davranış `null` dönüp kaydı `setClosedWithoutDialing` ile kalıcı kapatıyor, diğer numaraların kalan hakkını hiç bakmadan yakıyordu.
- **F3 — Boş-iz kısayolunun eski kayıtları eksik saymasını gidermek.** `getDialedNumberSequence()` içindeki kısayol yalnız **üç** kolon da boşken uygulanır. Eski satırlarda (`call_process` boş, `retry_history` dolu) kısayol tek arama sayıp limitin hiç tetiklenmemesine yol açıyordu; o durum artık tutarsızlık sayılıp `retry_history` + fresh fallback'ine düşer.
- **F4 — K2 muafiyetinin tek yerde tutulması.** `Campaign.isPerNumberAttemptLimitActive(Integer, RETRY_STRATEGY)` (`Campaign.java:1224`) eklendi; `RetryStrategy.isPerNumberLimitActive` (`RetryStrategy.java:249`) ve `CallProducer.isPerNumberLimitActive` (`CallProducer.java:746`) aynı metodu çağırır. Producer daha önce yalnız `getMaxAttemptPerNumber()`'a bakıyordu: strateji 1 kampanyasında UI freeze DB değerini **temizlemediği** için alan dolu kalabiliyor ve producer, strateji muaf olmasına rağmen limiti uygulayıp kaydı kapatıyordu.
- **F5 — Kapanan kaydın bekleyen plan taşımaması.** `CampaignDbOperations.setClosedWithoutDialing` (`CampaignDbOperations.java:1666-1672`) kapanışta `retry_date` ve `retry_number` kolonlarını (binding varsa) `NULL`'lar. Aksi halde müşteri kaydı `complete_date`/`is_fetched` boşaltarak yeniden aktive ettiğinde geçmiş tarihli `retry_date` duran satır FRESH değil RETRY fetch'inden geçiyor, yeni eklenen numara yerine eski (limitli) plan deneniyor ve CDR outdialing tipi yanlış sınıflanıyordu.

Ek olarak `Call.rawDataHasValue` (`Call.java:915`) `isNotBlank` ile sertleştirildi: boş string taşıyan bir tarih kolonu "bekleyen plan var" gibi okunmasın.

### Uygulama sırasında bulunup giderilen ikincil hata

F2 düzeltmesi tek başına **fazladan arama** üretti (`maxAttemptPerNumber = 3` iken `number_2`'ye 4 arama). Neden: plan yönlendirildiğinde iz hâlâ **eski planlanan** numarayı gösteriyor; o numaranın sayacı şişiyor, fiilen aranan numaranın araması ise hiç sayılmıyordu.

Çözüm `Call.redirectPendingDialTo(String, boolean)` (`Call.java:891`): iz bir **dial log**'u olduğu için bekleyen geçmiş kaydının son girdisi fiilen aranan numarayla değiştirilir. Yazım **zamanı** ve semantiği değişmez, yalnız yanlış kalan son girdi düzeltilir. Kayıt düzeltilemiyorsa (bozuk satır, boş geçmiş) metot `false` döner ve producer **arama yapmaz**; yanlış sayımla limit aşma riski alınmaz.

### Yeniden türetilen fixture'lar ve nedenleri

İnceleme, mevcut `+1` sapmasını "beklenen davranış" olarak kodlayan fixture'ların **silinmemesini**, senaryo niyeti korunarak gerçek runtime durumuna göre yeniden türetilmesini ve her değişikliğin nedeninin bu plana yazılmasını istedi. Hiçbir test silinmedi.

| Test | Eski fixture | Yeni fixture | Neden |
|---|---|---|---|
| `CallProducerFreshNumberSelectionCharacterizationTest.retryNumberAtItsLimitIsNotDialable` → `…NotDialableWhenNoOtherNumberExists` | `call_process="F;R;R"`, 2 retry girdisi, `retry_date` **yok** | `call_process="F;R;R;R"`, 3 retry girdisi, `retry_date` **dolu**, `colResultRetryDate` bağlı | `retry_date` taşımayan satır gerçek bir bekleyen-retry satırı **değildir**. Eski hâl "3 tamamlanmış" gibi okunuyordu; arama öncesi doğru okuma "2 tamamlanmış + 1 bekleyen"dir. Limitin gerçekten dolu olduğu durum 3 tamamlanmış + 1 bekleyen plandır. Ad, "kalan numara yoksa" koşulunu da açıkça söyleyecek şekilde genişletildi. |
| *(yeni)* `…retryNumberWithPendingPlanStillHasItsFinalDial` | — | `call_process="F;R;R"`, 2 retry girdisi, `retry_date` dolu, limit 3 | Aynı fixture'ın limitin **henüz dolmadığı** hâli. Düzeltme öncesi bu test `null` dönerdi (numaranın son hakkı yanıyordu); RED→GREEN kanıtı burada durur. |
| `…callbackNumberAtItsLimitIsNotDialable` → `…callbackNumberAtItsLimitFallsBackToRemainingNumber` | `call_process="F;R;R"`, `cb_history` **yok**, `callback_date` **yok**; iddia: "başka numaraya DÜŞÜLMEZ, kayıt kapanır" | `call_process="F;R;R;CB"`, `cb_history="number_1"`, `callback_date` dolu, `colResultCbHistory` + `colResultCallbackDate` bağlı; iddia: `number_2`'ye düşülür | İki ayrı sorun: (a) bekleyen callback'in gerçek izi `CB` adımı + `cb_history` girdisi + `callback_date`'tir (`Call.addCallbackHistoryEntry` bunları kilitli yazar), eski fixture hiçbirini taşımıyordu; (b) iddianın kendisi **F2 ile değişti** — kabul kriteri 3 limitli numaranın "pas geçilmesini", kaydın kapanmamasını istiyor. |
| *(yeni)* `…callbackNumberAtItsLimitWithNoAlternativeIsNotDialable` | — | aynı gerçekçi bekleyen-callback izi, **tek** telefon kolonu | F2'nin sınırı: kalan numara yoksa davranış eskisi gibi kalır (aranamaz → kayıt kapanır, kriter 7). Böylece F2 "limit hiç uygulanmıyor" hâline kaymaz. |
| `…callbackRecordAtLimitIsClosedWithCallbackReason` | `call_process="F;R"`, `cb_history` yok, `callback_date` yok | `call_process="F;R;CB"`, `cb_history="number_1"`, `callback_date` dolu | Aynı (a) gerekçesi. Eski fixture'ın "2 tamamlanmış" görünümü aslında "1 tamamlanmış + 1 bekleyen"di; test doğru sonucu **yanlış nedenle** üretiyordu. Kapanış nedeni iddiası (`CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK`) korundu. |
| `MaxAttemptPerNumberCounterTest.plannedButNotYetDialedEntryIsCountedConservatively` → `postHangupSequenceCountsEveryTrailEntry` | Aynı fixture; iddia "muhafazakârlık: bir fazla sayılır" | Aynı fixture; iddia "hangup sonrası okumada izdeki her girdi yapılmış aramadır" | Sonuç aynı, **yorum yanlıştı**. "Bir fazla sayma" masum bir muhafazakârlık değil, arama öncesi okumada numaranın son hakkını yakan hatanın kaynağıydı. İnceleme adın ve iddianın yeni anlam ayrımına göre güncellenmesini istedi. |
| *(yeni)* `…completedSequenceExcludesPendingPlanEntry`, `…completedSequenceEqualsFullTrailWhenNoPendingPlan`, `…untouchedRecordHasNoCompletedDialYet` | — | `getCompletedDialSequence()` üç hâli | F1'in eklediği ikinci okumayı doğrudan kilitler: bekleyen plan düşülür, plan yoksa iki okuma eşittir, hiç işlem görmemiş kayıtta sayım sıfırdır. |

**Strateji tarafı fixture'ları hiç değişmedi.** F4'ün paylaşılan politikası bilinçli olarak **`static`** yazıldı (instance `@Transient` metot değil): Mockito ile mock'lanmış `Campaign` nesneleri instance metodunda varsayılan `false` döndürüp 9 strateji testini kırıyordu; `static` metot iki değeri parametre aldığı için mock'ların stub'lanmış getter'ları kararı sürmeye devam ediyor.

### Yeni test sınıfı

`tests/dialer/MaxAttemptPerNumberEndToEndDialLoopTest` (11 test) — tek katmanlı testlerin göremediği sapmayı yakalayan **round-trip** testi. Tek mutable `JSONObject` satırı üzerinde producer → dial → strateji → persist döngüsünü çevirir; `persist` adımı `call_process`, `retry_history`, `cb_history`, `retry_number`, `retry_date`, `callback_*` kolonlarını `DBEngine` davranışını modelleyecek şekilde (null'ları satırdan **silerek**) geri yazar ve numara başına **fiilî** arama sayısını sayar. `LOOP_FUSE = 500` ile sonsuz döngüye karşı korunur.

`featureOffLeavesLoopBoundedOnlyByOverallLimit` testinde kendi beklentim yanlıştı (5 yazmıştım, gerçek 6): doğru değer `retryOveralAttempt + 1`, çünkü fresh arama `retry_history`'de tutulmaz ("+1 is for first call"). Bu mevcut ve **doğru** davranıştır; test javadoc'una yazıldı.

### Korunan invariantlar (incelemenin çizdiği sınırlar)

- [x] `retry_history` / `call_process` / `cb_history` **yazım zamanı ve semantiği değişmedi**; F1 yalnız ayrı bir OKUMA ekledi, `redirectPendingDialTo` yalnız yanlış kalan son girdiyi düzeltir.
- [x] `advanceRetryNumberPastIneligible` sınırlı-yürüyüş sigortası kaldırılmadı; `RetryDncExhaustionInfiniteLoopRegressionTest` silinmedi (8/8 yeşil).
- [x] `createRetry` kontrol sırası (başarılı kapanış → genel limit → numara başına) ve `isCallAnswered()`'ın ilk çağrılması (RetryRulesCycle yan etkisi) korundu.
- [x] Yeni TRC id / CDR satırı yok; `telephony_rc`, `user_id`, `user_result_code` hiçbir yolda ezilmiyor.
- [x] K2 (strateji 1 muafiyeti) kaldırılmadı; **tutarlı** hâle getirildi (F4).
- [x] Mevcut hiçbir test silinmedi; strateji testlerinin fixture'larına dokunulmadı.
- [x] Yeni static **mutable** state yok (F4'ün `static` metodu saf fonksiyon).
- [x] Hot path: yeni DB query / blocking çağrı / geniş `synchronized` blok yok. `getCompletedDialSequence()` mevcut dizinin son elemanını düşürür (`subList` kopyası, kayıt başına bir kez); `hasPendingDialPlan()` iki `rawData` okumasıdır.

### Doğrulama

```text
mvn -o ... -Dtest=CallProducerFreshNumberSelectionCharacterizationTest,MaxAttemptPerNumberCounterTest test
mvn -o ... -Dtest=MaxAttemptPerNumber*Test,RetryStrategy*CharacterizationTest,RetryDncExhaustionInfiniteLoopRegressionTest test
```

| Sınıf | Sonuç |
|---|---|
| `CallProducerFreshNumberSelectionCharacterizationTest` | 24/24 (22 → +2 yeni) |
| `MaxAttemptPerNumberCounterTest` | 18/18 (15 → +3 yeni) |
| `MaxAttemptPerNumberEndToEndDialLoopTest` | 11/11 (yeni sınıf) |
| `MaxAttemptPerNumberEarlyRecordTerminationTest` | 9/9 |
| `MaxAttemptPerNumberSkipsExhaustedNumberTest` | 6/6 |
| `MaxAttemptPerNumberFeatureOffRegressionTest` | 9/9 |
| `MaxAttemptPerNumberCloseReasonPersistenceTest` | 8/8 |
| `RetryStrategySixStrategiesBaselineCharacterizationTest` | 18/18 (değişmedi) |
| `RetryStrategySuccessfulCloseMatrixCharacterizationTest` | 7/7 (değişmedi) |
| `RetryDncExhaustionInfiniteLoopRegressionTest` | 8/8 |
| **Toplam (hedefli set)** | **94/94, BUILD SUCCESS** |

Geniş kapanış koşusu (28 sınıf: yukarıdakiler + `RetryLimitTest`, `RetryStrategyFactoryTest`,
`RetryStrategyTemplateTest`, `RetryRulesCycleTest`, `ReachRetryPerNumberTest`,
`ReachRetrySingleNumberTest`, `ReachRetryPerNumberDncRetryCharacterizationTest`,
`RoundRobinNextCycleTest`, `RoundRobinNextNumberTest`, `RoundRobinPerCycleTest`,
`CallHistoryColumnsLockstepCharacterizationTest`, `CallProducerTest`, `CallOriginatorTest`,
`CampaignMaxAttemptPerNumberFieldContractTest`, `CampaignDataTypeCheckerCloseReasonBindingTest`,
`CampaignDbOperationsClosedWithoutDialingSqlContractTest`, `CampaignInMapperTest`,
`CampaignHistoryColumnMappingRegressionTest`):

```text
mvn -o ... -Dtest=MaxAttemptPerNumber*Test,RetryStrategy*Test,RetryLimitTest,RetryRulesCycle*Test,
                  ReachRetry*Test,RoundRobin*Test,RetryDncExhaustionInfiniteLoopRegressionTest,
                  CallHistoryColumnsLockstepCharacterizationTest,CallProducer*Test,CallOriginatorTest,
                  Campaign*ContractTest,CampaignInMapperTest,
                  CampaignHistoryColumnMappingRegressionTest test
```

→ **258/258 PASS, BUILD SUCCESS.** Mevcut strateji testlerinin hiçbirine dokunulmadı ve hepsi
yeşil kaldı.

`CampaignResultJobServiceTest`'teki 4 başarısızlık bu çalışmadan **bağımsızdır**; Faz 2'de pristine `HEAD` (03c0f7cc) worktree'sinde birebir aynı 4 başarısızlık üretilerek pre-existing olduğu kanıtlandı.

`npm.cmd run test:angular` bu fazda da **koşulamadı** (aynı `node_modules` blocker'ı). Faz 6 yalnız Java tarafını değiştirdiği için frontend spec'lerinin sonucu değişmez; 12 spec hâlâ `npm ci` bekliyor.

### Commit ayrımı (`gbpd-commit-discipline`)

İnceleme F1/F2 ile F4/F5'in ayrı commit'lerde tutulmasını istedi. Ayrım:

| Commit | Kapsam | Dosyalar |
|---|---|---|
| 1 — sayaç anlamı | F1, F2, F3 + `redirectPendingDialTo` | `Call.java`, `CallProducer.java` (`resolveCalledNumber`, `fallBackToEligibleNumber`, `perNumberDialCounts`), `MaxAttemptPerNumberEndToEndDialLoopTest`, `MaxAttemptPerNumberCounterTest`, `CallProducerFreshNumberSelectionCharacterizationTest` |
| 2 — muafiyet ve kapanış | F4, F5 | `Campaign.java`, `RetryStrategy.java`, `CallProducer.isPerNumberLimitActive`, `CampaignDbOperations.java`, `CampaignDbOperationsClosedWithoutDialingSqlContractTest` |

**Commit atılmadı.** Kullanıcı manuel kontrolü tamamlayıp haber verecek.

### Review notu — `CallProducer.java` diff'i

> ⚠️ **BU BÖLÜM YANLIŞ TEŞHİS İÇERİYORDU — düzeltmesi `Faz 7 / P0`'dadır.** Aşağıdaki metin
> tarihsel kayıt olarak bırakıldı; **geçerli teşhis ve çözüm için `Faz 7 / P0` bölümü okunmalıdır.**
> Kısaca: teşhisin ilk yarısı (HEAD blob'unun CRLF olması) doğru, sonucu (*"diff patlaması
> kaçınılmaz, bu çalışmayla ilgisi yok"*) **yanlıştı** — sapmayı bu çalışma üretti (çalışma kopyası
> CRLF'den LF'ye dönmüştü) ve düzeltilebilirdi. `Faz 7 / P0`'da düzeltildi; diff artık
> `--ignore-cr-at-eol` bayrağı olmadan **198/15**.

`CallProducer.java`'nın **HEAD'deki blob'u CRLF ile saklanmış** (`git show HEAD:...CallProducer.java | od -c` → `\r\n`), oysa deponun diğer Java blob'ları LF. Sistem gitconfig'inde `core.autocrlf=true` olduğu için bu dosyaya yapılan **herhangi** bir değişiklik diff'te tüm satırları yeniden yazar (`1158/995`). ~~Bu depoya ait, önceden var olan bir durumdur; bu çalışmayla ilgisi yoktur~~ ve derlemeyi etkilemez.

Gerçek değişikliği görmek için:

```text
git diff --ignore-cr-at-eol -- gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallProducer.java
```

→ **+178 / -15**. ~~Dosyanın kendisi baştan sona CRLF'tir (1158/1158 satır)~~, `CRCRLF` bozulması yoktur (`od -c` ile komşu dosyalarla karşılaştırıldı). Diğer 30 değişen dosyanın ham diff'i ile `--ignore-cr-at-eol` diff'i **birebir aynıdır**.

*(Üzeri çizili iki iddia ölçümle çürütüldü; bkz. `Faz 7 / P0`.)*

### Faz 6 Tamamlanma Notu

**08.09.2026 — Faz 6 tamamlandı.** Uçtan uca hata giderildi, hedefli set 94/94 yeşil, Faz 1 karakterizasyon sınıfları ve strateji testleri **değişmeden** yeşil kaldı. Değişen üretim dosyaları (5): `Call.java`, `Campaign.java`, `CallProducer.java`, `RetryStrategy.java`, `CampaignDbOperations.java`. Yeni test sınıfı (1): `MaxAttemptPerNumberEndToEndDialLoopTest`. Yeniden türetilen fixture: 4 (+5 yeni test). Silinen test: **0**.

Kapanış hâlâ tek blocker'a bağlı: `npm ci` sonrası AngularJS spec'lerinin koşulması.

## Faz 7 — İkinci İnceleme Turu (08.09.2026)

Faz 6 kabul edildikten sonra yapılan ikinci bağımsız inceleme, bir commit blocker (P0), iki
deploy blocker (P1), üç doğruluk hatası (P2) ve dört tutarlılık maddesi (P3) bildirdi. Bu faz
onların uygulanmasıdır; Faz 1-6 içeriği korunmuştur.

### P0 — `CallProducer.java` satır sonları (commit blocker)

**Faz 6'daki teşhisim yanlıştı ve ölçümle çürütüldü.** Faz 6'nın `Review notu` bölümü sapmayı
"depoya ait, önceden var olan bir durum; bu çalışmayla ilgisi yok" diye kapatmıştı. Doğrusu:

| İddia | Durum | Ölçüm |
|---|---|---|
| `CallProducer.java`'nın HEAD blob'u CRLF, deponun diğer Java blob'ları LF | ✅ **doğru** | `git show HEAD:…CallProducer.java \| tr -cd '\r' \| wc -c` → **995**; `Call.java` → **0**, `RetryStrategy.java` → **0** |
| "Dosyanın kendisi baştan sona CRLF'tir (1158/1158)" | ❌ **yanlış** | `tr -cd '\r' < …CallProducer.java \| wc -c` → **0** (dosya LF'ye dönmüştü) |
| "Diff patlaması kaçınılmaz, bu çalışmayla ilgisi yok" | ❌ **yanlış** | Faz 1-5'te **aynı dosya, aynı HEAD blob'u** ile diff **146 satır** ve temizdi; git o zaman `LF will be replaced by CRLF` uyarısı basmıyordu |

Kök neden ayrımı:

- **Depo anomalisi (önceden var, bu işin dışı):** `CallProducer.java`'nın commit'li blob'u CRLF
  saklanmış. `core.autocrlf=true` iken git, index/HEAD sürümünde CRLF gören dosyalar için
  CRLF→LF normalizasyonunu **devre dışı bırakır**. Bu yüzden **CRLF** bir çalışma kopyası bu
  blob'a karşı temiz diff verir.
- **Bu turda oluşan sapma (bu işin sorumluluğu):** çalışma kopyası LF'ye dönmüştü (Faz 3-4'te
  `sed -i` kullanılmıştı). LF kopya CRLF blob'a karşı **her satırı** farklı gösteriyordu →
  `1158/995`.

Hatanın nedeni ölçüm aracının yanlış seçilmesiydi: doğrulamada `grep -c $'\r$'` kullanılmıştı ve
Git Bash'te `grep` metin modunda `\r`'ı ayıklayıp `$` ile eşleştirdiği için **yanlış pozitif**
verdi (1158/1158). `od -c` ve `tr -cd '\r'` doğru aracı; sonraki bütün kontroller bunlarla
yapıldı.

**Düzeltme:** dosya yalnız satır sonları CRLF'e çevrildi. İçerik doğrulaması: `tr -d '\r'` alınmış
yeni dosya, eski dosyayla `cmp` ile **birebir aynı** (tek fark eklenen CR baytları). Sonuç:

```text
git diff --stat -- gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallProducer.java
 …/powerdialer/engine/dialer/CallProducer.java | 213 +++++++++++++++++++--
 1 file changed, 198 insertions(+), 15 deletions(-)
```

`--ignore-cr-at-eol` bayrağına **artık gerek yok** ve `git diff` bu dosya için LF/CRLF uyarısı
**basmıyor**. Değişen 31 dosyanın tamamı tarandı: hiçbirinde ham diff ile `--ignore-cr-at-eol`
diff'i arasında fark kalmadı.

**Araç seçimi (tekrar düşmemek için):** `Edit` aracının CRLF'i **koruduğu** ayrı bir sonda
dosyasıyla ölçülerek doğrulandı (3 satırlık CRLF dosyaya 2 satır eklendi → 5 satır / 5 CR, hepsi
`\r\n`). Bu dosyada bundan sonra yalnız satır bazlı edit kullanılır; `sed -i` kullanılmaz. Her
edit turundan sonra `tr -cd '\r' | wc -c` ile satır sonu sayısı doğrulanır.

**Depo anomalisinin kendisi bu işin kapsamı dışıdır.** Temizlenmek istenirse (blob'u LF'ye
normalize etmek) ayrı ve tek başına bir commit olmalıdır; feature commit'lerinin içine
karıştırılmamalıdır.

### P1.1 — Otomatik binding tip kontrolü yapmıyordu (deploy blocker)

`CampaignDataTypeChecker.applyDefaultColumnMapping` yalnız kolonun **varlığına** bakıp binding
atıyordu (`columnsToTypes.containsKey`). Hemen ardından çalışan `checkColumn` tip uyuşmazlığında
`campaign.start.wrong.types` fırlatır. Sonuç: müşterinin result/CRM tablosunda `close_reason`
adında ama farklı tipte (INT/DATE) bir kolon varsa, **operatör hiçbir seçim yapmadığı hâlde** o
kampanya bu sürümden sonra hiç başlamaz. `close_reason` müşteri şemalarında olası bir addır.

**Düzeltme:** binding yalnız mevcut kolonun tipi o anahtarın `RESULT_FIELDS_ALLOWED_TYPES`
kümesindeyse atanır; değilse binding **boş kalır**, kampanya normal başlar ve yalnız kapanış nedeni
yazılmaz. Uygun tip yokken WARN log'u basılır.

**Kapsam:** guard `cb_history` ve `call_process` için de uygulandı — aynı yardımcı onları da
besliyor ve bugün de yanlış tipli böyle bir kolon kampanyayı başlatılamaz hâle getirebiliyordu.
Zorunlu minimum `CLOSE_REASON` idi; üçe birden uygulamak aynı sınıf hatayı tek noktada kapatır.

**Operatörün elle seçtiği binding'in doğrulaması zayıflatılmadı:** `checkColumn` aynen duruyor.
Orada sessiz yok sayma operatörü yanıltırdı. (Frontend tarafı bu ayrımı zaten yapıyordu:
`setDefaultValueForResultColumn` tip kontrolü içeriyor — backend artık aynı davranışta.)

Testler (`CampaignDataTypeCheckerCloseReasonBindingTest`, 7 → **13**):
uygun tip bağlanır / yanlış tipli `close_reason`-`cb_history`-`call_process` bağlanmaz **ve start
reddedilmez** / elle seçilmiş yanlış tip **hâlâ reddedilir** / yanlış tipli varsayılan kolon başka
bir kolona düşmez / parse edilemeyen tip bağlanmaz. RED aşamasında dördü de tam beklenen hatayla
(`campaign.start.wrong.types`) kırıldı.

### P1.2 — Migration 2.2 "opsiyonel" diyor ama koşulsuz çalışıyordu (deploy blocker)

Bölüm 2.2 yorumu `OPSIYONEL … atlanabilir` diyor ama blok `EXEC sp_executesql @sql;` ile koşulsuz
çalışıyor ve kampanya DB'sindeki `complete_date` kolonu olan **tüm** tablolara `call_process` +
`cb_history` ekliyordu. Kolonlar eklendiği anda `applyDefaultHistoryColumnMappings` o DB'deki
**tüm** kampanyalarda binding'i otomatik atar ve `insertCallResultToDB` bu kolonlara yazmaya
başlar — yani **bu işin kapsamı dışındaki** kampanyaların result yazımı da değişirdi. DBA dosyayı
bütün olarak çalıştıracağı için "atlanabilir" pratikte geçersizdi.

**Seçilen çözüm: (b) bloğu çalıştırmayan hâle getirmek** (2.4 deseni: üretir, `EXEC` etmez).
Gerekçe: (1) kullanıcı DBA'lere **iki** script vermeyi planlıyor, üçüncü bir dosya bu isteğe ters;
(2) 2.4 aynı dosyada bu deseni zaten kuruyor, tutarlı; (3) kapsam dışı kampanyaların yazımını
değiştiren bir karar bir insanın bilinçli onayıyla verilmeli — script artık ALTER ifadelerini
ekrana basıyor ve "eklenmesine karar verilirse bu iş kapsamı dışındaki kampanyaların result
yazımının da değişeceği" uyarısını yazıyor.

**Tablo keşfinin sınırı yazıldı.** Keşif `complete_date` kolonunun varlığına dayanıyor; kampanya
DB'sinde bu ada sahip alakasız bir tablo varsa (arşiv/staging/rapor) ona da kolon eklenir. Sınır
2.1 ve 2.5 yorumlarına açıkça yazıldı: kolon nullable ve uygulama yalnız kampanyaya **bağlı**
tabloya yazdığı için zararsız, ama beklenmeyen tabloda kolon görmek kafa karıştırır; 2.5 çıktısı
2.1'in kapsamını da gösterir. **Kapsam kendi başına daraltılmadı** — result tablosu adları kampanya
bazında serbest ve ana DB bu script'in koştuğu DB'den erişilebilir olmayabilir; daraltma
gerekiyorsa DBA/geliştirici mutabakatıyla eklenmeli. Öneri olarak iki yol yazıldı: tablo adı
prefix'i veya `pd_campaign.db_result_table` listesiyle eşleme.

Ayrıca 1.2 yorumuna P1.1'in tip guard'ı işlendi (otomatik atama yalnız tip uygunsa yapılır).

### P2.1 — `close_reason` yanlış etiketleniyordu

**(a) Strateji katmanı.** `noEligibleNumberLeft()` DNC'yi de "uygun değil" saydığı için, özellik
açıkken tüm numaraları DNC'de olan kayıt bu daldan kapanıp `CLOSE_REASON_NUMBER_LIMIT_REACHED`
yazıyordu. Kapanışın **kendisi** doğru (strateji 0 ve 4 için bu dal DNC tükenmesine karşı yeni ve
doğru bir kapanış yolu; o stratejilerde eskiden guard yoktu), **etiketi** yanlıştı.

**(b) Producer katmanı.** `resolveUncallableReason` aday kontrolünü `getNumbersInRawData()` ile
yapıyordu; o metot `isNotEmpty` kullandığı için yalnızca boşluktan oluşan bir kolonu da "dolu
numara" sayıyor ve gerçekte **geçerli numarası olmayan** kayıt "limit doldu" etiketi alıyordu.
Producer o kolonu zaten atlıyor — etiket ile gerçeklik çelişiyordu.

**Karar: yeni sabit EKLENMEDİ, mevcut üç metnin değeri DEĞİŞTİRİLMEDİ.** DNC kaynaklı kapanışta
neden **boş bırakılır**. Gerekçe: (1) yeni bir müşteri-görünür metin iş birimiyle mutabık değil ve
lokalizasyon kararı (K14) hâlâ açık; (2) boş bırakmak DNC kapanışlarında bugünkü (özellik-kapalı)
davranışı birebir korur — o yolda da bu kolona hiçbir şey yazılmıyor. Kriter tekti: rapor
yanıltmasın.

Uygulama: `applyExhaustedCloseReason` artık `anyCandidateReachedItsLimit(...)` sorusunu soruyor.
Adaylardan hiçbiri kendi limitini doldurmadıysa kapanış limit kaynaklı değildir (kalan tek sebep
DNC'dir) ve neden yazılmaz. Callback ayrımı da bu kontrolün **içine** alındı; DNC kaynaklı bir
callback kapanışında da metin yazılmaz. Producer tarafı `getLimitCandidateNumbers()` kullanıyor.

Testler: yeni `MaxAttemptPerNumberCloseReasonAccuracyTest` (4) — DNC kapanışı metin yazmaz / limit
kapanışı metni aynen yazar / karışık durumda (biri DNC, biri limitli) limit metni yazılır /
özellik kapalıyken hiçbir şey yazılmaz. Producer tarafı
`CallProducerFreshNumberSelectionCharacterizationTest` içinde iki test.

### P2.2 — Kolon dışı callback numarası, kolonlar tükendiğinde düşüyordu

`createRetry` içinde `noEligibleNumberLeft()` callback dalından **önce** geliyor ve adaylar yalnız
telefon **kolonlarındaki** numaralardı. Agent'ın elle girdiği, kolonlarda olmayan bir callback
numarası limitin **altında** olsa bile, kolonların tümü limitliyse kayıt kapanıyordu — K16 ile
çelişiyor ("kolon dışı callback numarası kendi değeriyle sayılır") ve müşterinin **kendi**
talebiyle oluşan randevunun düşmesi over-dialing'den maliyetli.

**Düzeltme:** bekleyen callback numarası da aday kümesine dahil edildi (kendi sayacıyla);
`createRetry` kontrol **sırası değişmedi** (SINIRLAR gereği). Aday kümesi `Call` üzerinde tek
noktada durur: `getLimitCandidateNumbers()`.

Testler: yeni `MaxAttemptPerNumberOffColumnCallbackTest` (7) — kolon dışı callback limit altında →
kayıt kapanmaz ve o numara planlanır / limit dolu → callback nedeniyle kapanır (kriter 7) / aday
kümesinde yer alır / bekleyen callback yoksa küme yalnız kolonlardır / callback bir kolonla aynıysa
tekrarlanmaz / kolonlar limitli ve callback yoksa kayıt **hâlâ** kapanır (erken kapatma
zayıflatılmadı) / özellik kapalıyken dal değişmedi.

### P2.3 — Trim ve "boş" kuralı uyumsuzluğu limiti sessizce devre dışı bırakıyordu

Sayım anahtarları **ham** kolon değerinden geliyordu (`getFirstDialedNumber`, `retry_history`,
`cb_history`), producer sorgusu ise **trimlenmiş** değerden (`trimPhoneNumber`). Veride baştaki
veya sondaki boşluk varsa harita ıskalanıyor, sayaç 0 kabul ediliyor ve limit **hiç
uygulanmıyordu**. Ayrıca `getNumbersInRawData` `isNotEmpty` kullandığı için yalnızca boşluktan
oluşan bir kolon "dolu numara" sayılıyor ve `noEligibleNumberLeft` sonsuza kadar `false` kalıyordu
— erken kapatma o kayıtta hiç tetiklenmiyordu.

**Düzeltme:** tek normalizasyon kuralı `Call.normalizeNumberForLimit(String)` (trim; boş sonuç =
numara yok). Uygulandığı yerler — hem **harita anahtarları** hem **sorgular**:

| Yer | Ne değişti |
|---|---|
| `RetryStrategy.getDialCountsByNumber()` | anahtarlar normalize |
| `RetryStrategy.isNumberAttemptLimitReached(String)` | sorgu normalize |
| `RetryStrategy.getCandidateNumbers()` | `getLimitCandidateNumbers()`'a geçti |
| `CallProducer.perNumberDialCounts(Call)` | anahtarlar normalize |
| `CallProducer.isNumberWithinLimit(...)` | sorgu normalize |
| `CallProducer.resolveUncallableReason(Call)` | `getLimitCandidateNumbers()`'a geçti |

**Kolonlara YAZILAN değer değişmedi** (geçmiş semantiği korunur); normalizasyon yalnız
karşılaştırma/sayım içindir. Aynı numaranın iki kolonda bulunmasının tek sayılması (03.08.2026
duplicate-key semantiği) aday kümesinin tekilliğiyle korunur.
`ReachRetryPerNumber.isRetryLimitForNumberReached` (strateji 1, K2 muaf) **kapsam dışı** —
dokunulmadı.

**`getNumbersInRawData` / `getNumberColumnsInRawData` sözleşmeleri bilinçli olarak
DEĞİŞTİRİLMEDİ.** O metotlar özellik-kapalı yollarda (`anyNumberPresentAfterDNC`) ve
`advanceRetryNumberPastIneligible` sigortasının yürüyüş sınırında kullanılıyor; daraltmak
özellik kapalı kampanyaların davranışını değiştirirdi (SINIRLAR: feature-off birebir aynı kalır).
Normalizasyon bu yüzden **özellik-kapılı** aday kümesinde durur. Sözleşmenin değişmediği ayrıca
test edilir.

> **Kalan sınır (kapsam dışı, bilinçli):** boşluklu bir kolon değerinde limit artık uygulanır ama
> numara halkası bir sonraki numaraya **ilerleyemez**, kayıt kapanır. Sebep bu işin dışında:
> `Call.findNextNumberToCall` halkadaki konumu **ham eşitlikle** arıyor
> (`"number_1".equals("  number_1  ")` → false), yani boşluklu veride halka yürüyüşü bugün de
> kırık ve bu **özellik-kapalı tüm stratejileri** etkiliyor. O metodu normalize etmek feature-off
> davranışını değiştireceği için burada yapılmadı. Önemli olan yön korunuyor: **over-dialing yok.**
> Ayrı ve dar kapsamlı bir iş olarak ele alınmalı; iki test bu sınırı ve kök nedenini kayda
> geçiriyor (`paddedColumnValueStillCountsTowardItsLimit` +
> `unpaddedEquivalentAdvancesToNextNumberInstead`).

Testler: yeni `MaxAttemptPerNumberNormalizationTest` (11) + producer tarafında 3 test.

### P3.1 — `close_reason` binding'i UI akışlarında tamamlandı

Faz 2'de yalnız `setDefaultValuesForUndefinedResultTableColumns` güncellenmişti. Kolon backend'de
oluşuyor ve binding kampanya start'ında otomatik atandığı için **işlevsel kayıp yoktu**, ama
operatör sihirbazda binding'i boş görüyor ve kolonu dropdown'da bulamıyordu. Dördü tamamlandı:

| Akış | Dosya | Ne eklendi |
|---|---|---|
| result tablosu seçildiğinde default binding | `campaignFormCtrl.js` | `setDefaultValueForResultColumn('close_reason')` |
| tanımsız result kolonları (Faz 2) | `campaignFormCtrl.js` | — (zaten vardı) |
| kaynak tabloya result kolonları | `campaignFormCtrl.js` | binding + `callResultColumnsToCreate` picklist |
| yeni result tablosu oluşturma | `dbTableFormCtrl.js` | binding + `addColumnToCreate` picklist |

**Test kararı:** karma/Jasmine spec'i `node_modules` blocker'ı nedeniyle koşulamıyor. Gerçek
controller'ı ($scope, $q, dbTableService, $modalInstance) instantiate eden bir harness'i
**koşturamadığım hâlde tahminle yazmak, doğrulanmamış test eklemek olurdu**; bu yüzden spec
dosyasına yapılacak işin tarifi ve gerekçesi yazıldı, doğrulama offline **koşulabilen** bir
sözleşme testine taşındı: yeni `tests/angular/campaign/CampaignCloseReasonBindingUiContractTest`
(6 test, repodaki `tests.angular.*` deseninin aynısı; JS/HTML'i metin olarak doğrular). `npm ci`
sonrasında yapılacak iş: dört akış için davranışsal spec yazmak.

### P3.2 — `redirectPendingDialTo` liste seçimi ize dayandırıldı

Liste seçimi çağıranın `callbackNumber != null` bilgisinden geliyordu ve izin son adımına
bakmıyordu; `callback_number` dolu ama bekleyen plan bir RETRY olan satırda `cb_history`'nin son
elemanı düzeltilirdi. Pratikte oluşmuyor (`insertCallResultToDB`, `callback_date` null'sa
`callback_number`'ı da NULL'lar) ve fail-safe yön "arama yapma" idi — ama varsayım artık kodda
yazılı: seçim `call_process` izinin **son adımından** türetiliyor (`CB` → `cb_history`, `R` →
`retry_history`); iz yoksa veya son adım bir arama adımı değilse metot `false` döner ve arama
yapılmaz. İmza `redirectPendingDialTo(String)` oldu; çağıran artık bayrak geçirmiyor.

### P3.3 — Kapanışta callback alanlarının korunması (kod değişikliği YOK)

F5 kapanışta `retry_date` ve `retry_number`'ı NULL'lıyor, `callback_date` / `callback_number`'a
**dokunmuyor**. Asimetri bilinçlidir ve gerekçesi şudur:

- `retry_date` **makine tarafından üretilmiş zamanlama durumudur**. Geçmiş tarihli kalırsa,
  yeniden aktive edilen kayıt FRESH değil RETRY fetch'inden geçer, yeni eklenen numara yerine eski
  (limitli) plan denenir ve CDR outdialing tipi yanlış sınıflanır. Atılması güvenli.
- `callback_date` / `callback_number` **müşterinin kendi talebidir** (randevu). Silmek, bizim
  ürettiğimiz bir zamanlamayı değil müşteri taahhüdünü atmak olur. Kampanya yöneticisi limiti
  yükseltip kaydı yeniden aktive ederse randevunun durması doğrudur.

**Etkisi:** yeniden açılan böyle bir kayıt CALLBACK olarak sınıflanır. Bu tutarlıdır: producer
callback numarasını limitli bulursa kayıt **kapatılmaz**, kalan numaraların kotasına düşülür (F2);
kota da yoksa aynı nedenle yeniden kapanır. **Kod değişikliği gerekmiyor.**

### P3.4 — Javadoc: iki okumanın ayrımı

`Call.getDialedNumberSequence` javadoc'undaki *"sayaç en fazla 1 fazla sayar, over-dialing
korumasında bu yönde hata yapmak doğrudur"* ifadesi kaldırıldı. Yerine iki okumanın ayrımı net
yazıldı: bu dizi **hangup sonrası** okumadır (izdeki her girdi yapılmış aramadır, doğru
kullanıcısı `RetryStrategy`), **arama öncesi** okuma `getCompletedDialSequence()`'dir ve eski
yorumun yanlış olduğu açıkça söylendi.

### N1 / N2 — Kayda geçirilenler

Bu iki madde `Deploy öncesi kalan işler` ve `Karar bekleyen kapsam farkları` altına işlendi
(aşağıda). Kod yazılmadı.

### Korunan invariantlar (SINIRLAR)

- [x] Faz 6 semantiği korundu: `getCompletedDialSequence` / `getDialedNumberSequence` ayrımı, `hasPendingDialPlan`, `redirectPendingDialTo`'nun varlık sebebi, `fallBackToEligibleNumber`, `Campaign.isPerNumberAttemptLimitActive`'in TEK karar noktası olması.
- [x] `retry_history` / `call_process` / `cb_history` **yazım** zamanı ve semantiği değişmedi; "+1 is for first call" varsayımı yerinde.
- [x] `advanceRetryNumberPastIneligible` bounded-walk sigortası kaldırılmadı; `RetryDncExhaustionInfiniteLoopRegressionTest` silinmedi (8/8 yeşil).
- [x] `createRetry` kontrol sırası ve `isCallAnswered()`'ın ilk çağrılması korundu.
- [x] Yeni TRC id / CDR satırı yok; `telephony_rc`, `user_id`, `user_result_code` ezilmiyor.
- [x] K2 muafiyeti kaldırılmadı.
- [x] Feature off (null/0/negatif) davranışı birebir aynı — her yeni test sınıfında ayrı bir feature-off testi var.
- [x] Hiçbir test silinmedi; mevcut fixture'lardan **hiçbiri** bu fazda değiştirilmedi (yalnız yeni test eklendi).
- [x] Yeni static **mutable** state yok (`normalizeNumberForLimit` saf fonksiyon).
- [x] Hot path: yeni DB query / blocking çağrı / geniş `synchronized` blok yok. `getLimitCandidateNumbers()` karar başına bir kez memoize ediliyor (`RetryStrategy.candidateNumbers`); normalizasyon kayıt başına birkaç `String.trim()`.
- [x] `ReachRetryPerNumber.isRetryLimitForNumberReached` (K2 muaf) dokunulmadı.
- [x] `checkColumn`'daki `CLOSE_REASON` satırı **kaldırılmadı** (P1.1'in yanlış çözümü).

### Doğrulama

```text
mvn -o ... -Dtest=MaxAttemptPerNumber*Test test
mvn -o ... -Dtest=MaxAttemptPerNumber*Test,RetryStrategy*Test,RetryLimitTest,RetryRulesCycle*Test,
                  ReachRetry*Test,RoundRobin*Test,RetryDncExhaustionInfiniteLoopRegressionTest,
                  CallHistoryColumnsLockstepCharacterizationTest,CallProducer*Test,CallOriginatorTest,
                  Campaign*ContractTest,CampaignInMapperTest,CampaignDataTypeChecker*Test test
```

| Komut | Faz 6 referansı | Faz 7 | Durum |
|---|---|---|---|
| `MaxAttemptPerNumber*Test` | 61/61 | **83/83** | +22, düşen yok |
| Geniş set | 289/289 | **328/328** | +39, düşen yok |

Sınıf bazında yeni/artan: `MaxAttemptPerNumberNormalizationTest` **11** (yeni),
`MaxAttemptPerNumberOffColumnCallbackTest` **7** (yeni),
`MaxAttemptPerNumberCloseReasonAccuracyTest` **4** (yeni),
`CampaignCloseReasonBindingUiContractTest` **6** (yeni),
`CampaignDataTypeCheckerCloseReasonBindingTest` 7 → **13**,
`CallProducerFreshNumberSelectionCharacterizationTest` 24 → **29**.
Değişmeyenler yeşil kaldı: Baseline 18, SuccessfulClose 7, Lockstep 13, EndToEnd 11,
EarlyTermination 9, Skips 6, FeatureOff 9, CloseReasonPersistence 8, DncExhaustion 8 ve altı
stratejinin mevcut testleri.

P0 doğrulaması:

```text
git diff --stat -- gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallProducer.java
 1 file changed, 198 insertions(+), 15 deletions(-)
```

LF/CRLF uyarısı **yok**; `--ignore-cr-at-eol` gerekmiyor.

**Koşulamayan:** `npm.cmd run test:angular` — `gbpowerdialer/node_modules` yarım (145 paket
dizininin 73'ü boş; `karma`, `jasmine-core`, `angular-mocks`, `karma-jasmine`,
`karma-chrome-launcher` ve `node_modules/.bin` dâhil). Offline kuralı gereği indirme denenmedi.
P3.1'in doğrulaması bu yüzden Java sözleşme testine taşındı; davranışsal spec `npm ci` bekliyor.

`CampaignResultJobServiceTest`'teki 4 başarısızlık bu çalışmadan bağımsızdır (Faz 2'de pristine
`HEAD` 03c0f7cc worktree'sinde birebir üretilerek kanıtlandı).

### Commit ayrımı (`gbpd-commit-discipline`)

| Commit | Kapsam | Dosyalar |
|---|---|---|
| 1 — **yalnız satır sonları** | P0 | `CallProducer.java` (içerik değişikliği **YOK**; `tr -d '\r'` ile eski hâliyle `cmp` birebir aynı) |
| 2 — deploy blocker'ları | P1.1, P1.2 | `CampaignDataTypeChecker.java`, `FEAT-325559-max-attempt-per-number.sql`, `CampaignDataTypeCheckerCloseReasonBindingTest` |
| 3 — doğruluk + tutarlılık | P2.1, P2.2, P2.3, P3.1, P3.2, P3.4 | `Call.java`, `RetryStrategy.java`, `CallProducer.java`, `campaignFormCtrl.js`, `dbTableFormCtrl.js`, `campaignFormMaxAttemptPerNumber.spec.js`, 3 yeni dialer testi + `CampaignCloseReasonBindingUiContractTest` + `CallProducerFreshNumberSelectionCharacterizationTest` |

Commit 1'in tek başına durması inceleme için kritiktir: o commit'te **hiçbir içerik değişikliği
yoktur**, sonraki commit'lerin diff'i de bu sayede okunabilir kalır.

**Commit atılmadı.** Kullanıcı manuel kontrolü tamamlayıp haber verecek.

### Faz 7 Tamamlanma Notu

**08.09.2026 — Faz 7 tamamlandı.** P0 commit blocker'ı giderildi ve Faz 6'daki yanlış teşhis
düzeltme notuyla işaretlendi; P1.1/P1.2 deploy blocker'ları, P2.1/P2.2/P2.3 doğruluk hataları ve
P3.1-P3.4 tutarlılık maddeleri uygulandı. Hedefli set **83/83**, geniş set **328/328**, düşen
sayı yok, silinen test yok, mevcut fixture değişmedi.

Değişen üretim dosyaları (6): `Call.java`, `RetryStrategy.java`, `CallProducer.java`,
`CampaignDataTypeChecker.java`, `campaignFormCtrl.js`, `dbTableFormCtrl.js`. Migration script'i
güncellendi. Yeni test sınıfları (4): `MaxAttemptPerNumberNormalizationTest`,
`MaxAttemptPerNumberOffColumnCallbackTest`, `MaxAttemptPerNumberCloseReasonAccuracyTest`,
`CampaignCloseReasonBindingUiContractTest`.

Kapanış hâlâ tek blocker'a bağlı: `npm ci` sonrası AngularJS spec'lerinin koşulması.

### Deploy öncesi kalan işler — Faz 7 eklemeleri

1. **İş birimine bildirilecek (N1):** özellik **kapalı** kampanyalarda da bir davranış değişikliği
   var. `resolveCalledNumber` artık tembel değerlendirildiği için "tüm telefon kolonları NULL ama
   callback/retry numarası dolu" kayıtlar eskiden sessizce düşüyordu, artık **aranıyor**. Doğru
   yönde bir düzeltme ama deploy sonrası uykuda kalmış böyle kayıtlar aranmaya başlar; hacim
   sürprizi olmaması için önceden bildirilmeli.
2. **Migration 2.2 kararı:** blok artık otomatik çalışmıyor. `call_process` / `cb_history`
   kolonlarının eklenip eklenmeyeceği bilinçli bir karardır ve **bu işin kapsamı dışındaki
   kampanyaların result yazımını da değiştirir**. Karar verilirse 2.2'nin ürettiği ifadeler elle
   çalıştırılır.
3. **Migration 2.1 kapsamı:** 2.5 çıktısı gözden geçirilip listede kampanya result tablosu olmayan
   tablo olup olmadığı kontrol edilmeli (keşif `complete_date` kolonuna dayanıyor).
4. **`CallProducer.java` blob anomalisi:** depoda CRLF saklanan blob temizlenecekse ayrı ve tek
   başına bir commit olarak planlanmalı (bu iş kapsamı dışı).

### Karar bekleyen kapsam farkları (N2 — kod yazılmadı)

| Konu | Durum |
|---|---|
| Talebin "Etkilenen Ürünler"i arasında gbwebphone'un *Callback Takip ve Arama Detay/Raporlama Ekranları* sayılıyor; K7 ile kapsam dışı bırakıldı | Müşteri "limit nedeniyle kapandı" bilgisini şu an **yalnız DB kolonunda** görür. Ekran ihtiyacı doğarsa ayrı iş. |
| Üç kapanış metni lokalize edilmiyor (K14) | Müşteri raporda Türkçe bekliyorsa uyum sorunu olur. P2.1'de yeni metin **eklenmemesinin** gerekçelerinden biri de budur. |
| Talebin *"Cycle / Macro Interval Limit: kaydın girebileceği maksimum tur sayısı"* maddesi `macroMaxAttempt` olarak yorumlandı; ayrı bir "maksimum tur" alanı eklenmedi | Müşteri gerçekten ayrı alan bekliyorsa açık kalır. |
| Boşluklu numara değerinde numara halkasının ilerlemeyip kaydı kapatması (`Call.findNextNumberToCall` ham eşitlik) | Özellik-kapalı tüm stratejileri etkileyen mevcut bir hata; ayrı ve dar kapsamlı iş olarak ele alınmalı. |

## Faz 8 — Aktive Edilen Kayıtların Numara Başına Limitle Tutarlılığı (28.09.2026)

Faz 7 sonrası test özeti hazırlanırken kabul kriteri 8'in ("yeni eklenen numara aranır, eskiler atlanır") doğal akışı — kayıt limitle kapanır, veri manipülasyon ekranından **aktive edilir**, yeni numara eklenir — uçtan uca izlendi ve üç kusur bulundu. Bu faz onların giderilmesidir; Faz 1-7 içeriği korunmuştur. Çalışma, develop'a birleşen PR 25715 ve ardından gelen AMD işi (325470, PR 25716) üzerine kuruldu: `feature/325559-numara-basina-azami-arama-limiti` dalı `origin/develop`'a fast-forward edildi (merge commit yok).

### Kusurlar ve kök nedenleri

**K1 — Aktive edilen kayda eklenen yeni numara hiç aranmıyor.** Doğrulandı (sonda testi): eski `retry_number` duran aktive satır → `resolveCalledNumber` = `null`; aynı satırda `retry_number` boşken yeni numara aranıyor.

Writer/reader zinciri:

| Adım | Ne oluyor |
|---|---|
| Strateji kapanışı (`createRetry` → `completeCall` → `insertCallResultToDB`) | `retry_date` NULL yazılır (`CampaignDbOperations.java:2395-2398`), `retry_number` **yalnız doluyken yazıldığı** için eski değeriyle kalır (`:2405`) |
| Aktivasyon (`CampaignRecordService.activateRecordReturningCount`, `:967`) | `retry_date`, `callback_date`, `complete_date`, `is_fetched`, TRC temizlenir; `retry_number`, `callback_number` **temizlenmez** |
| Producer (`CallProducer.resolveCalledNumber`) | Dolu `retry_number`/`callback_number`'ı **bekleyen plan** gibi okur. Limitteyse `fallBackToEligibleNumber` → `redirectPendingDialTo` bekleyen plan olmadığı için `false` → `null` → kayıt `setClosedWithoutDialing` ile tekrar kapanır |

Kök neden **okuyucuda**: producer, bir numaranın plan olduğunu `hasPendingDialPlan()` (`retry_date`/`callback_date` varlığı) ile doğrulamadan kabul ediyor. Bekleyen planı olmayan satırdaki `retry_number`/`callback_number` bir plan değil, **bayat işaretçidir**.

**K2 — Aktivasyon sonrası arama sayılmıyor.** İz (`call_process` + `retry_history`) aramaları **planlama anında** kaydeder; ilk (fresh) arama `F` ile örtük temsil edilir ve yalnız izin ilk yazımında eklenir. Aktive edilen kayıt fresh fetch'ten gelir ama izde `F` zaten vardır; bu aramanın planlama adımı yoktur → **hiçbir yere yazılmaz**. K1 düzeltilince yeni numara limitten 1 fazla arama alırdı (limit 2 → 3 arama).

**K3 — Aktivasyonda eski kapanış nedeni kalıyor.** Aktivasyon, kaydın kapanış durumunu geri almak için `complete_date`, `is_disabled`, TRC vb. temizliyor; bu işle eklenen kapanış durumu kolonu `close_reason` bu sözleşmeye **eklenmemiş**.

### Tasarım kararları

**K1 + K2 — producer'da tek tutarlı dal.** Özellik aktifken, izi olan ama bekleyen planı olmayan satır "aktive edilmiş kayıt" olarak ele alınır:

1. Bayat `retry_number`/`callback_number` yok sayılır: callback ve retry dalları atlanır. Aranamayan kaydın kapanış etiketi de callback'i **plan varlığıyla** (`isCallback()` = `callback_date` dolu) ayırt eder; bayat `callback_number` yüzünden yanlışlıkla `max attempt reached on callback` yazılmaz (bkz. Faz 8 sonuçları, "Uygulamada plandan sapma").
2. Numara fresh semantiğiyle seçilir: colSort'a göre limitini doldurmamış ilk numara (`selectFreshNumber`).
3. Arama, izde **tek girdiyle** kaydedilir: `retry_history` + `R` adımı kilitli (lockstep) eklenir. Böylece "+1 is for first call" varsayımı korunur (yalnız ilk fresh arama örtük kalır) ve sonraki `createRetry` hangup sonrası okuması bu aramayı sayar.

Karar notları:

- **Neden producer?** Planlama adımı olmayan bir aramanın numarasına karar veren tek bileşen producer; F2'deki `redirectPendingDialTo` ile aynı desen (izi fiilen aranan numaraya hizalamak). İz yazımı `Call` üzerinde kalır (`recordUnplannedDial`).
- **Yazım zamanı istisnası (bilinçli):** Faz 6/7 sınırı "mevcut iz yazımlarının zamanı ve semantiği değişmez" idi. Mevcut hiçbir yazım değişmedi; **daha önce hiç yazılmayan** bir arama için yeni bir yazım noktası eklendi. Planlı aramalar karar anında (planlama) yazıldığı gibi bu arama da karar anında (producer) yazılır.
- **Neden aktivasyonda `retry_number` temizlenmiyor?** Özellik kapalıyken aktive edilen kayıt bugün bayat `retry_number`'dan (halka konumu) aranıyor; temizlemek özellik kapalı kampanyaların davranışını değiştirirdi (SINIRLAR). Düzeltme özellik kapısının arkasında, okuyucuda.
- **Kalan numaralar varken hangi numara?** Fresh semantiği (limitini doldurmamış ilk kolon). Kabul kriteri 8 her iki okumada da sağlanıyor; fresh semantiği satırın geldiği fetch tipiyle tutarlı ve öngörülebilir.

**K3 — aktivasyon sözleşmesinin tamamlanması.** `close_reason` (binding varsa) aktivasyonda NULL'lanır.

- **Neden sıcak yol değil?** Alternatif "her sonuç yazımında neden yoksa `close_reason = NULL`" idi. Reddedildi: her çağrının UPDATE'ine dokunur ve müşterinin kendi `close_reason` kolonu NOT NULL ise **tüm sonuç yazımını düşürürdü**.
- **Neden ayrı UPDATE?** Aynı risk aktivasyon için de geçerli: NOT NULL bir kolon aktivasyonun tamamını düşürmemeli. Temizleme ayrı ve izole bir UPDATE'tir; başarısız olursa WARN loglanır, aktivasyon sonucu etkilenmez (FIX-320173'teki izole yan etki deseni). Admin/API akışıdır, sıcak yol değildir.

### Kapsam dışı — karar bekleyen (K4)

**Yeni numara mevcut numaralardan ÖNCEKİ bir kolona eklenirse ilk aramanın atfı kayar.** `F` adımının numarası okuma anında "colSort'a göre ilk dolu kolon" olarak türetilir (`Call.getFirstDialedNumber`). Aktivasyonda yeni numara, ilk aranan numaranın kolonundan önceki bir kolona yazılırsa `F` yeni numaraya atfedilir: eski numara 1 eksik sayılır ve 1 kez fazla aranabilir. Tam çözüm ilk aranan numaranın bir yerde saklanmasını gerektirir — yeni kolon (bu işte "yeni sayaç kolonu yok" kararıyla çelişir) veya `call_process` biçim değişikliği (dış okuyucuları etkileyebilir). Bu fazda **düzeltilmedi**; kullanıcı kararı bekliyor. Geçici önlem: yeni numarayı mevcut numaralardan sonraki bir kolona eklemek.

### TDD sırası

1. RED — yeni `MaxAttemptPerNumberReactivationTest`: uçtan uca aktivasyon senaryosu (yeni numara TAM limit kadar aranır, eski numara hiç aranmaz), bayat `retry_number` / `callback_number`, izin kilitli yazımı, kapanış etiketi.
2. Karakterizasyon (yeşil kalmalı): özellik kapalıyken aktive satır bayat `retry_number`'dan aranır ve iz değişmez; hiç işlem görmemiş kayıt ve bekleyen planlı satır bu dala girmez.
3. RED — yeni `CampaignRecordActivationCloseReasonContractTest`: aktivasyon `close_reason`'ı temizler, binding yokken sorguya girmez, temizleme hatası aktivasyonu düşürmez.
4. GREEN — `Call` (`hasPendingDialPlan` public, `hasDialHistory`, `recordUnplannedDial`), `CallProducer` (aktive kayıt dalı), `CampaignRecordService` (izole temizleme).
5. Regression — Faz 7 kapanış komutlarının ikisi (düşen sayı olmamalı).

### Doğrulama komutları

```text
mvn -o ... -Dtest=MaxAttemptPerNumber*Test test
mvn -o ... -Dtest=MaxAttemptPerNumber*Test,RetryStrategy*Test,RetryLimitTest,RetryRulesCycle*Test,
                  ReachRetry*Test,RoundRobin*Test,RetryDncExhaustionInfiniteLoopRegressionTest,
                  CallHistoryColumnsLockstepCharacterizationTest,CallProducer*Test,CallOriginatorTest,
                  Campaign*ContractTest,CampaignInMapperTest,CampaignDataTypeChecker*Test,
                  CampaignRecord*Test test
```

### Performans etkisi

- Producer: aktive kayıt tespiti mevcut in-memory listelere (`call_process`, `retry_history`, `cb_history`) ve iki `rawData` okumasına bakar; yalnız özellik aktifken, kayıt başına bir kez. Yeni DB çağrısı yok.
- Aktivasyon: kayıt başına değil **istek başına** bir ek UPDATE; admin/API akışı, sıcak yol değil.

### Riskler

| Risk | Önlem |
|---|---|
| Kuyruğa alınıp hiç aranmadan yazılan aktive çağrı, izde aranmış görünür | Yalnız dial edilmiş çağrılar yazılıyor (`CampaignStopJob` yalnız `originatedCalls`); kalan kenar durumda hata yönü **muhafazakâr** (eksik arama, fazla değil) |
| Aktive kaydın ilk aranan numarası değişir (bayat `retry_number` yerine ilk uygun kolon) | Yalnız özellik aktifken; kabul kriteri 8 ile uyumlu; dokümante edildi |
| K4 (atıf kayması) açık kalır | Kapsam dışı olarak işaretlendi, geçici önlem yazıldı, karar bekliyor |

### Faz 8 sonuçları (28.09.2026)

#### Uygulama

- [x] `Call.hasPendingDialPlan()` public yapıldı; javadoc'una "plan olmanın tek ölçütü" sözleşmesi yazıldı.
- [x] `Call.hasDialHistory()` eklendi (üç geçmiş kolonundan biri dolu mu).
- [x] `Call.recordUnplannedDial(String)` eklendi: `retry_history` + `R` kilitli yazılır; boş numara `IllegalArgumentException` ile reddedilir (sözleşme ihlali görünür olsun).
- [x] `CallProducer.isReactivatedRecord` + `resolveReactivatedRecord`: özellik aktif + iz var + bekleyen plan yok → fresh semantiğiyle limitini doldurmamış ilk kolon, izde tek girdi, `retryNumber` halka konumu için hizalanır.
- [x] `CallProducer.resolveUncallableReason`: callback etiketi ölçütü `getCallbackNumber() != null` → `isCallback()`.
- [x] `CampaignRecordService.clearCloseReasonOfActivatedRecords`: aktivasyon UPDATE'inden sonra, izole ve binding'e bağlı `close_reason = NULL`; hata WARN, aktivasyon sonucu değişmez.

#### Uygulamada plandan sapma

Planda "bayat callback numarası Call üzerinde de temizlenir" yazıyordu. Uygulamada `Call.getCallbackNumber()`'ın alan `null` olduğunda değeri **ham satırdan yeniden okuduğu** görüldü: `setCallbackNumber(null)` etkisiz, satır yanıltıcı olurdu. Asıl kusur okuyucudaydı: `resolveUncallableReason` "callback mi?" sorusunu `callback_number` doluluğuyla soruyordu — K1 ile **aynı sınıf** (bayat işaretçiyi plan saymak). Strateji katmanı (`RetryStrategy.applyExhaustedCloseReason`) zaten doğru ölçütü (`isCallback()`) kullanıyordu; producer aynı ölçüte hizalandı. `setCallbackNumber(null)` satırı kaldırıldı, plan metni güncellendi.

Aynı sınıf için tarama: `getCallbackNumber() != null` ile "callback mi?" kararı veren başka okuyucu yok. `createRetry`'nin callback dalı `isCallback() && getCallbackNumber() != null` (ikisi birden), `getLimitCandidateNumbers` `isCallback()` kullanıyor.

#### Testler

| Sınıf | Test | Kapsam |
|---|---|---|
| `MaxAttemptPerNumberReactivationTest` (yeni) | 10 | Uçtan uca aktivasyon (yeni numara TAM limit, eski numara 0), aktivasyon boyunca limit aşılmaz, bayat `retry_number`/`callback_number`, izin kilitli yazımı, kapanış etiketi; karakterizasyon: özellik kapalı, hiç işlem görmemiş kayıt, bekleyen planlı satır, bayat callback'in aday sayılmaması |
| `CampaignRecordActivationCloseReasonContractTest` (yeni) | 6 | Aktivasyon `close_reason`'ı temizler, mevcut aktivasyon UPDATE'i değişmedi, custom binding, binding yokken sorgu yok, temizleme hatası aktivasyonu düşürmez, çalışan kampanyada aktivasyon reddi |

RED aşaması: dialer testlerinde 5 beklenen başarısızlık (K1: `null`, K2: iz `[F, R]`, etiket), aktivasyon testlerinde 2. Test kurulumunda iki bulgu: aktivasyon logu oturum açmış kullanıcıyı `SecurityContextHolder`'dan ve işlem logunu `ActionLogService` üzerinden yazıyor; testte ikisi de mock'landı (`DNCServiceTest` deseni).

**Mevcut hiçbir test ve fixture değiştirilmedi.**

#### Doğrulama

```text
mvn -o ... -Dtest=MaxAttemptPerNumberReactivationTest,CampaignRecordActivationCloseReasonContractTest test   -> 16/16
mvn -o ... -Dtest=MaxAttemptPerNumber*Test test                                                                 -> 93/93
mvn -o ... -Dtest=<Faz 7 genis komutu> test                                        -> 437 calisti, 0 hata, 10 atlandi
mvn -o ... -Dtest=CampaignRecord*Test test                                                                      -> 25/25
```

| Komut | Faz 7 | Faz 8 | Not |
|---|---|---|---|
| `MaxAttemptPerNumber*Test` | 83/83 | **93/93** | +10 aktivasyon testi |
| Faz 7 geniş komutu | 328/328 | **437 çalıştı, 0 hata** | Artış develop'tan gelen testler (AMD `RetryStrategyAmdFamilyBaselineCharacterizationTest` 24, `RetryStrategyBadNumberNextNumberFeatureTest` 60 ve diğer merge'ler) + bu fazın testleri. 10 atlama AMD'nin iki sınıfındaki `Assume.assumeTrue(...)` — belirli stratejide çalışacak şekilde tasarlanmış parametreli testler; bu işle ilgisiz |
| `CampaignRecord*Test` | — | **25/25** | Aktivasyon ve veri manipülasyonu |

Satır sonu denetimi: değişen dört dosyanın tamamı tek tip CRLF, ham diff ile `--ignore-cr-at-eol` diff'i birebir aynı (`CallProducer.java` 50/1, `Call.java` 38/1, `CampaignRecordService.java` 27/0).

`npm.cmd run test:angular` bu fazda gerekmedi (frontend değişmedi).

#### Faz 8 tamamlanma notu

**28.09.2026 — K1, K2, K3 giderildi.** Kabul kriteri 8'in aktivasyonlu akışı uçtan uca doğrulandı: yeni numara tam limit kadar aranıyor, limitini dolduran eski numara bir daha aranmıyor, aktivasyon kapanış nedenini temizliyor. K4 (yeni numaranın ilk aranan numaradan önceki bir kolona eklenmesi) karar bekliyor. **Commit atılmadı.**

### Faz 8b — Strateji 0 kararı ve uygulaması (28.09.2026) — **Faz 10'da kaldırıldı (strateji 0 muaf)**

**Karar bekleyen konu kapandı.** Konu: "Reach retry limit for single number" (strateji 0) stratejisinde, kayıtta birden fazla dolu numara varken aranan numara limitini doldurduğunda ne olacağı. Kullanıcı kararı: **sıradaki aranabilir numaraya geçilir**, geçiş strateji katmanında yapılır.

Gerekçe:

1. **Emsal karar:** FEAT-325470'in S3 kararı "kullanılmayan numarada sonraki numaraya geç" kuralını strateji 0 dahil 6 stratejide uyguluyor; gerekçesi "kural numara geçerliliğiyle ilgili, retry ritmiyle değil". Numara başına limit de aynı türden bir kural (numara artık aranamaz). Geçiş olmasaydı aynı kampanyada kullanılmayan numara sıradakine geçer, limiti dolan numara kaydı kapatırdı.
2. **"Yapıyı bozmamak" ilkesi:** strateji ritmini korur — tek numarayı art arda arar — ve yalnız o numara aranamaz hâle gelince sıradakine geçer.
3. **Bütçe:** kaydın Toplam Deneme'den kalan hakkı diğer numaralarda kullanılır; fazladan arama olmaz.

Uygulamadaki sorun ve çözüm: önceki hâlde geçiş **dolaylıydı**. Strateji 0 `setRetryNumber`'da numara yürüyüşü yapmadığı için limitini doldurmuş numarayı yeniden planlıyor, producer arama öncesi planı reddedip diğer numaraya düşüyor ve izin son girdisini düzeltiyordu (`redirectPendingDialTo`). İki katman aynı kayıt için farklı karar veriyordu — Faz 6 incelemesinin işaretlediği desen. Artık geçiş planlama anında stratejide yapılıyor:

- [x] `ReachRetrySingleNumber.setRetryNumber`: özellik aktifken `processRetryNumberWithDNC()` (DNC + numara başına limit yürüyüşü). Özellik kapalıyken hiç çalışmaz; strateji hiç numara değiştirmez (birebir eski davranış).
- [x] Producer'daki F2 yönlendirmesi güvenlik ağı olarak yerinde (ör. limit kampanya çalışırken düşürülürse).
- [x] FEAT-325470'in 913 dalı bu değişiklikten etkilenmez: o dalda `setRetryNumber` çağrılmıyor.

Yeni test sınıfı `MaxAttemptPerNumberSingleNumberStrategyTest` (7):

| Test | Ölçtüğü |
|---|---|
| `movesToNextEligibleNumberWhenCalledNumberReachesItsLimit` | Limit dolunca strateji sıradaki numarayı **planlar**; iz planlama anında doğru numarayı taşır |
| `keepsDialingTheSameNumberWhileItIsBelowItsLimit` | Ritim korunur: limit altındayken aynı numara |
| `staysOnTheNewNumberAfterSwitching` | Geçişten sonra yeni numarada kalır |
| `skipsDncNumberWhenMovingOn` | Sıradaki numara DNC'deyse atlanır |
| `closesRecordWhenEveryNumberIsExhausted` | Tüm numaralar dolunca `closing for reached number limit` |
| `featureOffKeepsSingleNumberBehaviourUnchanged` | Özellik kapalıyken birebir eski davranış |
| `endToEndNumbersAreConsumedOneAtATimeAndEveryPlanIsHonoured` | Uçtan uca: `number_1, number_1, number_2, number_2, number_3, number_3`; producer her adımda stratejinin planladığı numarayı arar |

RED aşaması: 3 beklenen başarısızlık — uçtan uca testte 2. adımda planlanan `number_1`, aranan `number_2` (geçişi producer yapıyordu); strateji katmanı testlerinde `number_1` yeniden planlanıyordu. Mevcut hiçbir test strateji 0'ın limitli numarayı yeniden planlamasını kodlamıyordu; hiçbir test değişmedi.

Doğrulama:

```text
mvn -o ... -Dtest=MaxAttemptPerNumberSingleNumberStrategyTest test                 -> 7/7
mvn -o ... -Dtest=<Faz 7 genis komutu>,CampaignRecord*Test test    -> 459 calisti, 0 hata, 10 atlandi
  icinde: MaxAttemptPerNumber* 11 sinif 100/100, ReachRetrySingleNumberTest 3/3,
          RetryStrategyBadNumberNextNumberFeatureTest 60 (5 Assume), RetryStrategyAmdFamilyBaselineCharacterizationTest 24 (5 Assume)
```

Etki notu (müşteri): strateji 0 kullanan ve **numara başına limit girilen** kampanyalarda kayıt artık birden fazla numarasını sırayla arar. Alan boş bırakılan strateji 0 kampanyalarında hiçbir şey değişmez.

**Faz 8 (K1-K3 + strateji 0) tek commit'te toplandı.** K4 karar bekliyor.

## Faz 9 — Etkinleştirme Checkbox'ı (28.09.2026)

Faz 8 commit'inden (`19b76133`) sonra gelen arayüz isteği. Faz 1-8 içeriği korunmuştur.

### 9.1 — Alanı açan checkbox

**İstek:** kampanya yöneticileri alana rastgele değer girip davranışı bozmasın; alan bir checkbox ile bilinçli olarak etkinleştirilsin. "Reach retry limit before next number" stratejisinde her durumda pasif kalsın.

**Tasarım kararı — yalnız arayüz durumu, DB kolonu yok:**

- Tek doğruluk kaynağı alanın kendisidir: değer doluysa özellik açıktır (backend'in `null` = kapalı sözleşmesi değişmedi). Ayrı bir bayrak kolonu bayrak/değer tutarsızlığı (bayrak açık + değer boş, bayrak kapalı + değer dolu) doğurur ve DTO, Redis contract, migration gerektirirdi.
- Checkbox durumu kampanya yüklenince değerden türetilir (`vm.syncMaxAttemptPerNumberEnabled`; yeni/kopya kampanya ve düzenleme yüklemesi). İşaret kaldırılınca değer temizlenir (`vm.onMaxAttemptPerNumberEnabledChange`) ve kayıtla özellik kapanır. İşaretliyken alan **zorunludur**; "işaretli ama boş" kaydedilemez.
- Model controller'da (`campaign.maxAttemptPerNumberEnabled`) durur, `campaign.item`'a yazılmaz; backend'e bilinmeyen alan gitmez.
- Checkbox'ın pasif olma koşulları alanınkiyle aynı (tekrar arama kapalı, strateji 1, `EditCampaignTech` yetkisi yok); alan ayrıca checkbox işaretsizken pasif.

- [x] `campaignForm.html`: checkbox (alandan önce), alanın `ng-disabled`'ına `!campaign.maxAttemptPerNumberEnabled`, `ng-required`, zorunluluk yıldızı; açıklama yorumu güncellendi.
- [x] `campaignFormCtrl.js`: türetme ve değişim işleyicisi, iki yükleme noktasında türetme çağrısı.
- [x] 4 dil: `campaign.maxAttemptPerNumberEnabled` (TR "Numara Başına Limiti Etkinleştir", EN "Enable Max Attempt Per Phone Number", RU, UA).
- [x] Karma spec güncellendi: alan ifadesi yeni hâline getirildi, checkbox ifadesi ve 4 vaka eklendi; `buildScope` checkbox durumunu destekliyor (verilmezse işaretli kabul edilir, mevcut vakalar aynı koşulları ölçmeye devam eder).

Testler: yeni `CampaignMaxAttemptPerNumberCheckboxUiContractTest` (9; RED 9/9). Karma spec `node_modules` blocker'ı nedeniyle koşulamıyor; ek offline doğrulamalar:

- Spec'teki iki ifade kopyası şablondaki `ng-disabled` değerleriyle **birebir aynı** (Node ile karşılaştırıldı).
- Şablondan okunan `ng-disabled`/`ng-required` ifadelerinin doğruluk tablosu Node'da değerlendirildi: 14/14 vaka beklenen sonucu verdi (işaretli/işaretsiz, strateji 0/1/2/5, tekrar arama kapalı, yetki yok).
- Değişen 6 JS dosyası `node --check` ile parse edildi: sözdizimi hatası yok.

### 9.2 — Yerleşim: alan checkbox'ın altında

**İstek:** manuel UI kontrolünde alanın checkbox'ın sağındaki kolonda durması grubu dağıtıyordu; alan checkbox'ın altına alınsın.

- [x] `campaignForm.html`: checkbox ve alan artık aynı `col-lg-4` içinde, alt alta iki `form-group` (LSC sekmesindeki mevcut desen). Hiçbir `ng-*` ifadesi değişmedi; yalnız kolon sınırı kaldırıldı ve açıklama yorumuna yerleşim notu eklendi.
- [x] Sözleşme testi: `fieldSitsUnderTheCheckboxInTheSameColumn` (checkbox ile alan arasında yeni kolon/satır açılmaz). RED: 10 vakadan yalnız yeni vaka düştü → GREEN 10/10.

Karma spec'i yerleşime bağlı değil (yalnız ifadeleri değerlendirir); değişiklik gerekmedi.

### Doğrulama

```text
mvn -o ... -Dtest=CampaignMaxAttemptPerNumberCheckboxUiContractTest test          -> 10/10
mvn -o ... -Dtest=<campaignForm.html okuyan 7 UI sozlesme sinifi> test            -> 37/37
mvn -o ... -Dtest=<Faz 7 genis komutu>,CampaignRecord*Test test                   -> 469 calisti, 0 hata, 10 atlandi (AMD Assume)
  icinde: MaxAttemptPerNumber* 11 sinif 100/100
node expr-check.js (spec ifadeleri == sablon)                                     -> AYNI (2/2)
node truth.js (ng-disabled/ng-required dogruluk tablosu)                          -> 14/14
```

`npm.cmd run test:angular` koşulamadı (`node_modules` yarım; offline kuralı gereği indirme denenmedi).

## Faz 10 — Tek Numara Stratejisi de Muaf (29.09.2026)

**Bulgu (test ortamı, kampanya 115):** "Reach retry limit for single number" (strateji 0) + limit 2 ile iki numaralı kayıt `Telefon2 ×2 → Telefon3 ×2` arandı (Faz 8b davranışı). Beklenen, stratejinin tek numarada kalmasıydı. Checkbox kapalıyken (kampanya yeniden başlatıldıktan sonra) strateji tek numarada kaldı; sorun yalnız limit açıkken.

**Karar (analiz D10):** strateji 0, strateji 1 gibi numara başına limitten muaftır. Faz 8b'deki numara geçişi kaldırıldı.

- [x] `Campaign.isPerNumberAttemptLimitActive`: `REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER` de muaf (tek karar noktası; producer ve retry stratejileri bunu okur).
- [x] `ReachRetrySingleNumber`: `setRetryNumber` Faz 8b öncesi hâline döndü (aranan numara); `isPerNumberLimitActive()` override'ı `false` (strateji 1'deki iki katmanlı sigorta).
- [x] `campaignForm.html`: checkbox ve alan `retryStrategy == 0` iken de pasif; `ng-required` ve yıldız `!= 0` koşulu aldı; açıklama metni iki stratejide görünür. Metin ("Bu stratejide numara başına limit Toplam Deneme alanından uygulanır.") iki strateji için de doğru, dil dosyası değişmedi.
- [x] Javadoc'lar (`Campaign`, `RetryStrategy`, `CallProducer`), migration yorumu ve `DIALER-RUNTIME.md` muafiyet maddesi güncellendi. Migration ve mimari dokümandaki "alanı boşaltmak anında eski davranışa döner" ifadesi düzeltildi: çalışan kampanyada boş değer kaydetmeyle aktarılmıyor (`CampaignPropertyCopier` null değeri atlar); kampanya yeniden başlatılmalı.

Testler (TDD; RED 12 → GREEN):

| Sınıf | Değişiklik |
|---|---|
| `MaxAttemptPerNumberFeatureOffRegressionTest` | +2: strateji 0 alanı yok sayar (kampanya alanı ve override katmanı; strateji 1 vakalarının eşi) |
| `MaxAttemptPerNumberEndToEndDialLoopTest` | +2: producer strateji 0'da limiti uygulamaz; bekleyen retry planı diğer numaraya yönlendirilmez |
| `MaxAttemptPerNumberEarlyRecordTerminationTest` | Liste testinden strateji 0 çıkarıldı (`everyApplicableStrategy...`); K15 vakası tersine çevrildi (kayıt limitle kapanmaz). Gerekçe javadoc'ta |
| `MaxAttemptPerNumberSingleNumberStrategyTest` | 7 → 5: geçiş ve DNC yürüyüşü vakaları çıkarıldı; kapanış ve uçtan uca vakalar tersine çevrildi (uçtan uca: Toplam Deneme 5 iken 6 aramanın hepsi `number_1`) |
| `RetryStrategyBadNumberNextNumberFeatureTest` | Strateji 0 parametresinde beklenti `number_3` → `number_2` (limit yok sayılır; AMD geçişi aynen). Gerekçe javadoc'ta |
| `CampaignMaxAttemptPerNumberCheckboxUiContractTest` | 10 → 11: strateji 0 koşulları + açıklama metni vakası |
| karma spec | İfade kopyaları yeni hâline getirildi; strateji 0 için iki vaka "açık" → "pasif" |

RED: 49 testten 12'si beklenen biçimde düştü; uçtan uca vaka test ortamındaki sırayı birebir üretti (`[number_1, number_1, number_2, number_2, number_3, number_3]`).

```text
mvn -o ... -Dtest=<degisen 5 sinif> test                                   -> RED 12/49 -> GREEN 49/49
mvn -o ... -Dtest=<Faz 7 genis komutu>,CampaignRecord*Test,
               AwaitingUserResultTimeoutAmbiguousTrcRegressionTest,
               ProcessCallResultPersistFirstRegressionTest test               -> 479 calisti, 0 hata, 10 atlandi (AMD Assume)
  icinde: MaxAttemptPerNumber* 11 sinif 102/102; CampaignMaxAttemptPerNumberCheckboxUiContractTest 11/11
node expr-check.js (spec ifadeleri == sablon)                                -> AYNI (2/2)
node truth.js (strateji 0 vakalari eklenmis tablo)                           -> 17/17
```

Geniş komutun ilk koşusunda `RetryStrategyBadNumberNextNumberFeatureTest[0-REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER]` düştü (yukarıdaki beklenti güncellemesi); başka düşen olmadı.

`npm.cmd run test:angular` koşulamadı (`node_modules` yarım).
