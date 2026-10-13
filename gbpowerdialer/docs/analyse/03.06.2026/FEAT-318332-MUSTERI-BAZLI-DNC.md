# FEAT Müşteri Bazlı DNC (Customer-Scope DNC)

## Bağlam

Task `318332` kapsamında müşteri talebi: Mevcut DNC yapısı yalnız tekil telefon numarası engelliyor. Kampanya datasında bir müşterinin birden fazla numarası (gsm, ev, iş) farklı kolonlarda tutulabiliyor. Müşteri "beni hiçbir numaramdan aramayın" dediğinde diğer numaraları sistem tarafından bilinmediği için aranmaya devam ediyor; bu KVKK ihlali ve şikayet riski üretiyor.

Talep edilen başlıklar:

1. Kampanya ana tablolarına `customer_no` alanının base alan olarak eklenmesi; üçüncü parti import yapılarının bu alanı doldurması.
2. Telefon kolonlarının kampanya bazlı `pd_pn_column` tablosundan dinamik okunması.
3. Call Results admin eşleştirme ekranına Customer No mapping alanı eklenmesi.
4. Agent (gbwebphone) ve harici yönetim arayüzü (Puzzle) için "Numara Bazlı DNC" / "Müşteri Bazlı DNC (tüm numaralar)" seçeneği.
5. Günlük/aylık yeni datalarda `date_entered` delta kontrolüyle DNC havuzunun senkronizasyonu (yeni eşleşenlerin engellenmesi, DNC'den çıkanların temizlenmesi).

Edge case'ler: (E1) `customer_no` null ise satırdaki telefon kolonlarına fallback, (E2) DNC tetiklendiği anda bellekteki kuyruklarda bekleyen çağrıların originate edilmeden düşürülmesi, (E3) gece yarısı veri yüklemelerinde tarih kayması — `GETDATE()` yerine son başarılı import/sync zaman damgası.

## İş Etiketi

`feat`

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/model/DNCPhoneNumber.java`
- `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java`
- `gbpowerdialer/src/main/java/com/powerdialer/model/CallResultCode.java`
- `gbpowerdialer/src/main/java/com/powerdialer/serialization/CallResultCodeDeserializer.java`
- `gbpowerdialer/src/main/java/com/powerdialer/util/Constants.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dto/CampaignFormDTO.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dao/impl/DNCPhoneNumberDao.java`
- `gbpowerdialer/src/main/java/com/powerdialer/service/DNCPhoneNumberService.java`
- `gbpowerdialer/src/main/java/com/powerdialer/service/DNCDataManipulationService.java`
- `gbpowerdialer/src/main/java/com/powerdialer/controller/DNCDataManipulationController.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/DNCEngine.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/WebphoneSubscriber.java`
- Yeni: `gbpowerdialer/src/main/java/com/powerdialer/engine/CustomerDncService.java`
- Yeni: `gbpowerdialer/src/main/java/com/powerdialer/batch/scheduler/DncDeltaSyncScheduler.java`
- `gbpowerdialer/src/main/resources/application.properties` (+ profil property dosyaları)
- `gbpowerdialer/src/main/webapp/pages/campaignForm.html`
- `gbpowerdialer/src/main/webapp/js/constants.js`
- `gbpowerdialer/src/main/webapp/js/controllers/campaignFormCtrl.js`
- `gbpowerdialer/src/main/webapp/js/localization/localization_{en,tr,ru,ua}.js`

## Mevcut Davranış

- **DNC veri modeli**: `DNC` → `pd_dnc_list` (kampanya OneToOne default DNC, tenant-global flag, ManyToMany `pd_campaign_dnc_list`). `DNCPhoneNumber` → `pd_dnc_pn` (`phone_number` NOT NULL, `reason` ORDINAL enum, `expired_date`). Tip kavramı yok; tüm kayıtlar telefon numarasıdır.
- **Runtime DNC filtresi**: `DNCEngine` bellekte `Map<Campaign, Map<DNC, Map<phone, expireMs>>>` + tenant-global map tutar (60 sn TTL + event-driven `updateDnc`/`updatePhoneNumberInDnc`). `CallProducer` fetch sonrası `applyDncToCalls` çağırır; aday numaralar DB'de revalidate edilir (`findActivePhoneNumbersByDncIdsAndPhoneNumbers`), eşleşen call'lar listeden çıkarılır ve `CampaignDbOperations.setFilteredByDncListStatus` ile kapatılır: `complete_date = now`, `telephony_result_code = 76 (TELEPHONY_RC_ID_FILTERED_BY_DNC)`, `is_disabled = 1`, `user_id/user_result_code = NULL`; CDR TRC=76 ile yazılır.
- **Wrap-up DNC**: gbwebphone → Redis `channelGBWebPhone` → `WebphoneSubscriber.handleResultCodeEntityMessage` → `updateDNCByResultCode(call)`. Result code `isDncIdentifier()` ise kampanyanın default DNC'sine, `SYSTEM_RESULT_CODE.DONT_CALL_CAMPAIGN(9989)/DONT_CALL_TENANT(9990)` ise ilgili DNC'ye **yalnız `call.getCalledNumber()`** eklenir. Call bellekte yoksa CDR fallback'i `cdr.getContactNumber()` ile aynı işi yapar. Scope kavramı yok.
- **Telefon kolonları**: `PhoneNumberColumn` → `pd_pn_column` (campaign_id, col_number, col_sort...). `campaign.getEnabledPhoneNumberColumns()` ile dinamik çözümleme **zaten mevcut** (talep maddesi 2 karşılanmış durumda; yeni geliştirmeler bu mevcut yapıyı kullanacak).
- **Call Results eşleştirme**: ayrı ekran değil; campaign formunun Call Results tab'i. Tüm mapping'ler `pd_campaign` üzerinde string kolonlardır (`col_result_called_number` vb.). `customer_no` mapping'i yok.
- **Harici DNC API**: `POST /data/dnc/add|disable/{campaignIdOrName}` (token doğrulamalı, `TokenCampaignValidatorService`) default DNC'ye telefon ekler/pasifler. Customer kavramı yok.
- **Batch altyapısı**: `CampaignResultJobService` (`@Scheduled` cron + `pd_job_config`/`pd_campaign_job_log`), `MirrorScheduler` (`AtomicBoolean` re-entrancy), `JITExpireValidator` (fetch sırasında DNC'den sonra çalışan, 60 sn cache'li, batch SELECT'li JIT filtre — bu feature için birebir örnek pattern). `date_entered` kavramı ve delta-sync job'ı yok.
- **Tablo oluşturma**: `createDefaultSourceTableForCampaign` / `createDefaultResultTable` / `addDefaultResultColumnsToSourceTable` base kolonları üretir; `customer_no` ve `date_entered` yok. Kampanya tabloları kampanya bazlı ayrı DB/sunucuda olabilir (`dbConnectionString`); PD'nin kendi `pd_*` tablolarıyla cross-DB JOIN mümkün değildir.

## Beklenen Davranış

- Agent wrap-up'ta DNC içerikli result code ile birlikte **scope** gönderebilir: `PHONE` (bugünkü davranış) veya `CUSTOMER`.
- `CUSTOMER` scope'ta: satırın `customer_no` değeri campaign mapping'i (`colResultCustomerNo`) üzerinden okunur; ana tabloda o müşteriye ait tüm satırlardaki tüm enabled telefon kolon değerleri toplanır; DNC'ye 1 adet `CUSTOMER` marker + her numara için `PHONE` kaydı yazılır (Kural 1 — çift katmanlı kayıt); müşterinin diğer satırları `is_disabled=1 + TRC=76 + complete_date` ile aranamaz yapılır; bellekteki `dataQueue`/`callQueue`'da bekleyen eşleşen çağrılar originate edilmeden düşürülür (E2).
- `customer_no` boşsa (E1): hata üretilmez; mevcut satırın (rawData) telefon kolonlarındaki numaralar `PHONE` olarak DNC'ye yazılır, `CUSTOMER` marker yazılmaz.
- Puzzle, token korumalı yeni API ile müşteri numarası bazlı engelleme yapabilir (`/data/dnc/addCustomer/...`); mevcut telefon bazlı endpoint'ler değişmez.
- Delta-sync scheduler: `colDateEntered` mapping'i dolu ve `dncEnabled=true` kampanyalarda, son başarılı senkron watermark'ından yeni `date_entered` değeri varsa yalnız yeni satırları tarar; telefon veya customer_no eşleşenleri kapatır; customer eşleşmelerinde telefonları `PHONE` olarak DNC'ye genişletir. Yeni data yoksa ucuz `MAX()` probe ile süreci pas geçer (Kriter 3). Watermark karşılaştırması `GETDATE()` kullanmaz (E3).
- Fetch-time customer emniyeti (müşteri cevabı 03.06.2026 — "call producer'da görüp 76 ile kaydı kapatmam lazım"): `colResultCustomerNo` mapping'li kampanyalarda producer fetch'i, sayfadaki distinct customer_no'ları tek batch sorguyla aktif CUSTOMER marker'lara karşı kontrol eder; eşleşen çağrılar delta-sync cron'u beklenmeden telefon filtresiyle aynı semantikle (TRC=76 + CDR) düşürülür. Sorgu hatasında fail-open (telefon filtresi birincil koruma, delta-sync telafi eder).
- "DNC'den çıkanların temizlenmesi" (eski satırların geri açılması): **KAPSAM DIŞI** — müşteri cevabı (03.06.2026): "geri açılmasın, böyle bir talep yok". DNC'den çıkışın tek yolu expire süresinin dolmasıdır; süresi dolan numara yalnız yeni yüklenen/henüz aranmamış satırlarda normal şekilde aranır hâle gelir (mevcut expire-aware davranış yeterli). Bu karar üzerine cleanup scaffold'u kod tabanından tamamen kaldırılmıştır.
- Call Results tab'inde Called Number yanında opsiyonel "Customer No" kolon mapping dropdown'u görünür.
- gbwebphone agent ekranındaki iki seçenekli UI ayrı repo'nun işidir; bu repo yalnız Redis contract'ını (`dncScope`) ve backend davranışını sağlar.

## Kod Okuma Bulguları

- `WebphoneSubscriber.updateDNCByResultCode` (~671) ve `updateDNCByCdrFallback` (~707) genişletme noktalarıdır; `processActiveCallResultCode` (~298) result code'u set edip DNC güncellemesini çağırır.
- `CallResultCode` custom deserializer kullanır (`node.has(...)` guard'ları) → opsiyonel `dncScope` alanı geriye dönük uyumlu eklenebilir; eski mesajlar null scope ile bugünkü yola düşer.
- `Call.rawData` `SELECT * FROM src LEFT JOIN res` satırını taşır → `customer_no` değeri ve mevcut satırın telefonları (E1 fallback, `getNumbersInRawData` ~527) ek sorgu olmadan okunabilir.
- `DNCEngine.applyDncToCalls` yalnız `calledNumber` üzerinden çalışır; cache değer tipi `Map<String, Long>`'dur. CUSTOMER kayıtları telefon cache'ine girerse customer_no string'i numara gibi engellenir → cache yükleme (`getPhoneNumbersWithExpire` ~155) ve revalidation DAO sorgusu `dnc_type=PHONE AND phone_number IS NOT NULL` filtresi almak zorundadır.
- `DNCEngine.filterCallsByDncs` private'tır ve fetch koleksiyonu üzerinde çalışır; kuyruk düşürme için CDR üretim bloğu (`saveCDR` ~511) davranış değiştirmeden extract edilip paylaşılmalıdır.
- `CallProducerConsumerTemplate.offerRemoveCall` kuyruktan çıkarırken `removeFetchedDataFlagFromCall` çağırır (satır yeniden aranabilir olur). DNC düşürmede bu **yanlıştır**; satır `setFilteredByDncListStatus` ile kapatılmalıdır.
- `CallUpToDateKeeper`'a kayıt originate anında yapılır; oradaki çağrılar Asterisk'e gönderilmiştir, "originate öncesi iptal" kapsamına girmez. E2'nin gerçek karşılığı `CallProducer.dataQueue` (PriorityBlockingQueue) + `CallConsumer.callQueue` (LinkedBlockingDeque) taramasıdır; her ikisi public getter ile `CampaignContext` üzerinden erişilebilir, `DialerFacade.getCampaignContexts()` mevcuttur.
- `existsByPhoneNumberAndDncId` idempotency kontrolü mevcut (yalnız deleted=0 bakar); customer genişletmesinde tekrar kullanılabilir.
- `DNCDataManipulationController/Service` Puzzle entegrasyonu için hazır şablondur (token doğrulama, DataSupplier, batch insert, tek `dncEngine.updateDnc`).
- `JITExpireValidator` + `MirrorScheduler` + `CampaignResultJobService`, delta-sync scheduler'ın izleyeceği üç pattern'i verir: 60 sn cache, `AtomicBoolean` re-entrancy, sayfalı batch, `TransactionTemplate`, cron property.
- `hbm2ddl.auto` kapalı, repo'da migration framework yok → `pd_*` DDL'leri DBA'ya script olarak teslim edilir.
- `setFilteredByExpireStatus` (~1394) `is_disabled=1` işaretlemesinin ikinci örneğidir; `countFreshDataListSize` `is_disabled=1` satırları fresh data dışında tutar (PR 23034). Fresh fetch sorgusu `complete_date IS NULL` koşuluyla DNC'li satırları zaten eler.

## Tasarım Kararları

1. **CUSTOMER scope = PHONE kayıtlarına genişletme (expansion).** Runtime hot path'te customer_no eşleşmesi yapılmaz; engelleme anında müşterinin tüm numaraları `PHONE` kaydına çevrilir. Mevcut fetch-time DNC filtresi sıfır değişiklikle çalışmaya devam eder. `CUSTOMER` marker'ı, delta-sync'in yeni datalarda yeniden genişletme yapması ve izlenebilirlik içindir.
2. **`pd_dnc_pn` şeması**: `dnc_type INT NOT NULL DEFAULT 0` (`DNC_TYPE {PHONE=0, CUSTOMER=1}`, ORDINAL — mevcut `REASON` pattern'i) + `customer_no NVARCHAR(255) NULL` + `phone_number` nullable'a gevşetilir (CUSTOMER marker'da telefon yoktur; `customer_no` değerinin `phone_number` kolonuna yazılması REDDEDİLDİ — telefon cache'ine sızma riski).
3. **Satır işaretleme**: yeni `dnc_enabled` kolonu eklenmez; talepteki "dnc_enabled=1" mevcut `is_disabled=1 + TRC=76 + complete_date` mekanizmasıyla karşılanır (kullanıcı kararı).
4. **Hot-drop**: `DNCEngine.dropQueuedCallsForNumbers(DNC, Set<String>)` — yalnız o DNC listesini kullanan kampanyaların (`getDnc()`/`getDncs()`/global+aynı tenant) iki kuyruğu taranır; düşürülen call'lar `setFilteredByDncListStatus` + CDR(TRC=76) + `incrementTelephonyResultCode(76)` + stop-condition kontrolü ile mevcut DNC filtre davranışının aynısını alır. `is_fetched` resetlenmez.
5. **Redis contract**: `CallResultCode.dncScope` opsiyonel; deserializer `node.has("dncScope")` + bilinmeyen değerde null. Channel adı ve mevcut alanlar değişmez.
6. **CDR-fallback sınırı (v1)**: call bellekte yoksa CUSTOMER scope PHONE'a degrade olur (`cdr.contactNumber`); Redis subscriber thread'inde kampanya DB lookup'ı yapılmaz. Kalan numaraları delta-sync yakalar. WARN log yazılır.
7. **date_entered**: `Campaign.colDateEntered` mapping alanı (kullanıcı kararı). Mapping boş → kampanya delta-sync'e girmez; hata yok.
8. **Delta-sync**: watermark tabanlı (`pd_dnc_sync_log`, son başarılı pencerenin `MAX(date_entered)` değeri). Cross-DB JOIN yok; adaylar kampanya DB'sinden sayfalı çekilir, PD DB'de IN-chunk ile doğrulanır, eşleşenler kampanya DB'de batch UPDATE ile kapatılır. Hata durumunda watermark ilerlemez. (Revizyon 03.06.2026: cleanup/geri açma müşteri cevabıyla kapsam dışı kaldı ve scaffold kod tabanından kaldırıldı — bkz. madde 10.)
9. **Fetch-time customer emniyeti** (müşteri cevabı 03.06.2026): `DNCEngine.applyDncToCalls`, telefon filtresine ek olarak `colResultCustomerNo` mapping'li kampanyalarda fetch sayfası başına TEK batch IN-sorgusuyla aktif CUSTOMER marker eşleşmesini kontrol eder ve eşleşen çağrıları aynı TRC=76 semantiğiyle düşürür. Böylece data yüklemesi ile delta-sync cron'u arasındaki boşluk penceresi kapanır. Mapping'siz kampanyada sıfır maliyet; sorgu hatasında fail-open. Expansion (telefonların PHONE'a yazılması) fetch yolunda YAPILMAZ — gece delta-sync'in işidir (hot path'te insert maliyeti ve DNCEngine↔CustomerDncService çevrimi bilinçli olarak alınmadı).
10. **Cleanup kapsam dışı** (müşteri cevabı 03.06.2026: "geri açılmasın, böyle bir talep yok"): daha önce çift kilitli scaffold olarak planlanan geri açma akışı tamamen kaldırıldı — scheduler cleanup dalı, `fetchDncFilteredRows`/`reenableDncFilteredRowsByUniqueKeys` metodları, cleanup property'leri ve `dnc_cleanup_enabled` DDL'i silindi. İhtiyaç doğarsa git geçmişinden geri alınabilir.
9. **Base kolon**: `createDefaultSourceTableForCampaign`'e `customer_no` eklenir (yalnız PD'nin oluşturduğu default tablolar). `date_entered` müşteri/DBA tarafına rehber DDL olarak bırakılır; mevcut müşteri tablolarına kolon eklemek müşteri/DBA sorumluluğudur.

## Riskler

- **Telefon cache kirlenmesi**: CUSTOMER kayıtları `dnc_type` filtresi olmadan cache'e girerse customer_no değerleri numara gibi engellenir. Önlem: cache yükleme + revalidation sorgusu + `getPhoneNumbersByList` türevi tüm okuma noktalarına PHONE filtresi; regression testi.
- **`phone_number` NOT NULL gevşetme**: `@NotBlank` kaldırma admin/file-import akışlarını etkilememeli (o akışlar her zaman telefon set eder ve PHONE kalır). Validator/DTO etkisi testle kilitlenir.
- **Kuyruk düşürmede yanlış pattern**: `removeFetchedDataFlagFromCall` çağrılırsa satır yeniden aranabilir olur (double-dial riski). Önlem: ayrı drop yolu + "fetched flag resetlenmez" testi.
- **Yanlış kampanyadan çağrı düşürme**: DNC listesini paylaşmayan kampanyaların kuyruğuna dokunulmamalı. Önlem: DNC-paylaşım filtresi + testi.
- ~~**Cleanup yan etkisi**~~: müşteri kararıyla (03.06.2026) geri açma kapsam dışı kaldı; bu risk kalemi düştü.
- **Fetch-time customer kontrolü maliyeti**: `colResultCustomerNo` mapping'li kampanyalarda producer fetch sayfası başına +1 indexed IN-sorgusu eklenir (call başına değil; mevcut DNC revalidation/JIT expire ile aynı sınıf). Mapping'siz kampanyalarda sıfır maliyet. Sorgu hatası fail-open'dır — arama durmaz, delta-sync telafi eder.
- **Delta-sync hacmi**: büyük import'larda aday seti bellek baskısı yapabilir. Önlem: id bazlı sayfalama + IN-chunk (≤ ~2000) + batch UPDATE; numaralar topluca belleğe alınmaz.
- **Cross-DB**: kampanya tabloları ayrı sunucuda olabilir; tek SQL'de `pd_dnc_pn` ile JOIN denenmez (contract testi ile kilitlenir).
- **Redis/Dozer geriye uyumluluk**: `dncScope` yokken davranış birebir aynı kalmalı; `CampaignFormDTO` yeni alanları null gelebilmeli. Regression testleri zorunlu.
- **Hibernate şema uyumu**: DDL uygulanmadan yeni entity alanlarıyla uygulama açılırsa sorgu hatası oluşur. Önlem: PR-A DDL'leri deploy öncesi koşulur; analizdeki DDL bölümü DBA'ya teslim edilir.

## Performans Etkisi

- **Hot path sınıfı: ek maliyet var ve gerekçesi/azaltımı yazıldı.** Çağrı başına/event başına yeni DB query, scan veya lock eklenmez. Tek ek maliyet fetch cycle başınadır: `colResultCustomerNo` mapping'li kampanyalarda producer fetch sayfası başına +1 indexed IN-sorgusu (aktif CUSTOMER marker kontrolü — müşteri cevabı 03.06.2026 gereği boşluk penceresini kapatır). Mapping'siz kampanyada sıfır maliyet; mevcut DNC revalidation ve JITExpireValidator ile aynı frekans/maliyet sınıfı.
- Cache yükleme (60 sn'de bir, kampanya başına): satır başına 1 enum/null kontrolü — ihmal edilebilir.
- Hot-drop: DNC tetiklenmesi başına (nadir; wrap-up + async `webphoneEventExecutor` üzerinde) O(dataQueue+callQueue) bounded tarama (dataQueue ≤ cacheLimit, callQueue ≤ concurrentLines) + kampanya başına 1 batch UPDATE + CDR insert'leri.
- Customer expansion: tetik başına 1 parametrized batch SELECT (kampanya DB, `customer_no` index'i önerilir) + chunk'lı batch UPDATE + tek `updateDnc` cache yenilemesi.
- Delta-sync: kampanya başına 1 indexed `MAX()` probe; yeni data varsa sayfalı SELECT + IN-chunk kontrol + batch UPDATE. Veri yokken maliyet ~tek aggregate sorgu.
- Sonuç: `Hot path, ek maliyet yok` (hot-drop ve expansion hot path dışında, nadir-event sınıfında).

## TDD Stratejisi

Tüm WP'ler önce başarısız test ile başlar (`gbpd-tdd-workflow`); mevcut davranışlar regression testleriyle kilitlenir. Test sınıfları `src/test/java/tests/<domain>` altında `<Konu><Davranış><TestTipi>Test` formatında:

- **PR-A**: `DncPhoneNumberCustomerScopeMappingTest` (yeni alanlar + default PHONE + nullable kontrat), `DncTypeEnumOrdinalContractTest` (ordinal sırası kilidi), `DncEngineCustomerRowsExcludedFromPhoneCacheTest` (CUSTOMER kaydı telefon cache'ine giremez), `DncPhoneNumberDaoCustomerScopeQueryContractTest` (sorgu filtresi + yeni customer sorgusu), `CampaignCustomerNoColumnMappingTest` (entity kolon adları + DTO eşleşmesi).
- **PR-B**: `CustomerDncServiceExpandsCustomerToPhoneRowsTest`, `CustomerDncServiceFallsBackToRowNumbersWhenCustomerNoNullTest` (E1), `CustomerDncServiceSkipsExistingNumbersBehaviorTest` (idempotency), `CampaignDbOperationsMarkSiblingsByCustomerNoSqlContractTest` (UPDATE alan semantiği + parametrized + chunk), `DncEngineDropsBufferedCallsForBlockedNumbersTest` (E2), `DncEngineQueueDropRespectsDncListSharingTest`, `DncEngineQueueDropDoesNotResetFetchedFlagTest`.
- **PR-C**: `CallResultCodeDeserializerDncScopeBackwardCompatTest` (alan yok→null / PHONE / CUSTOMER / çöp→null), `WebphoneSubscriberCustomerScopeWrapupTest`, `WebphoneSubscriberPhoneScopeUnchangedRegressionTest` (bugünkü mesajlar birebir aynı yol), `WebphoneSubscriberCdrFallbackCustomerDegradesToPhoneTest`.
- **PR-D**: `DncCustomerApiInsertsCustomerScopeTest`, `DncDataManipulationPhoneEndpointsUnchangedRegressionTest`.
- **PR-E**: `DncDeltaSyncSkipsWhenNoNewDataTest` (Kriter 3 + E3), `DncDeltaSyncBlocksNewlyMatchingByPhoneTest`, `DncDeltaSyncBlocksNewlyMatchingByCustomerNoTest`, `DncDeltaSyncDoesNotAdvanceWatermarkOnFailureTest`, `DncDeltaSyncReentrancyGuardTest`, `DncDeltaSyncCleanupDisabledByDefaultTest`, `DncDeltaSyncCrossDbNoJoinContractTest`.
- **PR-F**: `tests/angular/campaign/CampaignCustomerNoPicklistContractTest` (template + constants.js + 4 lokalizasyon kilidi) + Karma spec (`src/test/angular`), `npm.cmd run test:angular`.

Mockito + constructor boundary tercih edilir; DB bağlantısı açılmadan SQL contract'ları string seviyesinde doğrulanır (mevcut pattern).

## Önerilen Çözüm Yönü (Work Package / PR dilimleri)

Her PR tek başına merge edilebilir ve üretim davranışını değiştirmez (config/flag açılana dek karanlık):

1. **PR-A (WP1+WP2, pasif şema/entity)**: `DNCPhoneNumber.dncType/customerNo` + `phone_number` nullable; `Constants.DNC_TYPE`; `DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_NO/DATE_ENTERED`; `Campaign.colResultCustomerNo/colDateEntered`; `CampaignFormDTO` alanları; DAO PHONE filtresi + `findActiveCustomerNosByDncIdsAndCustomerNos`; `DNCEngine.getPhoneNumbersWithExpire` guard'ı.
2. **PR-B (WP3+WP4, çekirdek — çağıran yok)**: `CustomerDncService.blockByCustomerScope/blockByCustomerNo` (+`createCustomerMarker`); `CampaignDbOperations.setFilteredByDncListStatusByPhoneNumbers/ByCustomerNo`; `DNCEngine.buildAndSaveDncCdrs` extraktı + `dropQueuedCallsForNumbers` (`@Lazy DialerFacade` ile çevrim kırma).
3. **PR-C (WP5+WP6, aktivasyon)**: `CallResultCode.dncScope` + deserializer; `WebphoneSubscriber.updateDNCByResultCode(Call, DNC_TYPE)` scope dallanması; CDR fallback degrade.
4. **PR-D (WP7, Puzzle API)**: `POST /data/dnc/addCustomer/{campaignIdOrName}` + `processInsertCustomersForDefaultDnc` (sonda tek cache refresh + tek queue-drop).
5. **PR-E (WP8, delta-sync)**: `DncDeltaSyncScheduler` + `pd_dnc_sync_log` + `jobs.dnc-delta-sync.cron` / `jobs.dnc-delta-sync.cleanup.enabled=false` property'leri.
6. **PR-F (WP9, UI)**: Call Results tab'ine Customer No dropdown (`ng-required` YOK), `constants.js` + tip kısıtları, `campaignFormCtrl` init listesi, 4 lokalizasyon, default source table'a `customer_no`.

Bağımlılık: PR-A → PR-B → (PR-C, PR-D, PR-E paralel); PR-F yalnız PR-A'ya bağlıdır.

## DDL Listesi (DBA'ya teslim — MS SQL; diğer DB tipleri execution plan'da)

Koşulabilir idempotent script: `docs/analyse/03.06.2026/FEAT-318332-MUSTERI-BAZLI-DNC-DDL.sql` (Bölüm 1 = PD ana DB, **deploy önkoşulu**; Bölüm 2 = kampanya data tabloları şablonu; sonunda geri alma notları). Aşağıdaki liste özet/referanstır.

```sql
-- PR-A: pd_dnc_pn
ALTER TABLE pd_dnc_pn ADD dnc_type INT NOT NULL CONSTRAINT DF_pd_dnc_pn_dnc_type DEFAULT 0; -- 0=PHONE, 1=CUSTOMER
ALTER TABLE pd_dnc_pn ADD customer_no NVARCHAR(255) NULL;
ALTER TABLE pd_dnc_pn ALTER COLUMN phone_number NVARCHAR(255) NULL;
CREATE NONCLUSTERED INDEX IX_pd_dnc_pn_type_phone    ON pd_dnc_pn (dnc_list_id, dnc_type, phone_number)  WHERE deleted = 0;
CREATE NONCLUSTERED INDEX IX_pd_dnc_pn_type_customer ON pd_dnc_pn (dnc_list_id, dnc_type, customer_no)   WHERE deleted = 0;

-- PR-A: pd_campaign
ALTER TABLE pd_campaign ADD col_result_customer_no NVARCHAR(255) NULL;
ALTER TABLE pd_campaign ADD col_date_entered NVARCHAR(255) NULL;

-- PR-E: delta-sync watermark/log
CREATE TABLE pd_dnc_sync_log (
  id               BIGINT IDENTITY PRIMARY KEY,
  campaign_id      BIGINT        NOT NULL,
  max_date_entered DATETIME      NULL,
  started_at       DATETIME2     NOT NULL,
  finished_at      DATETIME2     NULL,
  status           NVARCHAR(50)  NOT NULL, -- SUCCESS/FAILED
  rows_blocked     INT           NULL,
  error_message    NVARCHAR(500) NULL
);
CREATE NONCLUSTERED INDEX IX_pd_dnc_sync_log_campaign_status ON pd_dnc_sync_log (campaign_id, status) INCLUDE (max_date_entered);

-- Müşteri kampanya tabloları (rehber; müşteri/DBA sorumluluğu, kolon adları mapping'le eşleşmeli)
-- ALTER TABLE <tablo> ADD customer_no NVARCHAR(255) NULL;
-- ALTER TABLE <tablo> ADD date_entered DATETIME NULL;
-- CREATE NONCLUSTERED INDEX IX_<tablo>_date_entered ON <tablo> (date_entered);
-- CREATE NONCLUSTERED INDEX IX_<tablo>_customer_no  ON <tablo> (customer_no);
```

## Kapsam Dışı

- "DNC'den çıkanların temizlenmesi" / eski satırların geri açılması (müşteri cevabı 03.06.2026: "geri açılmasın, böyle bir talep yok" — expire dolan numara yalnız yeni datalarda aranır hâle gelir, mevcut davranış yeterli; scaffold kod tabanından kaldırıldı).
- gbwebphone agent ekranındaki iki seçenekli kapatma UI'ı (ayrı repo; bu repo `dncScope` contract'ını sağlar).
- Puzzle arayüzünün kendisi (bu repo yalnız token korumalı API yüzeyini verir).
- CDR-fallback yolunda gerçek customer expansion (v1'de PHONE'a degrade; delta-sync telafi eder).
- `CallUpToDateKeeper`/originate edilmiş çağrıların canlı hattan koparılması (çağrı zaten Asterisk'tedir).

## Açık Sorular ve Netleştirme İhtiyacı

Açık soru kalmadı. 03.06.2026 oturumunda geliştiriciyle netleştirildi: task id `318332`; satır işaretleme mevcut `is_disabled=1 + TRC=76 + complete_date` mekanizmasıyla yapılacak (yeni `dnc_enabled` kolonu yok); cleanup çift kilitli scaffold olarak default kapalı gelecek; `date_entered` mapping alanı (`colDateEntered`) ile çözülecek.

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyor. Execution plan `docs/execution-plan/03.06.2026/FEAT-318332-MUSTERI-BAZLI-DNC.md` altında, bu analiz geliştirici tarafından incelenip nihai plan onaylandıktan sonra oluşturulacak. Plan, PR dilimi bazlı TDD test planını, hedefli doğrulama komutlarını (`mvn -o -Dtest=... test`, `npm.cmd run test:angular`) ve başarılı test kapanış kriterini içerecek.
