# Veri Kalıcılığı ve Veritabanı

Uygulama Hibernate tabanlı entity/service/DAO katmanına sahiptir. Ayrıca kampanya verisi için kaynak ve sonuç tablolarına dinamik erişen ayrı operasyonlar bulunur. Bu iki alanı aynı DB modeli gibi ele almak risklidir.

## Ana Persistence Katmanı

- `applicationContext.xml`, `BasicDataSource`, Hibernate `SessionFactory` ve `HibernateTransactionManager` tanımlar.
- Entity paketleri `com.powerdialer.model` altında yer alır.
- DAO interface'leri `com.powerdialer.dao`, implementasyonları `com.powerdialer.dao.impl` altındadır.
- Service katmanı `com.powerdialer.service` altında controller/API ile DAO arasında çalışır.
- Hibernate dialect profillere göre SQL Server veya Oracle olabilir.

## Genel Katman Düzeni

Tipik akış:

1. Spring MVC controller veya API resource isteği alır.
2. Service sınıfı domain işlemini yürütür.
3. DAO sınıfı Hibernate üzerinden entity okur/yazar.
4. Transaction boundary Spring tarafından yönetilir.
5. Bazı akışlarda action log, admin log veya result log kayıtları eklenir.

## Kampanya Veri Operasyonları

Kampanya verisi dinamik tablo/kolon ve dosya yükleme davranışına sahiptir. İlgili alanlar:

- `CampaignDataManipulationController`
- `CampaignDataManipulationService`
- `CampaignDataTypeChecker`
- `UploadToolFileService`
- `UploadStatusStore`
- `DBEngine`
- `CampaignDbOperations`
- `FilterUtils`

Bu alanlar sadece CRUD değildir; dosya upload, streaming import, veri doğrulama, priority recalculation ve source/result table operasyonlarını birlikte taşır.

## Çağrı ve Sonuç Verisi

İlgili servis ve modeller:

- `Call`
- `CDR`
- `CampaignResultService`
- `CampaignResultJobService`
- `CampaignRecordService`
- `CampaignJobLog`
- `CampaignJobResultLog`
- `ResultCode`
- `TelephonyResultCode`

Çağrı sonucu, telephony result code, user result code, retry ve callback davranışları persistence ile doğrudan bağlantılıdır.

### Arama geçmişi kolonları ve numara başına sayaç

Kampanya result tablosundaki üç string kolonu, kaydın arama geçmişini taşır ve **numara başına arama sayacının tek kaynağıdır**; ayrı bir sayaç kolonu yoktur.

| Campaign binding | Varsayılan ad | Ayraç | Yazan yer | Semantik |
|---|---|---|---|---|
| `colResultRetryHistory` | `retry_history` | `;` | `RetryStrategy.updateRetryHistory` | Retry **planlanırken** sıradaki numara |
| `colResultCbHistory` | `cb_history` | `,` | `Call.addCallbackHistoryEntry` | Callback **oluşturulurken** numara |
| `colResultCallProcess` | `call_process` | `;` | `Call.appendCallProcessStep` | `F` / `R` / `CB` adım izi |

Sayımı mümkün kılan invariant, bu üç kolonun **kilitli adımlarla** yazılmasıdır: her retry planlaması `retry_history`'ye bir numara **ve** ize bir `R` ekler; her callback oluşturması `cb_history`'ye bir numara **ve** bir `CB` ekler; iz her zaman `F` ile başlar. Bu sayede `Call.getDialedNumberSequence()` izi kursor gibi kullanarak arama dizisini birebir yeniden kurar.

Bilinçli davranış notları:

