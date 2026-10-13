# FEAT AMD Türevli Sonuç Kodları (86/87/88) ve Retry Stratejisi Uygulama Planı

## Analiz Referansı

`docs/analyse/16.09.2026/FEAT-325470-AMD-SONUC-KODLARI-RETRY-STRATEJISI.md`

Bulgular F1-F9 ve kararlar S1-S8 o dosyada. Bu plan onları tekrar etmez, uygular.

## Onaylanan Plan Özeti

- **Taban:** `feature/325559-numara-basina-azami-arama-limiti` (`633de602`). Branch `feature/325470-amd-sonuc-kodlari-retry-stratejisi` bu taban üzerine taşındı (S6). PR sırası 325559 → 325470.
- **Santral contract (S1):** `DialerHangup` cause değeri `pd_telephony_rc.code`'dur. Busy (code 17 → id 11) ve no answer (code 19 → id 13) bugün zaten geliyor; yenileri santral **913 / 914 / 915** olarak gönderecek. Dialer tarafında bütün kararlar `code` ile verilir, `id` ile değil.
- **Veri:** `pd_telephony_rc`'ye üç satır (S2): id 86 / code 913 `AMD Bad Number (unallocated number)` / `Wrong Number`; id 87 / code 914 `AMD Not Reached` / `No answer`; id 88 / code 915 `AMD Unknown Tone` / `Answering Machine`. `IDENTITY_INSERT` ile açık id, idempotent script, elle çalıştırılır, uygulama restart'ı gerekir.
- **AMD ailesi:** `ManagerConnectionDecorator`'daki üç `getId() == 71` kontrolü `code` tabanlı `{900, 913, 914, 915}` kümesine taşınır; 900 için davranış birebir korunur.
- **Bad number (913):** `RetryStrategy.createRetry` template'inde, **6 stratejide** (S3): aranan numara bu karar için geçersiz sayılır; `colSort` halkasında sonraki uygun numara varsa ona retry, yoksa kayıt kapanır. Retry zamanı stratejinin normal hesabıdır (S4). Kalıcılık kolonu **yok** (S5 = Seçenek B). Kapanışta `close_reason` binding'i varsa `all numbers unreachable` yazılır (S7).
- **87/88:** engine değişikliği yok; bugünkü davranış karakterizasyon testiyle kilitlenir.
- **UI:** kod değişikliği yok; iki ekran da veri güdümlü (F2).
- **Kapsam dışı:** F8 (Non-Turkcell Ambiguous id 10083) DBA tarafından düzeltilecek (S8).

## Amaç

Santralin ayrıştırdığı AMD sonuçlarını, üretimdeki çağrı akışını ve mevcut 900/DNC/numara-limiti davranışlarını bozmadan, retry stratejilerine iş talebindeki kurallarla bağlamak.

## Kapsam Dışı

- `unreachable_numbers` benzeri kalıcı "geçersiz numara" kolonu (S5). Sonuç: birden fazla numarası da 913 alan kayıt, halka ilk numaraya dönünce onu bir kez daha deneyebilir; döngü genel attempt limiti (ve 325559 numara başına limiti) ile sınırlıdır. Bilinçli kabul.
- `RING_VALID_RESULT_CODES` / `_V2` kümelerine yeni id eklenmesi (predictive AR_ring ölçüm konusu).
- id 83 / code 71 mükerrer "AMD" satırı.
- `CallAttemptPolicy` / `UserPopulationEvaluator` içindeki `85L` id kontrolleri (F8, S8).
- Dialplan / santral tarafı değişiklikleri (santral ekibi).
- Frontend kodu, localization, WRO.

## Performans Etkisi

