# REFACTOR Müşteri Bazlı DNC — customer_no↔phone ilişkisini ayrı tabloya taşıma Change/Fix Planı

## Bağlam

FEAT-318332 (müşteri bazlı DNC) UAT'inde müşteri, `pd_dnc_pn` tablosunu raw sorguladığında rahatsız oldu: (1) CUSTOMER marker'ı `phone_number = NULL` boş satır olarak görünüyor, (2) `dnc_type`'ı "scope" sandı ama o kayıt-tipi, (3) customer-scope numaraları `dnc_type=0` görünüyor. Müşteri kararı: **"customer_no ile eklenen numaraların customer_no↔phone ilişkisi ayrı bir tabloda tutulsun."** (Önceki tartışmadaki D seçeneği.) Geliştirici, test ortamında olduğumuz için `pd_dnc_pn`'deki atıl `dnc_type`/`customer_no` kolonlarının da kaldırılmasını onayladı.

Referans feature: `docs/analyse/03.06.2026/FEAT-318332-MUSTERI-BAZLI-DNC.md` + `docs/execution-plan/03.06.2026/FEAT-318332-MUSTERI-BAZLI-DNC.md` (PR-A…PR-H). Bu plan PR-A/PR-H'nin `pd_dnc_pn` tabanlı customer-scope depolamasını yeni tabloya taşır.

## İş Etiketi

refactor

(Change-request niteliğinde; dial-time engelleme davranışı DEĞİŞMEZ, yalnız customer-scope depolama/raporlama modeli yeniden yapılandırılır.)

## Öncelik

normal

## Etkilenen Alanlar

- **DB:** yeni `pd_dnc_customer` tablosu; `pd_dnc_pn`'den `dnc_type` + `customer_no` kolonları ve bunlara bağlı index'ler düşürülür; marker (NULL-phone) satırları silinir.
- `model/DNCPhoneNumber.java`: `dncType` + `customerNo` alanları kaldırılır; `phone_number` yeniden NOT NULL kabul edilir (nullable gevşetmesi geri).
- yeni `model/DNCCustomerPhone.java` (+ DAO `IDNCCustomerPhoneDao`/`DNCCustomerPhoneDao`, service).
- `service/DNCPhoneNumberService.java`: `createCustomerMarker`, 4-arg `create(...,customerNo)`, `findActiveCustomerNosByDncIdsAndCustomerNos`, `excludeCustomerMarkers`/`addCustomFilters` override (PR-G/H) kaldırılır.
- `engine/CustomerDncService.java`: marker + phone-stamp yerine `pd_dnc_customer`'a (customer_no, phone) yazar; `pd_dnc_pn`'e saf PHONE yazmaya devam eder.
- `engine/DNCEngine.java`: `dnc_type=PHONE` guard'ları ve marker cache-dışlama mantığı sadeleşir (artık tüm satırlar PHONE).
- `dao/impl/DNCPhoneNumberDao.java`: `findActivePhoneNumbersByDncIdsAndPhoneNumbers` filtresinden `dnc_type` çıkar; `findActiveCustomerNos...` kaldırılır.
- `batch/scheduler/DncDeltaSyncScheduler.java`: "hangi müşteri engelli" sorgusu marker yerine `pd_dnc_customer`'dan; yeni numarayı hem `pd_dnc_pn`'e hem `pd_dnc_customer`'a yazar.
- `dto/DNCPhoneNumberListDTO.java`: `customerNo` alanı kaldırılır.
- `webapp/js/controllers/dncPhoneCtrl.js` + `localization_*`: PR-H'nin `customerNo` grid kolonu + `dnc.customerNo` etiketi kaldırılır (grid saf telefon listesi).
- `util/Constants.java`: `DNC_TYPE` enum'u **KORUNUR** — `dncScope` rolünde (agent'ın numara/müşteri seçimi: `CallResultCode.dncScope`, `CallResultCodeDeserializer`, `WebphoneSubscriber`) hâlâ gerekli. Yalnız `pd_dnc_pn` kolon rolü (entity `dncType`) sökülür. **Öneri:** enum `DNC_TYPE` → `DNC_SCOPE` rename (tek kalan rolü scope; `PHONE`/`CUSTOMER` sabitleri ve Redis wire değeri değişmez).
- Testler: PR-A/PR-H'nin dnc_type/marker/customer_no testleri yeni modele göre yeniden yazılır; yeni `pd_dnc_customer` yazma/okuma testleri.

