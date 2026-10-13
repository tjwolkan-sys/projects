# FIX Data Manipulation Null Kolon Filtresi Change/Fix Planı

## Bağlam

PowerDialer Data Manipulation ekranında kullanıcı koşul girerken bir kolonun `NULL` olmasını filtreleyemiyor. Örnek ihtiyaç:

```sql
SELECT *
FROM [gbWebPhone_Turkcell_Camp].[dbo].[TC_BRY_TAHS_HUKUK_ONCESI] WITH (NOLOCK)
WHERE is_disabled = 0
  AND telephony_result_code = 10
  AND ListID = '521242'
  AND user_result_code IS NULL
```

Ekran örneğinde `user_result_code` kolonunun koşul operatörü dropdown'ında `IS NULL` seçeneği bulunmadığı için aynı sorgu ekran üzerinden kurulamaz.

## İş Etiketi

fix

## Öncelik

normal

## Etkilenen Alanlar

- AngularJS route: `#/campaignDataManipulationAndUpdate`
- Template: `gbpowerdialer/src/main/webapp/pages/campaignDataManipulationAndUpdate.jsp`
- Controller: `gbpowerdialer/src/main/webapp/js/controllers/campaignDataManipulationAndUpdateCtrl.js`
- Backend enum contract: `gbpowerdialer/src/main/java/com/powerdialer/util/Constants.java`
- Yeni ekran servis akışı: `gbpowerdialer/src/main/java/com/powerdialer/service/CampaignRecordService.java`
- Eski data manipulation API akışı: `CampaignDataManipulationController -> CampaignDataManipulationService -> CampaignDbOperations -> FilterUtils`
- Test alanları: `gbpowerdialer/src/test/java/tests/angular/campaign`, `gbpowerdialer/src/test/java/tests/datamanipulation`, mevcut `FilterUtilsTest`

## Mevcut Davranış

- UI operatör dropdown'ı `EQUAL`, `NOTEQUAL`, `LIKE`, `IN` gibi seçenekler gösteriyor; `IS NULL` ve `IS NOT NULL` yok.
- Template `f.condition` alanını kullanıyor, ancak controller'daki null cleanup kodu `f.operator` alanını kontrol ediyor.
- `ctrl.addFilter()` yeni satırı `{ field: '', operator: '=', value: '' }` olarak açıyor; template ise `condition` bekliyor.
- `Constants.FILTER_CONDITION` enum'unda `ISNULL` ve `ISNOTNULL` var, fakat `parse()` metodu bu değerleri kabul etmiyor.
- `CampaignRecordService.prepareConditionFilters()` null check operatörlerini SQL koşuluna çevirmiyor; değer boşsa koşulu sessizce atlıyor.
- `CampaignRecordService.buildWhereClauseWithParams()` priority operasyonlarında her koşul için `?` parametresi üretme eğiliminde; null check için parametre üretilmemeli.
- Eski `DataManipulateRequest` tabanlı API'de `CampaignDataManipulationService.validateFilter()` `EQUAL/NOTEQUAL` dışındaki koşullarda value zorunlu tuttuğu için `ISNULL/ISNOTNULL` value olmadan geçemez.
- `FilterUtils` value `"null"` ve `EQUAL/NOTEQUAL` ile null koşul üretebiliyor, ancak explicit `ISNULL/ISNOTNULL` operatörü güvenli şekilde desteklenmiyor.

## Beklenen Davranış

- Data Manipulation ekranında operatör listesinde `IS NULL` ve `IS NOT NULL` seçenekleri görünmeli.
- Bu operatörler seçildiğinde value girişi gerekmemeli ve backend'e gereksiz value taşınmamalı.
- `/campaign-records/filter-test` ve `/campaign-records/fetch` payload'larında `condition: "ISNULL"` ve `condition: "ISNOTNULL"` kabul edilmeli.
- `CampaignRecordService` şu koşulları üretebilmeli:
  - `user_result_code IS NULL`
  - `user_result_code IS NOT NULL`
- Eski `/campaignDataManipulation/read|update|delete` API'leri de `ISNULL`, `ISNOTNULL`, `is null`, `is not null`, `IS_NULL`, `IS_NOT_NULL` varyantlarını parse edebilmeli.
- Null check koşulları prepared statement parametresi üretmemeli.

## Kod Okuma Bulguları

