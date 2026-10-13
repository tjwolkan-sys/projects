# FEAT Fresh Data Monitoring

## Bağlam

Task `283218` kapsamında monitoring ekranında callback list ve retry list yanında hiç aranmamış fresh data sayısının da görünmesi isteniyor. Mevcut Dialer ekranında `List Remaining`, `Retry List` ve `Callback List` değerleri var; `List Remaining` tüm kalan datayı kapsadığı için fresh data ayrı takip edilemiyor.

## İş Etiketi

`feat`

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java`
- `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dto/CampaignListDTO.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallProducer.java`
- `gbpowerdialer/src/main/webapp/js/controllers/campaignCtrl.js`
- `gbpowerdialer/src/main/webapp/js/localization/localization_*.js`
- `gbpowerdialer/src/test/java/tests/campaign/CampaignRuntimeCountersCharacterizationTest.java`
- `gbpowerdialer/src/test/java/com/powerdialer/api/CampaignsResourceTest.java`

## Mevcut Davranış

- Campaign monitoring grid'i `campaign/realtimeInfo` endpoint'inden gelen `CampaignListDTO` alanlarını gösteriyor.
- Grid'de `List Remaining`, `Retry List` ve `Callback List` kolonları var.
- `CallProducer`, kampanya çalışırken `actualListSize`, `totalListSize`, `retryListSize`, `callbackListSize` ve `listRemaining` değerlerini hesaplayıp `Campaign` runtime alanlarına yazıyor.
- `CampaignDbOperations.countRetryListSize` tamamlanmamış ve `retry_date IS NOT NULL` olan kayıtları sayıyor.
- `CampaignDbOperations.countCallbackListSize` tamamlanmamış ve `callback_date IS NOT NULL` olan kayıtları sayıyor.
- `List Remaining`, completed olmayan tüm uygun kayıtları kapsadığı için fresh data sayısı ayrı response alanı ve UI kolonu olarak bulunmuyor.

## Beklenen Davranış

- Monitoring ekranında retry ve callback listeleri yanında `Fresh Data` sayısı ayrı bir kolon olarak gösterilecek.
- Fresh data, filtreler dikkate alınarak tamamlanmamış ve henüz fetch edilmemiş, retry/callback adayı olmayan kayıtları sayacak.
- Fresh data koşulu:
  - `complete_date IS NULL`
  - `is_fetched IS NULL`
  - `callback_date IS NULL`
  - `retry_history IS NULL` veya boş string
- Campaign üzerinde `applyFilterRetries` aktifse retry/callback listelerinde olduğu gibi campaign filtreleri fresh data sayımına da eklenecek.
- Response shape geriye uyumlu kalacak; mevcut alanlar değişmeyecek, yalnız yeni `freshDataListSize` alanı eklenecek.

## Kod Okuma Bulguları

- `CampaignResource.realtimeInfo` ve `CampaignController.realtimeInfo`, `campaignService.realTimeInfo` üzerinden `CampaignListDTO` döndürüyor.
- `CampaignService.realTimeInfo`, `dialerFacade.getCampaignsRealtimeInfo(ids)` çıktısını Dozer ile `CampaignListDTO`'ya çeviriyor.
- `CampaignListDTO` alanları UI tarafında doğrudan merge edilerek grid satırlarına yazılıyor.
- `campaignCtrl.js` kolon listesinde `listRemaining`, `retryListSize` ve `callbackListSize` ardışık tanımlı; yeni kolon aynı bölgede konumlandırılmalı.
- `resetCampaign` stopped/deleted/permission dışı durumlarda runtime metrikleri sıfırlıyor; yeni alan burada da sıfırlanmalı.
- Localization key'leri `localization_tr.js`, `localization_en.js`, `localization_ru.js`, `localization_ua.js` dosyalarında ayrı tutuluyor.
- Mevcut testlerde `CampaignRuntimeCountersCharacterizationTest`, Campaign -> `CampaignListDTO` mapping contract'ını; `CampaignsResourceTest`, realtime JSON response contract'ını kontrol ediyor.

## Riskler

- Fresh data sayımı campaign result table üzerinde dinamik SQL ile yapılacak; SQL Server/Oracle uyumluluğu ve alias kullanımı mevcut helper'larla korunmalı.
- Monitoring polling sırasında hesaplanan metrik runtime state'e yazıldığı için geniş tablo sayımlarının periyodik maliyeti artabilir.
- `retry_history` boşluk, null ve boş string varyasyonları üretim datasında farklı görünebilir; ilk uygulamada null ve boş string contract'ı testle kilitlenmeli.
- Response field adı değişirse AngularJS grid ve dış API tüketicileri etkilenebilir; mevcut field'lar değiştirilmemeli.
- Outbound IVR callback sayımındaki özel durum fresh data sayımı için doğrudan kopyalanmamalı; fresh data campaign result table contract'ına bağlı kalmalı.

## Performans Etkisi

- Yeni metrik, producer'ın mevcut periyodik liste sayımı sırasında ek bir `COUNT(*)` sorgusu anlamına gelir.
- Sorgu mevcut retry/callback count query'leriyle aynı pattern'i kullanmalı; hot path içinde row fetch, geniş payload serialization veya Java tarafında koleksiyon taraması eklenmemeli.
- Fresh data sayımı polling endpoint'inde anlık DB sorgusu olarak değil, producer'ın runtime counter güncelleme akışında hesaplanan Campaign alanı olarak DTO'ya taşınmalı.

## TDD Stratejisi

- Önce `CampaignDbOperations` fresh data where-clause davranışı için dar regression/characterization testi eklenecek. DB bağlantısı açmadan mock `DBEngine` veya mevcut private helper test pattern'iyle üretilen SQL contract'ı doğrulanacak.
- `CampaignRuntimeCountersCharacterizationTest` yeni `freshDataListSize` alanının `Campaign` -> `CampaignListDTO` mapping'inde taşındığını doğrulayacak.
- `CampaignsResourceTest` realtime response JSON içinde `freshDataListSize` alanını doğrulayacak.
- AngularJS tarafında kolon/template contract'ı için mevcut test altyapısı uygunsa Java/JUnit frontend contract testi eklenecek; environment veya pattern yetersizse en azından localization ve grid field kullanımını hedefli statik contract testiyle kilitlemek değerlendirilecek.

## Önerilen Çözüm Yönü

1. `Campaign` ve `CampaignListDTO` içine `freshDataListSize` alanı ekle.
2. `CampaignDbOperations` içine `countFreshDataListSize(Campaign campaign)` ekle; mevcut `createCountSelectQueryForResultTable`, `getResultAliasWithDot` ve `createWhereFromFilters` helper'larını kullan.
3. `CallProducer` liste metriklerini güncellediği yerde fresh data sayısını hesaplayıp Campaign runtime alanına yaz.
4. `campaignCtrl.js` grid'ine `Fresh Data` sayısı kolonunu ekle ve `resetCampaign` içinde alanı sıfırla.
5. Dört localization dosyasına `campaign.freshDataListSize` key'i ekle.
6. Hedefli Java testleri ve mümkünse frontend contract testiyle response/UI field contract'ını doğrula.

## Açık Sorular ve Netleştirme İhtiyacı

Açık soru yok. `is_fetch` ifadesi mevcut kod contract'ındaki `is_fetched` result kolonuna karşılık kabul edildi; callback date alanı mevcut `callback_date`, retry history alanı mevcut `retry_history` olarak ele alınacak.

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyor. Execution plan `docs/execution-plan/05.05.2026/FEAT-283218-FRESH-DATA-MONITORING.md` altında, bu analiz incelenip geliştirici nihai planı onayladıktan sonra oluşturulacak. Plan, TDD test planını ve başarılı test kapanış kriterini içerecek.
