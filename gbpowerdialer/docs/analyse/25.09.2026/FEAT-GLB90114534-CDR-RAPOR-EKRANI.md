# FEAT CDR Rapor Ekranı (Options Altında Kampanya Bazlı `pd_cdr` Görünümü)

## Bağlam

PowerDialer arayüzünün Options bölümüne, `pd_cdr` tablosunun kampanya kırılımında UI görünümünü sunan yeni bir ekran isteniyor. Kullanıcı önce kampanya seçecek, ardından o kampanyanın CDR kayıtlarını sıralanabilir, filtrelenebilir ve sayfalanabilir bir grid'de görecek; rapor olarak dışa aktarabilecek. Temel şart: bu ekranın sorguları canlı dialer'ı hiçbir koşulda kilitlememeli veya yavaşlatmamalı.

Bu analiz 24-25.09.2026 tarihlerinde kod okuması ve geliştiriciyle yapılan netleştirme üzerine yazıldı. Task id yok; kimlik developer username'idir.

## İş Etiketi

`feat`

## Kullanıcı Kararları (25.09.2026)

- Task id yok; dosya kimliği `GLB90114534`.
- Rol kapsamı: SUPERVISOR yalnız yetkili olduğu kampanyaları görür; TEAMLEAD kapsam dışı. SUPERADMIN ve TENANT_ADMIN, Options menüsünün mevcut gate'i gereği ekranı görür (bkz. Açık Sorular 1).
- Sınırlar: tarih aralığı üst sınırı 31 gün, sayfalama penceresi 10.000 satır, sayfa boyutu üst sınırı 100 satır.
- Export: v1'de CSV; XLSX server-side export 2. faz. Export satır üst sınırı 100.000.
- Kolonlar: agent için hem username hem ad-soyad gösterilir. `contact_id` varsayılan görünür. `filter`, `skills` ve `server` varsayılan gizli kolon.
- Kampanya seçimi tek kampanya.
- Üretim DB'de RCSI ve ONLINE index desteği bilinmiyor; bu geliştirme doğrudan üretime çıkmayacağı için üretim öncesi kontrol listesine bırakıldı.

## Etkilenen Alanlar

Backend (yeni):

