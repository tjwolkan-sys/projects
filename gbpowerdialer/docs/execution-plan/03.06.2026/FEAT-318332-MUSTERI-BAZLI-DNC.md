# FEAT Müşteri Bazlı DNC Uygulama Planı

## Analiz Referansı

`docs/analyse/03.06.2026/FEAT-318332-MUSTERI-BAZLI-DNC.md` (geliştirici tarafından 03.06.2026 tarihinde incelendi ve onaylandı)

## Onaylanan Plan Özeti

Task `318332` için müşteri bazlı DNC desteği eklenecek. Çekirdek tasarım: CUSTOMER scope, engelleme anında müşterinin tüm telefon numaralarının `PHONE` tipli `pd_dnc_pn` kayıtlarına genişletilmesiyle (expansion) çalışır; runtime hot path'teki telefon bazlı DNC filtresi değişmez. `pd_dnc_pn`'e `dnc_type` + `customer_no` kolonları, `pd_campaign`'e `col_result_customer_no` + `col_date_entered` mapping kolonları eklenir. Redis `CallResultCode` contract'ına opsiyonel `dncScope` alanı (additive) eklenir. DNC tetiklendiği anda bellekteki `dataQueue`/`callQueue` kuyruklarından eşleşen çağrılar düşürülür (hot-drop). Puzzle için token korumalı `addCustomer` API'si ve `date_entered` watermark'lı delta-sync scheduler'ı eklenir. Satır işaretleme mevcut `is_disabled=1 + TRC=76 + complete_date` mekanizmasıyla yapılır; cleanup çift kilitli ve default kapalıdır; `date_entered` mapping alanıyla (`colDateEntered`) çözülür.

Çalışma branch'i: `feature/318332-musteri-bazli-dnc` (base: `develop`).

## Amaç

Bir müşterinin tüm erişim numaralarını tek aksiyonla (agent wrap-up, Puzzle API veya delta-sync) kalıcı olarak aranamaz yapmak; KVKK uyumunu mevcut çağrı üretim hızını bozmadan sağlamak.

## Kapsam Dışı