- **Fresh arama `retry_history`'de yer almaz.** İz retry planlanırken yazıldığı için ilk retry kararında üç kolon da boştur; o durum "bir kez (fresh) aranmış" olarak yorumlanır. Kodun dört yerindeki `+1 is for first call` varsayımı aynı semantiğe dayanır; `retry_history`'ye fresh numarayı eklemek `anyRetryLeft`, macro hesabı, cycle indeksi ve attempt politikasını topluca kaydırır.
- **İki okuma vardır.** `R`/`CB` adımları arama planlandığında yazılır. Hangup **sonrası** (`RetryStrategy`) izdeki her girdi fiilen yapılmış bir aramadır ve dizinin tamamı sayılır (`Call.getDialedNumberSequence`). Arama **öncesi** (`CallProducer`) satırda bekleyen plan varsa izin son girdisi henüz yapılmamıştır ve sayılmaz (`Call.getCompletedDialSequence`). İki katmanın aynı diziyi aynı anlamla okuması numaranın son hakkını yakıyordu (Faz 6); "bir fazla saymak muhafazakârdır" yorumu yanlıştı.
- **Plan olmanın tek ölçütü tarih kolonudur.** Bekleyen plan `retry_date` veya `callback_date` doluluğuyla belirlenir (`Call.hasPendingDialPlan`). `retry_number` / `callback_number` tek başına plan değildir: strateji kapanışı `retry_number`'ı temizlemez, kayıt aktivasyonu yalnız tarih kolonlarını temizler; tarihsiz bir numara kolonu **bayat işaretçidir** (Faz 8).
- **Planlama adımı olmayan arama da izde kaydedilir.** Aktive edilen kayıt fresh fetch'ten yeniden gelir ama izinde `F` zaten vardır; bu aramanın planlama adımı yoktur. Özellik aktifken producer aramayı karar anında `retry_history` + `R` olarak kilitli yazar (`Call.recordUnplannedDial`); aksi hâlde sayım onu görmez (Faz 8).
- **Bilinen sınır — ilk aramanın atfı.** `F`'nin numarası okuma anında "colSort'a göre ilk dolu kolon"dur. Kayıt ömrü içinde ilk aranan numaranın kolonundan **önceki** bir kolona numara eklenirse `F` o numaraya atfedilir. Tam çözüm ilk aranan numaranın saklanmasını gerektirir; karar bekliyor.
- **Tutarsızlıkta fallback.** İz ile geçmiş listelerinin uzunlukları uyuşmazsa (örnek: `call_process` kolonu eşlenmemiş ya da kolon sonradan eklendiği için eski kayıtlarda boş) sayım `fresh + retry_history` fallback'ine düşer ve DEBUG seviyesinde loglanır. Fallback bir hata değil, eski kayıtların beklenen yoludur.

### Kapanış nedeni (`close_reason`)

Numara başına limit nedeniyle kapatılan kayıtların nedenini taşır. Kampanyada `col_result_close_reason` binding'i ile bağlanır; değer `Constants.CLOSE_REASON_*` sabitlerinden biridir ve lokalize edilmez.