- `gbpowerdialer/src/main/java/com/powerdialer/controller/CdrReportController.java`
- `gbpowerdialer/src/main/java/com/powerdialer/service/CdrReportService.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dao/ICdrReportDao.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dao/impl/CdrReportDao.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dto/CdrRowDTO.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dto/CdrCampaignOptionDTO.java`
- `gbpowerdialer/src/main/resources/application.properties` (`cdr.report.*` property'leri)

Backend (mevcut, yalnız okunur veya küçük ek):

- `gbpowerdialer/src/main/java/com/powerdialer/security/PermissionUtil.java` (`canViewCampaign`, `addCampaignPermissionFilter` yeniden kullanılır)
- `gbpowerdialer/src/main/java/com/powerdialer/dao/impl/CDRDao.java` (NOLOCK + `setTimeout` + timeout yakalama deseni örnek alınır; dosya değişmez)
- `gbpowerdialer/src/main/java/com/powerdialer/util/Constants.java` (grid filtre koşul sabitleri ve `CDR_OUTDIALING_TYPE`, `CAMPAIGN_TYPE` enum'ları okunur)

Frontend (yeni):

- `gbpowerdialer/src/main/webapp/js/controllers/cdrCtrl.js`
- `gbpowerdialer/src/main/webapp/js/services/cdrFactory.js`
- `gbpowerdialer/src/main/webapp/pages/cdrList.jsp`

Frontend (mevcut, ek satır):

- `gbpowerdialer/src/main/webapp/js/app.js` (route)
- `gbpowerdialer/src/main/webapp/index.jsp` (Options menüsü)
- `gbpowerdialer/src/main/webapp/WEB-INF/dispatcher-servlet.xml` (view-controller)
- `gbpowerdialer/src/main/webapp/WEB-INF/wro.xml`, `gbpowerdialer/src/main/webapp/pages/sources.jsp`, `gbpowerdialer/karma.conf.js` (yeni JS dosyalarının kaydı)
- `gbpowerdialer/src/main/webapp/js/localization/localization_{en,tr,ru,ua}.js`

Test (yeni):

- `gbpowerdialer/src/test/java/tests/cdr/*`
- `gbpowerdialer/src/test/java/tests/angular/routing/*`, `tests/angular/localization/*`, `tests/angular/grid/*`
- `gbpowerdialer/src/test/angular/grids/cdrCtrl.spec.js`

Doküman:

- `docs/architecture/API-AND-UI.md` ve `docs/architecture/DATA-PERSISTENCE-AND-DB.md` (yeni endpoint ve okuma yolu notu)
- `docs/analyse/25.09.2026/FEAT-GLB90114534-CDR-RAPOR-EKRANI-DDL.sql` (DBA teslimatı)

## Mevcut Davranış

- UI'da CDR, çağrı kaydı veya rapor ekranı yok. `js/` ve `pages/` altında `cdr`, `callRecord`, `callHistory`, `report` araması yalnız ilgisiz `lsc.responseKeys` anahtarını buluyor.
- Backend'de `pd_cdr` için list, rapor veya export endpoint'i yok. `CDRDao` `GenericDao<CDR>`'dan türediği için `getAll(page, sort, filters)` metodunu miras alıyor; ancak hiçbir controller bunu açmıyor.
- `pd_cdr` entity'si `com.powerdialer.model.CDR` (`@Table(name = "pd_cdr")`). LAZY `tenant`, `campaign`, `server`, `telephonyRC`, `assignedUser`, `connectedUser` ilişkileri; `userResultCodeId` düz `Long` kolon; `deleted` alanı yok.
- `pd_cdr` çağrı hot path'inde yazılıyor: originate anında INSERT (`CallOriginator.saveInitialCDR`), müşteri answer ve agent answer UPDATE'leri (`ManagerConnectionDecorator.handleCallAnswer/handleAgentAnswer`), hangup/result UPDATE (`processCallResult` → `persistFinalCdr`), gecikmeli user result code ve webphone fallback UPDATE'leri. Answer ve hangup yazımları Asterisk sunucusu başına tek thread'li AMI event worker'da senkron `saveOrUpdate + flush` ile çalışıyor.
- Mevcut `pd_cdr` native okumaları (`CDRDao` gamma sorguları) `WITH (NOLOCK)` ipucu ve bir kısmı `setTimeout` ile yazılmış; `isQueryTimeout` ile timeout yakalama ve fallback deseni mevcut (`CDRDao.java:420`, `:431-461`).
- Options ekranları `ui-grid` 3.2.6 ile `GET <entity>/list?page&sort&filters` kontratını ve `{data, totalItems}` yanıtını kullanıyor. Tam server-side sayfalama/sort/filter referansı `dncCtrl.js:149-292`; tarih aralığı filtre header'ı `adminLogsCtrl.js:147-209` ve `utils.js addCustomFilter` (koşul 32 başlangıç, 256 bitiş, format `dd/MM/yyyy HH:mm`).
- Grid export'u tamamen client-side (`ui-grid-exporter` CSV/PDF + `menuCtrl.js getExportMenuItemsXLSX`) ve yalnız yüklü sayfayı dışa aktarıyor. Server-side indirme için tek emsal `GET campaign/upload/failedRows/{filename}` streaming CSV.
- Yetki gerçek kapısı method-level `@PreAuthorize`. Kampanya bazlı `@permissionUtil.canViewCampaign(#id)`; filters içinden kampanya doğrulama örneği `AttachedDataController.isGetAllAllowed`. Yetkiye göre kampanya daraltan `PermissionUtil.addCampaignPermissionFilter(Criteria)` hazır (`PermissionUtil.java:216-240`).
- Options menüsü `index.jsp:37-52`; parent gate `ROLE_SUPERADMIN`, `ROLE_TENANT_ADMIN`, `ROLE_SUPERVISOR` veya `EditCampaignNonTech`.

## Beklenen Davranış

- Options menüsünde yeni "Çağrı Kayıtları (CDR)" maddesi; route `/cdrList`.
- Ekranda tenant (yalnız SUPERADMIN için) → kampanya seçimi, tarih aralığı (varsayılan bugün 00:00 → şimdi) ve "Uygula" butonu. Kampanya ve aralık seçilmeden sorgu atılmaz.
- Grid: server-side sayfalama, sort ve kolon filtreleri; sayfa boyutları 25/50/100; `gridStateService` ile kolon/filtre/sort durumu saklanır; filtre satırı aç/kapa; grid menüsünden görünen sayfa için CSV/PDF/XLSX.
- "Tümünü CSV indir" butonu aynı kampanya, aralık ve filtrelerle server-side streaming CSV üretir; satır sınırı aşılırsa dosya kesilir ve UI bilgilendirilir.
- Tüm sorgular kampanya + tarih aralığı ile sınırlıdır; aralık, sayfa ve pencere sınırları aşılırsa açıklayıcı hata döner.
- Sorgular `pd_cdr` yazıcılarını bloklamaz, süre sınırlıdır ve eş zamanlı sorgu sayısı sınırlıdır; dialer havuzu tükenmez.
- Devam eden çağrılar (`telephony_rc IS NULL`) "devam ediyor" olarak işaretlenir.
- SUPERVISOR yalnız `ViewCampaign` yetkisi olan kampanyaları listeler ve sorgular; yetkisiz kampanya id'si 403 döner.

## Kod Okuma Bulguları

### Neden generic grid altyapısı kullanılmıyor

- `GenericDao.getAll` Hibernate Criteria kullanır (`GenericDao.java:126-141`). Criteria ile `WITH (NOLOCK)` verilemez; `LockMode` üzerinden SQL Server dialect'i yalnız `updlock/holdlock` üretir.
- Her sayfa isteğinde `COUNT(DISTINCT id)` çalışır; yalnız `page.fetchRowCount=false` gönderilirse atlanır (`GenericDao.java:258-269`).
- Sort için whitelist yok; herhangi bir property ile `ORDER BY` üretilir (`GenericDao.java:271-287`).
- Hatalı filtre sessizce düşürülür ve yalnız loglanır (`GenericDao.java:249-251`).
- Query timeout yok; `applicationContext.xml` içinde `defaultQueryTimeout`, `hibernate.jdbc.fetch_size` veya `javax.persistence.query.timeout` tanımlı değil.
- `CDR` entity'sini Dozer ile DTO'ya çevirmek LAZY ilişkileri tetikler; `Campaign` 16 EAGER, `User` 6 EAGER ilişki taşıdığı için satır başına ek sorgu ve bellek maliyeti oluşur.

### Havuz ve hot path

- Web katmanı ve dialer aynı JVM'de aynı DBCP2 havuzunu paylaşır: `maxTotal=200`, `maxWaitMillis=1000` (`applicationContext.xml:45-55`). Havuz dolarsa dialer 1 sn içinde bağlantı alamaz.
- AMI event worker `ThreadPoolExecutor(1,1)`'dır (`ManagerConnectionDecorator.java:343-367`); `pd_cdr` yazımlarındaki her lock beklemesi event kuyruğuna gecikme olarak döner. `docs/change-fix-plan/18.06.2026/FIX-GLB90048674-AMI-EVENT-WORKER-LAG-URC-ABANDON-KAYBI.md` bu worker'ın senkron DB IO nedeniyle predictive burst'te geri kaldığını ölçmüştür.
- `ActionLogService` içindeki `@OptionalIsolationLevel(READ_UNCOMMITTED)` AOP'si `connection.setTransactionIsolation` çağırıp geri almaz; havuz `defaultTransactionIsolation` tanımlamadığı için kirli izolasyon aynı bağlantıyı sonradan alan dialer akışına sızabilir. Bu desen bu iş için kullanılmaz.
- `DBEngine.createDataSourceAndCheckConnection` kampanya havuzlarında `defaultQueryTimeout=10` ve MSSQL için `READ_UNCOMMITTED` varsayılanı kurar (`DBEngine.java:113-122`). Ayrı okuma havuzu gerekirse emsal budur (2. faz).

### DB ve index

- Üretim profilleri SQL Server (`SQLServer2012Dialect`, `mssql-jdbc 10.2.3.jre8`); Oracle ve Postgres profilleri de tanımlı. Mevcut `CDRDao` native SQL'i zaten yalnız SQL Server'da çalışır (`TOP`, `NOLOCK`, `GETDATE`).
- `IX_pd_cdr_call_start_time` index'i mevcut (`CDRDao.java:414` yorumu). `call_dial_time` indexsiz; üzerinde tam tarama saniyeler sürmüş (`docs/history/29.06.2026.md`). `campaign_id` için index kanıtı yok.
- Repo'da `pd_cdr` DDL'i yok; `hbm2ddl.auto` kapalı; migration framework yok. Yeni index DBA script'iyle uygulanır.
- `interaction_id` tekilliği bazı DB'lerde bozuk (`docs/change-fix-plan/13.08.2026`); satır başına tek kayıt varsayılmaz, `id` birincil anahtar olarak kullanılır.
- `call_start_time` çağrı üretildiğinde set edilir ve sonradan güncellenmez; `campaign_id` hiç değişmez. Bu ikili üzerine kurulacak index yalnız INSERT'te bakım maliyeti taşır.

### Kolon ve lookup kaynakları

- `pd_telephony_rc`: `id`, `code`, `name`, `description` (`TelephonyResultCode`). `pd_cdr.telephony_rc` id tutar, code değil.
- `wbp_result_code`: `id`, `result_code_text`, `description` (`ResultCode`). `pd_cdr.user_result_code_id` FK mapping'i olmayan düz kolon.
- `wbp_user`: `id`, `username`, `firstname`, `lastname` (`User`).
- `outdialing_type_id`: 1 NEW, 2 IMMEDIATE_RETRY, 3 SCHEDULED_RETRY, 4 CALLBACK (`Constants.CDR_OUTDIALING_TYPE`, `Constants.java:1683-1688`).
- `campaign_type`: `Constants.CAMPAIGN_TYPE` ordinal'i (`Constants.java:532`).

### UI kayıt zorunlulukları

- Yeni JS dosyası üç yere birden yazılmalı: `wro.xml` `global` group (üretim bundle), `pages/sources.jsp` (dev), `karma.conf.js` (test). İkisi bugün zaten birbirinden sapmış durumda.
- JSP template için `dispatcher-servlet.xml` içine `<mvc:view-controller path="/pages/cdrList" view-name="cdrList"/>` kaydı gerekir.
- `httpRequestInterceptor` (`app.js:39-52`) `exception==true` olmayan hatalarda `/error/<status>` sayfasına yönlendirir; ekranda kalmak için istekler `skipErrorRedirect: true` ile gönderilir.
- `GridStatePersistenceContractTest` yeni grid controller'ının 4 gated `gridStateService.save` çağrısı, `saveDefaultStateIfNotExist`, `restore` ve `reset` içermesini bekler; `GridNumericColumnTypeContractTest` sayısal kolonlarda `type : 'number'` biçimini arar.
- Lokalizasyon anahtarları 4 dosyada da bulunmalı; `CampaignDataManipulationTruncationWarningContractTest` bu deseni örnekler.

## Tasarım Kararları

1. **UI kontratı aynı, backend yolu ayrı.** Grid `GET /cdr/list?page&sort&filters` ile mevcut `JSONObject` bağlama ve `{data, totalItems}` yanıtını aynen kullanır; backend `GenericController/GenericDao` yerine amaca özel `CdrReportController → CdrReportService → CdrReportDao` zinciriyle çalışır.
2. **Tek tablo native SQL, lookup'lar Java'da.** Ana sorgu yalnız `pd_cdr` üzerinde çalışır. Telephony RC, user result code ve agent bilgileri sayfa başına üç küçük lookup ile (`pd_telephony_rc` tamamı, `wbp_result_code` tamamı, `wbp_user` sayfadaki id'ler `IN (...)`) DTO'ya doldurulur. DB planı tek index seek olur.
3. **Zorunlu kapsam.** `filters['campaign.id']` (exact, tek değer) ve `filters['callStartTime']` (32 ve 256 koşullu iki terim) zorunludur. Aralık kolonu `call_start_time`; indexli ve her satırda dolu. Aralık üst sınırı `cdr.report.max-range-days=31`.
4. **Whitelist.** Filtre ve sort alanları sabit bir `Map<gridField, sqlColumn>` üzerinden çevrilir; bilinmeyen alan veya koşul 400 döner. Sıralama her zaman `..., c.id DESC` tiebreak ile deterministiktir.
5. **NOLOCK dialect'e bağlı.** SessionFactory dialect'i `SQLServerDialect` ise `WITH (NOLOCK)` eklenir; diğer dialect'lerde ipucu boş kalır. Sayfalama Hibernate `setFirstResult/setMaxResults` ile dialect'e bırakılır.
6. **Sınırlı sayım.** `fetchRowCount=true` yalnız filtre değişiminde gönderilir. Sayım `SELECT COUNT(*) FROM (SELECT TOP (cap+1) 1 ... ) t` ile pencere sınırında kesilir; UI "10.000+" gösterir. SQL Server dışı dialect'te düz `COUNT(*)` kullanılır.
7. **Pencere ve sayfa sınırı.** `first`, `max` doğrulanır: `max ≤ 100`, `(first-1)*max + max ≤ 10.000`; aşımda 400 ve "aralığı daraltın" mesajı.
8. **Index dışı sort koruması.** Sort kolonu `call_start_time` veya `id` değilse önce sınırlı sayım yapılır; sonuç pencere sınırını aşıyorsa 400 "bu sıralama için aralığı daraltın" döner. Böylece sıralama maliyeti en fazla 10.000 satırla sınırlı kalır.
9. **Süre sınırı.** Liste sorguları `setTimeout(cdr.report.list.query-timeout-seconds=10)`, export `setTimeout(cdr.report.export.query-timeout-seconds=60)`; timeout 504 ve kullanıcı mesajına eşlenir (`CDRDao.isQueryTimeout` deseni).
10. **Eş zamanlılık sınırı.** `Semaphore` ile liste için 3, export için 1 eş zamanlı sorgu; `tryAcquire` başarısızsa 429 "meşgul, tekrar deneyin". Havuzdan en fazla 4 bağlantı rapor için kullanılır.
11. **Export.** `GET /cdr/export` aynı filtre kontratını alır; `text/csv; charset=UTF-8`, UTF-8 BOM, `;` ayraç, `Content-Disposition: attachment; filename=cdr_<campaignId>_<from>_<till>.csv`. Önce sınırlı sayım ile kesme kararı verilir ve `X-Records-Truncated: true` header'ı yazılır; sonra `scroll(ScrollMode.FORWARD_ONLY)` + `setFetchSize(500)` ile satır satır `commons-csv CSVPrinter` üzerinden akıtılır. Satır sınırı `cdr.report.export.max-rows=100000`. Export sıralaması v1'de sabit `call_start_time ASC, id ASC`. Excel formül enjeksiyonuna karşı `=`, `+`, `-`, `@` ile başlayan hücreler `'` öneki alır.
12. **Kampanya seçimi.** `GET /cdr/campaigns` `Campaign` Criteria projection (`id`, `name`, `tenant.id`, `tenant.name`) + `addCampaignPermissionFilter` + `deleted=false`; realtime ve memento yükü olan `campaign/list` kullanılmaz.
13. **Yetki.** Controller class-level `@PreAuthorize("hasAnyRole('ROLE_SUPERADMIN','ROLE_TENANT_ADMIN','ROLE_SUPERVISOR')")`; `list` ve `export` ayrıca `this.isCampaignFilterAllowed(#filters)` ile `permissionUtil.canViewCampaign(term)` kontrolü. `CampaignRecordController` gibi yetkisiz kalmaz.
14. **Transaction.** Tüm servis metodları `@Transactional(readOnly = true)`. Scalar native sonuç kullanıldığı için session first-level cache büyümez.
15. **Devam eden satırlar.** `telephony_rc IS NULL` satırlar DTO'da `inProgress=true`; `call_end_time` bu satırlarda geçicidir, UI "devam ediyor" rozeti gösterir.
16. **Agent filtresi iki adımlı.** Username/ad-soyad filtresi önce `wbp_user` üzerinde küçük bir sorguyla id listesine çevrilir, sonra ana sorguya `assigned_user_id IN (:ids)` olarak girer; ana sorgu tek tablo kalır. Agent kolonlarında sort v1'de `assigned_user_id` / `connected_user_id` id'sine eşlenir.

## Kolon Sözleşmesi (`CdrRowDTO`)

| DTO alanı | Kaynak | Varsayılan | Filtre | Sort |
|---|---|---|---|---|
| `id` | `c.id` | gizli | exact | evet |
| `interactionId` | `c.interaction_id` | görünür | exact, starts-with | evet |
| `contactNumber` | `c.contact_number` | görünür | exact, starts-with, contains | evet |
| `contactId` | `c.contact_id` | görünür | exact | evet |
| `callStartTime` | `c.call_start_time` | görünür | zorunlu aralık | evet (varsayılan DESC) |
| `callDialTime` | `c.call_dial_time` | görünür | gt/lt | evet |
| `callAnswerTime` | `c.call_answer_time` | görünür | is null / not null, gt/lt | evet |
| `callHandlingStartTime` | `c.call_handling_start` | görünür | is null / not null | evet |
| `callEndTime` | `c.call_end_time` | görünür | gt/lt | evet |
| `ringSeconds` | Java: answer − dial | görünür | yok | hayır |
| `talkSeconds` | Java: end − handling_start | görünür | yok | hayır |
| `telephonyRcId`, `telephonyRcCode`, `telephonyRcName` | `c.telephony_rc` + lookup | görünür | select (id exact) | id ile |
| `userResultCodeId`, `userResultCodeText` | `c.user_result_code_id` + lookup | görünür | select (id exact) | id ile |
| `assignedUserId`, `assignedUsername`, `assignedUserFullName` | `c.assigned_user_id` + lookup | görünür | iki adımlı username/ad-soyad contains | id ile |
| `connectedUserId`, `connectedUsername`, `connectedUserFullName` | `c.connected_user_id` + lookup | görünür | iki adımlı | id ile |
| `outdialingType` | `c.outdialing_type_id` → enum adı | görünür | select | evet |
| `campaignType` | `c.campaign_type` → enum adı | görünür | select | evet |
| `rcSequence` | `c.rc_sequence` | görünür | exact, gt/lt | evet |
| `abandoned` | `c.abandoned` | görünür | select true/false | evet |
| `inProgress` | Java: `telephony_rc IS NULL` | görünür | select | hayır |
| `filter` | `c.filter` | gizli | contains | hayır |
| `skills` | `c.skills` | gizli | contains | hayır |
| `serverId`, `serverName` | `c.server_id` + lookup | gizli | select | id ile |
| `groupId` | `c.group_id` | gizli | exact | evet |
| `contactNumberColumn`, `contactNumberOrder` | `c.contact_number_column`, `c.contact_number_order` | gizli | exact | evet |
| `outgoingCallId`, `internalCallId`, `connectedIvrNumber` | ilgili kolonlar | gizli | exact | hayır |

Tarih alanları mevcut grid'lerdeki gibi `cellFilter: 'date:"dd/MM/yyyy HH:mm:ss"'` ile gösterilir; sayısal alanlar `type : 'number'` bildirir.

## Property Listesi

```properties
cdr.report.max-range-days=31
cdr.report.max-page-size=100
cdr.report.max-window-rows=10000
cdr.report.list.query-timeout-seconds=10
cdr.report.list.max-concurrent=3
cdr.report.export.max-rows=100000
cdr.report.export.query-timeout-seconds=60
cdr.report.export.max-concurrent=1
cdr.report.export.fetch-size=500
```

Değerler `@Value("${...:varsayılan}")` ile okunur; property yoksa yukarıdaki varsayılanlar geçerlidir.

## Riskler

- **Dirty read.** NOLOCK ile devam eden çağrılarda geçici değer görülebilir; sayfa bölünmesi sırasında nadiren satır atlanabilir veya çift görünebilir. Rapor amaçlı ekran için kabul edilir; `inProgress` rozeti ve doküman notuyla görünür kılınır.
- **Index oluşturma hot tabloyu etkiler.** 13M+ satırlık tabloda ONLINE olmayan index oluşturma yazımları bloklar. DDL yalnız bakım penceresinde veya `ONLINE = ON` ile koşulur; önce test ortamı.
- **Index dışı sort maliyeti.** Pencere sınırı kuralı ve timeout ile sınırlanır; yine de büyük aralıklarda kullanıcı "aralığı daraltın" mesajı alır.
- **Havuz baskısı.** Semaphore ile rapor en fazla 4 bağlantı kullanır; ölçüm sonrası gerekirse 2. fazda ayrı küçük okuma havuzu eklenir.
- **CSV kesme.** 100.000 satır sınırı; header ve UI mesajıyla görünür. Uzun export'larda reverse proxy/Tomcat timeout'u ortam bazlı doğrulanmalı.
- **Zaman dilimi.** Filtre tarihleri `dd/MM/yyyy HH:mm` ile JVM saat diliminde parse edilir; mevcut grid'lerle aynı davranış.
- **Dialect farkı.** NOLOCK ve `TOP` yalnız SQL Server; diğer dialect'lerde ipucu boş, sayım düz `COUNT(*)`. Oracle/Postgres profilleri derlenir ve çalışır, ancak performans garantisi yalnız SQL Server için verilir.
- **Frontend kayıt sapması.** `wro.xml`, `sources.jsp` ve `karma.conf.js` üçlüsü tek işte güncellenir; contract testi bunu kilitler.
- **Yetki.** Kampanya id'si filters JSON'unda geldiği için `@PreAuthorize` SpEL'i controller metodu üzerinden filters'ı parse eder; boş veya çoklu campaign filtresi reddedilir.

## Performans Etkisi

- **Hot path sınıfı:** `Hot path değil`. Endpoint'ler admin/API akışıdır; call, event veya scheduler tick başına çalışmaz.
- **Hot tabloya okuma:** NOLOCK ile yazıcılar bloklanmaz; sorgu süresi timeout'la, kapsam zorunlu kampanya + aralıkla, satır sayısı sayfa/pencere/export sınırlarıyla, bağlantı sayısı semaphore ile sınırlıdır.
- **Dolaylı hot path maliyeti:** `IX_pd_cdr_campaign_start` index'i her `pd_cdr` INSERT'inde bir ek index yazımı getirir (originator thread'i, `saveInitialCDR`). UPDATE yolları anahtar kolonlara dokunmadığı için etkilenmez. Maliyet dar iki kolonlu anahtarla sınırlıdır.
- **Ölçüm notu:** Execution plan test ortamında `SET STATISTICS IO/TIME` ile index seek ve sayfa sorgusu süreleri kaydedilir; üretim öncesi RCSI ve edition kontrolü yapılır.

## TDD Stratejisi

Java testleri `gbpowerdialer/src/test/java/tests/cdr` altında, Mockito ve constructor boundary ile; SQL contract'ları DB açmadan string seviyesinde doğrulanır.

- `CdrReportQueryBuilderContractTest`: SQL Server dialect'inde `WITH (NOLOCK)` var, diğer dialect'te yok; `campaign_id` ve `call_start_time` aralık parametreleri zorunlu; whitelist dışı sort/filter alanı hata; `ORDER BY` sonunda `c.id DESC`; sınırlı sayım `TOP (cap+1)`.
- `CdrReportRequestValidationTest`: aralık üst sınırı, sayfa boyutu, pencere sınırı, tarih formatı, çoklu/boş kampanya filtresi reddi, index dışı sort + pencere aşımı reddi.
- `CdrReportControllerContractTest` (MockMvc): `GET /cdr/list` `{data, totalItems}` shape; yetkisiz kampanya 403; eksik kampanya 400; semaphore doluyken 429; timeout 504.
- `CdrReportExportStreamingTest`: BOM, `;` ayraç, `Content-Disposition`, satır sınırında kesme ve `X-Records-Truncated`, formül enjeksiyonu kaçışı, export semaphore.
- `CdrRowDtoEnrichmentTest`: ring/talk süreleri null-safe; lookup eşlemeleri; `inProgress` bayrağı; enum adları.
- `CdrCampaignOptionsPermissionTest`: `/cdr/campaigns` projection'ının `addCampaignPermissionFilter` ve `deleted=false` uyguladığı.

Angular Java contract testleri `gbpowerdialer/src/test/java/tests/angular/*`:

- `routing/CdrListRouteAndBundleContractTest`: route, `templateUrl`, controller alias, `wro.xml` ve `sources.jsp` sırası, `dispatcher-servlet.xml` view-controller, `index.jsp` menü maddesi ve gate.
- `localization/CdrLocalizationKeysContractTest`: 4 dil dosyasında `menu.cdr.list` ve `cdr.*` anahtarları.
- `grid/GridStatePersistenceContractTest` registry'sine `cdrCtrl` eklenir; `grid/GridNumericColumnTypeContractTest` sayısal kolonları doğrular.

Karma/Jasmine `gbpowerdialer/src/test/angular/grids/cdrCtrl.spec.js`:

- Kampanya veya aralık seçilmeden `getAll` çağrılmaz.
- `paginationChanged` sonrası `fetchRowCount=false`, `filterChanged` sonrası `fetchRowCount=true`.
- `sortChanged` ve `filterChanged` `page/sort/filters` nesnelerini factory'ye doğru geçirir; filtre 500 ms debounce.
- Export URL aynı kampanya, aralık ve filtreleri taşır.
- 400/429/504 yanıtlarında `alertService` mesajı üretilir ve `/error` yönlendirmesi olmaz.

Doğrulama komutları (offline, `.codex/TOOL-PATHS.local.properties` dikkate alınarak):

```text
mvn -o -Dtest=CdrReportQueryBuilderContractTest,CdrReportRequestValidationTest,CdrReportControllerContractTest,CdrReportExportStreamingTest,CdrRowDtoEnrichmentTest,CdrCampaignOptionsPermissionTest test
mvn -o -Dtest=CdrListRouteAndBundleContractTest,CdrLocalizationKeysContractTest,GridStatePersistenceContractTest,GridNumericColumnTypeContractTest test
npm.cmd run test:angular
```

`node_modules` şu an mevcut değil; Karma koşusu için environment blocker execution plan'da açık yazılır.

## Önerilen Çözüm Yönü (PR dilimleri)

1. **PR-0 DDL (DBA):** `FEAT-GLB90114534-CDR-RAPOR-EKRANI-DDL.sql` test ortamında koşulur; kontrol sorgularıyla index ve RCSI durumu kaydedilir.
2. **PR-1 Backend, karanlık:** `CdrReportController`, `CdrReportService`, `CdrReportDao`, DTO'lar, property'ler, whitelist, sınırlar, semaphore, export; Java testleri. UI olmadığı için üretim davranışı değişmez.
3. **PR-2 UI:** route, menü, view-controller, `cdrCtrl.js`, `cdrFactory.js`, `cdrList.jsp`, 4 lokalizasyon dosyası, `wro.xml` + `sources.jsp` + `karma.conf.js`; Angular contract ve Karma testleri; `docs/architecture` güncellemesi.
4. **PR-3 (2. faz, opsiyonel):** SXSSF ile XLSX export (POI 3.13 mevcut), ayrı küçük okuma havuzu (`DBEngine` deseni), çoklu kampanya, kayıtlı filtre setleri.

Bağımlılık: PR-1 bağımsız; PR-2 PR-1'e ve test ortamında PR-0'a bağlı.

## DDL Özeti (MS SQL; tam script ayrı dosyada)

```sql
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_pd_cdr_campaign_start' AND object_id = OBJECT_ID('dbo.pd_cdr'))
  CREATE NONCLUSTERED INDEX IX_pd_cdr_campaign_start
    ON dbo.pd_cdr (campaign_id, call_start_time)
    WITH (ONLINE = ON, SORT_IN_TEMPDB = ON);
```

Enterprise edition değilse `ONLINE = OFF` ile bakım penceresinde koşulur. Geri alma: `DROP INDEX IX_pd_cdr_campaign_start ON dbo.pd_cdr`.

## Kapsam Dışı

- Server-side XLSX export (2. faz).
- Çoklu kampanya seçimi, kayıtlı filtreler, zamanlanmış rapor.
- Ayrı okuma havuzu veya read replica (ölçüm sonrası 2. faz).
- `pd_cdr` yazma yolunda, CDR lifecycle'ında veya retention/purge'da değişiklik.
- `/api/**` harici API üzerinden CDR erişimi.
- Next.js karşılığı; ekran AngularJS legacy standardıyla açılır, route/contract testleri ileride taşıma için kilit görevi görür.

## Açık Sorular ve Netleştirme İhtiyacı

1. **Rol gate'i (varsayım, onay bekliyor):** "sadece SUPERVISOR" cevabı TEAMLEAD'in dışlanması olarak yorumlandı; SUPERADMIN ve TENANT_ADMIN, Options menüsünün mevcut gate'i gereği ekranı görmeye devam eder. Yalnız SUPERVISOR görmeliyse belirtin.
2. Yanıtlandı: task id yok, kimlik `GLB90114534`.
3. Yanıtlandı: 31 gün / 10.000 pencere / 100 sayfa.
4. Yanıtlandı: v1 CSV, XLSX 2. faz, export sınırı 100.000.
5. Yanıtlandı: agent için username ve ad-soyad birlikte; `contact_id` görünür; `filter`, `skills`, `server` gizli.
6. Yanıtlandı: tek kampanya.
7. Yanıtlandı: RCSI ve ONLINE bilgisi yok; üretim öncesi kontrol listesine alındı, geliştirme doğrudan üretime çıkmayacak.

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyor. Execution plan `docs/execution-plan/25.09.2026/FEAT-GLB90114534-CDR-RAPOR-EKRANI.md` altında, bu analiz geliştirici tarafından incelenip Açık Sorular 1 kapatıldıktan ve nihai plan onaylandıktan sonra oluşturulacak. Plan, PR dilimi bazlı TDD test planını, hedefli doğrulama komutlarını ve başarılı test kapanış kriterini içerecek.