- **Hot path durumu:** EVET. `ManagerConnectionDecorator` hangup finalize yolu ve `RetryStrategy.createRetry` her çağrı sonunda çalışır.
- **Call/event başına ek maliyet:** AMD ailesi kontrolü `Set<Integer>.contains(code)` (4 eleman, O(1)); bugünkü üç `long` karşılaştırmanın yerine geçer. 913 dalı yalnız `code == 913` çağrılarda çalışır: `getNumbersInRawData()` tek geçiş + en fazla bir tur numara yürüyüşü (mevcut DNC yürüyüşüyle aynı sınıf).
- **IO/scan/lock/serialization/log etkisi:** Yeni DB sorgusu, blocking çağrı, `synchronized` blok yok. `close_reason` mevcut UPDATE cümlesine 325559'un koşullu eklemesiyle girer; ek round-trip yok. Yeni log yalnız 913 dalında tek INFO satırı (numara geçişi veya kapanış).
- **Azaltım/ölçüm notu:** 900 davranışı değişmediği için mevcut AMD hacminde (Turkcell ~23 bin/gün) maliyet farkı beklenmez. Faz 3/4 sonunda hedefli testler; PRP'de deploy sonrası `pd_cdr` dağılımı ve startup validate logu kontrolü.

## Tasarım Notları (uygulamayı bağlayan kararlar)

### Constants

- `TELEPHONY_RC_AMD = 900`, `TELEPHONY_RC_AMD_BAD_NUMBER = 913`, `TELEPHONY_RC_AMD_NOT_REACHED = 914`, `TELEPHONY_RC_AMD_UNKNOWN_TONE = 915` (`TELEPHONY_RC_AMBIGUOUS_RECORD = 912` satırının altına).
- `AMD_FAMILY_TELEPHONY_RC_CODES = unmodifiableSet{900, 913, 914, 915}` + `static boolean isAmdFamilyTelephonyResultCode(TelephonyResultCode trc)` (null-güvenli, `getCode()` ile).
- `CLOSE_REASON_ALL_NUMBERS_UNREACHABLE = "all numbers unreachable"` (325559 `CLOSE_REASON_*` bloğuna; ham metin, lokalize edilmez, 255 karakter/tırnak kuralına uyar).

### ManagerConnectionComposition