- Yazım **koşulludur**: yalnız binding eşliyken ve gerçekten bir kapanış nedeni üretildiğinde `insertCallResultToDB`'nin mevcut UPDATE'ine eklenir. Kolonu bulunmayan kampanyaların result yazımı bu sayede etkilenmez.
- **Başarılı kapanışta yazılmaz.** Müşteri yanıtı sonrası kapanan kayıtta neden alanı boş kalır; aksi halde rapor "limit doldu" gibi okunur.
- **Son çağrının TRC/URC'si ezilmez.** Kapanış nedeni ayrı alanda tutulur; bu, geçmişte URC/TRC kaybı üreten hata sınıfına karşı bilinçli bir sınırdır.
- Hiç aranmamış kayıtlar `CampaignDbOperations.setClosedWithoutDialing` ile kapatılır. Bu metot DNC kapatmasından iki noktada ayrılır: **telephony result code yazmaz** (yapılmamış çağrı için sonuç kodu üretilmez) ve **`user_id` / `user_result_code` alanlarına dokunmaz** (kayıt geçmişte agent disposition'ı almış olabilir).
- **Aktivasyon temizler.** Kayıt aktivasyonu (`CampaignRecordService.activateRecordReturningCount`) kapanış durumunu geri alırken `close_reason`'ı da (binding varsa) NULL'lar. Bu temizleme aktivasyon UPDATE'inden **ayrı ve izole** çalışır: müşterinin kendi tablosunda NOT NULL bir `close_reason` kolonu varsa NULL yazımı reddedilir ve bu, aktivasyonun kendisini düşürmemelidir (hata WARN olarak loglanır). Sıcak yoldaki sonuç yazımı neden yokken kolona dokunmaz; aynı NOT NULL riski orada tüm çağrı sonucu yazımını düşürürdü.

## Kampanya Onboarding Kontrol Listesi

Result tablosu kolon binding'lerine dayanan özellikler açılırken bu adımlar atlanmamalıdır. Repoda Flyway/Liquibase yoktur ve `hibernate.hbm2ddl.auto` kapalıdır; DDL `gbpowerdialer/src/main/resources/sql/migration/` altındaki script'lerle **elle** uygulanır.

### Numara başına azami arama limiti (FEAT-325559)

Migration script'i iki bölümlüdür ve **iki ayrı veritabanında** çalışır: `BÖLÜM 1` PD ana DB (`pd_campaign`), `BÖLÜM 2` kampanya DB'leri. Kampanya DB tarafında tablo adı **sabitlenmez**: result tabloları `complete_date` kolonuna sahip olmalarıyla tanındığı için `sys.tables` üzerinden dinamik keşfedilir ve kolonu olmayanlara `sp_executesql` ile tek batch'te eklenir. Aynı script tekrar çalıştırılabilir.

1. **PD ana DB (bir kez):** `pd_campaign.max_attempt_per_number INT NULL` ve `pd_campaign.col_result_close_reason NVARCHAR(255) NULL`.
2. **Kampanya DB (her camp DB'de bir kez, tüm result tablolarına):** `close_reason NVARCHAR(255) NULL`. Yeni tablolar uygulama tarafından oluşturulduğunda kolon zaten üretilir (`createDefaultResultTable`, `addDefaultResultColumnsToSourceTable`). `close_reason` yalnız yazılır, sorgulanmaz — **index eklenmez.**
3. **Binding elle atanmaz.** Kolon result tablosunda mevcutsa binding otomatik atanır: backend `CampaignDataTypeChecker.applyDefaultHistoryColumnMappings`, frontend `campaignFormCtrl.setDefaultValuesForUndefinedResultTableColumns`. Operatörün bilinçli seçtiği farklı bir kolon ezilmez.
4. **Geçmiş kolonlarının kapasitesi kontrol edilir (zorunlu).** Numara başına sayaç `retry_history` string'inin kendisidir; her arama yaklaşık 12 karakter ekler. `NVARCHAR(255)` bir kolon ~21 aramadan sonra dolar ve SQL Server bunu sessizce kırpmaz, UPDATE'i "String or binary data would be truncated" ile düşürür. O UPDATE `insertCallResultToDB`'nin tek büyük UPDATE'i olduğu için **tüm çağrı sonucu yazımı kaybolur** ve hata `catch` içinde yutulur.

   Kontrol tek tabloya değil camp DB'deki tüm result tablolarına uygulanır; script'in `2.3` bölümü sınırlı tipli her kolonu raporlar. `2.4` bölümü gerekli `ALTER COLUMN ... NVARCHAR(MAX)` ifadelerini **üretir ama çalıştırmaz**: `NVARCHAR(MAX)`'a geçiş metadata-only değildir, büyük tablolarda yeniden yazma ve uzun kilit oluşturabilir. DBA çıkan ifadeleri bakım penceresinde kendisi çalıştırır. Genel deneme limiti yükseltilmeden önce zorunludur.

5. **`call_process` kolonu bulunmalıdır (önerilir).** Eşli değilse sayım fallback'e düşer ve fresh arama atfı zayıflar; özellik çalışmaya devam eder ama kaydın ilk numarası bir fazla arama alabilir. Script'in `2.2` bölümü eksik `call_process` / `cb_history` kolonlarını opsiyonel olarak ekler; bu kolonlar daha eski bir task'a aittir ve yalnız sayım doğruluğu için buraya alınmıştır.
6. **Paylaşımlı result tablosu semantiği bilinmelidir.** `dbResultTable` kampanya başına zorunlu değildir ve satır eşleşmesi yalnız unique key ile yapılır (`rowExistsInResultTableByCallUniqueKey`, sorguda `campaign_id` yok). Aynı tabloyu kullanan kampanyalar aynı sayaç kolonlarını paylaşır; limit "kontak satırı + numara" bazlıdır ve o tabloyu kullanan tüm kampanyaları kapsar.
7. **Doğrulama:** script'in `2.5` bölümü her result tablosu için `close_reason` / `call_process` / `cb_history` / `retry_history` varlığını tablo hâlinde döndürür.

### Özelliği geri alma

Kampanyada `max_attempt_per_number` alanını boşaltmak davranışı anında eski hâline döndürür; kod deploy'u veya DDL geri alma gerektirmez. Bu, özelliğin birincil kill-switch'idir.

### Telephony result code satırı ekleme — `pd_telephony_rc` (FEAT-325470)

`pd_telephony_rc` PD ana DB'de, uygulama tarafından yazılmayan bir referans tablosudur (entity `TelephonyResultCode`; `@SequenceGenerator` bildirimi vardır ama kolon fiilen `IDENTITY`'dir, sequence yoktur). Dialer tabloyu startup'ta bir kez okur ve `code` ile anahtarlar; `id` yalnız `pd_cdr.telephony_rc` ve kampanya result tablosundaki `telephony_result_code` kolonlarına yazılır (raporlar).

Yeni satır eklerken (`FEAT-325470-amd-telephony-result-codes.sql` emsali):

1. **`code` tabloda boş bir değerden seçilir** ve `Constants.TELEPHONY_RC_*` sabitine bağlanır: dahili 9xx serisi ya da Q.850'de atanmamış 112..126 aralığı (AMD ayrıştırma kodları 113/114/115 böyle seçildi). Yeni code `Constants.ASTERISK_ERROR_CODES` ile kesişmemelidir. Kod tarafı yalnız `code` ile karar verir.
2. **`id` açıkça yazılır** (`SET IDENTITY_INSERT dbo.pd_telephony_rc ON/OFF`): IDENTITY ortamlar arası atlayabilir (Non-Turkcell'de "Ambiguous" satırı 10083 almıştı, Turkcell'de 85) ve raporlar id bekler. Script iki yönlü çakışma korumasıyla başlar (`id IN (...) AND code NOT IN (...)` ve `code IN (...) AND id NOT IN (...)`), `IF NOT EXISTS (... WHERE id = ...)` ile idempotent yazar. **Code değişikliği** gerekirse yeni satır açılmaz; aynı script `UPDATE ... SET code = <yeni> WHERE id = <id> AND code = <eski>` adımıyla önceki sürümle yazılmış satırları taşır (bkz. 913/914/915 → 113/114/115, 05.10.2026).
3. **Startup doğrulaması güncellenir:** `ManagerConnectionComposition.requiredInternalTelephonyResultCodes()` listesine yeni code eklenir; `tests/persistence/TelephonyResultCodeMigrationContractTest` script satırlarını sabitlerle kilitler.
4. **Uygulama restart edilir**; harita ve iki UI ekranı (Telephony Result Code List, Result Code Schedule Form dropdown) aynı servlet-context haritasından beslenir, UI kodu değişmez.
5. **Dış taraf senkronu:** santral yeni cause'u göndermeye ancak restart sonrasında başlar; aksi hâlde hangup'lar 901 fallback'ine (id 73) düşer.

Mevcut satırlar (05.10.2026): 900 AMD (id 71), 901..911 finalize/fallback kodları, 912 Ambiguous, 113 AMD Bad Number (id 86), 114 AMD Not Reached (id 87), 115 AMD Unknown Tone (id 88). PRP (`gbWebphone_test`) ve S2 test (`gbWebPhone_S2_test`) ortamlarında ilk sürümün 913/914/915 kodlu satırları vardır; revize script oradaki satırları 113/114/115'e günceller.

## Riskli Noktalar

- Dinamik SQL veya dinamik tablo adı kullanılan yerlerde string manipulation hataları üretim verisini etkileyebilir.
- SQL Server ve Oracle profilleri aynı davranışı garanti etmeyebilir.
- Campaign entity'si hem kalıcı alanları hem runtime counter'ları taşıdığı için refactor dikkat ister.
- Upload ve streaming import akışları uzun süren işlemler olabilir; transaction ve bellek kullanımı gözlemlenmelidir.
- DAO/service pattern'i tek biçimli görünse de controller bazında özel yan etkiler bulunabilir.

## Change Contract

- Entity alanı değişirse Hibernate mapping, JSON response ve frontend kullanımı birlikte kontrol edilir.
- Kampanya veri tablosu davranışı değişirse küçük fixture ile karakterizasyon testi yazılır.
- Result tablosuna yeni kolon yazımı **koşullu** yapılır: binding eşli değilse kolon adı sorguya hiç girmemelidir. Eksik kolona yazım denemesi SQL Server'da "Invalid column name" ile tüm UPDATE/INSERT'i düşürür ve çağrı sonucu kaydedilemez (FEAT-318332 vakası).
- Arama geçmişi kolonlarının (`retry_history`, `cb_history`, `call_process`) yazım sırası ve kilitli adım invariantı değiştirilmez; numara başına sayaç bu invariant'a dayanır. Değişiklikten önce `CallHistoryColumnsLockstepCharacterizationTest` ile mevcut davranış kilitlenir.
- Kayıt kapatan yeni bir batch operasyonu eklenirken `user_id` / `user_result_code` alanlarına dokunulup dokunulmayacağı bilinçli karar olarak yazılır; hiç aranmamış kayıtta NULL'lamak doğru, geçmişte aranmış kayıtta URC kaybıdır.
- Kaydı **yeniden başlatan** bir yol (aktivasyon, reset, toplu güncelleme) eklenirken veya değiştirilirken üç grup birlikte değerlendirilir: kapanış durumu kolonları (`complete_date`, `is_disabled`, TRC, `close_reason`), plan işaretçileri (`retry_date`/`retry_number`, `callback_date`/`callback_number`) ve arama izi. Numara başına sayaç ile plan kavramı bu kolonlardan **türetildiği** için, birini temizleyip diğerini bırakan bir yol türetimi sessizce bozar (Faz 8).
- Dinamik SQL değişikliğinde SQL Server ve Oracle farkları not edilir.
- Büyük veri yükleme değişikliklerinde bellek ve süre etkisi ayrıca ölçülür.
- Migration sırasında DB owner'lığı servis bazında netleşmeden tablo yazma sorumluluğu taşınmaz.