## Mevcut Davranış

Customer-scope engelleme: `pd_dnc_pn`'e 1 CUSTOMER marker (`dnc_type=1, phone=NULL, customer_no=X`) + her numara için PHONE satırı (`dnc_type=0`, PR-H'den beri `customer_no=X` damgalı) yazar. Dial-time filtre `dnc_type=PHONE` satırları telefon cache'ine yükler. Delta-sync "hangi müşteri engelli"yi marker'dan okur. DNC Form grid'i PR-H'de marker'ı gizler + `customer_no` kolonu gösterir.

## Beklenen Davranış

- `pd_dnc_pn` **saf telefon block-list**: yalnız gerçek numaralar, `dnc_type`/`customer_no` kolonları YOK, NULL-phone satır YOK. Dial-time engelleme birebir aynı (telefon bazlı).
- `pd_dnc_customer` **(customer_no, phone_number) ilişki tablosu**: customer-scope'ta engellenen her numara için 1 satır (+ standart audit kolonları). Raporlama + delta-sync buradan.
- Customer-scope engelleme: numarayı **hem** `pd_dnc_pn`'e (engelleme) **hem** `pd_dnc_customer`'a (ilişki) yazar.
- Numara bazlı DNC: yalnız `pd_dnc_pn` (değişmez).
- DNC Form grid'i: saf telefon listesi (customer_no kolonu yok). Müşteri-scope raporu `pd_dnc_customer`'dan (ayrı sorgu/ekran, ileride istenirse).

## Kod Okuma Bulguları

