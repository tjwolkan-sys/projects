# FEAT Numara Başına Azami Arama Limiti (Max Attempt Per Phone Number)

## Bağlam

Task `325559`. Müşterinin (Denizbank) ana talebi net ve kendi kendini tarif ediyor:

> gbpowerdialer arama motoruna, mevcut genel döngü ve toplam arama limitlerinden bağımsız çalışan ve kayıttaki her bir telefon numarasının maksimum kaç kez aranabileceğini belirleyen opsiyonel "Numara Başına Azami Arama Limiti" (Max Attempt Per Phone Number) parametresinin kazandırılmasıdır. Ayrıca, müşteri yanıtı (Answer / Success) ile Randevulu Arama (Callback) durumlarında arama sonlandırma ve limit kontrolü mantıklarının netleştirilmesi beklenmektedir.

Problem kaynağı: bir kontağın 14-15 telefon kolonundan kampanya bazında 6 kolon seçiliyor; NULL numaralar arama halkasından tamamen düştüğü için kayıt bazlı toplam limit yalnız dolu numaralara bölüşüyor ve dolu numara sayısı azaldıkça numara başına arama artıyor.

Bu iş **Denizbank tetikledi ama ürün standardı olarak** geliştirilir: yeni parametre ve kapanış nedeni alanı tüm kampanyalarda bulunur, davranış tüm retry stratejilerine entegre olur ve mevcut yapı bozulmaz. Parametre boş bırakıldığında bugünkü davranış birebir korunur.

### Kararlar

| # | Karar |
|---|---|
| K1 | **Yeni `RETRY_STRATEGY` enum değeri eklenmez.** İş birimi dilinde "yeni retry stratejisi" olan davranış, mevcut 6 stratejinin tamamına **opsiyonel parametre** olarak entegre edilir. Ortak chokepoint `RetryStrategy` template'i olduğu için bu hem iş isteğini karşılar hem de yapıyı bozmaz. |
| K2 | Strateji `REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER` (ordinal 1) seçiliyken yeni alan **UI'da freeze** edilir ve motor tarafında **yok sayılır**. Bu strateji numara başına limiti zaten `retryOveralAttempt` ile uyguluyor; çakışma böylece hiç doğmaz, birleştirme kuralı gerekmez. |
| K3 | **Callback aramaları numara başına sayaca dahildir.** |
| K4 | Numara başına sayaç için **ayrı sayaç kolonu eklenmez**; sayım mevcut `retry_history` + `call_process` + `cb_history` kolonlarından türetilir. |
| K5 | Kapanış nedeni alanı: kampanyada **`col_result_close_reason NVARCHAR(255)`** binding'i (config DB, tek script ile tüm kampanyalara), değer müşteri result tablosundaki bu binding'in gösterdiği kolonda. FEAT-321997 `col_result_callback_attempts` deseni. Örnek değer: `"closing for reached number limit"`. |
| K6 | `retry_history` ve `call_process` kolonları veri manipülasyon ekranından **etkilenmiyor**, silinmiyor. Kriter 8 (yeni numara eklenmesi) bu sayede çalışır. |
| K7 | Geliştirme **yalnız dialer tarafında**; gbwebphone kapsam dışı. |
| K8 | İş bir şikâyet/incident değil, ürün standardı yeni geliştirmedir; öncesi/sonrası karşılaştırması zorunlu değildir. |
| K9 | B1'deki "+1" sızıntısı (mevcut kayıt tavanının 11'e sızması) bu iş kapsamında **araştırılmaz**; bulgu kayıtta kalır. |
| K10 | Limit dolarak kapanan kayıt **kalıcı kapanır**. Ek kod gerekmez: `complete_date` + `is_disabled` yazımı ve üç fetch sorgusunun `complete_date IS NULL` şartı bunu zaten sağlıyor. |
| K11 | Hiç aranmamış eleme için **yeni TRC id ve CDR satırı üretilmez**. Kapanış `complete_date` + `is_disabled` + kapanış nedeni ile yapılır. |
| K12 | Numara başına limit **"kontak satırı + numara" bazlıdır** ve o result tablosunu kullanan tüm kampanyaları kapsar. Müşteri tacizi koruması numaranın kendisi için olduğundan bilinçli semantiktir. |
| K13 | Kampanya yöneticileri yeni parametreyi seçerken diğer retry değerlerini de talepteki gibi kendileri girecek (ör. `Overall Attempts` 10 → 60). Mevcut değerler hedef konfigürasyon değildir. |

### Denizbank kampanya envanteri (config DB, 07.09.2026)

Mevcut durum kaydı; **hedef konfigürasyon değil** (bkz. K13).

| id | Ad | Strateji | Overall | MacroMax | MacroInt | RetryDelay | countAll | Result tablosu |
|---|---|---|---|---|---|---|---|---|
| 54 | DENIZBANK_TAHSILAT | 2 (`..._SCHEDULE_NEXT_NUMBER`) | 10 | 10 | 24 | 0 | ✓ | DENIZBANK_GENEL |
| 55 | DENIZBANK_TAHSILAT_2 | 5 (`RETRY_RULES_MANAGES_CYCLE`) | 10 | 10 | 24 | 0 | ✓ | DENIZBANK_GENEL |
| 56 | DENIZBANK_TAHSILAT_3 | 5 | 10 | 10 | 24 | 5 | ✓ | DENIZBANK_GENEL |
| 63 | ..._AKTIVASYON_INAKTIF | 5 | 9 | 9 | 24 | 15 | ✓ | DENIZBANK_TELEMARKETING_AKTIVASYON |
| 64 | ..._AKTIVASYON_NEVERAKTIF | 5 | 9 | 9 | 24 | 0 | ✓ | DENIZBANK_TELEMARKETING_AKTIVASYON |

Tümünde `col_result_attempts = "ATTEMPTS"`, `col_result_retry_history = "retry_history"`, `col_result_call_process = "call_process"`, `col_result_cb_history = "cb_history"` **eşli**; `timezone_type = 0` (OFF), `col_call_from_time` / `col_call_to_time` **NULL**.

**Kapsam açısından kritik:** kampanyaların **dördü strateji 5** (`RETRY_RULES_MANAGES_CYCLE`), yalnız biri Round-robin. Yani davranış strateji 5'te de doğru çalışmak zorunda; tasarım tek bir stratejiye özelleşemez.

## İş Etiketi

`feat`

`Öncelik: normal`

## Etkilenen Alanlar

### Backend — engine (yüksek risk, çağrı hot path)

- `engine/dialer/retry/RetryStrategy.java` — template. Numara başına sayım, eligibility predicate, `processRetryNumberWithDNC` genelleştirmesi, erken kapatma guard'ı, kapanış nedeni set'i.
- `engine/dialer/retry/ReachRetryPerNumber.java` — K2 gereği yeni alanı yok sayar; mevcut `retryOveralAttempt` tabanlı davranışı korunur.
- `engine/dialer/retry/ReachRetrySingleNumber.java` — `setRetryNumber()` yürüyüş yapmıyor; per-number limit dolduğunda kaydın kapanması için `isOverallRetryLimitReached()` genişletilir (bkz. A2).
- `engine/dialer/retry/RoundRobinNextNumber.java`, `RoundRobinNextCycle.java`, `RoundRobinPerCycle.java`, `RetryRulesCycle.java` — `isOverallRetryLimitReached()` override'larına ortak guard.
- `engine/dialer/retry/NoEligibleRetryNumberException.java` — mevcut sigorta, yeniden kullanılır.
- `model/Call.java` — sayım kaynağı erişimleri (`getRetryHistory`, `getCallProcessHistory`, `getCbHistory`, `getNumbersInRawData`, `getNumberColumnsInRawData`).
- `engine/dialer/CallProducer.java:589` (`createCallsFromRawData`), `:760` (`extractNumberToCallFromPhoneNumbers`), `:192` (`processAndQueueFetchedCalls`) — fresh çağrıda limit-farkında numara seçimi, hiç aranamayacak kayıtların batch elenmesi, NULL-numara sızıntısının kapatılması.
- `engine/db/CampaignDbOperations.java:1487` (`setFilteredByDncListStatus` deseni) — hiç aranmamış kayıtlar için yeni batch kapatma metodu; `:2160-2288` (`updateCampaignListTableRow`) — kapanış nedeninin koşullu yazımı.

### Backend — config ve contract (düşük risk, additive)

- `model/Campaign.java` — `maxAttemptPerNumber` (`Integer`, nullable, `@Min(1)`, `@Column(name = "max_attempt_per_number")`) ve `colResultCloseReason` (`String`, nullable, `@Column(name = "col_result_close_reason")`).
- `dto/CampaignFormDTO.java` — aynı isimli iki alan (Dozer isim-eşli, ek mapping gerekmez).
- `util/CampaignPropertyCopier.java` — reflection tabanlı generic kopyalama; **değişiklik gerekmez**, yeni alanlar otomatik kapsanır.
- `util/Constants.java` — `DEFAULT_CAMPAIGN_COLUMN_NAMES` (`:900-942`) içine `CLOSE_REASON("close_reason")`; `RESULT_FIELDS_ALLOWED_TYPES` (`:1174-1265`) içine NVARCHAR ailesi girdisi (`RETRY_HISTORY` satırı `:1185` şablonu); `ALLOWED_TECH_FIELDS` (`:987`) içine `maxAttemptPerNumber`; kapanış nedeni metin sabitleri.
- `service/CampaignDataTypeChecker.java:61-85` — `CLOSE_REASON` için `checkColumn` satırı.
- `engine/db/CampaignDbOperations.java:377` (`createDefaultResultTable`) ve `:442` (`addDefaultResultColumnsToSourceTable`) — yeni tablolarda `close_reason` kolonu.
- `redis/event/interactionnotifier/dto/CampaignInDTO.java` + `mapper/CampaignInMapper.java:74` (`mapRetryFields`) — additive alan; mevcut alanlar **silinmez, yeniden adlandırılmaz**.