- `validateInternalTelephonyResultCodeMappings` zorunlu listesine 900, 913, 914, 915 eklenir (eksikse startup'ta ERROR log, açılış engellenmez; mevcut desen).

### ManagerConnectionDecorator

- `AMD_HANGUP_CAUSE_CODE` (id 71) sabiti kaldırılır; `:1143`, `:1163`, `:1289` kontrolleri `Constants.isAmdFamilyTelephonyResultCode(...)` olur. Başka davranış değişikliği yok.

### RetryStrategy (325559 tabanı)

`createRetry` kontrol sırası korunur ve **dördüncü dal** eklenir: başarılı kapanış → genel limit → numara başına limit tükenmesi (325559) → **bad number (913)** → mevcut numara seçimi.

```text
} else if (isUnreachableNumberResult()) {                 // TRC code == 913
    String next = selectNextNumberAfterUnreachable();     // colSort halkası, aranan numaradan sonra başlar
    if (next == null) {
        call.setCloseReason(CLOSE_REASON_ALL_NUMBERS_UNREACHABLE);   // yazım CampaignDbOperations'ta binding'e koşullu
        completeCallWithoutRetry(); return;
    }
    if (call.isCallback()) { call.setCallbackDate(null); call.setCallbackNumber(null); }  // mevcut callback→retry dönüşümüyle aynı
    call.setRetryNumber(next);
}
setRetryDateAndIncrementRetryCounts(); updateRetryHistory();   // değişmez
```

- `selectNextNumberAfterUnreachable()`: adaylar `call.getNumbersInRawData()` (colSort sıralı, dolu); başlangıç, aranan numaranın konumundan sonrası (aranan numara kolonlarda yoksa, örn. kolon dışı callback numarası, baştan); en fazla bir tam tur; ilk `!isNumberIneligible(n) && !isSameNumber(n, calledNumber)` aday döner, yoksa `null`. Karşılaştırma `Call.normalizeNumberForLimit` ile (trim). İstisna fırlatmaz; bounded, deterministik.
- Aranan numaranın "geçersiz" sayılması **`isNumberIneligible` predicate'ine eklenmez**: 325559'un `noEligibleNumberLeft()` guard'ı bunu "limit tükendi" diye etiketlerdi (yanlış kapanış nedeni). Unreachable kuralı yalnız kendi dalında yaşar.
- Retry zamanı: dal, strateji override'larına dokunmaz; `RetryRulesCycle.isCallAnswered()` 913 için schedule satırını zaten yüklemiş olur (varsa interval oradan, yoksa `retryDelay`), diğer stratejilerde `retryDelay` + macro kuralı (S4).
- `ReachRetrySingleNumber` dâhil 6 strateji aynı daldan geçer (S3); stratejiye özel `setRetryNumber` 913'te çağrılmaz (zorunlu numara geçişi).
- Attempt sayımı, `retryHistory`/`call_process` yazım semantiği, `NoEligibleRetryNumberException` sigortası değişmez.

### Migration (`gbpowerdialer/src/main/resources/sql/migration/FEAT-325470-amd-telephony-result-codes.sql`)

- Tek bölüm, PD ana DB, her ortamda bir kez, idempotent (`IF NOT EXISTS (... WHERE code = 913)` vb.), `SET IDENTITY_INSERT dbo.pd_telephony_rc ON/OFF`, id çakışma koruması (`id IN (86,87,88)` başka code ile doluysa `RAISERROR` ve hiçbir satır yazılmaz), sonunda doğrulama `SELECT`. FEAT-325559 script biçimi (başlık, PRINT mesajları) izlenir. Kolonlar: `id bigint NOT NULL identity`, `name nvarchar(255) NULL`, `description nvarchar(255) NULL`, `code int NOT NULL`.

### Santral contract notu (doküman)

`docs/architecture/ASTERISK-AMI-AGI.md`'ye `DialerHangup` cause'unun `pd_telephony_rc.code` olduğu, dahili 9xx serisi ve AMD ailesi listesi eklenir.

## TDD Planı

### Faz 1 — Karakterizasyon (davranış değişmeden) — **TAMAMLANDI 16.09.2026**

- [x] `tests/dialer/RetryStrategyAmdFamilyBaselineCharacterizationTest`: 6 strateji için code 900 / 17 / 19 / 1 sonuçlarında bugünkü numara seçimi (aynı numara veya stratejinin normal seçimi) ve tarih (`retryDelay`); `RetryRulesCycle`'da TRC eşli schedule satırı varsa interval oradan, yoksa `retryDelay`; code 1 (unallocated) bugün aynı numaraya retry alıyor (913 ile kontrast). Fixture: `RetryStrategySixStrategiesBaselineCharacterizationTest` deseni (spy `Call`, mock `Campaign`/`JSONObject`). **Parameterized, 24 test (5'i Assume ile atlanır: schedule senaryosu yalnız strateji 5).**
- [x] `tests/ami/AmdFamilyHangupCharacterizationTest`: `UnmappedHangupCauseFallbackRegressionTest` fixture'ı (`ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", event)`) ile code 900 / id 71: (a) preview kampanya + agent atanmış + grup result code'lu + URC yok iken 900 awaiting-URC'ye düşmez, finalize olur (`dbOperations.insertCallResultToDB` çağrılır); aynı kurulumda code 17 awaiting'e düşer; (b) `answerDate` dolu + `callHandlingStartTime` boş iken 900 TRC 84'e çevrilmez, code 17 çevrilir; (c) predictive ALPHA'da `answerDate` dolu + 900 → `dynamicBoostManager.onCallFailed` çağrılır (setter mevcut: `setDynamicBoostManager`). **6 test.**

**Faz 1'de doğrulanan ek bulgu:** strateji 3 (`RoundRobinNextCycle`) tarih hesabını `RoundRobinPerCycle`'dan miras alır; 3 numaralı kayıtta ilk retry'da cycle dolmadığı için retry delay uygulanmaz (0 dk). Analizdeki "diğer stratejiler retry delay" ifadesi strateji 3 ve 4 için "cycle dolunca retry delay" olarak okunmalıdır; bugünkü davranıştır, değiştirilmedi.

### Faz 2 — Veri ve sabitler — **TAMAMLANDI 16.09.2026**

- [x] `tests/persistence/TelephonyResultCodeMigrationContractTest`: migration SQL dosyası okunur; (id, code, name, description) üçlüleri beklenen değerlerle, code'lar `Constants` sabitleriyle, `IDENTITY_INSERT ON/OFF` çifti ve idempotent guard'ların varlığı, `ManagerConnectionComposition.requiredInternalTelephonyResultCodes()` zorunlu kod listesinin 900/913/914/915 içerdiği doğrulanır. **6 test; kırmızı (NoSuchField/NoSuchMethod + dosya yok) → yeşil.**