- `CustomerDncService.blockCustomer` (satır 91-110): `persistCustomerMarker` + `persistPhoneNumbers(...,customerNo)` → yeni modelde `persistCustomerRelation(dnc, customerNo, numbers)` (pd_dnc_customer) + `persistPhoneNumbers(dnc, numbers)` (pd_dnc_pn, customer_no'suz).
- `DNCPhoneNumberService.create(...)` 4-arg (PR-H) → 3-arg'a döner (customer_no yok).
- `DNCPhoneNumberService.createCustomerMarker` / `findActiveCustomerNosByDncIdsAndCustomerNos` → kaldırılır (delta-sync + idempotency yeni tablodan).
- `DNCPhoneNumberService.addCustomFilters`/`excludeCustomerMarkers` (PR-H) → kaldırılır (marker kalmadı).
- `DNCEngine.getPhoneNumbersWithExpire` + `updatePhoneNumberInDnc`: `dnc_type != PHONE`/null guard'ları → sadeleşir (tüm satırlar PHONE).
- `DncDeltaSyncScheduler`: marker probe'u → `pd_dnc_customer` DISTINCT customer_no.

## Root Cause / Değişiklik Gerekçesi

Kök neden bir bug değil; tek-tablo (marker + expansion) tasarımı dial-time hot-path'i korumak için seçilmişti, ancak raw `pd_dnc_pn` raporlaması için kafa karıştırıcı (NULL satır + dnc_type=kayıt-tipi). Müşteri, customer_no↔phone ilişkisini ayrı tabloda temiz görmek istiyor. Ayrı tablo bu ihtiyacı karşılar, `pd_dnc_pn`'i saf telefon listesine indirir; dial-time davranışı korunur (numaralar yine pd_dnc_pn'de).

## Riskler

- **Dial-time engelleme davranışı DEĞİŞMEMELİ** (en kritik). Numaralar yine `pd_dnc_pn`'de PHONE satırı; `dnc_type` kolonu kalksa da cache tüm satırları yükler. Regression: `DNCEngineTest` + phone-block testleri yeşil kalmalı.
- **Kolon drop + deploy sıralaması:** önce kolonlara REFERANS ETMEYEN kod deploy edilir, SONRA DBA kolonları düşürür. Ters sıra Hibernate/SQL hatası verir. (Test ortamı; koordine edilir.)
- **Mevcut test verisi tutarsız:** PR-H öncesi eski satırlarda customer_no damgası yok, marker var; otomatik migration eksik eşler. Test ortamı olduğundan customer-scope test satırlarını sıfırlayıp yeni modelle yeniden UAT önerilir (Açık Soru 2).
- **DNC_TYPE enum'u** başka yerde kullanılıyorsa kaldırma kırılır → kullanım taraması yapılır (kalırsa enum bırakılır, sadece pd_dnc_pn'den sökülür).
- Idempotency: `pd_dnc_customer` üzerinde `(dnc_list_id, customer_no, phone_number)` unique index mükerrer çifti engeller.

## TDD Stratejisi

- Karakterizasyon/regression: `DNCEngineTest`, `DNCPhoneNumberServiceTest`, `WebphoneSubscriber*Test` — dial-time + wrap-up davranışı korunur.
- Yeni davranış: `CustomerDncService` testleri → marker/stamp yerine `pd_dnc_customer`'a (customer_no, phone) yazıldığını + `pd_dnc_pn`'e saf PHONE yazıldığını doğrular.
- Delta-sync testleri: "engelli müşteri" `pd_dnc_customer`'dan okunur; yeni numara iki tabloya yazılır.
- Grid: saf telefon listesi (customer_no kolonu yok) contract testi (PR-H contract testi güncellenir/kaldırılır).
- Idempotency testi: aynı (customer, phone) iki kez → tek satır.

## Açık Sorular

- [x] **1. `pd_dnc_customer` FK + tipler:** Parent tablo **`pd_dnc_list`**; `pd_dnc_pn.dnc_list_id` FK'si zaten mevcut. Yeni tablo FK'si `dnc_list_id → pd_dnc_list(id)` (DDL'de aktif). `phone_number` tipi `pd_dnc_pn` ile aynı.
- [x] **2. Mevcut test verisi:** Customer-scope test satırları **sıfırlanıp yeni modelle yeniden UAT** yapılacak (best-effort migration B KULLANILMAYACAK; test ortamı + PR-H öncesi tutarsızlık).
- [x] **3. `phone_number` NOT NULL:** **Evet** — marker/NULL satırlar temizlendikten sonra `pd_dnc_pn.phone_number` yeniden NOT NULL yapılır.
- [x] **4. `DNC_TYPE` enum'u:** **SİLİNMEZ.** Enum `dncScope` rolünde (agent'ın numara/müşteri seçimi) kalıcı olarak gerekli. Yalnız `pd_dnc_pn` kolon rolü (entity `dncType` + `DNCEngine`/`DAO` `dnc_type` guard'ları + `createCustomerMarker`) sökülür. **Rename önerisi (onay bekliyor):** `DNC_TYPE` → `DNC_SCOPE` (netlik; `PHONE`/`CUSTOMER` sabitleri ve Redis wire değeri değişmez → webphone contract'ı etkilenmez).

## DDL (DBA teslim — ben DB'ye dokunmam; hedef/test/prod'da DBA çalıştırır)

### A) Yeni tablo
```sql
IF OBJECT_ID('dbo.pd_dnc_customer','U') IS NULL
BEGIN
  CREATE TABLE dbo.pd_dnc_customer (
    id           BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_pd_dnc_customer PRIMARY KEY,
    dnc_list_id  BIGINT        NOT NULL,
    customer_no  NVARCHAR(255) NOT NULL,
    phone_number NVARCHAR(50)  NOT NULL,   -- pd_dnc_pn.phone_number ile aynı tip/uzunluk olacak
    reason       INT           NULL,
    enabled      BIT           NOT NULL CONSTRAINT DF_pd_dnc_customer_enabled DEFAULT 1,
    deleted      BIT           NOT NULL CONSTRAINT DF_pd_dnc_customer_deleted DEFAULT 0,
    create_date  DATETIME      NULL,
    create_user  BIGINT        NULL,
    CONSTRAINT FK_pd_dnc_customer_list FOREIGN KEY (dnc_list_id) REFERENCES dbo.pd_dnc_list(id)
  );
  CREATE UNIQUE INDEX UX_pd_dnc_customer_list_cust_phone
    ON dbo.pd_dnc_customer (dnc_list_id, customer_no, phone_number) WHERE deleted = 0;
  CREATE INDEX IX_pd_dnc_customer_list_cust
    ON dbo.pd_dnc_customer (dnc_list_id, customer_no);
END
```

### B) (Opsiyonel) Best-effort migration — PR-H sonrası damgalı satırlar
```sql
INSERT INTO dbo.pd_dnc_customer (dnc_list_id, customer_no, phone_number, reason, enabled, deleted, create_date, create_user)
SELECT dnc_list_id, customer_no, phone_number, reason, enabled, deleted, create_date, create_user
FROM   dbo.pd_dnc_pn
WHERE  customer_no IS NOT NULL AND phone_number IS NOT NULL AND deleted = 0
  AND  NOT EXISTS (SELECT 1 FROM dbo.pd_dnc_customer x
                   WHERE x.dnc_list_id = pd_dnc_pn.dnc_list_id AND x.customer_no = pd_dnc_pn.customer_no
                     AND x.phone_number = pd_dnc_pn.phone_number);
-- NOT: PR-H öncesi damgasız satırlar/markerlar buradan eşlenemez. Test ortamı için Açık Soru 2 (sıfırla+yeniden test) önerilir.
```

### C) Marker temizliği + atıl kolon drop (geliştirici talebi)
```sql
-- 1) CUSTOMER marker (NULL-phone) satırlarını sil → pd_dnc_pn'de NULL satır kalmaz
DELETE FROM dbo.pd_dnc_pn WHERE phone_number IS NULL;

-- 2) dnc_type / customer_no'ya bağlı index'leri DİNAMİK düşür (kolon drop önkoşulu)
DECLARE @dropIdx NVARCHAR(MAX) = '';
SELECT @dropIdx = @dropIdx + 'DROP INDEX ' + QUOTENAME(i.name) + ' ON dbo.pd_dnc_pn;' + CHAR(10)
FROM sys.indexes i
WHERE i.object_id = OBJECT_ID('dbo.pd_dnc_pn') AND i.name IS NOT NULL
  AND EXISTS (SELECT 1 FROM sys.index_columns ic
              JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
              WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                AND c.name IN ('dnc_type','customer_no'));
IF @dropIdx <> '' EXEC sys.sp_executesql @dropIdx;

-- 3) dnc_type DEFAULT constraint'ini (otomatik adlı olabilir) düşür
DECLARE @df sysname;
SELECT @df = dc.name FROM sys.default_constraints dc
  JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
  WHERE dc.parent_object_id = OBJECT_ID('dbo.pd_dnc_pn') AND c.name = 'dnc_type';
IF @df IS NOT NULL EXEC('ALTER TABLE dbo.pd_dnc_pn DROP CONSTRAINT ' + QUOTENAME(@df));

-- 4) Atıl kolonları düşür
IF COL_LENGTH('dbo.pd_dnc_pn','dnc_type')    IS NOT NULL ALTER TABLE dbo.pd_dnc_pn DROP COLUMN dnc_type;
IF COL_LENGTH('dbo.pd_dnc_pn','customer_no') IS NOT NULL ALTER TABLE dbo.pd_dnc_pn DROP COLUMN customer_no;

-- 5) (Opsiyonel, Açık Soru 3) NULL satır kalmadığından emin olduktan sonra phone_number'ı NOT NULL yap
-- ALTER TABLE dbo.pd_dnc_pn ALTER COLUMN phone_number NVARCHAR(50) NOT NULL;
```
> Deploy sırası: (i) kolonlara referans etmeyen yeni kod deploy edilir → (ii) A tablo + (B migration) → (iii) C marker temizliği + kolon drop. Böylece hiçbir aşamada kod olmayan kolonu aramaz.

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [x] Açık Sorular (1-4) geliştiriciyle netleşti (Q4: enum silinmez, DNC_SCOPE rename onaylandı).
- [x] `DNC_TYPE`/`customer_no` kullanımları taranıp etki listesi kesinleştirildi (main straggler yok).
- [x] Regression testleri (DNCEngine phone-block 14/14, WebphoneSubscriber 48/48) yeşil.
- [x] Yeni `pd_dnc_customer` model + DAO (`IDNCCustomerPhoneDao`/`DNCCustomerPhoneDao`) + service (`DNCCustomerPhoneService`).
- [x] `CustomerDncService` yeni tabloya (createIfAbsent) + pd_dnc_pn'e saf PHONE (create 3-arg) yazacak şekilde güncellendi; 3 test yeni davranışa çevrildi.
- [x] `DNCPhoneNumberService`/`DNCEngine`/`DAO`'dan marker + dnc_type + customer_no + grid-filtre yolları kaldırıldı.
- [x] `DncDeltaSyncScheduler` yeni tablodan (dncCustomerPhoneService) okuyacak şekilde güncellendi + 6 testi.
- [x] `DNCPhoneNumber` entity'den `dncType`/`customerNo` kaldırıldı; `DNC_TYPE`→`DNC_SCOPE` rename (CallResultCode/deserializer/WebphoneSubscriber).
- [x] `DNCPhoneNumberListDTO` + `dncPhoneCtrl.js` + localization×4 saf-telefon haline döndürüldü (PR-H eklentileri geri alındı; obsolete 6 test silindi).
- [x] Hedefli testler yeşile çevrildi (100/100); dial-time regression yeşil.
- [ ] DDL (A/B/C) script'i DBA'ya teslim; deploy sırası notu iletildi. (Bu yanıtta iletildi; DBA koşumu beklemede.)
- [x] history kaydı (`gbpd-agent-history`).

## Performans Etkisi

- Hot path durumu: **değişiklik yok**. Dial-time DNC filtresi yine `pd_dnc_pn` telefon cache'ini kullanır; kolon sayısı azalır (dnc_type/customer_no gider), guard'lar sadeleşir → marjinal iyileşme.
- Call/event/tick başına ek maliyet: yok.
- IO/scan/lock/serialization/log etkisi: customer-scope engelleme tetiğinde (nadir) `pd_dnc_customer`'a ek insert'ler (mevcut pd_dnc_pn insert'leriyle aynı sınıf). Delta-sync probe'u marker yerine yeni tablodan (indexed).
- Azaltım/ölçüm notu: yeni tablo unique index ile idempotent; delta-sync `DISTINCT customer_no` indexli.

## Doğrulama

- `mvn -o -s settings.xml -f gbpowerdialer/pom.xml -P properties-tr-dev -Dtest=CustomerDncService*Test,DncDeltaSync*Test,DncEngineCustomerScopeFetchFilterTest,DNCEngineTest,WebphoneSubscriber*Test,CallResultCodeDeserializerDncScopeBackwardCompatTest,DNCPhoneNumberServiceTest,DNCServiceTest,CampaignDbOperationsMarkSiblingsByCustomerNoSqlContractTest test` → **Tests run: 100, Failures: 0, Errors: 0** (BUILD SUCCESS, 10.07.2026). Tüm modül derlendi (783 main + 171 test); DNCEngine 14/14 + WebphoneSubscriber 48/48 dial-time/wrap-up regresyonu korundu.
- `npm.cmd run test:angular` ÇALIŞTIRILMADI (blocker: node_modules yok). Frontend değişikliği yalnız PR-H grid kolonu + i18n GERİ ALMA olduğundan Java tarafı derleme + mevcut contract testleriyle kapsandı.
- `pd_dnc_customer` için ayrı unit test yazılmadı; davranışı çağıran testler kapsıyor (CustomerDncService `createIfAbsent` verify + DncDeltaSync `findActiveCustomerNos` mock). createIfAbsent exists-guard + DAO sorgusu Hibernate gerektirdiğinden entegrasyon seviyesindedir.
- DDL (A/B/C) DBA tarafında koşulacak; deploy sonrası test ortamında yeni UAT (marker yok, `pd_dnc_customer` dolu, `pd_dnc_pn` saf telefon).

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] Planlanan testler çalıştırıldı ve başarılı oldu (100/100).
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı (10.07.2026). Customer-scope DNC depolaması yeniden yapılandırıldı: `pd_dnc_pn` saf telefon block-list oldu (marker + `dnc_type`/`customer_no` kolonları kaldırıldı), customer_no↔phone ilişkisi yeni `pd_dnc_customer` tablosuna taşındı; delta-sync + fetch-time customer filtresi yeni tabloya yönlendirildi; `DNC_TYPE` enum'u tek kalan rolü (agent dncScope seçimi) için `DNC_SCOPE`'a rename edildi (Redis wire değeri değişmedi). PR-F/G/H'nin pd_dnc_pn'e customer_no damgalama + grid kolonu + marker-gizleme eklentileri geri alındı; 6 obsolete test silindi, 9 test yeni davranışa/rename'e güncellendi. Hedefli testler **100/100 yeşil** (BUILD SUCCESS); dial-time davranışı değişmedi. **Blocker/beklemede:** (a) DDL A/B/C'yi DBA test/prod'da koşacak (deploy sırası: kod → tablo/migration → marker temizliği+kolon drop); (b) deploy sonrası test ortamında UAT (müşterinin `pd_dnc_customer` raw sorgusu: customer_no↔phone, NULL satır yok); (c) `npm run test:angular` node_modules kurulunca. Not: mevcut test-ortamı customer-scope verisi (PR-H öncesi/marker) sıfırlanıp yeniden UAT yapılacak (Açık Soru 2).
