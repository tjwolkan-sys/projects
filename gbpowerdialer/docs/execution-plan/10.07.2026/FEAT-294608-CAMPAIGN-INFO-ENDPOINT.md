# FEAT Campaign Info (Running + Idle) Harici Endpoint Uygulama Planı

## Analiz Referansı

Bu iş için ayrı `docs/analyse/...` dosyası açılmadı. Analiz kullanıcıyla interaktif yürütüldü; sorun tespiti, tasarım kararları ve auth yöntemi konuşmada netleştirilip onaylandı. Kullanıcı talebi: analiz dosyasını atlayıp doğrudan execution plan ile TDD geliştirmesine geçmek. Bu dosya kendi içinde bağlam, mevcut/beklenen davranış, riskler ve TDD planını taşır.

## İş Etiketi

`feat`

## Onaylanan Plan Özeti

`realtimeInfo` yalnız `DialerFacade.campaignContexts` içinde olan (running) kampanyaları döndürüyor; kampanya running değilse response'a hiç girmiyor. Harici bir uygulamanın running/idle ayrımı olmadan tüm istenen kampanyalar için `CampaignListDTO` alabilmesi gerekiyor (5-10 sn'de bir pollenecek). Mevcut `realtimeInfo` servis metodu hem web `CampaignController` hem de harici `CampaignResource` tarafından paylaşıldığı için **yerinde değiştirilmez**; frontend ve mevcut API kontratı aynen korunur. Bunun yerine harici API boundary'sine (`CampaignResource`) yeni bir endpoint eklenir; running ise context'ten canlı değer, idle ise DB entity + persist edilmiş `CampaignMemento` snapshot'ı (list ekranıyla aynı mantık) ile doldurulur. Auth mevcut `realtimeInfo` ile aynı: role/permission tabanlı `permissionUtil.restrictCampaignList`. Boş `ids` yalnız running döndürür (tüm-DB taraması yapılmaz). Idle değerler memento snapshot'ından gelir; `state` DB entity'sinin persist edilmiş değerinden (Dozer).

## Amaç

Harici bir tüketicinin, kampanya running olsun olmasın, istenen id'ler için tam dolu `CampaignListDTO` listesi alabileceği tek bir read-only endpoint sağlamak; mevcut frontend `realtimeInfo` akışını (1 sn poll + `angular.merge`) ve harici `realtimeInfo` API kontratını hiç değiştirmeden korumak.

## Kapsam Dışı

- Web `CampaignController.realtimeInfo` ve frontend `getRealTimeInfo` polling akışı (dokunulmaz; 1 sn, running-only aynen kalır).
- Mevcut `CampaignService.realTimeInfo` servis metodunun semantiği (dokunulmaz; `CampaignResource.realtimeInfo` ve web controller onu kullanmaya devam eder).
- Boş `ids` ile tüm kampanyaların (running + idle) döndürülmesi — bilinçli olarak kapsam dışı; boş `ids` yalnız running döndürür.
- Frontend değişikliği (yeni endpoint'i tüketen harici uygulama gbpowerdialer repo'su dışındadır) → AngularJS test katmanı bu planda gerekmez.
- Yeni auth mekanizması (token tabanlı `API_TOKEN_HEADER` deseni kullanılmadı; mevcut role/permission deseni seçildi).

## Performans Etkisi

- **Hot path durumu:** Hayır. Endpoint AMI/AGI originate, hangup/result, Redis call event, ACD seçimi veya campaign producer/consumer/originator loop'u değildir. Harici uygulama tarafından 5-10 sn'de bir pollenir (call/tick başına değil).
- **Call/event/tick başına ek maliyet:** Yok.
- **IO/scan/lock/serialization/log etkisi:** Poll başına idle kampanya sayısı kadar bir DB read (`ICampaignDao.getAll(null,false,idleIds)` — id `IN (...)` kriteri, tam tablo taraması değil) + bir batch memento read (`getCampaignMementosByCampaignIds`). Bu, kampanya list ekranının (`/list`) zaten kullandığı akışın aynısıdır; yeni sorgu tipi eklenmez. Running kısım context'ten (in-memory) gelir, ek IO yok. Boş `ids` erken döner, DB'ye hiç gitmez.
- **Azaltım veya ölçüm notu:** 5-10 sn kadans, 1 sn'lik frontend poll'una göre 5-10x daha seyrek. `idleIds` yalnız context'te olmayan istenen id'lerle sınırlıdır (running olanlar dışlanır). Memento read batch'tir (id başına ayrı query yok).

## TDD Planı

- [x] **Service birim testi (yeni davranış):** `CampaignInfoIncludingIdleProjectionTest` (3 test, PASS)
  - [x] running (context) + idle (DB+memento) karışık istekte ikisi de döner; running canlı agent size taşır, idle DB'den yüklenip memento `restore(dto)` uygulanır; DB yalnız idle id için çağrılır.
  - [x] idle kampanyada memento yoksa DTO DB entity'sinden olduğu gibi döner (NPE yok).
  - [x] boş `ids` yalnız running döndürür; DB ve memento hiç çağrılmaz.