- Route `gbpowerdialer/src/main/webapp/js/app.js` içinde `/campaignDataManipulationAndUpdate` olarak tanımlı.
- Template operatör dropdown'ı `ng-model="f.condition"` kullanıyor.
- Controller `testFilters()` ve `fetchRecords()` içinde null cleanup için `f.operator === 'IS_NULL' || f.operator === 'IS_NOT_NULL'` kontrolü yapıyor; bu alan template contract'ıyla uyuşmuyor.
- `CampaignRecordFilterDTO.FilterRow.condition` tipi `Constants.FILTER_CONDITION`; Jackson default enum name bekler, bu nedenle UI tarafında enum name olan `ISNULL`/`ISNOTNULL` değerleri kullanılmalı.
- `Constants.FILTER_CONDITION.toString()` SQL karşılığını döndürüyor; `CampaignRecordService` bu string'i tekrar `parse()` ile enum'a çevirdiği için parse metodunun `is null` ve `is not null` desteklemesi gerekiyor.
- `FilterUtils.createWhereForPredefinedType()` null value akışında sadece `EQUAL` ve `NOTEQUAL` için doğru `IS` / `IS NOT` üretimi yapıyor.
- Mevcut `FilterUtilsTest` null value davranışını `EQUAL + "null"` ve `NOTEQUAL + "null"` ile kilitlemiş durumda; explicit null check operatörleri için ek regression testi gerekli.

## Root Cause / Değişiklik Gerekçesi

Kök neden tek katmanda değil:

- UI seçenek listesi null operatörünü sunmuyor.
- Controller payload temizliği yanlış property adı ve farklı operatör token'ları kullanıyor.
- Backend enum parse contract'ı enum'da var olan null operatörlerini kabul etmiyor.
- SQL üretim noktaları null operatörlerinin valuesuz çalışmasını özel durum olarak ele almıyor.

Bu yüzden sadece dropdown'a seçenek eklemek eksik çözüm olur; UI, DTO enum contract'ı, parse ve SQL builder aynı işte dar kapsamlı olarak güncellenmeli.

## Riskler

- Dynamic SQL üretimi kampanya veri tablolarında çalıştığı için yanlış koşul üretimi kayıt listeleme, priority ve update/delete akışlarını etkileyebilir.
- `IN/NOT IN` ve mevcut `EQUAL + null` davranışı geriye uyum için korunmalı.
- Null check için yanlışlıkla `?` parametresi üretilirse prepared statement parametre sayısı SQL ile uyuşmaz.
- AngularJS template/controller contract'ı bozulursa mevcut kayıt getirme ve filtre test ekranı çalışmayabilir.
- Eski data manipulation API'leri ekran dışından kullanılıyor olabilir; enum parse ve validation geriye uyumlu olmalı.

## TDD Stratejisi

- Java regression testleriyle `FILTER_CONDITION.parse()` null token varyantlarını kilitle.
- `FilterUtilsTest` veya yeni `tests.datamanipulation` regression testiyle explicit `ISNULL/ISNOTNULL` koşullarının parametresiz SQL ürettiğini kanıtla.
- `CampaignRecordService` SQL koşul üretimi için mümkünse yeni küçük package-private helper veya reflection ile null check regression testi yaz.
- AngularJS template/controller contract'ı için Java/JUnit contract testi yaz:
  - Template içinde `ISNULL` ve `ISNOTNULL` option değerleri bulunur.
  - Controller null condition kontrolünde `f.condition` kullanır, `f.operator` kullanılmaz.
  - `ctrl.addFilter()` default condition contract'ı `EQUAL` ile uyumludur.
- Kod değişikliğinden sonra hedefli Maven testleri ve frontend contract testi çalıştırılır.
- Frontend controller davranışı gerçek AngularJS injector ile izole edilebilir hâle getirilirse ek olarak `npm.cmd run test:angular` çalıştırılır; değilse Java/JUnit contract test sonucu ve manuel ekran doğrulama adımları raporlanır.

## Açık Sorular