### Faz 3 — AMD ailesi (engine, davranış korunur + genişler) — **TAMAMLANDI 16.09.2026**

- [x] `tests/ami/AmdFamilyHangupFeatureTest`: Faz 1 (a)(b)(c) senaryoları code 913 / 914 / 915 için 900 ile aynı sonucu verir; code 17 etkilenmez. Faz 1 karakterizasyon testi yeşil kalır. **4 test; kırmızı (3 senaryo) → yeşil.** Ortak fixture `tests/ami/AmdHangupTestHarness` (test yardımcısı, plan dışı ekleme).

### Faz 4 — Bad number kuralı — **TAMAMLANDI 16.09.2026**

- [x] `tests/dialer/RetryStrategyBadNumberNextNumberFeatureTest` (6 strateji için Parameterized; **60 test, 5'i Assume ile atlanır; kırmızı 31 → yeşil**):
  - tek numara + 913 → `completeCall`, `closeReason = "all numbers unreachable"`, retryDate null;
  - üç numara, aranan n1 → retry n2; aranan n2 → n3; aranan n3 → n1 (halka);
  - n2 DNC'de → n3; n2 numara başına limitini doldurmuş (325559 aktif) → n3;
  - diğer tüm numaralar DNC'de → complete + unreachable nedeni;
  - callback çağrısı, kolon dışı callback numarası 913 → ilk uygun kolon numarasına retry, `callbackDate`/`callbackNumber` temizlenir;
  - retry tarihi `retryDelay` ile (strateji 0-4) ve `RetryRulesCycle`'da 913 schedule satırı varsa onun interval'i ile hesaplanır;
  - `retryHistory`'ye yeni numara ve `call_process`'e `R` adımı eklenir; `attempts` davranışı değişmez;
  - 913 dışı TRC'lerde (900, 17, 19, 1) dal hiç çalışmaz (baseline ile aynı çıktı).
- [x] `tests/dialer/RetryDncExhaustionInfiniteLoopRegressionTest` genişletmesi: 913 + diğer tüm numaralar DNC → istisna yok, döngü yok (kolon okuma sigortası patlamaz), complete + `all numbers unreachable`; 913 + aranan numaranın kendisi DNC'de → complete. **+2 test (8 → 10); mevcut 8 test değişmedi.**
- [x] Regression kapısı yeşil: `RetryStrategySixStrategiesBaselineCharacterizationTest` 18, `RetryStrategySuccessfulCloseMatrixCharacterizationTest` 7, `MaxAttemptPerNumber*` 9 sınıf 83 test, `ReachRetryPerNumberDncRetryCharacterizationTest` 5, `CallHistoryColumnsLockstepCharacterizationTest` 13, legacy `com.powerdialer.engine.dialer.retry.*` 27 (`ReachRetryPerNumberTest`, `ReachRetrySingleNumberTest`, `RetryLimitTest`, `RetryRulesCycleTest`, `RetryStrategyFactoryTest`, `RetryStrategyTemplateTest`).

### Frontend

- Frontend kodu değişmediği için Karma çalıştırılmaz; gerekçe Test Özeti'nde yazılır.

## Uygulama Kontrol Listesi

### Faz 0 — Ön koşullar

- [x] Analiz açık soruları (S1-S8) geliştirici yanıtlarıyla kapatıldı ve analize işlendi.
- [x] Branch 325559 tabanına taşındı (`633de602`), stash conflict'leri iki taraf korunarak çözüldü.
- [x] Geliştirici bu planı onayladı (16.09.2026, "planı onaylıyorum").
- [ ] Santral ekibine yazılı iletildi: yeni sonuçlar için `DialerHangup` cause **913 / 914 / 915**; busy 17 ve no-answer 19 mevcut hâliyle sürer. (Kod dışı aksiyon; geliştirici iletir.)

### Faz 1 — Karakterizasyon

- [x] `RetryStrategyAmdFamilyBaselineCharacterizationTest` yazıldı ve yeşil (davranış değişmeden; strateji 3 gecikme beklentisi gerçek davranışa göre düzeltildi).
- [x] `AmdFamilyHangupCharacterizationTest` yazıldı ve yeşil.

### Faz 2 — Veri ve sabitler

- [x] `Constants`: `TELEPHONY_RC_AMD`, `TELEPHONY_RC_AMD_BAD_NUMBER`, `TELEPHONY_RC_AMD_NOT_REACHED`, `TELEPHONY_RC_AMD_UNKNOWN_TONE`, `AMD_FAMILY_TELEPHONY_RC_CODES`, `isAmdFamilyTelephonyResultCode(Integer)`, `CLOSE_REASON_ALL_NUMBERS_UNREACHABLE`.
- [x] `ManagerConnectionComposition`: zorunlu kod listesi `public static int[] requiredInternalTelephonyResultCodes()` olarak çıkarıldı (testlenebilirlik), 900/913/914/915 eklendi; `validateInternalTelephonyResultCodeMappings` bu listeyi kullanır.
- [x] Migration SQL `FEAT-325470-amd-telephony-result-codes.sql` yazıldı (idempotent, `IDENTITY_INSERT`, çakışma koruması + `RAISERROR`, doğrulama SELECT).
- [x] `TelephonyResultCodeMigrationContractTest` kırmızı → yeşil.

### Faz 3 — AMD ailesi

- [x] `AmdFamilyHangupFeatureTest` kırmızı (913/914/915 için 3 senaryo).
- [x] `ManagerConnectionDecorator` üç kontrol `isAmdFamilyTelephonyResultCode(TelephonyResultCode)` yardımcısına (code tabanlı) taşındı, `AMD_HANGUP_CAUSE_CODE` kaldırıldı.
- [x] Faz 1 + Faz 3 testleri yeşil.

### Faz 4 — Bad number kuralı

- [x] `RetryStrategyBadNumberNextNumberFeatureTest` kırmızı (31 başarısızlık).
- [x] `RetryStrategy`: `isUnreachableNumberResult()`, `switchRetryNumberAfterUnreachable()`, `selectNextNumberAfterUnreachable()`, `createRetry` dördüncü dalı, tek INFO log. `createRetry` if/else zinciri davranış korunarak erken dönüşlü yapıya çevrildi (ortak tarih/geçmiş kuyruğu tek yerde); baseline testleri değişmeden yeşil.
- [x] `RetryDncExhaustionInfiniteLoopRegressionTest` genişletmesi yeşil.
- [x] Regression kapısındaki sınıflar yeşil.
- [x] Refactor gerekmedi; testler son hâlde tekrar koşuldu.

### Faz 5 — Doküman ve kapanış

- [x] `docs/architecture/DIALER-RUNTIME.md`: `createRetry` kontrol sırasına 4. dal ve "Kullanılmayan numara kuralı (FEAT-325470)" bölümü; Change Contract güncellendi.
- [x] `docs/architecture/ASTERISK-AMI-AGI.md`: "DialerHangup cause contract'ı ve AMD ailesi (FEAT-325470)" bölümü.
- [x] `docs/architecture/DATA-PERSISTENCE-AND-DB.md`: "Telephony result code satırı ekleme — `pd_telephony_rc` (FEAT-325470)" bölümü.
- [x] History kaydı (`docs/history/16.09.2026.md`) ve bu plandaki checkbox'lar işlendi.
- [x] `mvn -o -DskipTests package` WAR BUILD SUCCESS (`target/gbpowerdialer.war`, 16.09.2026 18:52, exit 0).

## Doğrulama

Tool path'ler `.codex/TOOL-PATHS.local.properties` dosyasından alınır; Maven offline (`-o`) çalıştırılır.

```text
gbpowerdialer
  mvn -o -Dtest=RetryStrategyAmdFamilyBaselineCharacterizationTest,AmdFamilyHangupCharacterizationTest test
  mvn -o -Dtest=TelephonyResultCodeMigrationContractTest test
  mvn -o -Dtest=AmdFamilyHangupFeatureTest,UnmappedHangupCauseFallbackRegressionTest,AwaitingUserResultTimeoutAmbiguousTrcRegressionTest test
  mvn -o -Dtest=RetryStrategyBadNumberNextNumberFeatureTest,RetryDncExhaustionInfiniteLoopRegressionTest test
  mvn -o -Dtest='tests.dialer.RetryStrategy*,tests.dialer.MaxAttemptPerNumber*,tests.dialer.ReachRetryPerNumberDncRetryCharacterizationTest' test
  mvn -o -Dtest='com.powerdialer.engine.dialer.retry.*' test
  mvn -o -DskipTests package
```

Frontend: `npm.cmd run test:angular` **çalıştırılmaz**; AngularJS/JSP/WRO/localization dosyası değişmiyor (F2).

Ortam doğrulaması (PRP, deploy sonrası, geliştirici/QA):

1. Migration çalıştırıldı; `SELECT id, code, name FROM pd_telephony_rc WHERE code IN (913,914,915)` → id 86/87/88.
2. Uygulama restart; startup logunda `pd_telephony_rc is missing internal result code mapping` ERROR'u yok.
3. Telephony Result Code List ve Result Code Schedule Form dropdown'unda üç yeni satır görünür.
4. Santral test çağrıları: `pd_cdr.telephony_rc` 86/87/88 yazılır; logda `Hangup cause '...' is not mapped` WARN'ı yok; 86 alan çok numaralı kayıtta result tablosunda `retry_number` sonraki numara, tek numaralı kayıtta `complete_date` dolu ve `close_reason = all numbers unreachable` (binding varsa).

Deploy sırası: (1) migration her ortamda, (2) dialer deploy + restart, (3) santral dialplan geçişi, (4) `pd_cdr` dağılımı ve WARN log kontrolü.

## Kapanış Kriteri

- [x] Faz 1-4 testleri yazıldı, çalıştırıldı ve yeşil.
- [x] Regression kapısı (325559 ve retry karakterizasyon sınıfları) yeşil.
- [x] WAR paketlendi (`mvn -o -DskipTests package`, exit 0, `target/gbpowerdialer.war`).
- [x] Mimari dokümanlar ve history güncellendi.
- [x] Final yanıtta kısa Test Özeti sunuldu (16.09.2026).

## Riskler ve Geri Dönüş

- **Santral id gönderirse** (F1): davranış sessizce bozulur; ortam doğrulaması adım 4 bunu yakalar (WARN + trc 73). Geri dönüş gerekmez; santral cause'u düzeltir.
- **Doğal kill-switch:** santral 913/914/915 göndermezse yeni dallar hiç çalışmaz; 900 davranışı değişmediği için deploy tek başına risksizdir.
- **Kod geri dönüşü:** tek commit revert; `pd_telephony_rc` satırları kalabilir (zararsız, harita fazladan üç anahtar taşır).
- **325559 PR'ı gecikirse:** bu branch onun üstünde kaldığı için PR sırası korunmalı; 325559 develop'a girmeden 325470 PR'ı açılmaz.
- **Numara halkası:** yeni dal istisna fırlatmaz ve bir turla sınırlıdır; `RetryDncExhaustionInfiniteLoopRegressionTest` genişletmesi sigortadır.

## Açık Sorular

Yok. S1-S8 16.09.2026'da geliştirici yanıtlarıyla kapatıldı (analiz dosyası "Kararlar" bölümü).

## Tamamlanma Notu

**Uygulama 16.09.2026'da tamamlandı** (branch `feature/325470-amd-sonuc-kodlari-retry-stratejisi`, taban 325559 `633de602`; commit henüz atılmadı).

Çalıştırılan komutlar ve sonuçlar (offline Maven, `.codex/TOOL-PATHS.local.properties` path'leri):

```text
gbpowerdialer
  mvn -o -Dtest=RetryStrategyAmdFamilyBaselineCharacterizationTest,AmdFamilyHangupCharacterizationTest test
    ilk kosum: 30 test, 1 failure (strateji 3 gecikme beklentisi) -> beklenti gercek davranisa gore duzeltildi -> 30/30 (5 skipped)
  mvn -o -Dtest=TelephonyResultCodeMigrationContractTest test
    kirmizi: 6/6 (NoSuchField/NoSuchMethod, migration dosyasi yok) -> Constants/MCC/SQL sonrasi 6/6 yesil
  mvn -o -Dtest=AmdFamilyHangupFeatureTest test
    kirmizi: 4 test, 3 failure (913/914/915 id-71 kontrolunden gecmiyor) -> decorator degisikligi sonrasi 4/4 yesil
  mvn -o -Dtest=RetryStrategyBadNumberNextNumberFeatureTest test
    kirmizi: 60 test, 31 failure -> RetryStrategy dordunce dal sonrasi 60 run, 0 failure, 5 skipped
  mvn -o "-Dtest=RetryStrategy*Test,MaxAttemptPerNumber*Test,RetryDncExhaustionInfiniteLoopRegressionTest,
          ReachRetryPerNumberDncRetryCharacterizationTest,RetryLimitTest,RetryRulesCycleTest,ReachRetryPerNumberTest,
          ReachRetrySingleNumberTest,CallHistoryColumnsLockstepCharacterizationTest" test
    22 sinif, tamamı yesil (surefire raporlari: failures=0 errors=0)
  mvn -o -Dtest=RetryDncExhaustionInfiniteLoopRegressionTest,RetryStrategyBadNumberNextNumberFeatureTest test
    70 run, 0 failure, 5 skipped -> BUILD SUCCESS
  mvn -o -Dtest=AmdFamilyHangupFeatureTest,AmdFamilyHangupCharacterizationTest,UnmappedHangupCauseFallbackRegressionTest,
          AwaitingUserResultTimeoutAmbiguousTrcRegressionTest,AwaitingUserResultCacheTransitionRegressionTest test
    18 run, 1 error: UnmappedHangupCauseFallbackRegressionTest.shouldFallbackToExpiredClearingWhenHangupCauseIsNotNumeric
    (NumberFormatException, ManagerConnectionDecorator:957 Integer.parseInt(event.getHangupCause())) -> ON-MEVCUT,
    FCT merge kaynakli, bu iste dokunulan satirlarla ilgisiz; kapsam disi birakildi.
  mvn -o -DskipTests package -> exit 0, target/gbpowerdialer.war (98,9 MB) uretildi (test kaynaklari da derlendi)
  npm.cmd run test:angular -> CALISTIRILMADI: frontend dosyasi degismedi (F2, veri gudumlu ekranlar).
```

Değişen üretim kodu: `Constants.java` (+41), `ManagerConnectionComposition.java` (zorunlu kod listesi metodu), `ManagerConnectionDecorator.java` (3 kontrol code tabanlı, sabit kaldırıldı), `RetryStrategy.java` (4. dal + 3 yardımcı), yeni `sql/migration/FEAT-325470-amd-telephony-result-codes.sql`. Yeni testler: `tests/dialer/RetryStrategyAmdFamilyBaselineCharacterizationTest`, `tests/dialer/RetryStrategyBadNumberNextNumberFeatureTest`, `tests/ami/AmdFamilyHangupCharacterizationTest`, `tests/ami/AmdFamilyHangupFeatureTest`, `tests/ami/AmdHangupTestHarness` (yardımcı), `tests/persistence/TelephonyResultCodeMigrationContractTest`; genişletilen: `tests/dialer/RetryDncExhaustionInfiniteLoopRegressionTest` (+2).

Plan dışı / sapma notları: (1) strateji 3'ün per-cycle zaman mirası (Faz 1 bulgusu, davranış değişmedi); (2) `AmdHangupTestHarness` ortak test fixture'ı eklendi; (3) MCC zorunlu kod listesi `public static` metoda çıkarıldı; (4) `createRetry` erken-dönüş yapısına çevrildi (davranış korunur, baseline yeşil); (5) `RetryDncExhaustionInfiniteLoopRegressionTest`'e planlanan 1 yerine 2 test eklendi.

Açık kalan kod dışı işler: santral ekibine 913/914/915 cause bildirimi; migration'ın her ortamda çalıştırılması + restart; PRP'de ortam doğrulaması (Doğrulama bölümü 1-4); PR sırası 325559 → 325470.

WAR paketleme sonucu: `mvn -o -DskipTests package` exit 0, `target/gbpowerdialer.war` üretildi (16.09.2026 18:52). Plan durumu: **tamamlandı**; kod dışı aksiyonlar (santral bildirimi, migration + restart, PRP doğrulaması, PR sırası) geliştiricide.

## Faz 6 — Cause kodu revizyonu 913/914/915 → 113/114/115 (05.10.2026)

Santral ekibinin talebi: id 86 → code **113**, id 87 → **114**, id 88 → **115**. Id'ler, satır adları ve davranış aynı; yalnız santralin göndereceği `code` değerleri değişiyor. Doğrulama: 113/114/115 dört DB'de (prod Turkcell/Non-Turkcell, PRP `gbWebphone_test`, S2 `gbWebPhone_S2_test`) boş; PRP ve S2'de ilk sürümün 913/914/915 satırları var (id 86/87/88), prod'da henüz satır yok. Branch: `feature/325470-amd-cause-kodlari-113-115` (develop `538b48db`).

### TDD

- [x] `TelephonyResultCodeMigrationContractTest` yeni kodlara göre güncellendi; kırmızı: **8 test, 5 failure** (sabitler 913, SQL'de UPDATE/guard yok). Yeni testler: `newCodesDoNotCollideWithKnownItuErrorCauses` (113-115 `ASTERISK_ERROR_CODES` ve attempt muafiyeti dışında), `migrationRecodesRowsWrittenByTheFirstVersion` (3 UPDATE), insert guard'ı id tabanlı, iki yönlü çakışma koruması.
- [x] Yeşil: sabit + script değişikliği sonrası `TelephonyResultCodeMigrationContractTest` 8/8.

### Değişiklikler

- [x] `Constants`: `TELEPHONY_RC_AMD_BAD_NUMBER = 113`, `TELEPHONY_RC_AMD_NOT_REACHED = 114`, `TELEPHONY_RC_AMD_UNKNOWN_TONE = 115`; Javadoc (Q.850 atanmamış aralık, `ASTERISK_ERROR_CODES` ile kesişmez, 16.09 → 05.10 geçiş notu). `AMD_FAMILY_TELEPHONY_RC_CODES` ve `requiredInternalTelephonyResultCodes()` sabitler üzerinden otomatik güncel.
- [x] Migration `FEAT-325470-amd-telephony-result-codes.sql` aynı dosyada revize: 0a/0b iki yönlü çakışma koruması (`RAISERROR` + `RETURN`), 1. eski kodlu satırları `UPDATE ... WHERE id = N AND code = 91N` ile taşıma, 2. eksik satırları `IDENTITY_INSERT` ile ekleme (guard `WHERE id = N`), 3. doğrulama SELECT. Hem ilk sürüm uygulanmış ortamlarda hem taze ortamlarda tek çalıştırmayla hedef duruma gelir.
- [x] Yorum/Javadoc metinleri (`ManagerConnectionComposition`, `ManagerConnectionDecorator`, `RetryStrategy`, 6 test sınıfı) 113/114/115.
- [x] Dokümanlar: `ASTERISK-AMI-AGI.md`, `DATA-PERSISTENCE-AND-DB.md`, `DIALER-RUNTIME.md`, analiz "Revizyon (05.10.2026)" bölümü, testçi rehberi (HTML).

### Doğrulama

- [x] `mvn -o -Dtest=TelephonyResultCodeMigrationContractTest,AmdFamilyHangupFeatureTest,AmdFamilyHangupCharacterizationTest,RetryStrategyBadNumberNextNumberFeatureTest,RetryDncExhaustionInfiniteLoopRegressionTest,RetryStrategyAmdFamilyBaselineCharacterizationTest test` → **112 test, 0 hata, 10 Assume ile atlandı, BUILD SUCCESS** (05.10.2026 16:05). Karma çalıştırılmadı: frontend dosyası değişmedi.
- [x] Ortam: revize script 05.10.2026 16:10'da PRP `gbWebphone_test` ve S2 `gbWebPhone_S2_test` üzerinde çalıştırıldı (her ikisinde de UPDATE yolu, 3 satır: 913→113, 914→114, 915→115; INSERT atlandı); doğrulama SELECT id 86/87/88 → code 113/114/115. Uygulama restart'ı yeni kodun PRP deploy'u ile birlikte yapılacak. Prod'da script deploy öncesi ilk kez çalışır (INSERT yolu).
- [ ] Santral ekibine bildirim: cause **113 / 114 / 115**; 913/914/915 artık haritada yok (901 fallback + "not mapped" WARN).