### Frontend (AngularJS)

- `webapp/pages/campaignForm.html:1628-1729` (Retry Rules tab) — `Max Attempt Per Phone Number` input; K2 freeze koşulu.
- `webapp/pages/campaignForm.html:963-983` şablonu — `colResultCloseReason` için result-kolon `<select>`'i (`restrictByType` filtresiyle).
- `webapp/js/constants.js:5` (`DEFAULT_CAMPAIGN_COLUMN_NAMES`) ve `:115-118` (`RESULT_FIELDS_ALLOWED_TYPES`) — client mirror'a `CLOSE_REASON`.
- `webapp/js/controllers/campaignFormCtrl.js:152-156` (`initializedResultColumns` listesi), `:596` (`setActualResultColumns`) — yeni binding'in kolon listesine katılması.
- `webapp/js/services/campaignFactory.js:67-93` (`_createNew`) — yeni kampanya default'u (bkz. A4).
- `webapp/js/localization/localization_{tr,en,ru,ua}.js` — `campaign.maxAttemptPerNumber` ve `campaign.colResultCloseReason` etiketleri (4 dosya).
- `resources/ValidationMessages{,_tr_TR,_ru_RU,_ua_UA}.properties` — `campaign.max_attempt_per_number.min` (4 dosya).
- `webapp/WEB-INF/wro.xml` — yeni dosya eklenmiyorsa değişiklik beklenmiyor; sıra kontrolü yapılır.

### Migration