Yok.

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [x] `.codex/TOOL-PATHS.local.properties` doğrulama komutları öncesi kullanıldı.
- [x] Null operatör parse davranışı için başarısız Java regression testi yaz.
- [x] Dynamic SQL null check üretimi için başarısız Java regression testi yaz.
- [x] AngularJS template/controller contract'ı için başarısız Java/JUnit contract testi yaz.
- [x] UI dropdown'a `IS NULL` / `IS NOT NULL` seçeneklerini ekle.
- [x] Controller payload cleanup ve `addFilter()` default condition contract'ını düzelt.
- [x] `Constants.FILTER_CONDITION.parse()` null token varyantlarını destekleyecek şekilde genişlet.
- [x] `CampaignRecordService` null check koşullarını valuesuz SQL'e çevirecek şekilde güncelle.
- [x] Priority operation path'inde null check koşullarının parametresiz çalışmasını sağla.
- [x] Eski `CampaignDataManipulationService` validation ve `FilterUtils` explicit null operator davranışını geriye uyumlu düzelt.
- [x] Hedefli Maven testlerini çalıştır.
- [x] Gerekirse AngularJS test veya manuel ekran doğrulamasını çalıştırıp sonucu yaz.
- [x] Plan checkbox'ları, doğrulama ve history kaydını tamamla.

## Performans Etkisi

- Hot path durumu: Bu ekran operatör/admin data manipulation akışıdır; AMI/AGI, Redis event, campaign originator veya scheduler tick hot path'i değildir.
- Call/event/tick başına ek maliyet: Yok.
- IO/scan/lock/serialization/log etkisi: Yeni IO veya lock eklenmeyecek. Mevcut filtreli DB sorgusunun koşul operatörü genişletilecek.
- Azaltım veya ölçüm notu: Null check SQL koşulu parametresiz ve sabit maliyetli üretilecek; collection scan veya verbose log eklenmeyecek.

## Doğrulama

Kırmızı test:

```powershell
C:/Users/GLB90048674/javatools/maven-bin-dist/bin/mvn.cmd -o -s C:/Users/GLB90048674/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90048674/.m2/repository -Dtest=DataManipulationNullFilterRegressionTest,CampaignRecordNullFilterSqlRegressionTest,CampaignDataManipulationNullFilterContractTest test
```

Sonuç: Beklenen şekilde başarısız oldu; 7 test çalıştı, 6 failure ve 1 parse error görüldü. Hatalar UI null option eksikliği, `f.operator` contract drift'i, `FILTER_CONDITION.parse("is null")` parse hatası ve null check SQL'lerinde gereksiz placeholder üretimini doğruladı.

Yeşil test:

```powershell
C:/Users/GLB90048674/javatools/maven-bin-dist/bin/mvn.cmd -o -s C:/Users/GLB90048674/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90048674/.m2/repository -Dtest=DataManipulationNullFilterRegressionTest,CampaignRecordNullFilterSqlRegressionTest,CampaignDataManipulationNullFilterContractTest test
```

Sonuç: Başarılı; 8 test, 0 failure, 0 error. Frontend değişikliği Java/JUnit AngularJS contract testiyle doğrulandı.

Genişletilmiş hedefli yeşil test:

```powershell
C:/Users/GLB90048674/javatools/maven-bin-dist/bin/mvn.cmd -o -s C:/Users/GLB90048674/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90048674/.m2/repository -Dtest=FilterUtilsTest,DataManipulationNullFilterRegressionTest,CampaignRecordNullFilterSqlRegressionTest,CampaignDataManipulationNullFilterContractTest test
```

Sonuç: Başarılı; 17 test, 0 failure, 0 error. Mevcut `FilterUtilsTest` ile `EQUAL + null` / `NOTEQUAL + null` geriye uyumu da doğrulandı.

Hedefli doğrulama komutları:

```powershell
# .codex/TOOL-PATHS.local.properties değerleriyle, offline Maven
mvn -o -Dtest=FilterUtilsTest,DataManipulationNullFilterRegressionTest,CampaignDataManipulationNullFilterContractTest test

# AngularJS gerçek unit test eklenirse
npm.cmd run test:angular
```

Maven/NPM komutları çalıştırılmadan önce local tool path dosyası okunacak; `MAVEN_CMD`, `MAVEN_SETTINGS`, `MAVEN_REPO_LOCAL`, `JAVA_HOME` ve `NPM_CMD` varsa gerçek komut bu değerlere göre kurulacak.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] UI'da `IS NULL` ve `IS NOT NULL` seçilebiliyor.
- [x] `user_result_code IS NULL` koşulu ile kayıt getirme backend SQL üretiminde destekleniyor.
- [x] Eski `EQUAL + null` ve `NOTEQUAL + null` davranışı bozulmadı.
- [x] Null check koşulları prepared statement parametresi üretmiyor.
- [x] Planlanan hedefli testler çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı. Final yanıtta kısa Test Özeti sunulacak.