- [x] **Resource contract testi:** `CampaignsResourceTest.shouldGetCampaignInfoIncludingIdle` — `GET /api/campaigns/info` `campaignService.campaignInfo(...)`e delege eder, running+idle DTO'ları JSON dizisi olarak döner, `restrictCampaignList` uygulanır.
- [x] **Regression:** mevcut `CampaignRealtimeInfoAgentSizeProjectionRegressionTest` ve `CampaignsResourceTest.shouldGetRealtimeInfo` yeşil kaldı (memento-restore refactor'ı `realTimeInfo` yolunu bozmadı).

## Uygulama Kontrol Listesi

- [x] Onaylanan planın tüm adımları execution plan'a işlendi.
- [x] `CampaignInfoIncludingIdleProjectionTest` yazıldı.
- [x] `CampaignsResourceTest.shouldGetCampaignInfoIncludingIdle` yazıldı.
- [x] `CampaignService.campaignInfo(Long[] ids)` eklendi (`@Transactional(readOnly=true)`): running için `realTimeInfo`, idle için DB entity + memento snapshot; boş `ids` yalnız running.
- [x] `CampaignService.restoreCampaignFromMemento(ListDetailed)` davranış-koruyan şekilde `restoreCampaignsFromMemento(List)` private helper'ına delege edildi (yeni idle akışı da bunu kullanır).
- [x] `CampaignResource.info(...)` endpoint'i eklendi: `GET /api/campaigns/info`, `restrictCampaignList` ile filtrelenir, `realtimeInfo` ile birebir aynı `ids` parse deseni.
- [x] Hedefli testler kırmızıdan yeşile çevrildi (8/8 PASS).
- [x] Hot path etkisi doğrulandı (hot path değil; `Performans Etkisi` tamamlandı).
- [x] Doküman ve history güncellemesi tamamlandı.

## Etkilenen Kod Alanları

- `gbpowerdialer/src/main/java/com/powerdialer/service/CampaignService.java` (yeni `campaignInfo` + `idleCampaignInfo` + memento helper refactor).
- `gbpowerdialer/src/main/java/com/powerdialer/api/CampaignResource.java` (yeni `/info` endpoint).
- `gbpowerdialer/src/test/java/tests/campaign/CampaignInfoIncludingIdleProjectionTest.java` (yeni).
- `gbpowerdialer/src/test/java/com/powerdialer/api/CampaignsResourceTest.java` (yeni test metodu).

## Doğrulama

Hedefli offline Maven (JDK8 + Maven 3.9.15, `.codex/TOOL-PATHS.local.properties`):

```text
mvn -o -s <settings.xml> -Dmaven.repo.local=<repo> -f gbpowerdialer/pom.xml \
  -Dtest=CampaignInfoIncludingIdleProjectionTest,CampaignRealtimeInfoAgentSizeProjectionRegressionTest,CampaignsResourceTest test
```

Sonuç (2026-07-10 15:02, offline JDK8 + Maven 3.9.15):
- `compile`: **BUILD SUCCESS** (780 main + 179 test kaynağı derlendi).
- `CampaignInfoIncludingIdleProjectionTest`: **3/3 PASS**.
- `CampaignsResourceTest`: **4/4 PASS** (yeni `shouldGetCampaignInfoIncludingIdle` + mevcut `shouldGetRealtimeInfo`/`shouldGetCampaigns`/`shouldReturnCampaignWithSchedule`).
- `CampaignRealtimeInfoAgentSizeProjectionRegressionTest`: **1/1 PASS**.
- Toplam: **8/8 PASS**. Frontend değişikliği olmadığından `npm.cmd run test:angular` kapsam dışı.

## Kapanış Kriteri

- [x] Planlanan Java testleri çalıştırıldı ve başarılı oldu (8/8 PASS).
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı: `CampaignService.campaignInfo` (+ `idleCampaignInfo` + memento-restore helper refactor) ve `CampaignResource.info` (`GET /api/campaigns/info`). Mevcut `realTimeInfo` servis metodu, web `CampaignController.realtimeInfo` ve harici `CampaignResource.realtimeInfo` dokunulmadan korundu; frontend akışı etkilenmez. Auth mevcut `realtimeInfo` ile aynı (role/permission tabanlı `restrictCampaignList`). Hedefli Java testleri offline yeşil. Değişiklikler commit edilmedi; commit/PR hijyeni geliştiriciye bırakıldı.

## Riskler ve Geri Dönüş

- Yeni endpoint additive'dir; mevcut `realtimeInfo` (web + API) ve `realTimeInfo` servis metodu dokunulmadığından frontend ve mevcut API kontratı için regresyon riski yok. Sorun olursa `/info` endpoint'i kaldırılarak eski duruma dönülür.
- `restoreCampaignFromMemento` refactor'ı davranış-koruyandır (aynı gövde private helper'a taşındı, null-guard eklendi); `/list` memento restore akışı için risk düşük, regression testi ile kapatıldı.
- Idle branch DB read + memento read ekler; 5-10 sn kadansta ve `/list` ile aynı sorgu tipinde olduğundan performans riski düşük.

## Açık Sorular

Açık soru yok. Auth (role/permission tabanlı `restrictCampaignList`), idle snapshot kaynağı (memento), boş `ids` davranışı (yalnız running) ve endpoint adı (`/api/campaigns/info`) kullanıcı tarafından onaylandı.