`resources/sql/migration/FEAT-325559-max-attempt-per-number.sql` — elle çalıştırılan script (repo'da Flyway/Liquibase yok, `hbm2ddl` kapalı). FEAT-318332 DDL'indeki `COL_LENGTH(...) IS NULL` idempotent guard deseni kullanılır.

- Config DB (bir kez, tüm kampanyalar için):
  - `ALTER TABLE dbo.pd_campaign ADD max_attempt_per_number INT NULL;`
  - `ALTER TABLE dbo.pd_campaign ADD col_result_close_reason NVARCHAR(255) NULL;`
- Kampanya DB (her camp DB'de bir kez, **tüm result tablolarına**): `close_reason NVARCHAR(255) NULL`. Tablo adı sabitlenmez; result tabloları `complete_date` kolonuyla tanındığı için `sys.tables` üzerinden dinamik keşfedilip kolonu olmayanlara `sp_executesql` ile tek batch'te eklenir (FEAT-318332 `camp_db.sql` deseni). Binding elle atanmaz, uygulama tarafından otomatik atanır (K18).

### Test

- `src/test/java/tests/dialer/` — karakterizasyon + yeni davranış testleri.
- Mevcut pinning testler: `RetryLimitTest`, `RetryStrategyFactoryTest`, `RetryStrategyTemplateTest`, `RoundRobinNextCycleTest`, `RoundRobinNextNumberTest`, `RoundRobinPerCycleTest`, `RetryDncExhaustionInfiniteLoopRegressionTest`, `CallProducerTest`, `CallOriginatorTest`, `CampaignInMapperTest`.
- `src/test/angular/` — campaign form alanı ve K2 freeze davranışı.

## Mevcut Davranış

### Retry kararının tek chokepoint'i

`RetryStrategy.createRetry(Call)` (`RetryStrategy.java:40`). Çağrı noktaları:

| Nokta | Dosya |
|---|---|
| Normal hangup sonrası | `engine/asterisk/ami/ManagerConnectionDecorator.java:1450` (`manageRetries`) |
| LSC/originate iptali | `engine/dialer/CallOriginator.java:678` (`cancelCall`) |
| AMI bağlantı kaybı | `engine/dialer/CallOriginator.java:771` (`cleanUpCallsOnConnectionLoss`) |
| Call expire temizliği | `engine/CallCleaner.java:58` (`clearExpiredCall`) |

`createRetry` akışı:

```
if ((isCallAnswered() && !isResultCodeAllowsRetries()) || isOverallRetryLimitReached())
    completeCallWithoutRetry()
else
    callback dalı  ->  retryNumber = callbackNumber, callbackDate/Number temizlenir
    veya setRetryNumber()          [NoEligibleRetryNumberException -> complete]
    setRetryDateAndIncrementRetryCounts()
    updateRetryHistory()
```

### Başarılı kapanış kuralı (talebin "netleştirme" istediği nokta)

`createRetry:43` bugün şu davranışı üretiyor:

| TRC | URC | Sonuç |
|---|---|---|
| 16 (`NORMAL_CLEARING`) | yok | **kayıt kapanır** (`isResultCodeAllowsRetries()` false → `!false` = true) |
| 16 | final status | **kayıt kapanır** |
| 16 | final değil | retry devam eder |
| 16 | DNC identifier | retry devam eder (diğer numaralara geçilir) |
| 16 dışı | — | retry devam eder (limitler dâhilinde) |

Yani "müşteri yanıtladı + kesin sonuç → süreç kalıcı sonlanır" kuralı **zaten uygulanıyor**. Talep bunun korunmasını istiyor; yeni limitin bu kararı ezmemesi gerekiyor.

### Strateji bazında numara başına fiili tavan

| # | Strateji | Sınıf | Numara başına fiili tavan | `processRetryNumberWithDNC` çağırıyor mu? |
|---|---|---|---|---|
| 0 | `REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER` | `ReachRetrySingleNumber` | tek numara aranır | **Hayır** (yürüyüş yok) |
| 1 | `REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER` | `ReachRetryPerNumber:47` | **zaten numara bazlı**, limit = `retryOveralAttempt` | Evet (override, limit+DNC) |
| 2 | `..._OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER` | `RoundRobinNextNumber` | `retryOveralAttempt / dolu numara` | Evet |
| 3 | `..._OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_CYCLE` | `RoundRobinNextCycle` | `retryOveralAttempt / dolu numara` | Evet |
| 4 | `..._PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER` | `RoundRobinPerCycle:23` | `retryOveralAttempt` (numara sayısıyla ölçekli) | Evet |
| 5 | `RETRY_RULES_MANAGES_CYCLE` | `RetryRulesCycle` | result code schedule'a bağlı | Evet |

Strateji 4 tavanı `retryOveralAttempt * numberSize` ile ölçeklediği için numara başına sabit kalıyor; talep metnindeki "bu strateji de boş numaralar yerine dolu numaraları döngüye sokup limit üstü arar" ifadesi **kodla uyuşmuyor**.

### Numara halkası ve mevcut atlama makinesi

- `Call.getNextNumberFromRawData()` (`Call.java:640`) → `findNextNumberToCall()` (`:671`) halkada bir sonraki numarayı verir; son aranan numara kolonlarda bulunamazsa **aynı numarayı** döndürür.
- `RetryStrategy.advanceRetryNumberPastIneligible(Predicate)` (`:178`) uygun olmayan numaraları atlar; yürüyüş en fazla bir tam turla sınırlı, aday kalmazsa `NoEligibleRetryNumberException`. 03.08.2026 sonsuz döngü vakasından sonra eklenen sigorta.
- `RetryStrategy.processRetryNumberWithDNC()` (`:167`) bugün yalnız DNC'ye bakıyor; `ReachRetryPerNumber:43` bunu override edip limiti de ekliyor.

**Sonuç: atlama ve erken kapatma iskeleti hazır.** Yeni limit, predicate'e eklenecek bir koşuldan ibaret.

### Boş numaraların düşmesi

`DBEngine.java:462` ResultSet→JSON dönüşümünde `if (rs.getString(j) != null)` — **NULL kolonlar satıra hiç konmuyor**. `Call.getNumberColumnsInRawData()` (`:650`) ve `getNumbersInRawData()` (`:659`) yalnız dolu kolonları döndürüyor. Müşterinin tarifi birebir doğru.

### Kalıcılık: sayım kaynağı kolonlar

| Campaign binding | Default ad | Tip | Yazan yer | Semantik |
|---|---|---|---|---|
| `colResultRetryHistory` | `retry_history` | `NVARCHAR(MAX)` | `RetryStrategy.java:109` | `;` ayrılmış, retry **planlanırken** sıradaki numara |
| `colResultCallProcess` | `call_process` | `NVARCHAR(MAX)` | `Call.java:778` | `;` ayrılmış `F`/`R`/`CB` izi |
| `colResultCbHistory` | `cb_history` | `NVARCHAR(MAX)` | `Call.java:766` | `,` ayrılmış, callback **oluşturulurken** numara |
| `colResultAttempts` | `attempts` | `INT` | `CampaignDbOperations.java:2261` | kayıt bazlı toplam deneme |

Kritik semantik: `retryHistory` retry planlanırken yazıldığı için kaydın **fresh (ilk) araması listede yok**. Kodun 4 yerinde `+1 is for first call` varsayımı bunu doğruluyor (`RoundRobinPerCycle.java:36`, `:60`, `:76`, `CallAttemptPolicy.java:50`). Bu yüzden `retryHistory`'ye fresh numarayı eklemek **kabul edilemez**: `anyRetryLeft`, macro hesabı, cycle indeksi ve attempt politikası topluca kayar.

### Aranmadan kayıt kapatma: iki mevcut precedent

- `DNCEngine.filterCallsByDncs()` (`:600`) → `CampaignDbOperations.setFilteredByDncListStatus()` (`:1487`): `complete_date` + `telephony_rc = 76` + `is_disabled` + `user_id/user_rc = NULL`, ardından CDR.
- `JITExpireValidator.applyExpireFilterToCalls()` (`:92`) → `setFilteredByExpireStatus()` (`:1677`).
- Producer hook: `CallProducer.processAndQueueFetchedCalls()` (`:192`) → DNC → expire → `dataQueue`.

### Fresh çağrıda numara seçimi

`CallProducer.createCallsFromRawData:615`:

```java
call.setCalledNumber(ObjectUtils.firstNonNull(
    trimPhoneNumber(call.getCallbackNumber()),
    trimPhoneNumber(call.getRetryNumber()),
    extractNumberToCallFromPhoneNumbers(jsonRow)));
```

`extractNumberToCallFromPhoneNumbers` (`:760`) colSort'a göre **ilk dolu** kolonu seçer, geçmişe hiç bakmaz. Retry kayıtlarında numara `retry_number`'dan geldiği için zaten limit-farkındadır; ancak **geçmişi olan fresh kayıt** (Kriter 4 ve Kriter 8) bu yoldan geçer ve limitli numarayı seçebilir.

## Beklenen Davranış

### Ekran — Campaign Form → Retry Rules

- Yeni alan `Max Attempt Per Phone Number`: `number`, `min=1`, **opsiyonel** (`ng-required` yok).
- Boş → feature off, mevcut davranış birebir korunur.
- `retryScheduleEnabled` kapalıysa disabled (mevcut retry alanlarıyla aynı).
- **K2:** `retryStrategy == 1` iken freeze (`ng-disabled`) ve backend'de yok sayılır (API/Redis'ten dolu gelse bile).
- Yetki: `EditCampaignTech` → `ALLOWED_TECH_FIELDS`.
- `synchronizeAttempts` (`campaignFormCtrl.js:591`) mantığına dokunulmaz; yeni alan `macroMaxAttempt`/`retryOveralAttempt` ile otomatik senkronize edilmez.

### Ekran — Campaign Form → result kolon binding'i

`colResultCloseReason` için `colResultRetryHistory` (`campaignForm.html:963-983`) şablonunun aynısı: `callResultColumns` listesinden `restrictByType` ile filtrelenmiş `<select>`. Farkı: **`ng-required` yok** (bkz. A4) — aksi halde kolonu henüz eklenmemiş her mevcut kampanya kaydedilemez hâle gelir.

### Limit hiyerarşisi

Talebin tarif ettiği katmanlı yapı; üç limit **birlikte** uygulanır ve kayıt hangisi önce dolarsa durur:

1. `Overall Retry Limit` (`retryOveralAttempt`) — kaydın toplam çağrı tavanı.
2. Macro aralık (`macroMaxAttempt` + `macroInterval`) — pencere başına deneme ve pencereler arası bekleme.
3. `Max Attempt Per Phone Number` (**yeni**) — tekil numaranın mutlak tavanı.

"Genel limitlerden bağımsız çalışan" ifadesi, yeni limitin **genel limitten türetilmemesi** anlamındadır; genel limiti geçersiz kılması anlamında değildir. Talebin kendi hiyerarşi bölümü de üçünü birlikte listeliyor.

Pratik sonucu: numara başına limitin devreye girebilmesi için genel limitin `perNumber × dolu numara sayısı` mertebesinde olması gerekir (Kriter 2'nin kendi örneği: per-number 10, 4 dolu numara, overall 60 → 40'ta kapanır). Kampanya yöneticisi bunu K13 gereği kendisi kurgulayacak. UI'da `maxAttemptPerNumber >= retryOveralAttempt` kombinasyonu için **bloklamayan bilgilendirme** değerlendirilir (numara sayısı kayıt bazında değiştiği için sert validasyon doğru olmaz).

> Terminoloji notu: talepte "Cycle / Macro Interval Limit: kaydın girebileceği maksimum tur sayısı" deniyor. Kodda **ayrı bir maksimum tur sayısı limiti yok**; `macroMaxAttempt` pencere başına deneme adedidir ve toplam, genel limitle sınırlanır. Bu iş kapsamında yeni bir "max cycle" alanı **istenmiyor** (talep tek yeni parametre tanımlıyor); ileride yanlışlıkla ayrı bir alan olarak yorumlanmaması için kayda geçirilmiştir.

### Numara başına sayım (yeni sayaç kolonu yok, callback dahil)

K3 gereği tek liste saymak yetmez. Bunu mümkün kılan gözlem: `call_process` izi ile `retry_history` ve `cb_history` **kilitli adımlarla** yazılıyor.

- `RetryStrategy.updateRetryHistory()` (`:108-111`): `retryHistory.add(retryNumber)` **ve** `addRetryCallProcessEntry()` (→ `"R"`) birlikte.
- `Call.addCallbackHistoryEntry()` (`:766-772`): `cbHistory.add(callbackNumber)` **ve** `appendCallProcessStep("CB")` birlikte.
- `Call.ensureCallProcessStartsWithFresh()` (`:783-787`): ilk adım her zaman `"F"`.

Dolayısıyla `call_process` bir **kursor**dur ve arama dizisi birebir yeniden kurulabilir:

```
sequence = []
rIdx = 0; cbIdx = 0
for step in callProcessHistory:            // "F;R;R;CB;R;..."
    F  -> sequence.add(firstDialedNumber)
    R  -> sequence.add(retryHistory[rIdx++])
    CB -> sequence.add(cbHistory[cbIdx++])

count(n) = |{ i : sequence[i] == n }|
```

- `firstDialedNumber` = colSort'a göre ilk dolu enabled kolon değeri; `extractNumberToCallFromPhoneNumbers` (`:760`) ile aynı kural.
- Sayım anahtarı **ham telefon kolonu değeri**dir; bugün `ReachRetryPerNumber` ve `setRetryNumber` de bu değeri kullanıyor. LSC'nin baştaki sıfırı kırpması (`LSCEngine.java:801`) `called_number`/`retry_number` *result* anahtarlarına yazıyor, telefon kolonlarına değil → sayım tutarlı kalır.
- Aynı numara iki kolonda ise değer bazında tek sayılır (03.08.2026 duplicate-key düzeltmesinin semantiği).
- **Muhafazakârlık (bilinçli):** `R`/`CB` adımları arama **planlandığında** yazılıyor, gerçekleştiğinde değil; dizinin son elemanı henüz yapılmamış olabilir, sayaç en fazla 1 fazla sayar. Over-dialing korumasında bu yönde hata yapmak doğrudur.
- **Dayanıklılık:** `call_process` veya `cb_history` eşli değilse ya da uzunluklar tutarsızsa güvenli fallback: `count(n) = occurrences(n, retryHistory) + (n == firstDialedNumber ? 1 : 0)`. Throttle'lı WARN ile loglanır. Denizbank'ın 5 kampanyasında üç kolon da eşli olduğu için ana yol geçerlidir.

### Kabul kriterlerinin koda eşlenmesi

| Kriter | Beklenen | Uygulama noktası |
|---|---|---|
| 1 — limit dolan numara atlanır, sıradakine geçilir | Tel-1 limitte → Tel-2 aranır | `processRetryNumberWithDNC()` predicate'i `DNC \|\| perNumberLimitReached` olur; 6 stratejinin tamamı ortak template'ten geçer |
| 2 — aktif numaraların hepsi dolunca kayıt kapanır (overall dolmasa da) | 4 dolu × 10 = 40 → kapanır (overall 60) | `isOverallRetryLimitReached()`'e ortak `noEligibleNumberLeft()` guard'ı; ayrıca mevcut `NoEligibleRetryNumberException` → `completeCallWithoutRetry()` yolu ikinci savunma |
| 3 — dengeli dağılmış geçmiş; Tel-2'ye 11. arama asla çıkmaz | Tur tur atlama | Aynı predicate; sayım dizisinden türer |
| 4 — Tel-1 limitte, Tel-2/3 sıfır → ilk çağrı Tel-2'ye | **Fresh çağrıda** limit-farkında seçim | `CallProducer.createCallsFromRawData:615` — `extractNumberToCallFromPhoneNumbers` limit-farkında hâle getirilir |
| 5 — TRC/URC tipinden bağımsız sayaç | Sonuç koduna bakılmaz | Sayım yalnız arama adedine bakar; mevcut yazım zaten sonuç kodundan bağımsız |
| 6 — callback vadesi, limit dolmamış → aranır | Normal akış | Değişiklik yok |
| 7 — callback vadesi, limit dolmuş → aranmaz, kayıt kapanır, agent ekranına düşmez | Originate edilmez | Producer fetch aşamasında batch eleme (callback'ler de aynı yoldan geçiyor) + kapanış nedeni |
| 8 — kapanmış kayda yeni numara eklenip aktive edilirse yalnız yeni numara aranır | Eski numaralar atlanır | Sayım `retry_history`/`cb_history`'den türediği için yeni numaranın sayacı 0; K6 sayesinde kolonlar korunuyor |
| Başarılı kapanış | Yanıt + kesin sonuç → derhal ve kalıcı sonlanır | `createRetry:43` mevcut kuralı **korunur**; per-number kontrolü bu kararı ezmez ve bu yolda kapanış nedeni **yazılmaz** |
| Tüm numaralar NULL | Hiç aranmadan statüyle kapanır | `CallProducer` — D1'deki sızıntının kapatılması |

### Kapanış nedenleri

K11 gereği hiçbir yolda yeni TRC id veya CDR satırı üretilmez.

| Yol | Gerçek çağrı sonucu | Yazılan |
|---|---|---|
| Son çağrıdan sonra tüm numaraların limiti doldu | Var — **TRC/URC korunur, EZİLMEZ** | `complete_date` + `is_disabled` + kapanış nedeni |
| Hiç aranmamış: tüm numaralar NULL | Yok | `complete_date` + `is_disabled` + kapanış nedeni |
| Hiç aranmamış: tüm numaralar limit dolu | Yok | `complete_date` + `is_disabled` + kapanış nedeni |
| Callback vadesi geldi, limit dolu (Kriter 7) | Yok (originate edilmiyor) | `complete_date` + `is_disabled` + kapanış nedeni |
| **Başarılı kapanış (yanıt + kesin sonuç)** | Var | `complete_date` — **kapanış nedeni YAZILMAZ** |

Talep metni üç ayrı statü adı veriyor ("Numara Limitleri Tamamlandı / Max Per Number Limit Exceeded", "Max Attempt Reached on Callback", "Geçerli Numara Bulunamadı / Limit Dolu"); K5 bunlardan birinin metnini veriyor. Kalan metinler A1'de.

Kapanma **kalıcıdır (K10)**: üç fetch sorgusu da `complete_date IS NULL` şartı taşıyor (fresh `:827`, retry `:982`, callback `:2709`); macro aralık yalnız açık kayıtların `retry_date`'ini öteler. Kaydı yalnız müşterinin veri manipülasyonu geri açabilir (Kriter 8).

Tamamlanma sayaçları etkilenmez: `countCompletedCalls` (`:2055`) `complete_date IS NOT NULL OR is_disabled = 1 OR is_fetched IS NOT NULL OR attempts >= overall` kullanıyor; `telephony_rc` koşulu **yok**.

Yazım mevcut `insertCallResultToDB` UPDATE'ine eklenir (`:2160-2288`) → **ek DB round-trip yok**. Yazım **koşulludur**: yalnız gerçekten bir kapanış nedeni üretildiğinde ve `colResultCloseReason` eşliyken SET listesine girer (`CUSTOMER_NAME`/`RELATION` deseni, `DISABLED` gibi koşulsuz değil). Hiç aranmamış yol için `setFilteredByDncListStatus` benzeri tek batch UPDATE.

### Kalıcılık ve ölçek (zorunlu)

- Numara başına **sayaç** kolonu eklenmez. Config DB'de iki yeni kolon (`max_attempt_per_number`, `col_result_close_reason`), müşteri result tablosunda bir kolon (`close_reason`).
- Sayım JVM'de tutulmaz; her `Call` fetch'te result tablosundan hydrate edilir. Yeni static mutable state yok, yeni runtime counter yok.
- Yatay ölçek: karar tamamen kaydın persisted geçmişinden türer, node-local değildir.
- Paylaşımlı result tablosu (K12): limit "kontak satırı + numara" bazlıdır ve o satırı kullanan tüm kampanyaları kapsar.

## Kod Okuma Bulguları

### F1 — Over-dialing mekanizması doğrulandı

`RetryStrategy.anyRetryLeft()` (`:150`) `countAllCallTypes` açıkken `call.getAttempts() < retryOveralAttempt`, kapalıyken `retryHistory.size() < retryOveralAttempt` döner. Strateji 2 ve 3'te `isOverallRetryLimitReached()` bu tek koşula iner; `retryOveralAttempt` kayıt bazlı düz bir tavandır ve round-robin onu yalnız dolu numaralara bölüştürür.

### F2 — Strateji 1 talebin çoğunu zaten karşılıyor

`ReachRetryPerNumber` limiti dolan numarayı atlıyor (`:44`) ve tüm numaralar dolunca kaydı kapatıyor (`:28`). Eksikleri: limit için ayrı alan yok (`retryOveralAttempt` kullanılıyor) ve arama sırası round-robin değil. K2 bu stratejiyi yeni alandan muaf tutuyor.

### F3 — Strateji 5 kapsam dışı bırakılamaz

Denizbank kampanyalarının dördü `RETRY_RULES_MANAGES_CYCLE`. Bu strateji `setRetryNumber()` içinde numarayı result-code-advanced konfigürasyonundan (`SAME`/`NEXT`/`PREVIOUS`, `RetryRulesCycle:115-129`) seçiyor ve ardından `processRetryNumberWithDNC()` çağırıyor — yani ortak predicate genelleştirmesi bu stratejide de otomatik çalışır. Ancak `isOverallRetryLimitReached()`'i result-code-sequence mantığı taşıyor (`:34-36`); ortak `noEligibleNumberLeft()` guard'ı buraya da eklenmelidir, aksi halde tüm numaralar dolduğunda kayıt kapanmaz ve yürüyüş sigortasına düşer.

### F4 — Strateji 0 yürüyüş yapmıyor

`ReachRetrySingleNumber.setRetryNumber()` (`:14-16`) yalnız `calledNumber`'ı set ediyor; `processRetryNumberWithDNC()` çağrısı **yok**. Bu stratejide numara atlama diye bir şey olmadığı için per-number limit ancak `isOverallRetryLimitReached()` üzerinden etki edebilir (bkz. A2).

### F5 — Fresh çağrı numara seçimi geçmişe bakmıyor

`extractNumberToCallFromPhoneNumbers` (`:760`) colSort'a göre ilk dolu kolonu seçiyor. Kriter 4 ve Kriter 8 tam olarak bu yolu hedefliyor; limit-farkında hâle getirilmesi gerekiyor.

### D1 — Tüm numaraları NULL olan kayıt sessizce sızıyor (mevcut defect, talep kapsamında)

`extractNumberToCallFromPhoneNumbers` hiç dolu numara yoksa `IllegalStateException` atıyor (`:768`); `createCallsFromRawData` catch'i (`:627`) sadece `LOG.warn` basıyor. Kayıt `is_fetched` işaretli (`:165` tüm batch'e uygulanıyor) ama `Call` hiç oluşmamış → ne aranıyor ne kapanıyor, `complete_date` NULL kalıyor. Talebin "Geçerli Numara Bulunamadı statüsüne çekilmeli" maddesi bu sızıntının kapatılmasıdır.

### D2 — Callback numarası telefon kolonlarından biri olmak zorunda değil

`WebphoneSubscriber.manageCallback()` (`:633-658`): agent serbest numara girebiliyor; `getColumnToUpdateByCallbackNumber()` (`:670`) numara mevcut kolonlarda yoksa `null` dönüyor ve numara hiçbir kolona yazılmıyor. Böyle bir numaranın per-number limiti kodda tanımsız (bkz. A3). Ek olarak `findNextNumberToCall()` (`:671-684`) son aranan numarayı kolonlarda bulamazsa **aynı numarayı** döndürüyor → halka o numarada kilitlenir ve bounded-walk sigortası devreye girer.

### D3 — Kapanış statüsünde TRC ezilmemeli

Son çağrının gerçek TRC/URC'sinin üzerine yazılması, geçmişte URC/TRC kaybı ailesini (trc 73/75/78/81/84) üreten hata sınıfıdır. Bu yüzden kapanış nedeni ayrı alanda tutulur ve TRC'ye dokunulmaz.

### D4 — `retry_history` kolon boyutu: sayaç string'in kendisi

Sayaç ayrı kolonda tutulmadığı için `retry_history` string'i sayacın kendisidir. Her arama ~12 karakter ekler (11 haneli numara + ayraç, ör. `05xxxxxxxxx;`); `NVARCHAR(255)` ~21 aramada dolar. SQL Server **sessizce kırpmaz, UPDATE'i hata ile düşürür** ("String or binary data would be truncated"); bu UPDATE `insertCallResultToDB`'nin tek büyük UPDATE'i olduğundan tüm çağrı sonucu yazımı kaybolur ve `catch (Exception e) { LOG.error(...) }` (`:2100-2102`) ile yutulur.

Default DDL `NVARCHAR(MAX)` üretiyor (`:377`) ama eski result tabloları sınırlı tipte olabilir. Kontrol tek tabloya değil, **camp DB'deki tüm result tablolarına** uygulanır — migration script'inin `2.3` bölümü sınırlı tipli her kolonu listeler, `2.4` bölümü gerekli `ALTER COLUMN ... NVARCHAR(MAX)` ifadelerini **üretir ama çalıştırmaz**.

`NVARCHAR(MAX)`'a geçiş metadata-only bir işlem değildir; büyük kampanya tablolarında tablo yeniden yazılabilir ve uzun süreli kilit oluşabilir. Bu yüzden DBA çıkan ifadeleri gözden geçirip uygun bakım penceresinde kendisi çalıştırır. Genel limit 60'a çıkarıldığında (K13) bu kontrol zorunludur.

### D5 — Yan tespit (bu iş kapsamında değil)

`resources/sql/migration/FEAT-321997-agent-callback-reset-strategy.sql` `ALTER TABLE campaign` yazıyor; entity `pd_campaign` map ediyor (`Campaign.java:50`) ve FEAT-318332 prod DDL'i `dbo.pd_campaign` kullanıyor. O script'te tablo adı hatalı görünüyor. Yeni migration `pd_campaign` ile yazılacak.

### B1 — Mevcut kayıt tavanının "+1" sızıntısı (K9: araştırılmıyor, kayıt için)

Camp 54 (`retryOveralAttempt = 10`, `countAllCallTypes = true`) için beklenen tavan kayıt başına 10 arama. `pd_cdr` ölçümü (`campaign_id = 54`, `call_start_time >= 2026-08-30`, ~8 gün): 5383 **distinct** kontakta tek bir numara tavanı aşmış — 11=4718, 12=492, 13=114, 14=51, 15=7, 16=1. Kütlenin %87,6'sı tam 11'de.

İki aday: (1) mükerrer `pd_cdr` satırı (12.08.2026 `saveInitialCDR` ∥ `persistFinalCdr` yarışı) → ölçüm artefaktı; (2) attempt sayacının bir dial için artmaması — `Constants.TELEPHONY_RESULT_CODES_EXCLUDED_FROM_ATTEMPTS = {907}` + `CallAttemptPolicy.restoreForNonAttemptResultIfNeeded()` (`:28`) ve `restoreForDeferredOriginationIfNeeded()` (`CallOriginator.java:633`).

İhtiyaç duyulursa ayırt edici sorgu:

```sql
-- Satır dönerse aday 1 (mükerrer CDR), dönmezse aday 2 (gerçek fazla arama).
SELECT interaction_id, COUNT(*) AS row_count
FROM pd_cdr
WHERE campaign_id = 54 AND contact_id = 803671
GROUP BY interaction_id
HAVING COUNT(*) > 1;
```

### Konfigürasyon gözlemleri (mevcut durum kaydı, feature işi değil)

- Camp 54'te `retry_delay = 0` → `calculatePlannedRetryDate()` (`:116`) `retryDate = now` verir ve `fetchDataForProducerRetries` (`:987`) satırı hemen tekrar çeker.
- Camp 54'te `timezone_type = 0` ve saat kolonları NULL olduğundan `isPlannedRetryDateExceedScope()` (`:137`) her zaman `false`; strateji 2'de result-code-schedule saat penceresi de yok. Yani retry planlamasında saat kısıtı bulunmuyor.
- `campaign.getResultCodeSchedules()` yalnız `RetryRulesCycle` tarafından okunuyor (`:55`, `:68`). Camp 54'te (strateji 2) Result Code Schedule List satırları — `Retry Amount 10`, `30/60/120 dk`, `09:00-19:50` — **uygulanmıyor**; camp 55/56/63/64'te (strateji 5) uygulanıyor.

## Riskler

| # | Risk | Etki | Azaltma |
|---|---|---|---|
| R1 | `processRetryNumberWithDNC` predicate'i 6 stratejide birden değişiyor | Alan boş olan diğer müşterilerin kampanyaları da bu koddan geçer | Alan boş/`<=0` iken predicate birebir eski DNC-only davranışa düşer; 6 strateji için karakterizasyon testi **önce** yazılır ve feature-off regression'ı ile korunur |
| R2 | Strateji 1'de çift limit | Mevcut Strateji 1 müşterilerinde davranış değişimi | **K2 ile kapandı**: alan freeze + backend'de yok sayılır. Regression: strateji 1 + alan dolu → davranış bugünküyle birebir aynı |
| R3 | Strateji 5'te ortak guard eklenmezse | Tüm numaralar dolduğunda kayıt kapanmaz, yürüyüş sigortasına düşer | F3'teki `noEligibleNumberLeft()` guard'ı `RetryRulesCycle.isOverallRetryLimitReached()`'e de eklenir; strateji 5 için ayrı test |
| R4 | Sonsuz döngü nüksü (03.08.2026 vakası) | Thread kilitlenmesi, CPU %100, URC kaybı | Bounded-walk sigortası korunur; `RetryDncExhaustionInfiniteLoopRegressionTest` genişletilir, **silinmez** |
| R5 | Erken kapatma agresif olursa erişilebilir müşteri aranmaz | İş kaybı | Sayım muhafazakâr; kapatma öncesi throttle'lı WARN; `pd_cdr` ile önce/sonra karşılaştırma imkânı |
| R6 | Hot path'te sayım maliyeti | Çağrı başına ek CPU | Sayım karar başına **bir kez** memoize edilir (bkz. Performans Etkisi) |
| R7 | `retry_history` truncate (D4) | UPDATE patlar, çağrı sonucu yazımı sessizce kaybolur | Onboarding kontrol sorgusu; genel limit yükseltilmeden önce zorunlu adım |
| R8 | Redis `CampaignInDTO` contract'ı | Downstream tüketici kırılması | Yalnız additive alan; mevcut alanlar silinmez/yeniden adlandırılmaz |
| R9 | `col_result_close_reason` UI'da zorunlu yapılırsa | Kolonu olmayan **tüm** mevcut kampanyalar kaydedilemez | `ng-required` **yok**; yazım koşullu (binding boşsa hiç yazılmaz) |
| R10 | B1'deki "+1" (K9 ile kabul edilen) | Numara başına limit de en fazla 11'e sızabilir | Kabul kriterlerindeki "hiçbir koşulda 11. arama atılmamalı" invariantı test ortamında doğrulanır; üretimdeki +1 ayrı iş |
| R11 | Başarılı kapanışta yanlışlıkla kapanış nedeni yazılması | Rapor yanıltıcı olur, "limit doldu" gibi görünür | Kapanış nedeni yalnız limit yolunda set edilir; testte açıkça assert edilir |

## Performans Etkisi

Değişen alan **çağrı hot path'idir**: `createRetry` her hangup'ta, `processRetryNumberWithDNC` her retry planlamasında çalışıyor.

Mevcut maliyet: `ReachRetryPerNumber.isRetryLimitForNumberReached()` yürüyüşün her adımında `retryHistory`'yi stream'liyor → O(numara × geçmiş). `Call.getNumberColumnsInRawData()` / `getNumbersInRawData()` her çağrıda `Campaign.getEnabledPhoneNumberColumns()`'u (`Campaign.java:1340`, stream + sort + collect) yeniden üretiyor.

Kurallar:

- Numara→sayı haritası **`createRetry` başına bir kez** hesaplanır ve strateji instance'ında memoize edilir. Strateji nesnesi her retry için yeniden üretildiği için (`RetryStrategyFactory.createStrategy`, `CallOriginator.java:744`) instance-level cache güvenlidir; static state gerekmez.
- Telefon kolonu listesi de aynı karar içinde bir kez alınır.
- Hot path'e **yeni DB query, blocking çağrı, geniş `synchronized` blok veya verbose payload log'u eklenmez.**
- Fetch aşamasındaki eleme **batch** çalışır (DNC/expire deseni): kayıt başına ayrı UPDATE değil, `uniqueKey IN (...)` tek UPDATE.
- Fetch SQL'ine per-number koşulu **eklenmez**: `retry_history` string'i üzerinde sayım non-sargable olur ve kampanya DB'si + `DBEngine` 10 sn sabit timeout'u ile fetch'i patlatır (27.07.2026 FKS_CAPRAZ query timeout vakası).
- Kapanış nedeni yazımı mevcut UPDATE'e eklenir; ek round-trip yok.

Net beklenen etki: retry planlama başına O(numara + geçmiş) tek geçiş; bugünkü O(numara × geçmiş) yürüyüşe göre **azalma**. Toplam dial hacminde kasıtlı düşüş (feature'ın amacı).

## TDD Stratejisi

1. **Karakterizasyon (önce, davranış değişmeden):** 6 stratejinin `isOverallRetryLimitReached` + `setRetryNumber` davranışı; `countAllCallTypes` açık/kapalı; macro tetiklemesi; `createRetry:43` başarılı kapanış matrisi; `retry_history`/`call_process`/`cb_history` yazım lockstep'i (fresh'in listede olmaması dâhil). Format `<Konu><Davranış><TestTipi>Test`, konum `src/test/java/tests/dialer/`.
2. **Feature-off regression:** `maxAttemptPerNumber = null` iken 6 stratejinin çıktısı karakterizasyonla **birebir aynı**. R1'in tek gerçek güvencesi. Ek olarak K2 regression'ı: strateji 1 + alan dolu → bugünkü `ReachRetryPerNumber` davranışı.
3. **Sayım birim testleri:** dizi yeniden kurulumu (`call_process` kursoru ↔ `retry_history`/`cb_history`); tutarsız uzunlukta fallback; `F` adımının ilk dolu kolona atfı; aynı numaranın iki kolonda olması; kolonlarda olmayan callback numarası (D2/A3).
4. **Yeni davranış:** Kriter 1-8 için ayrı testler; özellikle Kriter 3'ün tur simülasyonu ve "Tel-2'ye 11. arama asla çıkmaz" invariantı. Strateji 5 için ayrı erken-kapatma testi (R3).
5. **Sonsuz döngü koruması:** `RetryDncExhaustionInfiniteLoopRegressionTest`'in fuse deseni (`timeout = 10000` + lookup sigortası) per-number senaryosuna genişletilir.
6. **Producer:** tüm-NULL kayıt (D1), tüm-limit-dolu kayıt ve geçmişi olan fresh kayıt (Kriter 4) için `CallProducerTest` genişletilir — `dataQueue`'ya girmediği **ve** kapatıldığı doğrulanır.
7. **Kapanış nedeni:** limit yolunda yazıldığı, **başarılı kapanışta yazılmadığı** (R11) ve binding boşken feature'ın çalışmaya devam ettiği doğrulanır.
8. **Contract:** `CampaignInMapperTest` yeni alanlar; Angular form contract testi + `npm.cmd run test:angular`; K2 freeze ve `colResultCloseReason` select'i için Karma/Jasmine testi.

Hedefli doğrulama komutları execution plan'da listelenir. Frontend değiştiği için yalnız Maven testleriyle plan kapatılmaz.

## Önerilen Çözüm Yönü

| Faz | İçerik | Risk |
|---|---|---|
| 1 | Karakterizasyon testleri (TDD 1) | Yok |
| 2 | Config + contract: `maxAttemptPerNumber`, `colResultCloseReason`, `CLOSE_REASON` sabiti + allowed types, `CampaignDataTypeChecker`, DDL (config + result tabloları), `createDefaultResultTable`/`addDefaultResultColumnsToSourceTable`, admin UI (K2 freeze + result kolon select'i), DTO, `ALLOWED_TECH_FIELDS`, 4 localization + 4 ValidationMessages, `CampaignInDTO`/`CampaignInMapper`. Alan boş = feature off. | Düşük (additive) |
| 3 | Engine: arama dizisi yeniden kurulumu + eligibility predicate + `processRetryNumberWithDNC` genelleştirmesi + ortak `noEligibleNumberLeft()` guard'ı (6 strateji, F3/F4 dâhil) + kapanış nedeni (Kriter 1-5 + başarılı kapanış korunması) | **Yüksek** (call hot path) |
| 4 | Producer: fresh çağrıda limit-farkında numara seçimi (Kriter 4/8), tüm-NULL sızıntısının kapatılması (D1), tüm-limit-dolu ve limitli callback için batch eleme (Kriter 7) | Orta (producer path) |
| 5 | `docs/architecture/DIALER-RUNTIME.md` ve `DATA-PERSISTENCE-AND-DB.md` güncellemesi; onboarding checklist (D4 kolon tipi, `close_reason` kolonu, binding ataması) | Düşük |

## Açık Sorular ve Netleştirme İhtiyacı

Tümü 08.09.2026'da geliştirici onayıyla kapandı; öneriler aynen kabul edildi.

| # | Soru | Karar |
|---|---|---|
| A1 | Talep üç ayrı statü adı veriyor; K5 yalnız birinin metnini verdi. Diğer iki metin? | **K14:** `close_reason` için üç kod sabiti, verilen örneğin diliyle: limit dolan numaralar → `"closing for reached number limit"`; callback vadesinde limit dolu → `"max attempt reached on callback"`; hiç geçerli numara yok → `"no callable number available"`. Lokalize edilmez, raporda ham değer görünür. |
| A2 | Strateji 0 (`REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER`) tek numara arıyor ve numara atlama yapmıyor (F4). Alan uygulanacak mı, freeze mi? | **K15:** uygulanır, freeze edilmez. `isOverallRetryLimitReached()`'e eklenmesi yeterlidir; fiilen `min(overall, perNumber)` gibi davranır. |
| A3 | Telefon kolonlarından biri olmayan callback numarasının per-number limiti nasıl sayılacak (D2)? | **K16:** kendi değeriyle sayılır; dizide `CB` adımı olarak yer aldığı için sayacı doğal oluşur ve limiti dolduğunda o numaraya callback tetiklenmez. Muaf tutmak over-dialing kaçağı yaratacağı için reddedildi. |
| A4 | `col_result_close_reason` UI'da zorunlu mu, ve `maxAttemptPerNumber` yeni kampanyalarda default alacak mı? | **K17:** binding **opsiyonel** (`ng-required` yok) — zorunlu yapılırsa kolonu henüz eklenmemiş tüm mevcut kampanyalar kaydedilemez (R9). `maxAttemptPerNumber` yeni kampanyalarda **boş** kalır. |

### K18 — Kapanış nedeni kolonunun otomatik binding'i (uygulama sırasında eklendi)

`close_reason` kolonu result tablosunda mevcutsa binding **otomatik atanır**; operatörün elle seçmesi gerekmez. Repoda bunun iki yerleşik deseni var ve ikisi de kullanılacak:

- Frontend: `campaignFormCtrl.js:531-539` `setDefaultValuesForUndefinedResultTableColumns()` → `setDefaultValueForResultColumn('close_reason')` (`callback_date`, `callback_number`, `user_result_code`, `user_id` ile aynı yol).
- Backend: `CampaignDataTypeChecker.java:151-170` `applyDefaultHistoryColumnMappings()` / `applyDefaultColumnMapping()` → `CLOSE_REASON` (bugün `cb_history` ve `call_process` için aynısı yapılıyor).

Bu, A4/R9'un kalan onboarding yükünü de kaldırır: DDL çalıştırıldıktan sonra binding kendiliğinden oluşur, elle konfigürasyon adımı kalmaz.

**Plan durumu: `Uygulanabilir`.** Açık soru kalmadı (K1-K18).

## Execution Plan Zorunluluğu

Execution plan oluşturuldu: `docs/execution-plan/07.09.2026/FEAT-325559-NUMARA-BASINA-AZAMI-ARAMA-LIMITI.md` (08.09.2026, A1-A4 kapandıktan sonra). Plan faz bazlı TDD test planını, hedefli doğrulama komutlarını, `Performans Etkisi` bölümünü ve başarılı test kapanış kriterini içerir.

## D6 — Numara başına sayacın iki katmanda farklı anlam taşıması (bağımsız inceleme, 08.09.2026)

Bu bölüm Faz 1-5 uygulandıktan **sonra** yapılan bağımsız kod incelemesinin bulgusudur. Yukarıdaki analiz içeriği tarihsel kayıt olarak korunmuştur; bu bulgu analizin **eksik kalan** bir noktasını kapatır.

### Bulgu

Analizin `Numara başına sayım (yeni sayaç kolonu yok, callback dahil)` bölümü, sayacın kaynağını doğru tespit etti (`call_process` izi + `retry_history`/`cb_history` kilitli yazımı) ancak **tek bir okuma** varsaydı. Gerçekte limit iki katmanda uygulanıyor ve iki katman zaman ekseninin farklı noktalarında çalışıyor:

| Katman | Çalışma anı | İzin son girdisinin anlamı |
|---|---|---|
| `RetryStrategy.createRetry` | hangup **sonrası** | fiilen yapılmış arama |
| `CallProducer.resolveCalledNumber` | arama **öncesi** | henüz yapılmamış **plan** |

Analiz `R`/`CB` adımlarının arama **planlandığında** yazıldığını zaten kaydetmiş ve bunu "muhafazakârlık: sayaç en fazla 1 fazla sayar, over-dialing korumasında bu yönde hata yapmak doğrudur" diye yorumlamıştı. **Bu yorum yanlıştı.** Producer arama öncesi okuduğu için "1 fazla sayma" masum değil: numaranın **son hakkını yakar** ve fiili limiti `maxAttemptPerNumber - 1`'e düşürür.

Ölçülen sapma (`MaxAttemptPerNumberEndToEndDialLoopTest`, düzeltme öncesi): limit 3 → 2 arama, limit 40 → 36 arama, limit 1 → **0 arama** (numara hiç aranmaz).

### Neden Faz 1-4 testlerinde görünmedi

Her katman **kendi içinde** tutarlıydı. Strateji testleri hangup sonrası durumdan başlıyor, producer testleri arama öncesi durumdan; ikisi de kendi varsayımıyla doğru sonucu üretiyordu. Sapma yalnız **producer → dial → strateji → persist** round-trip'inde ortaya çıkıyor ve Faz 1-4'te round-trip testi yoktu.

Bu, analizin `TDD Stratejisi` bölümüne eklenen ders: **iki katmanda uygulanan bir invariant için katman testleri yeterli değildir; katmanlar arası round-trip testi zorunludur.**

### Türev bulgular

- **D6a — Limitli plan kaydı kalıcı kapatıyordu.** Planlanan retry/callback numarası limitini doldurmuşsa producer `null` dönüp kaydı `setClosedWithoutDialing` ile kapatıyordu; kayıttaki **diğer** numaraların kalan hakkı hiç bakılmadan yakılıyordu. Kabul kriteri 3 bunun tersini istiyor (limitli numara pas geçilir, kayıt kalan numaralarla devam eder).
- **D6b — Plan yönlendirmesi iz düzeltmesi gerektiriyor.** D6a düzeltilince iz hâlâ eski planlanan numarayı gösterdiği için o numaranın sayacı şişiyor, fiilen aranan numaranın araması hiç sayılmıyor ve o numara limitinden **bir fazla** arama alıyordu. İz bir dial log'u olduğundan, plan yönlendirildiğinde bekleyen geçmiş kaydının son girdisi fiilen aranan numarayla değiştirilmelidir (yazım zamanı ve semantiği değişmez).
- **D6c — K2 muafiyeti yarım uygulanmıştı.** Producer yalnız `getMaxAttemptPerNumber()`'a bakıyordu. UI'daki strateji 1 freeze'i DB değerini **temizlemediği** için alan dolu kalabiliyor; strateji muaf olmasına rağmen producer limiti uygulayıp kaydı kapatıyordu. Muafiyet kararı tek bir yerde durmalı.
- **D6d — Kapanan kayıt bekleyen plan taşımamalı.** Müşteri kaydı `complete_date`/`is_fetched` boşaltarak yeniden aktive ettiğinde, geçmiş tarihli `retry_date` duran satır FRESH değil RETRY fetch'inden geçiyor; yeni eklenen numara yerine eski (limitli) plan deneniyor ve CDR outdialing tipi yanlış sınıflanıyor. Kapanışta `retry_date`/`retry_number` NULL'lanmalı.
- **D6e — Boş-iz kısayolu eski kayıtları eksik sayıyordu.** `call_process` boş ama `retry_history` dolu olan (kolon sonradan eşlenmiş) satırlarda kısayol tek arama sayıp limitin hiç tetiklenmemesine yol açıyordu.

### Çözüm yönü

Sayacın **iki ayrı okuması** açıkça ayrıştırılır: hangup sonrası okuma (izin tamamı) ve arama öncesi okuma (bekleyen plan düşülür). Yazım tarafına — `retry_history`, `call_process`, `cb_history` kolonlarının yazım zamanı ve semantiğine — **dokunulmaz**; eklenen yalnızca ikinci bir okumadır. Uygulama ve doğrulama ayrıntısı execution plan'ın `Faz 6` bölümündedir.

**Analiz durumu: `D6 kapandı` (Faz 6, 08.09.2026).**

## D7 — İkinci inceleme turunun bulguları (08.09.2026)

Faz 6 kabul edildikten sonra yapılan ikinci bağımsız inceleme, analizin kapatmadığı beş noktayı
daha ortaya çıkardı. Uygulama ayrıntısı execution plan'ın `Faz 7` bölümündedir; burada analiz
düzeyindeki eksik kayda geçirilir.

### D7a — Otomatik binding, kolonun VARLIĞINI yeterli sayıyordu

Analizin `K18 — Kapanış nedeni kolonunun otomatik binding'i` maddesi binding'in kendiliğinden
atanmasını doğru tespit etti ama **tip** boyutunu atladı. `applyDefaultColumnMapping` yalnız
kolonun varlığına bakıyor, hemen ardından çalışan `checkColumn` ise tip uyuşmazlığında
`campaign.start.wrong.types` fırlatıyor.

Sonuç: müşterinin result/CRM tablosunda `close_reason` adında ama farklı tipte (INT/DATE) bir kolon
varsa, **operatör hiçbir seçim yapmadığı hâlde** o kampanya bu sürümden sonra hiç başlamaz.
`close_reason` müşteri şemalarında olası bir addır, dolayısıyla bu tesadüf gerçekçidir. Analizde
R9 "onboarding yükü kalkar" diye yazılmıştı; doğrusu "kalkar **ama** yanlış tipli bir ad çakışması
kampanyayı başlatılamaz hâle getirir".

Aynı zafiyet `cb_history` ve `call_process` için de vardı — bu iş onu ortaya çıkardı, sebebi
değil.

**Doğru davranış:** otomatik binding yalnız tip uygunsa atanır; değilse binding boş kalır ve
özellik çalışmaya devam eder (kapanış nedeni yazılmaz). Operatörün ELLE seçtiği binding'in
doğrulaması zayıflatılmaz.

### D7b — "Opsiyonel" DDL bloğunun kapsamı ilan edilenden geniş

Migration'ın `call_process` / `cb_history` bloğu "opsiyonel, atlanabilir" diye yazılmıştı ama
koşulsuz çalışıyordu. Kolonlar eklendiği anda `applyDefaultHistoryColumnMappings` o DB'deki **tüm**
kampanyalarda binding'i otomatik atar ve `insertCallResultToDB` bu kolonlara yazmaya başlar — yani
bu işin **kapsamı dışındaki** kampanyaların result yazımı da değişir.

Analizin `Kalıcılık ve ölçek (zorunlu)` bölümü bu iki kolonun sayım doğruluğunu artırdığını
yazmış, fakat eklenmesinin **yan etkisini** (kapsam dışı kampanyaların yazım davranışı)
değerlendirmemişti. Bu, DDL kararlarının "zararsız/additive" varsayılmasının sınırıdır: nullable
bir kolon eklemek şemayı bozmaz ama **otomatik binding varsa** davranış değiştirir.

Ayrıca tablo keşfinin `complete_date` kolonuna dayanması, kampanya DB'sinde bu ada sahip alakasız
tabloları da kapsama alır. Kolon nullable ve uygulama yalnız kampanyaya **bağlı** tabloya yazdığı
için zararsızdır; yine de sınır yazılı olmalıdır.

### D7c — Kapanış nedeni, kapanışın gerçek sebebini anlatmıyordu

`noEligibleNumberLeft` DNC'yi de "uygun değil" saydığı için, tüm numaraları DNC'de olan kayıt bu
daldan kapanıp "numara limiti doldu" metnini yazıyordu. Analizin `Kapanış nedenleri` bölümü üç
metni tanımlamış ama **bir kapanışın birden fazla sebebi olabileceğini** modellemiyordu:
`noEligibleNumberLeft` iki farklı elemeyi (DNC + limit) tek doğruya indiriyor, kapanış nedeni ise
tek bir sebep varsayıyor.

Yan tespit: strateji 0 ve 4'te bu dal aynı zamanda **yeni** bir kapanış yoludur — o stratejilerde
eskiden DNC tükenmesi guard'ı yoktu. Kapanışın kendisi iyileştirmedir; sorun etiketiydi.

Producer tarafında da benzer bir etiket hatası vardı: aday kontrolü `getNumbersInRawData()`
üzerinden yapıldığı için (bkz. D7d) geçerli numarası olmayan kayıt "limit doldu" etiketi alıyordu.

**Karar:** yeni metin eklenmez, mevcut üç metnin değeri değişmez; DNC kaynaklı kapanışta neden boş
bırakılır. Gerekçe: yeni bir müşteri-görünür metin iş birimiyle mutabık değil, lokalizasyon kararı
(K14) hâlâ açık ve boş bırakmak özellik-kapalı davranışı birebir korur.

### D7d — Tek bir "numara" tanımı yoktu

Analiz sayacın kaynağını (üç geçmiş kolonu) doğru tespit etti ama **numaraların hangi biçimde
karşılaştırıldığını** hiç sormadı. Kodda iki ayrı örtük tanım vardı:

| Yer | Tanım |
|---|---|
| `Call.getFirstDialedNumber`, `retry_history`, `cb_history` | **ham** kolon değeri |
| `CallProducer.selectFreshNumber`, `isNumberWithinLimit` | **trimlenmiş** değer |
| `Call.getNumbersInRawData` | `isNotEmpty` → yalnız boşluktan oluşan kolon "dolu numara" |

Sonuçları: (1) veride baştaki/sondaki boşluk varsa sayaç haritası ıskalanır, sayım 0 kabul edilir
ve limit **hiç uygulanmaz**; (2) yalnız boşluktan oluşan bir kolon `noEligibleNumberLeft`'i
sonsuza kadar `false` tutar, erken kapatma o kayıtta hiç tetiklenmez — kayıt ne aranır ne kapanır.

**Doğru davranış:** karşılaştırma/sayım için TEK normalizasyon (trim) kuralı; hem harita
anahtarlarına hem sorgulara uygulanır. Kolonlara YAZILAN değer değişmez. Aynı numaranın iki
kolonda bulunmasının tek sayılması korunur.

**Kalan sınır:** `Call.findNextNumberToCall` halkadaki konumu ham eşitlikle arıyor
(`"number_1".equals("  number_1  ")` → false), yani boşluklu veride numara halkası bugün de
kırık ve bu **özellik-kapalı tüm stratejileri** etkiliyor. Normalize etmek özellik-kapalı davranışı
değiştireceği için bu işte yapılmadı; ayrı ve dar kapsamlı bir iş olarak ele alınmalı.

### D7e — Kolon dışı callback numarası aday kümesinde yoktu

Analizin K16 maddesi "kolon dışı callback numarası kendi değeriyle sayılır" diyor, D2 de callback
numarasının telefon kolonlarından biri olmak zorunda olmadığını tespit etmişti. Ancak erken
kapatma kararının aday kümesi yalnız telefon **kolonlarından** kuruluyordu ve bu karar callback
dalından **önce** veriliyordu.

Sonuç: agent'ın elle girdiği, kolonlarda olmayan bir callback numarası limitin **altında** olsa
bile, kolonların tümü limitliyse kayıt kapanıyor ve müşterinin **kendi** talebiyle oluşan randevu
düşüyordu. Bu, over-dialing'den maliyetli bir kayıptır: analizin `Limit hiyerarşisi` bölümü
"callback'ler dahil" kuralını yazmış ama bu kuralın **erken kapatma** kararına da uygulanması
gerektiğini söylememişti.

**Doğru davranış:** bekleyen callback numarası da aday kümesine dahil edilir (kendi sayacıyla);
`createRetry` kontrol sırası değişmez.

### Analiz düzeyinde çıkarılan ders

Faz 6'nın dersi "iki katmanda uygulanan bir invariant için katman testleri yeterli değildir"
idi. Faz 7 iki ders daha ekliyor:

1. **Bir alanın adı sözleşme değildir, tipiyle birlikte sözleşmedir.** Otomatik binding'in
   varlık kontrolü ile doğrulamanın tip kontrolü ayrı yerlerde durduğunda, ikisinin arasında
   kampanyayı başlatılamaz hâle getiren bir boşluk açılır (D7a).
2. **Karşılaştırılan her değerin tek bir normalizasyon kuralı olmalıdır.** Aynı numara üç kolondan
   ve iki koddan farklı biçimlerde geliyorsa, sayaç sessizce devre dışı kalabilir — ve sessiz
   devre dışı kalma, gürültülü hatadan tehlikelidir (D7d).

**Analiz durumu: `D7 kapandı` (Faz 7, 08.09.2026).**

## D8 — Aktive edilen kayıtlar numara başına limitle tutarsızdı (28.09.2026)

Faz 7 sonrası test özeti hazırlanırken kabul kriteri 8'in ("yeni eklenen numara aranır, eskiler atlanır") **doğal** akışı uçtan uca izlendi: kayıt numara limitiyle kapanır, operatör veri manipülasyon ekranından kaydı **aktive eder** ve yeni bir numara ekler. Analizin Faz 1-7 boyunca ele aldığı akışlar tek bir kayıt ömrünü varsayıyordu; aktivasyon ise bir kaydı **ikinci bir ömre** başlatır. Bu varsayım üç yerde kırıldı. Uygulama ayrıntısı execution plan'ın `Faz 8` bölümündedir.

### D8a — Bayat plan işaretçileri

Analiz "plan"ı iki kolon çiftiyle tanımlamıştı: `retry_date` + `retry_number`, `callback_date` + `callback_number`. Oysa bu çiftlerin ömürleri farklı:

| Kolon | Strateji kapanışında | Aktivasyonda |
|---|---|---|
| `retry_date` | NULL yazılır | temizlenir |
| `retry_number` | **yalnız doluyken yazılır** → eski değer kalır | **temizlenmez** |
| `callback_date` | (callback kapanışında) korunur | temizlenir |
| `callback_number` | (callback kapanışında) korunur | **temizlenmez** |

Aktivasyondan sonra satırda tarihsiz bir `retry_number` / `callback_number` kalır. Producer bunları plan gibi okuyordu; numara limitteyken yönlendirecek bekleyen plan bulamıyor ve kayıt, **yeni numara hiç aranmadan** tekrar kapanıyordu. Doğru sözleşme: **plan olmanın tek ölçütü tarih kolonudur** (`hasPendingDialPlan`); tarihsiz numara kolonu bayat işaretçidir.

Aynı sınıftan ikinci bir okuyucu: aranamayan kaydın kapanış etiketi "callback mi?" sorusunu `callback_number` doluluğuyla soruyordu; aktive kayıt callback'le ilgisi olmayan bir kapanışta `max attempt reached on callback` alıyordu.

### D8b — Planlama adımı olmayan arama

Numara başına sayım, aramaları **planlama anında** yazılan iz girdilerinden kurar; ilk (fresh) arama örtük `F` ile temsil edilir. Bu model kayıt ömrü başına **tek** fresh arama varsayar. Aktive edilen kayıt fresh fetch'ten yeniden gelir ama izinde `F` zaten vardır ve bu aramanın planlama adımı yoktur → iz ona hiç girdi yazmaz → sayım onu görmez → numara limitten 1 fazla aranır.

Çözüm yönü: planlama adımı olmayan bir aramaya karar veren tek bileşen producer'dır; arama, karar anında izde tek girdiyle (planlı tekrar aramayla aynı biçimde) kaydedilir. Mevcut hiçbir yazım değişmez; daha önce yazılmayan bir arama için yazım noktası eklenir.

### D8c — Aktivasyonun kapanış durumu sözleşmesi eksikti

Aktivasyon kapanışı geri almak için `complete_date`, `is_disabled`, TRC vb. temizliyor; bu işle eklenen kapanış durumu kolonu `close_reason` sözleşmeye eklenmemişti. Bu analizin kalıcılık bölümü yeni kolonun **yazımını** tanımladı ama **silinmesini** (kaydın kapanış durumu geri alındığında) tanımlamadı.

Çözümün yeri sıcak yol değil aktivasyondur: "her sonuç yazımında neden yoksa NULL yaz" alternatifi her çağrının UPDATE'ine dokunur ve müşterinin kendi NOT NULL `close_reason` kolonunda **tüm sonuç yazımını** düşürürdü.

### D8d — Kalan sınır: ilk aramanın atfı (karar bekliyor)

`F` adımının numarası okuma anında "colSort'a göre ilk dolu kolon" olarak türetilir. Aktivasyonda yeni numara, ilk aranan numaranın kolonundan **önceki** bir kolona yazılırsa `F` yeni numaraya atfedilir: eski numara 1 eksik sayılır, 1 kez fazla aranabilir. Bu, "yeni sayaç kolonu yok, sayım mevcut kolonlardan yeniden kurulur" kararının doğal sınırıdır; kolon kümesinin kayıt ömrü boyunca değişmeyeceği varsayılmıştı. Tam çözüm ilk aranan numaranın saklanmasını gerektirir (yeni kolon veya `call_process` biçim değişikliği) ve bu bir ürün/mimari kararıdır. Geçici önlem: yeni numarayı mevcut numaralardan sonraki bir kolona eklemek.

### Analiz düzeyinde çıkarılan ders

Faz 6 "iki katmanda uygulanan invariant için katman testleri yetmez", Faz 7 "adı sözleşme değildir, tipiyle birlikte sözleşmedir" ve "her karşılaştırılan değerin tek bir normalizasyon kuralı olmalı" dersini verdi. Faz 8'in dersi:

**Türetilmiş bir durum, onu türettiğin kaynakların ömrü kadar geçerlidir.** Sayaç ve plan kavramları mevcut kolonlardan türetildi; kolonların yazıldığı, temizlendiği ve **temizlenmediği** yerler (strateji kapanışı, producer kapanışı, aktivasyon) tek tek çıkarılmadan türetimin doğruluğu kanıtlanmış sayılamaz. Özellikle bir kaydı yeniden başlatan her yol (aktivasyon, reset, manuel DBA işlemi) ayrı bir kayıt ömrü açar ve türetimin varsayımlarını sınar.

**Analiz durumu: `D8a-D8c kapandı` (Faz 8, 28.09.2026); `D8d karar bekliyor`.**

### D8e — Strateji 0'da numara geçişi (karar, 28.09.2026) — **29.09.2026'da D10 ile değişti**

F4 bulgusu strateji 0'ın (`REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER`) `setRetryNumber`'da numara yürüyüşü yapmadığını tespit etmiş, numara başına limitin orada yalnız kapanış guard'ı üzerinden etki edeceğini varsaymıştı. Uygulamada bu varsayım kayıtta birden fazla dolu numara olduğunda tutmadı: guard kaydın **tüm** numaralarına baktığı için kapanmıyor, strateji limitli numarayı yeniden planlıyor, producer da planı reddedip bir sonraki numaraya düşüyordu. Sonuç doğru (fazladan arama yok) ama dolaylıydı; iki katman farklı karar veriyordu.

Karar: numara başına limit bir **numara geçerliliği** kuralıdır; FEAT-325470'in S3 kararı (kullanılmayan numarada sonraki numaraya geçiş, strateji 0 dahil) ile aynı ilke uygulanır. Strateji 0 ritmini korur (tek numarayı art arda arar) ve o numara limitini doldurunca sıradaki aranabilir numaraya geçer. Geçiş strateji katmanında, planlama anında yapılır. Özellik kapalıyken strateji hiç numara değiştirmez.

Bu karar analizin K2 muafiyetiyle tutarlıdır: K2, numara başına limiti zaten kendi yapısında uygulayan strateji 1'i muaf tutuyordu; strateji 0'ın yapısında numara başına limit yoktu, bu yüzden kural ona uygulanır — ama stratejinin ritmi değiştirilmeden.

## D9 — Alan bilinçli olarak etkinleştirilir (müşteri kararı, 28.09.2026)

Opsiyonel sayısal bir alan, boş bırakıldığında davranışı birebir koruyor olsa da, yanlışlıkla veya rastgele girilen bir değer kampanyanın arama davranışını anında değiştirir. Alan bir checkbox ile etkinleştirilir. Checkbox yalnız arayüz durumudur: tek doğruluk kaynağı alanın değeridir (dolu = açık), böylece bayrak ile değer arasında ikinci bir kaynak ve tutarsızlık ihtimali oluşmaz; backend sözleşmesi, DTO ve Redis contract değişmez. Alan, kendisini açan checkbox'ın hemen altında ve aynı kolonda durur; kullanıcı ikisini tek bir grup olarak görür.

## D10 — Tek numara stratejisi de muaf (kullanıcı kararı, 29.09.2026)

Test ortamında "Reach retry limit for single number" (strateji 0) kampanyasında, birden fazla numarası olan kayıt D8e gereği numara limitini doldurunca diğer numaralardan da arandı. Beklenen davranış bu stratejide tek numaranın aranmasıydı.

Karar: strateji 0, strateji 1 gibi numara başına limitten **muaftır**. Alan ve checkbox bu stratejide de UI'da pasiftir; motor tarafında alan dolu gelse bile yok sayılır (`Campaign.isPerNumberAttemptLimitActive` ve `ReachRetrySingleNumber.isPerNumberLimitActive()` override'ı). Strateji aranan numarayı genel limite (Toplam Deneme) kadar arar; tek numarada `retryOveralAttempt` o numaranın sınırıdır. D8e'deki numara geçişi kaldırıldı.

Gerekçe: limitli numara, her stratejide o stratejinin DNC'deki numarayı işlediği gibi işlenir. Strateji 0'da aranan numara DNC'deyse kayıt kapanıyor, numara değişmiyor. Numara başına limitin bu stratejide diğer numaralara geçiş üretmesi stratejinin tanımını bozuyordu. Kullanılmayan numarada sonraki numaraya geçiş (FEAT-325470 S3) etkilenmez; o kural numara başına limitten bağımsızdır.
