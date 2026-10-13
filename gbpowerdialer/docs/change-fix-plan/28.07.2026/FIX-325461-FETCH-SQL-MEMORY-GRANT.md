# FIX-325461 — Fetch SQL'lerinde satır limitinin sorguya taşınması ve tarih literalinin parametreye çevrilmesi

- **İş Etiketi:** fix
- **Öncelik:** hotfix (kullanıcı kararı 28.07.2026; üretimde workaround devrede: etkilenen kampanyalarda Sort Conditions kaldırıldı, kalıcı fix sıralamayı geri açmanın önkoşulu)
- **Task:** 325461
- **Branch:** `fix/325461-fetch-sql-memory-grant` (base: `develop` = `origin/develop` @ 981fea94, 22.07.2026)
- **Tarih:** 28.07.2026
- **Durum:** Onaylandı, uygulama başladı (5 açık soru 28.07.2026'da cevaplandı; cevaplar aşağıya işlendi)

## Bağlam

27-28.07.2026'da `GUVENCELL_FKS_CAPRAZ` (campaign id=160) kampanyasında "fresh data 0, arama çıkmıyor, DB timeout" vakası incelendi. Kök neden canlı DB ölçümüyle kesinleşti: fetch sorguları `SELECT *` sonucunu `ORDER BY` ile sıralarken büyük bir **memory grant** istiyor, grant kuyruğa giriyor ve sorgu `DBEngine`'in 10 saniyelik query timeout'una takılıp ölüyor.

Kanıtlar (campaign DB `CAMPAIGN_DB`, 28.07.2026):

| Ölçüm | Sonuç |
| --- | --- |
| `SELECT COUNT(*)` (WHERE/JOIN/ORDER BY yok) | **4 ms**, 511 logical read |
| Retry fetch, ORDER BY'lı | **52.014 ms** elapsed / **15 ms CPU** |
| Aynı sorgu, ORDER BY'sız | **45 ms** (logical read aynı: 2474) |
| Fresh fetch, ORDER BY'lı (1154 satır) | **14.376 ms** / 47 ms CPU |
| Oturum beklemesi | **`RESOURCE_SEMAPHORE` 3 task / 68.270 ms** (≈ iki sorgunun toplamı) |

Elenen hipotezler: tablo hacmi/IO (30.562 satır, 23 MB), eksik index (28.07'de eklenen `IX_GUVENCELL_FKS_CAPRAZ_7COL` var, darboğaz değil), lock (`LCK_M_SCH_M` 0 ms; DBA da "lock yok" dedi), bozuk istatistik (28.07 FULLSCAN güncel), priority tablosunun boş kalması (`pd_multiple_sort_160` 6109 satır dolu).

DBA'nın canlı `sys.dm_exec_requests` çıktısı aynı sonucu bağımsız olarak doğruladı: `CAMPAIGN_DB_USER @ DIALER_APP_HOST`, `status=suspended`, `wait_type=RESOURCE_SEMAPHORE` (4114 ms / 4075 ms), `blocking_session_id=0`, sorgu `SELECT * FROM GUVENCELL_06AKG src LEFT JOIN pd_multiple_sort_113 pri ... ORDER BY src.ATTEMPTS ASC, src.id ASC` (campaign 112/113).

Nihai kanıt: 28.07 10:08-10:10 arasında etkilenen kampanyalarda Sort Conditions kaldırıldı; o dakikadan sonra timeout gelmedi. Aynı DB, aynı veri, aynı index, tek değişken `ORDER BY`.

Bedeli: sıralama kapalı olduğu için müşteri kontak önceliğini kaybetti. Bu planın teslimi, sıralamayı **güvenle geri açabilmektir**.

## Mevcut Davranış

### 1. Satır limiti yalnız istemci tarafında

`DBEngine.executeParametrizedQuery` (`gbpowerdialer/src/main/java/com/powerdialer/engine/db/DBEngine.java:436-449`) limiti `preparedStatement.setMaxRows(limit)` ile uyguluyor. Sürücü bunu sunucuda `SET ROWCOUNT` olarak uygular; **sorgu metnine hiçbir şey eklenmez** (DBA'nın gönderdiği canlı sorgu metninde `TOP` yok).

Sonuç: optimizer sorguyu "tüm eşleşen satırlar istenecek" varsayımıyla derler. `ORDER BY` blocking bir operatör olduğu için sunucu, eşleşen kümenin tamamını sıralamayı bitirmeden tek satır bile döndürmez; istemci limiti ancak pahalı iş bittikten sonra ağ transferini kısar. Sıralama `SELECT *` yüzünden tüm kolon yükünü taşıdığından memory grant talebi eşleşen satır sayısı × satır genişliği ile ölçeklenir.

Etkilenen SQL üreticileri (`gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java`):

- `fetchDataForProducerFresh` (satır 780) — `createSelectAllQuery` + WHERE + `createOrderByClause`, `maxRows` yalnız `dbEngine.executeQuery` parametresi olarak geçer.
- `fetchDataForProducerRetries` (satır 927) — aynı desen.
- `fetchDataForProducerCallbacks` (satır 872) — aynı desen.
- `filtersTest` (satır 709) — `Constants.FILTER_TEST_LIMIT = 10` ile aynı desen (kampanya formundaki "Test" butonu).

### 2. Retry/callback SQL'lerinde tarih literali inline

- `fetchDataForProducerRetries` satır 935-936: `double now = Utils.convertDateToDouble(new Date());` → `... AND res.retry_date <= 46229.42290931713`
- `addCallbackCondition` satır 886-888: `... AND res.callback_date <= <now>` (ayrıca satır 895 `nowMinusResetTimeout`)
- `createFromTillTimeRestrictions` satır 1028-1034: timezone açık kampanyalarda `nowTimeAtUTC` literali (aynı sınıf sorun, bu kampanyada timezone kapalı).

`Utils.convertDateToDouble` (`gbpowerdialer/src/main/java/com/powerdialer/util/Utils.java:41-43`) milisaniye çözünürlüklü bir double üretir; her çalıştırma **yeni bir sorgu metni** demektir. Sonuçları:

- Plan cache'te yeniden kullanım yok → **compat level 150'de varsayılan açık olan row-mode memory grant feedback hiç öğrenemiyor** (mekanizma plan reuse gerektirir), yani DB'nin aşırı grant'i kendi kendine küçültme yeteneği devre dışı.
- Her tick'te derleme maliyeti ve plan cache kirlenmesi.
- Korelasyon: 500 örnek timeout'un 252'si retry, 129'u callback ailesinden — plan'ı yeniden kullanamayan iki aile. (Fetch sırasının da payı var; ama plan reuse kaybı bağımsız ve gerçek bir kusur.)

### 3. Sayaç zinciri tek try/catch içinde

`CallProducer.calculateCampaignParameters` (`gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallProducer.java:833-845`) altı count sorgusunu tek `try/catch` içinde sırayla çağırıyor:

`countActualListSize` → `countTotalListSize` → `countRetryListSize` → `countCallbackListSize` → `countFreshDataListSize` → `calculateListRemaining` → `calculateContactsToHandleAmount`

Vakada 500 örneğin 101'i **2. sıradaki** `countTotalListSize`'da patladı; bu yüzden `retryListSize`, `callbackListSize`, `freshDataListSize` ve `listRemaining` hiç yazılmadı. `CampaignMemento` `freshDataListSize`'ı restore etmediği için ekranda kalıcı 0 göründü — müşterinin ilk şikâyeti ("fresh data kısmı dolmuyor") tam olarak buydu, oysa DB'de 1154 uygun satır vardı.

## Beklenen Davranış

1. **Satır limiti sorgunun parçası olacak.** MS SQL için `SELECT TOP (n) * ...`; optimizer Sort yerine **Top N Sort** seçer ve memory grant ~n satır için boyutlanır. Sonuç kümesi bugünküyle aynıdır: `TOP n ... ORDER BY` = "sıralamaya göre ilk n satır" — bugün de sunucu tüm kümeyi sıralayıp sürücü ilk n'i alıyor.
2. **`n <= 0` durumunda bugünkü davranış korunacak** (limit ifadesi üretilmez). Gerekçe aşağıda "Riskler" ve Açık Soru 2.
3. **`setMaxRows` kaldırılmayacak**; ikinci bir emniyet olarak kalır (aynı `n` ile çelişki üretmez).
4. **Retry/callback tarih koşulları parametreyle bağlanacak** (`?` + `Double` bind) → aynı kampanya için sorgu metni sabitlenir, plan yeniden kullanılır, grant feedback öğrenmeye başlar.
5. **Sayaç zinciri hata izolasyonlu olacak:** bir count sorgusu patladığında diğer sayaçlar yazılmaya devam eder; hata yine loglanır. Çağrı akışı, dialing sırası, SQL şekli ve kampanya kuralları değişmez.

## Etkilenen Kod Alanları

- `gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java`
  - `createSelectAllQuery` (736) → satır limiti üretebilen overload. Limit ifadesi tek bir `DB_TYPE` sınırında üretilir: prefix hook'u (MS SQL `TOP (n)`) ve suffix hook'u (Oracle `FETCH FIRST n ROWS ONLY`, PostgreSQL/MySQL `LIMIT n`) birlikte tasarlanır; **bu işte yalnız MS SQL kolu doldurulur**, diğer tipler bugünkü davranışta (limit ifadesi yok) bırakılır. Oracle'a taşıma planı olduğu için sınır şimdi hazırlanır, ileride tek kol eklemek yeterli olacak (Açık Soru 1).
  - `fetchDataForProducerFresh` (780), `fetchDataForProducerRetries` (927), `fetchDataForProducerCallbacks` (872): limit üretimi.
  - `fetchDataForProducerRetries` (936) ve `addCallbackCondition` (888, 895): literal → `?` + parametre listesi; çağrı `dbEngine.executeQuery` yerine `dbEngine.executeParametrizedQuery` (`ParametrizedQuery` ile).
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallProducer.java`
  - `calculateCampaignParameters` (833-845): sayaç başına hata izolasyonu.
- (Test) `gbpowerdialer/src/test/java/tests/campaign/db/` — yeni SQL regresyon testleri.
- (Test) `gbpowerdialer/src/test/java/tests/campaign/` — sayaç izolasyonu testi.

Değişmeyecek: `createOrderByClause`, `createFromClause`, `createWhereFromFilters`, filtre semantiği, `PriorityService`, Redis/AMI contract'ları, campaign lifecycle.

## Kapsam Dışı (bilinçli olarak ertelendi)

Kullanıcı 28.07.2026'da mevcut akışı bozmamak için kapsamı daralttı:

- **Fetch sırası ve callback bütçesi** (`fetchCallsWithoutSegmentation` 370-414): callbacks → retries → fresh sırası ve callbacks'in `remainDataToFetch`'in tamamını alması (ratio yok). Dialing kompozisyonunu değiştirir; ürün kararı gerektirir.
- **`CampaignMemento`'ya `freshDataListSize`**: kod küçük ama memento tablosuna kolon eklemek gerekiyor (DDL + DBA).
- **SQLException sonrası backoff** (producer tick zamanlaması).
- **`DBEngine.java:111` sabit `setDefaultQueryTimeout(10)`'un yapılandırılabilir olması.**
- **Timeout log'una campaign bilgisi eklenmesi** (`LoggerDecorator.java:97-101`; Elastic'te kampanya kırılımı olmadığı için teşhis `pd_action_log`'a inmek zorunda kaldı).
- **JMX metriğinin timeout'ta da yazması** (`CampaignDatasourceMetricCollectorAspect.java:30-35`: `proceed()` patlarsa süre hiç kaydedilmiyor).
- **Tüm sort field'ları `NATURAL_ORDER` iken gereksiz priority join'i ve her kayıtta `TRUNCATE`+INSERT** (`createFromClause` 747-751, `createEmptyPriorityRecords` 2770).
- **`startup_db_check=false` iken takılı `is_fetched` satırları** (28.07'de camp 160'ta 172 satır kalıcı görünmez).
- **`fetchCallsWithoutSegmentation` retry çağrısındaki `retryQuantity > 0` kontrolünün eksikliği** (satır 386-388) — bu planda yalnız not edilir, düzeltilmez (bkz. Açık Soru 2).
- **Kampanya formundaki "Test" butonu (`filtersTest`, 709) ve Data ekranı (`fetchDataByFilters` 1061 / `countDataByFilters` 1089)** — kullanıcı kararı 28.07.2026: bu planda kapsam dışı. Kalan risk aşağıda "Riskler" bölümünde yazıldı.
- **MS SQL dışı DB tipleri için limit üretimi** — sınır hazırlanır, kol doldurulmaz (Açık Soru 1).

## Riskler

- **Blast radius:** SQL üreteci tüm müşteri kampanyalarında ortak. Sözdizimi hatası veya DB tipi atlaması **tüm kampanyaları** etkiler. Azaltma: `DB_TYPE` bazlı üretim + her DB tipi için SQL string karakterizasyon testi; kapsamın MS SQL'e daraltılması seçeneği (Açık Soru 1).
- **`n <= 0` semantiği:** JDBC'de `setMaxRows(0)` **limitsiz** demektir. `fetchCallsWithoutSegmentation` retry çağrısı `retryQuantity = retryRatio * remain / 100` ile 0 gönderebiliyor (`> 0` kontrolü yok), yani bugün o durumda retry sorgusu tüm eşleşen satırları çekiyor. `TOP (0)` yazılırsa **hiç satır** dönerdi — davranış değişikliği. Bu planda `n <= 0` iken limit ifadesi üretilmez; mevcut davranış test ile kilitlenir.
- **Parametreli sorguya geçiş:** FREE filter metni kullanıcı tarafından yazılıyor; içinde `?` bulunması teorik olarak parametre indeksini kaydırır. Sürücü string literal içindeki `?` işaretlerini parametre saymaz, ama tırnak dışı `?` içeren bir FREE filter zaten geçersiz SQL üretir. Test senaryosu olarak eklenecek.
- **Kampanya SQL log formatı:** `DBEngineFileLogger` parametreli sorguda `... parameters: [...]` ekiyle yazar; teşhis kabiliyeti korunur, format hafif değişir.
- **Plan davranışı:** parametreli sorgu tek plan kullanacağı için parameter sniffing teorik olarak mümkün; ancak sorgu metni kampanya bazında zaten farklı (tablo/kolon/filtre inline) ve `retry_date <= ?` koşulu monoton artan bir eşik olduğundan seçicilik profili stabil.
- **Kalan risk — "Test" butonu ve Data ekranı kapsam dışı:** `filtersTest` de `SELECT *` + `ORDER BY` üretiyor ve limiti (10) yalnız istemci tarafında uyguluyor. Fetch sorguları düzeltilse bile, sıralama üretimde geri açıldıktan sonra DB'de grant baskısı oluşursa **kampanya formundaki Test butonu yine 10 saniyeye takılabilir** (vakada 12 kez oldu). Kabul edilen bilinçli risk; ayrı işte kapatılacak.
- **MS SQL dışı DB tipleri:** limit ifadesi üretilmediği için o kampanyalar bugünkü davranışta kalır — yani aynı arıza sınıfına açık kalmaya devam ederler. Oracle'a taşıma gündemde olduğundan sınır şimdi hazırlanıyor; taşıma işinde Oracle kolunun doldurulması ve `FETCH FIRST n ROWS ONLY` sürüm kontrolü (12c+) zorunlu maddedir.
- **Sayaç izolasyonu:** `calculateListRemaining` `getActualListSize()`'ı okuyor; `countActualListSize` patlarsa bayat değer üzerinden hesaplar. Bugün de aynı bayat değer kullanılıyor (o adıma hiç gelinmiyordu); izolasyon sonrası "bayat aktüel + güncel completed" karışımı oluşabilir. Azaltma: aktüel başarısızsa `listRemaining` güncellenmez.

## Performans Etkisi

Hot path'e (producer fetch tick'i, `CampaignDbOperations` fetch SQL'leri) dokunuluyor. Etki **belirgin pozitif**:

- `TOP (n)`: Sort → Top N Sort; memory grant eşleşen küme yerine ~n satır için boyutlanır. Ölçülen fark aynı sorguda 52.014 ms → 45 ms mertebesinde (ORDER BY'sız referans); üretimde grant kuyruğuna girmemek anlamına gelir.
- Parametre bağlama: tick başına derleme maliyeti düşer, plan cache kirlenmesi azalır, grant feedback devreye girebilir.
- Sayaç izolasyonu: sorgu sayısı ve sırası değişmez, yalnız hata yayılımı değişir. Ek IO/lock/allocation yok.
- Call/event başına ek maliyet **yok**; yeni DB sorgusu, blocking network çağrısı, tam koleksiyon taraması veya geniş `synchronized` blok eklenmiyor.

## TDD Stratejisi

Kanıtlı desen: `DBEngine` mock + `ArgumentCaptor<String>` ile üretilen SQL yakalanır (`gbpowerdialer/src/test/java/tests/campaign/CampaignFreshDataListSizeSqlRegressionTest.java` şablonu).

1. **Karakterizasyon (önce mevcut davranışı kilitle)** — `tests/campaign/db/FetchSqlRowLimitRegressionTest`:
   - Mevcut fresh/retry/callback SQL'lerinin bugünkü şekli (WHERE sırası, filtre birleşimi, ORDER BY varlığı) assert edilir; bu testler değişiklikten sonra da geçmeye devam etmeli.
2. **RED → GREEN (limit)** — aynı sınıf:
   - `maxRows = 100` iken üretilen SQL `SELECT TOP (100) *` ile başlar (MS SQL); `maxRows = 0` ve negatif iken limit ifadesi **üretilmez**; ORDER BY ve WHERE korunur.
   - MS SQL dışı bir connection string ile (ör. Oracle) limit ifadesinin **üretilmediği** kilitlenir — bugünkü davranışın korunduğunun kanıtı ve ileride Oracle kolu eklendiğinde bu testin bilinçli olarak güncellenmesi gerekir.
3. **RED → GREEN (parametre)** — `tests/campaign/db/FetchSqlDateParameterBindingRegressionTest`:
   - Retry SQL'inde `retry_date <= ?` bulunur, literal double **bulunmaz**; `ParametrizedQuery.getParameters()` beklenen değeri taşır.
   - Callback SQL'i için aynısı (`callback_date <= ?`, appointer reset dalı dâhil).
   - FREE filter metninde `?` içeren senaryoda parametre sayısının kaymadığı doğrulanır.
4. **RED → GREEN (sayaç izolasyonu)** — `tests/campaign/CampaignParametersCounterIsolationRegressionTest`:
   - `countTotalListSize` SQLException atarken `freshDataListSize`, `retryListSize`, `callbackListSize` yine set edilir; hata loglanır; `listRemaining` aktüel bayatken güncellenmez.
5. **Regresyon güvencesi:** `CallProducerTest`, `CampaignFreshDataListSizeSqlRegressionTest`, `CampaignRuntimeCountersCharacterizationTest` ve filtre/SQL üretimiyle ilgili mevcut testler koşulur.

## Uygulama Kontrol Listesi

- [x] Açık sorular cevaplandı ve plana işlendi.
- [x] Geliştirici planı onayladı (28.07.2026).
- [x] `FetchSqlRowLimitRegressionTest` RED doğrulandı (4 failure; yakalanan SQL `SELECT * FROM GUVENCELL_FKS_CAPRAZ src ...` — limit yok).
- [x] `FetchSqlDateParameterBindingRegressionTest` RED doğrulandı (4 failure; `src.retry_date <= 46229.50788949074` literali).
- [x] `createSelectAllQuery(Campaign, Integer)` overload'ı + `createRowLimitPrefix` (MS SQL `TOP (n)`, `n <= 0` guard, diğer DB tipleri bugünkü davranışta) eklendi.
- [x] Fresh/retry/callback fetch'leri limit üretiyor (`filtersTest` bilinçli olarak kapsam dışı).
- [x] Retry + callback tarih koşulları parametreye çevrildi; `fetchDataForProducerRetries`, `fetchDataForProducerCallbacks` ve (aynı WHERE üreticisini paylaştığı için) `countCallbacksToLoad` `executeParametrizedQuery`'ye taşındı.
- [x] `calculateCampaignParameters` sayaç başına izole edildi (`updateCounter` + `CounterUpdate`); türetilmiş sayaçlar bayat girdiyle hesaplanmıyor.
- [x] Yeni testler GREEN (12/12).
- [x] Etkilenen mevcut testler GREEN (12 sınıf, 93/93). `CountCompletedCallsFilterExpansionTest.captureFetchDataForProducerRetriesSql` yardımcı metodu parametreli yola göre güncellendi (assertion amacı değişmedi).
- [x] `CampaignParametersCounterIsolationRegressionTest` RED doğrulandı. İlk koşuda fixture kaynaklı NPE alınmıştı (`CampaignContext` ctor `endListBehaviour` istiyor); fixture düzeltildikten sonra değişiklikler `git stash` ile geçici çıkarılıp eski kodla tekrar koşuldu: **2 failure** — `fresh sayacı, önceki sorgu patlasa bile yazılmalı expected:<1154> but was:<0>` ve `total liste yazılmalı expected:<30562> but was:<0>`. Müşterinin gördüğü semptom testle birebir üretildi.
- [x] Tam test suite koşuldu (ortak SQL üreteci değiştiği için): **546 test, 5 failure + 3 error**, tamamı aşağıdaki iki pre-existing sınıfta; süre ~1 saat.
- [ ] Üretilen SQL kampanya log dosyasında gözle doğrulandı (test ortamı).
- [x] History kaydı eklendi (`docs/history/28.07.2026.md`) — uygulama kaydı tam suite sonucundan sonra eklenecek.
- [ ] Commit + PR (kullanıcı onayıyla).
- [ ] Üretimde etkilenen kampanyalarda Sort Conditions geri açıldı ve timeout dönmedi (kapanış).

## Hedefli Doğrulama Komutları

`.codex/TOOL-PATHS.local.properties` okunarak offline:

- `mvn -o -Dtest=FetchSqlRowLimitRegressionTest test`
- `mvn -o -Dtest=FetchSqlDateParameterBindingRegressionTest test`
- `mvn -o -Dtest=CampaignParametersCounterIsolationRegressionTest test`
- Etkilenen mevcut: `mvn -o -Dtest=CallProducerTest,CampaignFreshDataListSizeSqlRegressionTest,CampaignRuntimeCountersCharacterizationTest test`

Frontend değişikliği yok → Karma/Jasmine koşumu gerekmiyor.

### Üretim doğrulama sorguları (deploy sonrası)

Elastic (index prefix çalışan sete göre değişir; bu vakada `turkcell-*`) — **`"SQLTimeoutException"` phrase'i eşleşmez** (indekslenen token `java.sql.sqltimeoutexception`), doğru arama `"The query has timed out"`:

```json
GET /turkcell-*/_search
{
  "size": 5,
  "_source": ["@timestamp","message"],
  "query": { "bool": { "must": [
    { "range": { "@timestamp": { "gte": "now-1h", "lt": "now" } } },
    { "query_string": { "query": "\"The query has timed out\"" } }
  ] } },
  "aggs": { "dakika": { "date_histogram": { "field": "@timestamp", "fixed_interval": "1m", "time_zone": "+03:00" } } },
  "sort": [ { "@timestamp": { "order": "desc" } } ]
}
```

Kampanya kırılımı (dialer DB'si):

```sql
SELECT c.name, DATEPART(HOUR, a.create_date) AS saat, COUNT(*) AS hata
FROM pd_action_log a WITH(NOLOCK)
JOIN pd_campaign  c WITH(NOLOCK) ON c.id = a.campaign_id
WHERE a.log LIKE '%timed out%' AND a.create_date >= CAST(GETDATE() AS date)
GROUP BY c.name, DATEPART(HOUR, a.create_date)
ORDER BY saat, c.name;
```

Plan doğrulaması (campaign DB): üretilen sorgu SSMS'te actual plan ile koşulduğunda sıralama operatörü **Top N Sort** olmalı ve `SELECT` operatörünün `Memory Grant` değeri `TOP`'suz hâline göre belirgin küçük olmalı. DB sağlıklıyken süre farkı görünmeyebilir; karşılaştırma **grant değeri** üzerinden yapılır.

## Kapanış Kriteri

- Fresh/retry/callback fetch SQL'leri `maxRows > 0` iken satır limitini sorgu metninde taşıyor; `maxRows <= 0` iken bugünkü davranış korunuyor.
- Retry ve callback tarih koşulları parametreyle bağlanıyor; sorgu metni çalıştırmalar arasında sabit.
- Bir count sorgusu patladığında diğer sayaçlar güncellenmeye devam ediyor.
- Yeni testler + etkilenen mevcut testler PASS.
- Üretimde etkilenen kampanyalarda (160, 112, 113, 115, 164) Sort Conditions geri açıldıktan sonra `"The query has timed out"` kaydı dönmüyor ve `Fetched calls ... Fresh=NN` satırları geliyor.

## Açık Sorular (kapandı — 28.07.2026)

**Cevaplar:**

1. **DB tipi kapsamı** → **MS SQL ile ilerlenecek.** Kullanıcı notu: ileride DB'lerin Oracle'a taşınması planı var. Bu yüzden limit üretimi tek bir `DB_TYPE` sınırında, prefix/suffix hook'larıyla tasarlanıyor; yalnız MS SQL kolu doldurulup diğerleri bugünkü davranışta bırakılıyor. Oracle taşımasında yapılacak iş tek kol eklemek + `FETCH FIRST n ROWS ONLY` için 12c+ sürüm teyidi olacak; sorguların yeniden yazılması gerekmeyecek.
2. **`n <= 0` davranışı** → **öneri kabul edildi:** limit ifadesi üretilmez, bugünkü (limitsiz) davranış korunur ve test ile kilitlenir. `retryQuantity == 0` çağrısı ayrı işte düzeltilecek.
3. **Test butonu / Data ekranı** → **bu planda kapsam dışı.** Kalan risk "Riskler" bölümüne yazıldı (sıralama geri açıldıktan sonra Test butonu yine timeout alabilir).
4. **Üretim doğrulaması** → Workaround'un kesin uygulandığı kampanyalar **160 ve 115**; diğerleri (112, 113, 164) belirsiz. Kod değişikliği jenerik olduğu için bu belirsizlik **uygulamayı etkilemiyor**; yalnız kapanış adımında sıralamanın nerede geri açılacağını bilmek için gerekiyor. Liste deploy öncesi şu sorguyla çıkarılacak (dialer DB'si):
   ```sql
   SELECT id, name, col_sort_by, db_list_sort_direction, db_priority_table,
          update_date, update_user
   FROM pd_campaign
   WHERE deleted = 0 AND db_connection_string LIKE '%CAMPAIGN_DB_HOST%'
   ORDER BY update_date DESC;

   SELECT sf.campaign_id, c.name, sf.name AS sort_field, sf.enabled, sf.deleted, sf.update_date
   FROM pd_sort_field sf JOIN pd_campaign c ON c.id = sf.campaign_id
   WHERE c.deleted = 0 AND c.db_connection_string LIKE '%CAMPAIGN_DB_HOST%'
   ORDER BY sf.update_date DESC;
   ```
   28.07 10:08-10:10 civarında güncellenmiş ve `col_sort_by` boşalmış / sort field'ları kapatılmış kampanyalar workaround'un dokunduklarıdır.
5. **Deploy hattı** → **hotfix.** Öncelik alanı güncellendi. Uygulama bu branch'te (`develop` bazlı) yürür; PR hedefi ve cherry-pick edilecek release branch'i PR aşamasında netleşecek — kod değişikliği hedef branch'ten bağımsız olduğu için uygulamayı bloklamıyor.

Sorular ve gerekçeleri (kayıt için):

1. **DB tipi kapsamı.** Limit üretimi yalnız MS SQL için mi eklenecek, yoksa Oracle / PostgreSQL / MySQL de kapsanacak mı? Oracle'da `FETCH FIRST n ROWS ONLY` 12c+ gerektirir; daha eski sürümde `ROWNUM` ile alt sorgu sarmalaması gerekir ve bu ORDER BY ile birlikte davranış riski taşır. Üretimdeki dağılımı şu sorgu verir (dialer DB'si):
   ```sql
   SELECT CASE
            WHEN db_connection_string LIKE 'jdbc:sqlserver%'   THEN 'MSSQL'
            WHEN db_connection_string LIKE 'jdbc:oracle%'      THEN 'ORACLE'
            WHEN db_connection_string LIKE 'jdbc:postgresql%'  THEN 'POSTGRES'
            WHEN db_connection_string LIKE 'jdbc:mysql%'       THEN 'MYSQL'
            ELSE 'DIGER' END AS db_tipi,
          COUNT(*) AS kampanya
   FROM pd_campaign WHERE deleted = 0 GROUP BY 1 ORDER BY 2 DESC;
   ```
   Öneri: yalnız MS SQL kodlanır, diğer tipler bugünkü davranışta bırakılır ve bu plana bilinçli kısıt olarak yazılır.
2. **`n <= 0` davranışı.** Öneri: limit ifadesi üretilmez, bugünkü (limitsiz) davranış korunur; `retryQuantity == 0` çağrısı ayrı bir işte düzeltilir. Alternatif: `n <= 0` iken fetch hiç çağrılmaz (retry çekilmez) — bu bir davranış düzeltmesidir ve dialing kompozisyonunu etkiler. Onay gerekiyor.
3. **Kapsam: Test butonu ve Data ekranı.** `filtersTest` (Test butonu, limit 10) kapsama alınsın mı? Öneri: evet — vakada 12 kez timeout aldı ve UI'ı kilitledi. `fetchDataByFilters` / `countDataByFilters` (Data ekranı) da eklenecek mi, yoksa bu planda dışarıda mı kalsın?
4. **Üretim doğrulaması.** Sort Conditions'ı deploy sonrası hangi kampanyalarda, kim, hangi pencerede geri açacak? Kapanış kriteri buna bağlı. Ayrıca test ortamında memory grant baskısı olmadığı için kabul, plan/grant karşılaştırmasıyla mı yapılacak?
5. **Deploy hattı.** PR hedefi `develop` mi? Bu düzeltme hangi release'e alınacak, hotfix akışı gerekiyor mu? (Üretimde workaround devrede olduğu için `Öncelik: yüksek` yazıldı; hotfix ise plana `Öncelik: hotfix` işlenecek.)

## Bilinen Pre-existing Test Hataları (bu iş kaynaklı DEĞİL)

Tam suite'te çıkan 8 problem, değişiklikler `git stash` ile geçici olarak çıkarılıp aynı sınıflar tekrar koşularak **bu iş öncesinde de var oldukları kanıtlandı**:

- `com.powerdialer.engine.acd.DefaultFindMatchingUserStrategyTest` — 5 failure, `expected:<User [id=N...]> but was:<null>` (ACD skill/idle eşleştirme). Değiştirilen alanlarla teması yok.
- `com.powerdialer.engine.dialer.state.StopStateTest` — 3 error, `NotAMockException: Argument should be a mock, but is: class java.lang.Class` (`StopStateTest.java:168`), üçü de schedule testleri (`shouldStartWithSchedule`, `shouldNotStartIfScheduleIsNotForToday`, `shouldNotStartWhenScheduleFromIsAfterTill`). PowerMock/Mockito harness kullanımı kaynaklı.

Aynı stash'li koşumda `CountCompletedCallsFilterExpansionTest` 16/16 geçti; yani o dosyadaki yardımcı metot güncellemesi parametreli yola geçişin doğal ve asgari karşılığıdır.

Bu iki sınıf ayrı bir işte ele alınmalı; bu planın kapanışını bloklamıyorlar.

## Notlar

- **Repo hijyen bulgusu (bu iş kapsamı dışı):** `docs/history/README.md` ve 5 history dosyası (`02.07.2026.md`, `03.07.2026.md`, `05.06.2026.md`, `10.07.2026.md`, `25.06.2026.md`) çözülmemiş conflict marker'ı içeriyor (`AUTO GENERATED BY CONFLICT EXTENSION`). AGENTS.md history standardı tek taraflı conflict çözümünü yasakladığı için dokunulmadı; README indeksine bu günün satırı conflict bloğu bozulmadan sona eklendi. Ayrı bir docs-only iş olarak temizlenmesi öneriliyor (README'de her iki satır da meşru: `09.07.2026.md` ve `10.07.2026.md` dosyalarının ikisi de mevcut).
- DB tarafında deploy'suz iki seçenek DBA ile masada: default resource pool'da `REQUEST_MAX_MEMORY_GRANT_PERCENT` düşürülmesi ve `PARAMETERIZATION FORCED` (bu planın 2. maddesinin DB tarafı karşılığı; sniffing riski DBA kararı).
- DBA'nın "sorgulara NOLOCK ekleyin" talebi reddedildi: campaign datasource'u zaten `TRANSACTION_READ_UNCOMMITTED` ile açılıyor (`DBEngine.java:118-120`), yani NOLOCK semantiği mevcut; ayrıca bekleme `LCK_*` değil `RESOURCE_SEMAPHORE`. Doğrulama: `SELECT session_id, transaction_isolation_level FROM sys.dm_exec_sessions WHERE login_name = 'CAMPAIGN_DB_USER'` → `1` (ReadUncommitted).