- gbwebphone agent ekranındaki iki seçenekli kapatma UI'ı (ayrı repo; bu repo `dncScope` Redis contract'ını sağlar).
- Puzzle arayüzünün kendisi (bu repo yalnız API yüzeyini verir).
- CDR-fallback yolunda gerçek customer expansion (v1'de PHONE'a degrade; delta-sync telafi eder).
- Originate edilmiş (CallUpToDateKeeper'a kayıtlı) çağrıların canlı hattan koparılması.
- Mevcut müşteri kampanya tablolarına kolon/index ekleme (DBA/müşteri sorumluluğu; rehber DDL analizde).
- Fetch/originate algoritması, retry/callback tanımları, Redis channel adları.

## Performans Etkisi

- Hot path durumu: `Hot path, ek maliyet var ve gerekçesi/azaltımı yazıldı` (Revizyon 3 ile güncellendi). Dial-time DNC kontrolü telefon bazlı kalır; CUSTOMER expansion tetik anında (nadir event) çalışır. Tek hot-path eki: `colResultCustomerNo` mapping'li kampanyalarda producer fetch sayfası başına +1 indexed IN-sorgusu (aktif CUSTOMER marker kontrolü — müşteri gereksinimi; call başına değil, mevcut DNC revalidation/JIT expire ile aynı frekans sınıfı; mapping'siz kampanyada sıfır maliyet; hatada fail-open).
- Call/event/tick başına ek maliyet: Fetch/originate/AMI-Redis event başına yeni DB query, scan, lock, allocation eklenmez. Cache yükleme yolunda (60 sn'de bir) satır başına 1 enum/null kontrolü eklenir.
- IO/scan/lock/serialization/log etkisi: Hot-drop, DNC tetiklenmesi başına (wrap-up sonrası, async `webphoneEventExecutor` üzerinde) O(dataQueue+callQueue) bounded kuyruk taraması + kampanya başına 1 batch UPDATE + CDR insert'leri. Expansion tetik başına 1 parametrized batch SELECT + chunk'lı batch UPDATE + tek `updateDnc` cache yenilemesi. Delta-sync kampanya başına 1 indexed `MAX()` probe; yeni data varsa sayfalı batch akış.
- Azaltım veya ölçüm notu: Kuyruk taraması yalnız ilgili DNC listesini kullanan kampanyalarla sınırlandırılır; IN-list'ler ≤ ~2000 parametre ile chunk'lanır; delta-sync watermark sayesinde veri yokken tek aggregate sorguya iner; Redis subscriber thread'inde kampanya DB lookup'ı yapılmaz (CDR-fallback degrade kararı).

## PR Dilimleri ve Sıra

1. **PR-A (WP1+WP2)**: Şema + entity + cache/sorgu filtresi — pasif.
2. **PR-B (WP3+WP4)**: `CustomerDncService` + kuyruk hot-drop — çağıran yok, karanlık.
3. **PR-C (WP5+WP6)**: Redis `dncScope` + wrap-up bağlama — gbwebphone alanı gönderene dek pasif.
4. **PR-D (WP7)**: Puzzle `addCustomer` API'si — yeni route, additive.
5. **PR-E (WP8)**: Delta-sync scheduler — cron + mapping set edilene dek karanlık.
6. **PR-F (WP9)**: UI Customer No dropdown + i18n — opsiyonel alan.
7. **PR-G (WP9 ek, 15.06.2026)**: UI Date Entered (`date_entered`) dropdown + i18n — opsiyonel alan; delta-sync (`colDateEntered`) mapping'ini admin ekranından yönetilebilir kılar.

Bağımlılık: PR-A → PR-B → (PR-C, PR-D, PR-E paralel); PR-F ve PR-G yalnız PR-A'ya bağlıdır (PR-G, PR-F dropdown deseninin kardeşidir). Her PR kendi hedefli testleri yeşil olmadan kapatılmaz.

## TDD Planı

### PR-A — Şema + entity + cache filtresi

- [x] `DncPhoneNumberCustomerScopeMappingTest`: yeni `DNCPhoneNumber()` default `dncType=PHONE`; `dnc_type` ORDINAL mapping; `customer_no` kolonu nullable; `phone_number` kolonu nullable ve `@NotBlank` yok.
- [x] `DncTypeEnumOrdinalContractTest`: `DNC_TYPE` ordinal sırası PHONE=0, CUSTOMER=1 kilitlenir.
- [x] `DncEngineCustomerRowsExcludedFromPhoneCacheTest`: CUSTOMER tipli kayıt (customerNo dolu, phoneNumber null) telefon cache'ine giremez; `applyDncToCalls` customer_no string'iyle eşleşen calledNumber'ı engellemez. (Ek: event-driven `updatePhoneNumberInDnc` yoluna da aynı guard + testi eklendi.)
- [x] `DncPhoneNumberDaoCustomerScopeQueryContractTest`: aktif telefon sorgusu `dncType=PHONE AND phoneNumber IS NOT NULL` filtresi içerir; yeni `findActiveCustomerNosByDncIdsAndCustomerNos` sorgusu CUSTOMER tipiyle çalışır.
- [x] `CampaignCustomerNoColumnMappingTest`: `col_result_customer_no` + `col_date_entered` kolon adları, nullability ve `CampaignFormDTO` alan eşleşmesi.

### PR-B — CustomerDncService + hot-drop

- [x] `CustomerDncServiceExpandsCustomerToPhoneRowsTest`: customer_no → tüm satırlardaki enabled telefon kolon değerleri toplanır; CUSTOMER marker + PHONE kayıtları yazılır; tek `dncEngine.updateDnc`; dönen set doğru.
- [x] `CustomerDncServiceFallsBackToRowNumbersWhenCustomerNoNullTest` (E1): mapping boş veya rawData'da değer yok → DB SELECT yok, mevcut satırın numaraları PHONE olarak yazılır, CUSTOMER marker yazılmaz, exception yok. (Ek: calledNumber rawData'da olmasa bile engellenir.)
- [x] `CustomerDncServiceSkipsExistingNumbersBehaviorTest`: `existsByPhoneNumberAndDncId` true olan numaralar tekrar insert edilmez (idempotency). (Ek: API yolu ADMINISTRATIVE reason kilidi.)
- [x] `CampaignDbOperationsMarkSiblingsByCustomerNoSqlContractTest`: sibling UPDATE'i `complete_date + TRC=76 + is_disabled=1 + user NULL` semantiğini taşır, parametrized'dır; yalnız açık satırlar (complete_date IS NULL) ve tetikleyen satır hariç tutulur. (Not: UPDATE customer_no eşitliğiyle çalıştığı için IN-chunk gerekmez; chunk ihtiyacı delta-sync'in kendi akışında ele alınacak.)
- [x] `DncEngineDropsBufferedCallsForBlockedNumbersTest` (E2): dataQueue + callQueue'dan eşleşen call'lar çıkar, `setFilteredByDncListStatus` + CDR(TRC=76) çağrılır, eşleşmeyenler kalır.
- [x] `DncEngineQueueDropRespectsDncListSharingTest`: DNC'yi paylaşmayan kampanyanın kuyruğuna dokunulmaz (cache-bazlı `campaignUsesDnc`, lazy entity ilişkisine dokunmaz).
- [x] `DncEngineQueueDropDoesNotResetFetchedFlagTest`: `removeFetchedDataFlagFromCall(s)` hiçbir şekilde çağrılmaz.
- [x] Mevcut DNCEngine testleri yeşil kaldı (drop yolu mevcut private `saveCDR`/`checkStopCondition` helper'larını aynı sınıf içinden yeniden kullanır; ayrı extrakt gerekmedi — davranış birebir korunur).

### PR-C — Redis contract + wrap-up bağlama

- [x] `CallResultCodeDeserializerDncScopeBackwardCompatTest`: `dncScope` alanı yok → null; `"PHONE"` → PHONE; `"CUSTOMER"` → CUSTOMER; bilinmeyen değer → null, exception yok. (Ek: JSON null değeri de null'a düşer.)
- [x] `WebphoneSubscriberCustomerScopeWrapupTest`: DNC result code + scope=CUSTOMER → `blockByCustomerScope` + `dropQueuedCallsForNumbers` çağrılır; tekil `dncPhoneNumberService.create` yolu çalışmaz. (Ek: tetikleyen numara `dnc_numbers` kolonu için call'a işlenir.)
- [x] `WebphoneSubscriberPhoneScopeUnchangedRegressionTest`: scope=PHONE ve scope=null bugünkü yolu birebir izler (create + updatePhoneNumberInDnc + updateDnc).
- [x] `WebphoneSubscriberCdrFallbackCustomerDegradesToPhoneTest`: call bellekte yok + scope=CUSTOMER → yalnız `cdr.contactNumber` PHONE olarak eklenir, WARN log atılır.

### PR-D — Puzzle API

- [x] `DncCustomerApiInsertsCustomerScopeTest`: N customer_no → her biri için `blockByCustomerNo`; sonda TEK queue-drop (union set); processed count döner; blank satırlar atlanır. (Not: cache refresh `blockByCustomerNo` başına tek async/idempotent `updateDnc` olarak çalışır — PR-B contract'ı; ayrıca bastırma karmaşası bilinçli eklenmedi.)
- [x] `DncDataManipulationPhoneEndpointsUnchangedRegressionTest`: mevcut `/data/dnc/add|disable` davranışı değişmez (batch insert/disable + tek updateDnc; customer servisi ve hot-drop bu yollarda çalışmaz).

### PR-E — Delta-sync scheduler

- [x] `DncDeltaSyncSkipsWhenNoNewDataTest` (Kriter 3 + E3): watermark'tan yeni `MAX(date_entered)` yoksa aday çekimi/UPDATE/DNC sorgusu yok, sync-log yazılmaz (watermark değişmez); karşılaştırma watermark parametresiyle yapılır, `GETDATE()` kullanılmaz. (Ek: ilk koşuda watermark yokken koşulsuz MAX probe.)
- [x] `DncDeltaSyncBlocksNewlyMatchingByPhoneTest`: yeni satırların telefonları aktif DNC'de → `setFilteredByDncListStatusByUniqueKeys` batch mark; SUCCESS log'unda watermark pencerenin `MAX(date_entered)` değerine ilerler.
- [x] `DncDeltaSyncBlocksNewlyMatchingByCustomerNoTest`: yeni satırların customer_no'su aktif CUSTOMER kaydında → mark + `blockByCustomerNo` genişletmesi + union set ile tek hot-drop.
- [x] `DncDeltaSyncDoesNotAdvanceWatermarkOnFailureTest`: UPDATE hatasında FAILED log + watermark ilerlemez (retry güvenliği).
- [x] `DncDeltaSyncReentrancyGuardTest`: eşzamanlı ikinci tick hemen döner (`AtomicBoolean`).
- [x] ~~`DncDeltaSyncCleanupDisabledByDefaultTest`~~ **PLAN REVİZYONU 2 (03.06.2026, müşteri cevabı: "geri açılmasın, böyle bir talep yok")**: cleanup/geri açma KAPSAM DIŞI bırakıldı ve scaffold tamamen kaldırıldı — scheduler cleanup dalı, `CampaignDbOperations.fetchDncFilteredRows`/`reenableDncFilteredRowsByUniqueKeys`, `jobs.dnc-delta-sync.cleanup.*` property'leri, `dnc_cleanup_enabled` DDL'i, `pd_dnc_sync_log.rows_cleaned` kolonu ve cleanup test sınıfı silindi. (Aynı gün içindeki Revizyon 1 — opt-in'in DB-driven yapılması — bu kararla birlikte hükümsüz kaldı.)
- [x] **PLAN REVİZYONU 3 (03.06.2026, müşteri cevabı: "call producer'da görüp 76 ile kaydı kapatmam lazım")**: `DncEngineCustomerScopeFetchFilterTest` — `DNCEngine.applyDncToCalls`'a fetch-time customer kontrolü eklendi: sayfadaki distinct customer_no'lar tek batch IN-sorgusuyla aktif CUSTOMER marker'lara karşı doğrulanır, eşleşen çağrılar TRC=76 semantiğiyle düşürülür (delta-sync cron'una kadarki boşluk penceresi kapanır). Mapping'siz kampanyada sıfır maliyet kilidi, fail-open kilidi ve customer_no'suz sayfada sorgu atlama kilidi testte; expansion bilinçli olarak fetch yolunda yapılmaz (gece delta-sync'in işi).
- [x] `DncDeltaSyncCrossDbNoJoinContractTest`: kampanya DB sorguları `pd_dnc_pn` referansı içermez; pencere sorgusu bounded (`> ? / <= ?`) + keyset sayfalı; `setFilteredByDncListStatusByUniqueKeys` ve `reenableDncFilteredRowsByUniqueKeys` alan semantiği kilitli (re-enable yalnız TRC=76 + is_disabled=1 satırlar).

### PR-F — UI + i18n

- [x] `tests/angular/campaign/CampaignCustomerNoPicklistContractTest`: `campaignForm.html` içinde `campaign.colResultCustomerNo` select'i (+ `ng-required` YOK kilidi) + `restrictByType : ...CUSTOMER_NO`; `constants.js` içinde `CUSTOMER_NO`/`DATE_ENTERED` ve tip kısıtları; controller init listesi; 4 lokalizasyon dosyasında `campaign.colResultCustomerNo` key'i; karma.conf bundle kaydı. (Ek: `tests/campaign/db/CampaignSourceTableCustomerNoColumnTest` — default source tabloya customer_no base kolonu.)
- [x] Karma spec yazıldı (`src/test/angular/services/constants.spec.js`: CUSTOMER_NO/DATE_ENTERED tanımları + string tip kısıtları + Called Number tip regression'ı). KOŞUM BLOCKER: `node_modules` kurulu değil (`karma` binary yok); ajan dependency indirmez. Geliştirici `npm install` sonrası `npm.cmd run test:angular` koşmalı.

### PR-G — date_entered UI dropdown (15.06.2026)

- [x] `tests/angular/campaign/CampaignDateEnteredPicklistContractTest`: `campaignForm.html` içinde `campaign.colDateEntered` select'i (+ `ng-required` YOK kilidi) + `restrictByType : ...DATE_ENTERED`; `constants.js` içinde `DATE_ENTERED` tarih tip kısıtları (DATETIME dahil); controller init listesinde `colDateEntered`; 4 lokalizasyon dosyasında `campaign.colDateEntered` key'i.
- [x] Karma spec'e (`constants.spec.js`) date_entered tarih tip kısıtı `it()` bloğu eklendi (DATETIME/DATETIME2/SMALLDATETIME/TIMESTAMP). KOŞUM BLOCKER: PR-F ile aynı — `node_modules` yok.

## Uygulama Kontrol Listesi

### PR-A (WP1+WP2)

- [x] PR-A testlerini kırmızı yaz. (RED kanıtı: derleme hataları — `DNC_TYPE`, `setCustomerNo`, `setColResultCustomerNo`, `getColDateEntered` eksik sembolleri.)
- [x] `Constants.DNC_TYPE {PHONE, CUSTOMER}` enum'u (REASON yanına) + `DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_NO("customer_no")`, `DATE_ENTERED("date_entered")`.
- [x] `DNCPhoneNumber`: `dncType` (ORDINAL, field-init default PHONE), `customerNo`; `phoneNumber` nullable + `@NotBlank` kaldır.
- [x] `Campaign`: `colResultCustomerNo` (`col_result_customer_no`), `colDateEntered` (`col_date_entered`).
- [x] `CampaignFormDTO`: iki yeni alan.
- [x] `DNCPhoneNumberDao`: aktif telefon sorgusuna PHONE filtresi; yeni `findActiveCustomerNosByDncIdsAndCustomerNos`; `DNCPhoneNumberService` pass-through.
- [x] `DNCEngine.getPhoneNumbersWithExpire`: PHONE-olmayan/telefonsuz satır guard'ı. (Ek: `updatePhoneNumber` event yoluna da aynı guard eklendi — null key NPE ve yanlış engelleme riski.)
- [x] PR-A testlerini yeşile çevir; gerekirse küçük refactor + tekrar koş.

### PR-B (WP3+WP4)

- [x] PR-B testlerini kırmızı yaz. (RED kanıtı: ilk koşuda eksik metod/sınıf derleme hataları; strict-stub düzeltmesi sonrası davranış testleri yeşillendi.)
- [x] `DNCPhoneNumberService.createCustomerMarker(customerNo, reason, dnc)`.
- [x] Yeni `engine/CustomerDncService`: `blockByCustomerScope(Call, Campaign, DNC)` (WRAPUP_CODE + tetikleyen satır exclude), `blockByCustomerNo(String, Campaign, DNC)` (ADMINISTRATIVE), `collectCustomerPhoneNumbers` (parametrized SELECT, kampanya DB), idempotent persist + tek `updateDnc`.
- [x] `CampaignDbOperations.fetchRowsByCustomerNo` + `setFilteredByDncListStatusByCustomerNo` (parametrized, mevcut alan semantiği, yalnız açık satırlar, tetikleyen satır exclude). (Plan notu: telefon bazlı sibling UPDATE'i kapsamdan çıkarıldı — fallback'te satır işaretleme istenmiyor (Kriter 2 yalnız DNC kaydı bekler) ve fresh satırlar runtime DNC filtresiyle zaten yakalanır; ayrı-tablo şemasında DB-spesifik JOIN'li UPDATE karmaşası bilinçli olarak alınmadı.)
- [x] `DNCEngine`: `dropQueuedCallsForNumbers(DNC, Set<String>)` (cache-bazlı `campaignUsesDnc` + yarış-güvenli `remove(Object)`); DialerFacade erişimi `applicationContext.getBean` lazy-lookup pattern'iyle (DialerFacade.getDncEngine ile simetrik, çevrim yok). `saveCDR`/`checkStopCondition` aynı sınıf içinden yeniden kullanıldı; extrakt gerekmedi.
- [x] PR-B testlerini yeşile çevir; mevcut `DNCEngine` testleri yeşil kalmalı.

### PR-C (WP5+WP6)

- [x] PR-C testlerini kırmızı yaz.
- [x] `CallResultCode.dncScope` + `CallResultCodeDeserializer` guard'ı (bilinmeyen değer → null; toString'e dncScope eklendi).
- [x] `WebphoneSubscriber.updateDNCByResultCode(Call, DNC_TYPE)`: CUSTOMER dallanması (blockByCustomerScope + dropQueuedCallsForNumbers); PHONE/null yol birebir korunur; `updateDNCByCdrFallback` degrade + WARN.
- [x] PR-C testlerini yeşile çevir.

### PR-D (WP7)

- [x] PR-D testlerini kırmızı yaz.
- [x] `DNCDataManipulationController`: `POST /data/dnc/addCustomer/{campaignIdOrName}` (multipart + body varyantları, mevcut token doğrulama pattern'i).
- [x] `DNCDataManipulationService.processInsertCustomersForDefaultDnc` (union set ile sonda tek queue-drop). (Ek: `CampaignService.getCampaignWithPhoneNumberColumnsByNameOrId` — API yolunda lazy `phoneNumberColumns` koleksiyonunun transaction içinde initialize edilmesi; detached kullanım güvenli.)
- [x] PR-D testlerini yeşile çevir.

### PR-E (WP8)

- [x] PR-E testlerini kırmızı yaz.
- [x] `batch/scheduler/DncDeltaSyncScheduler`: eligibility (`dnc_enabled=1 && col_date_entered IS NOT NULL`), watermark okuma (`pd_dnc_sync_log` MAX SUCCESS), `findMaxDateEnteredAfter` probe + ucuz skip, `fetchRowsEnteredAfter` keyset sayfalı pencere, IN-chunk (≤1000) PD-DB DNC kontrolü, `setFilteredByDncListStatusByUniqueKeys` batch mark + `blockByCustomerNo` genişletmesi + union hot-drop, FAILED'da watermark ilerletmeme, `AtomicBoolean` guard. (REVİZE: cleanup scaffold'u müşteri kararıyla kaldırıldı; CampaignDbOperations'ta delta-sync için kalan metodlar `findMaxDateEnteredAfter`, `fetchRowsEnteredAfter`, `setFilteredByDncListStatusByUniqueKeys` + `partitionKeys` helper.)
- [x] `application.properties`: `jobs.dnc-delta-sync.cron=0 15 9 * * *`, `jobs.dnc-delta-sync.batch.size=500`, `jobs.dnc-delta-sync.cleanup.enabled=false` (REVİZE: `cleanup.campaign-ids` property'si kaldırıldı — kampanya opt-in'i `pd_campaign.dnc_cleanup_enabled` kolonundan dinamik okunur).
- [x] PR-E testlerini yeşile çevir. (Not: watermark değerleri uçtan uca string taşınır — tip dönüşümü yapılmaz; v1 hedefi DATETIME tipli date_entered kolonlarıdır, MSSQL/MySQL implicit convert ile çalışır.)

### PR-F (WP9)

- [x] PR-F testlerini kırmızı yaz (Java contract testi + Karma spec).
- [x] `campaignForm.html`: Called Number yanına `campaign.colResultCustomerNo` select'i (`ng-required` YOK, opsiyonel).
- [x] `constants.js`: `CUSTOMER_NO`, `DATE_ENTERED` + `RESULT_FIELDS_ALLOWED_TYPES[CUSTOMER_NO]` (string tipleri: VARCHAR/NVARCHAR/NVARCHAR2/CHARACTER_VARYING).
- [x] `campaignFormCtrl.js`: initializedResultColumns listesine `colResultCustomerNo`.
- [x] 4 lokalizasyon dosyasına `campaign.colResultCustomerNo` (en: "Customer No", tr: "Müşteri No", ru: "Номер Клиента", ua: "Номер Клієнта").
- [x] `createDefaultSourceTableForCampaign`'e `customer_no` base kolonu (MS_SQL/MYSQL/ORACLE/POSTGRE_SQL dört tip).
- [x] PR-F Java testlerini yeşile çevir. ([ ] `npm.cmd run test:angular` — environment blocker: node_modules yok; geliştirici kurulum sonrası koşacak.)
- [x] `karma.conf.js` files listesine `src/main/webapp/js/constants.js` eklendi (yeni spec koşulabilsin).

### PR-G (WP9 ek — date_entered UI)

- [x] PR-G testlerini kırmızı yaz (Java contract testi `CampaignDateEnteredPicklistContractTest` + Karma spec `it()` bloğu).
- [x] `campaignForm.html`: Customer No yanına `campaign.colDateEntered` select'i (`ng-required` YOK, opsiyonel; `restrictByType : ...DATE_ENTERED`).
- [x] `constants.js`: `RESULT_FIELDS_ALLOWED_TYPES[DATE_ENTERED]` (tarih tipleri: DATETIME/DATETIME2/SMALLDATETIME/DATETIMEOFFSET/DATE/TIMESTAMP/TIMESTAMP_WITHOUT_TIME_ZONE).
- [x] `campaignFormCtrl.js`: initializedResultColumns listesine `colDateEntered`.
- [x] 4 lokalizasyon dosyasına `campaign.colDateEntered` (en: "Date Entered", tr: "Veri Giriş Tarihi", ru: "Дата Ввода", ua: "Дата Введення").
- [x] PR-G Java testlerini yeşile çevir. ([ ] `npm.cmd run test:angular` — PR-F ile aynı environment blocker.)
- Not: `colDateEntered` alanı PR-A'da entity+DTO'ya eklenmişti; PR-G yalnız UI mapping katmanıdır. Default source table'a `date_entered` base kolonu EKLENMEZ (3rd-party/müşteri sorumluluğu — delta-sync mapping boşsa kampanyayı zaten atlar).

### Ortak

- [ ] Onaylanan planın tüm adımları execution plan'a işlendi.
- [ ] Her PR diliminde hot path performans notu korunur (yeni hot path maliyeti çıkarsa plan güncellenir).
- [ ] DDL script'leri (analizdeki bölüm) DBA'ya teslim edilir; PR-A merge'ünden önce hedef ortamda koşulması not edilir.
- [ ] Her PR dilimi sonunda history kaydı (`gbpd-agent-history`, append-only).
- [ ] Mimari doküman etkisi: DNC davranışı değiştiği için ilgili `docs/architecture` dosyasında (DIALER-RUNTIME / DATA-PERSISTENCE-AND-DB) customer-scope DNC bölümü güncellenir (PR-C veya PR-E ile birlikte).

## Doğrulama

Planlanan hedefli komutlar (local tool path `.codex/TOOL-PATHS.local.properties` varsa oradan; Maven offline):

```text
PR-A: mvn -o -Dtest=DncPhoneNumberCustomerScopeMappingTest,DncTypeEnumOrdinalContractTest,DncEngineCustomerRowsExcludedFromPhoneCacheTest,DncPhoneNumberDaoCustomerScopeQueryContractTest,CampaignCustomerNoColumnMappingTest test
PR-B: mvn -o -Dtest=CustomerDncService*Test,CampaignDbOperationsMarkSiblingsByCustomerNoSqlContractTest,DncEngineDropsBufferedCallsForBlockedNumbersTest,DncEngineQueueDrop*Test test
PR-C: mvn -o -Dtest=CallResultCodeDeserializerDncScopeBackwardCompatTest,WebphoneSubscriber*Test test
PR-D: mvn -o -Dtest=DncCustomerApiInsertsCustomerScopeTest,DncDataManipulationPhoneEndpointsUnchangedRegressionTest test
PR-E: mvn -o -Dtest=DncDeltaSync*Test test
PR-F: mvn -o -Dtest=CampaignCustomerNoPicklistContractTest test && npm.cmd run test:angular
PR-G: mvn -o -Dtest=CampaignDateEnteredPicklistContractTest,CampaignCustomerNoPicklistContractTest test && npm.cmd run test:angular
```

Sonuçlar her PR dilimi kapanışında bu bölüme işlenecek.

Sonuçlar:

- PR-A (03.06.2026): RED çalıştırması beklenen derleme hatalarıyla doğrulandı. GREEN çalıştırması: `mvn -o ... -Dtest=DncPhoneNumberCustomerScopeMappingTest,DncTypeEnumOrdinalContractTest,DncEngineCustomerRowsExcludedFromPhoneCacheTest,DncPhoneNumberDaoCustomerScopeQueryContractTest,CampaignCustomerNoColumnMappingTest,DNCEngineTest,DNCPhoneNumberServiceTest,DNCServiceTest,DNCTest,DNCValidatorTest,CampaignDefaultDncProtectionRegressionTest test` → `Tests run: 42, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS). Yeni 18 test + 24 mevcut DNC regression testi yeşil.
- PR-B (03.06.2026): GREEN çalıştırması: `mvn -o ... -Dtest=CustomerDncService*Test(3 sınıf),CampaignDbOperationsMarkSiblingsByCustomerNoSqlContractTest,DncEngineDropsBufferedCallsForBlockedNumbersTest,DncEngineQueueDropRespectsDncListSharingTest,DncEngineQueueDropDoesNotResetFetchedFlagTest,DncEngineCustomerRowsExcludedFromPhoneCacheTest,DNCEngineTest,DNCPhoneNumberServiceTest,CampaignFreshDataListSizeSqlRegressionTest test` → `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS). Yeni 13 test + 22 regression yeşil (DNCEngineTest 14/14 dahil — davranış korundu).
- PR-C (03.06.2026): GREEN çalıştırması: `mvn -o ... -Dtest=CallResultCodeDeserializerDncScopeBackwardCompatTest,WebphoneSubscriberCustomerScopeWrapupTest,WebphoneSubscriberPhoneScopeUnchangedRegressionTest,WebphoneSubscriberCdrFallbackCustomerDegradesToPhoneTest,WebphoneSubscriberTest test` → `Tests run: 57, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS). Yeni 10 test + mevcut WebphoneSubscriberTest 47/47 (Redis contract geriye uyumluluğu ve PHONE yolu birebir korundu).
- PR-D (03.06.2026): GREEN çalıştırması: `mvn -o ... -Dtest=DncCustomerApiInsertsCustomerScopeTest,DncDataManipulationPhoneEndpointsUnchangedRegressionTest,DataManipulationNullFilterRegressionTest test` → `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS).
- PR-E (03.06.2026): GREEN çalıştırması: `mvn -o ... -Dtest=DncDeltaSync*7 sınıf,CampaignDbOperationsMarkSiblingsByCustomerNoSqlContractTest,DNCEngineTest,CampaignFreshDataListSizeSqlRegressionTest test` → `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS). RED aşamasında testlerin yakaladığı eksik (`colUniqueKey` fixture alanı) giderildi; davranış doğrulandı.
- PR-F (03.06.2026): GREEN çalıştırması (Java): `mvn -o ... -Dtest=CampaignCustomerNoPicklistContractTest,CampaignSourceTableCustomerNoColumnTest,CampaignDncDefaultPicklistContractTest,DncDateInputsContractTest,CampaignFreshDataMonitoringContractTest test` → `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS). `npm.cmd run test:angular` ÇALIŞTIRILAMADI: `node_modules` kurulu değil (`karma` binary yok) — environment blocker; ajan dependency indirmez (AGENTS kuralı). Geliştirici `npm install` sonrası koşmalı; yeni `constants.spec.js` o koşuda doğrulanacak.
- PR-E revizyonu (03.06.2026, cleanup opt-in DB-driven): `mvn -o ... -Dtest=<7 DncDeltaSync sınıfı> test` → `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS; CleanupDisabledByDefault 3 test — pozitif kilit dahil). [Bu revizyon aynı gün müşteri cevabıyla hükümsüz kaldı — aşağıya bakınız.]
- Plan Revizyonu 2+3 (03.06.2026, müşteri cevapları — cleanup kapsam dışı + fetch-time customer kontrolü): `mvn -o ... -Dtest=DncEngineCustomerScopeFetchFilterTest,DNCEngineTest,DncEngineCustomerRowsExcludedFromPhoneCacheTest,DncEngineQueueDrop*2,DncEngineDrops*,DncDeltaSync*6 sınıf,CustomerDncService*3,CampaignDbOperationsMarkSiblingsByCustomerNoSqlContractTest,WebphoneSubscriberTest,CampaignFreshDataListSizeSqlRegressionTest test` → `Tests run: 92, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS). Yeni fetch-filter testi 4/4; cleanup kodu ve testleri kaldırıldı; tüm regression'lar yeşil.
- PR-G (15.06.2026, date_entered UI dropdown): GREEN çalıştırması (Java): `mvn -o ... -P properties-tr-dev -Dtest=CampaignDateEnteredPicklistContractTest,CampaignCustomerNoPicklistContractTest test` → `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS). Yeni 4 date_entered contract testi + 5 customer_no regression testi yeşil. `npm.cmd run test:angular` ÇALIŞTIRILAMADI: PR-F ile aynı environment blocker (`node_modules` yok); yeni `constants.spec.js` date_entered `it()` bloğu o koşuda doğrulanacak.

## Kapanış Kriteri

- [x] PR-A testleri çalıştırıldı ve başarılı (42/42, 03.06.2026).
- [x] PR-B testleri çalıştırıldı ve başarılı (35/35, mevcut DNCEngine testleri dahil, 03.06.2026).
- [x] PR-C testleri çalıştırıldı ve başarılı (57/57, WebphoneSubscriberTest regression dahil, 03.06.2026).
- [x] PR-D testleri çalıştırıldı ve başarılı (8/8, 03.06.2026).
- [x] PR-E testleri çalıştırıldı ve başarılı (31/31, 03.06.2026).
- [x] PR-F Java contract testleri çalıştırıldı ve başarılı (12/12, 03.06.2026). [ ] `npm.cmd run test:angular` koşumu environment blocker nedeniyle beklemede.
- [x] PR-G Java contract testleri çalıştırıldı ve başarılı (9/9, 15.06.2026). [ ] `npm.cmd run test:angular` koşumu PR-F ile aynı environment blocker nedeniyle beklemede.
- [x] PR-H testleri çalıştırıldı ve başarılı (113/113, 09.07.2026). [ ] `npm.cmd run test:angular` PR-F/G ile aynı environment blocker nedeniyle beklemede (Angular kolonu file-content contract testiyle kilitlendi).
- [x] Test çalıştırılamadıysa environment blocker açık yazıldı (Karma: node_modules yok).
- [x] Final yanıtlarda kısa Test Özeti sunuldu.

## Riskler ve Geri Dönüş

- Telefon cache'i kirlenmesi PR-A filtreleriyle engellenir; regression testi koruma altındadır. Geri dönüş: `dnc_type` filtresi geri alınırsa eski davranış (tüm kayıtlar telefon) döner; CUSTOMER kaydı yazan akışlar henüz aktif değilse etki yoktur.
- Hot-drop yanlış kampanyadan çağrı düşürürse data kaybı olur; DNC-paylaşım filtresi + testi bu riski kilitler. Geri dönüş: `dropQueuedCallsForNumbers` çağrıları kaldırılırsa fetch-time filtre davranışı tek başına devam eder (bugünkü durum).
- Cleanup yanlış satır geri açabilir; çift kilit + yalnız TRC=76 koşulu + default kapalı. Geri dönüş: property kapalı tutulur.
- Delta-sync büyük import'ta uzun sürebilir; sayfalama + chunk + watermark ile sınırlandırılır; cron gece düşük yoğunluk saatine alınır. Geri dönüş: cron property'si kaldırılırsa job hiç tetiklenmez.
- `phone_number` NOT NULL gevşetmesi beklenmedik validator etkisi yaratırsa: yalnız CUSTOMER marker yazımı durdurulur (PHONE akışları telefon set etmeye devam eder), şema değişikliği geri alınmaz.
- Redis `dncScope` alanı eski gbwebphone sürümleriyle null gelir → bugünkü yol; risk regression testiyle kilitli.

## Açık Sorular

Açık soru yok. (Analizdeki dört karar 03.06.2026'da geliştiriciyle netleşti; PR-F'teki customer_no tip kısıtı için varsayılan string tipleridir, müşteri formatı netleşirse uygulama sırasında genişletilir.)

## Tamamlanma Notu

Uygulama tamamlandı (PR-A..PR-H; PR-A..PR-F 03.06.2026, PR-G date_entered UI dropdown 15.06.2026, PR-H DNC grid customer_no + marker gizleme 09.07.2026). Tüm Java hedefli testleri yeşil: PR-A 42/42, PR-B 35/35, PR-C 57/57, PR-D 8/8, PR-E 31/31, PR-F 12/12, PR-G 9/9 (toplamda yeni 64+ test maddesi + mevcut DNC/Webphone/CampaignDb regression'ları). PR-G ile delta-sync'in `colDateEntered` mapping'i artık Call Results admin ekranından (SQL gerektirmeden) seçilebilir; bu, manuel test sırasında flag'lenen "date_entered için UI yok" boşluğunu kapatır. Plan durumu: **doğrulama beklemede — yalnız `npm.cmd run test:angular` koşumu** (environment blocker: `node_modules` kurulu değil; geliştirici `npm install` sonrası koşacak, yeni `constants.spec.js` o koşuda doğrulanacak). DDL script'leri (analiz dokümanındaki bölüm) deploy öncesi DBA tarafından hedef ortamda koşulmalıdır. Mimari doküman güncellemesi: DNC customer-scope davranışı `docs/analyse/03.06.2026/FEAT-318332-MUSTERI-BAZLI-DNC.md` analizinde ayrıntılı kayıtlıdır; `docs/architecture/DIALER-RUNTIME.md` güncellemesi PR'lar merge edildikten sonra ayrı küçük docs işi olarak yapılabilir (davranış henüz üretimde aktif değil — tüm akışlar config/flag kapalı durumda pasiftir).

## PR-H — DNC grid customer_no kolonu + marker gizleme (09.07.2026)

UAT sırasında müşteri, CUSTOMER marker'ının (`phone_number` NULL) DNC Form "Phone Numbers" grid'inde **boş satır** olarak göründüğünü bildirdi. Marker DB'de doğru (delta-sync yeniden-genişletme + idempotency için şart) ama telefon grid'ine sızmamalı. Geliştirici kararı: marker grid'den **gizlenir** + customer-scope telefon satırlarına `customer_no` **damgalanıp** grid'e kolon eklenir (yeni task/branch değil, feature branch'inde devam).

Değişiklikler:
- `DNCPhoneNumberService`: `create(phone, reason, dnc, customerNo)` overload'u (PHONE tipi korunur; 3-arg create null ile delegeler); `addCustomFilters` override + `excludeCustomerMarkers` → grid sorgusuna `phoneNumber IS_NOT_NULL` (GRID_FILTER=2048) filtresi (marker gizli, sayfalama sayacı doğru).
- `CustomerDncService.persistPhoneNumbers`: customer-scope'ta telefon satırlarına `customer_no` damgalar; E1 fallback + numara bazlı akışta null.
- `DNCPhoneNumberListDTO`: `customerNo` alanı (Dozer isimle map'ler).
- `dncPhoneCtrl.js`: grid'e `customerNo` kolonu; 4 lokalizasyon dosyasına `dnc.customerNo` (en Customer No / tr Müşteri No / ru Номер Клиента / ua Номер Клієнта).
- Testler: 3 çekirdek `CustomerDncService*Test` 4-arg imzaya güncellendi; yeni `DncPhoneNumberListExcludesCustomerMarkerTest` (grid filtresi, 3) + `DncPhoneNumberGridCustomerNoColumnContractTest` (Angular kolon + i18n, 2).

Doğrulama: `mvn -o -P properties-tr-dev -Dtest=CustomerDncService*Test,DncPhoneNumberListExcludesCustomerMarkerTest,DncPhoneNumberGridCustomerNoColumnContractTest,DncCustomerApiInsertsCustomerScopeTest,DncDataManipulationPhoneEndpointsUnchangedRegressionTest,WebphoneSubscriber*Test,CallResultCodeDeserializerDncScopeBackwardCompatTest,DNCEngineTest,DNCPhoneNumberServiceTest,DNCServiceTest,DncEngine*Test,DncDeltaSync*Test test` → **Tests run: 113, Failures: 0, Errors: 0** (BUILD SUCCESS). Numara-yolu (3-arg create delegasyonu) ve WebphoneSubscriber 48/48 korundu.

Notlar: (a) idempotency — zaten DNC'de olan numara tekrar customer-scope'a girerse ilk kaydı korunur, `customer_no` güncellenmez (ilk-engelleyen-kazanır). (b) Save akışı paginated liste ucundan (`/dncpn/list`) bağımsız DNC entity'sini kaydeder; gizlenen marker Save'de düşmez.
