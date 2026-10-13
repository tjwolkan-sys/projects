# FEAT İlk TDD Karakterizasyon Testleri

## Bağlam

Projede Java backend ve AngularJS frontend için ilk TDD testlerinin yazılması istendi. Kapsam geniş tutulmayacak; ancak üretim güvenliği açısından önemli iki contract seçilecek.

## İş Etiketi

feat

Bu iş yeni ürün davranışı eklemiyor; geliştirme standardını başlatan ilk test kapsama örneklerini oluşturuyor. Bu nedenle `feat` etiketiyle sınıflandırıldı.

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java`
- `gbpowerdialer/src/main/java/com/powerdialer/model/Call.java`
- `gbpowerdialer/src/main/webapp/js/app.js`
- `gbpowerdialer/src/main/webapp/WEB-INF/wro.xml`
- `gbpowerdialer/src/test/java/tests`
- `docs/execution-plan/21.04.2026`

## Mevcut Davranış

- `Campaign` runtime counter'ları `AtomicInteger` ile tutulur.
- `activeCalls`, `ringingCalls` ve `inCalls` decrement metodları sıfırın altına düşmemeye çalışır.
- `callsInProgress` bir `Set<String>` olduğu için aynı call id tekrar eklense de tek kayıt olarak tutulur.
- AngularJS route tanımları `app.js` içindedir.
- AngularJS bundle sırası `wro.xml` içindeki `global` group ile belirlenir.

## Beklenen Davranış

- İlk Java karakterizasyon testi, campaign runtime counter'larının negatifleşmeme ve duplicate call id davranışını kilitlemeli.
- İlk AngularJS contract testi, `/campaignList` route contract'ını ve kritik WRO yükleme sırasını kilitlemeli.
- Testler üretim kodunu değiştirmeden mevcut davranışı kayıt altına almalı.

## Kod Okuma Bulguları

- `Campaign.decrementActiveCalls`, `Campaign.decrementRingingCalls` ve `Campaign.decrementInCallsSize` counter sıfırdan büyükse decrement yapıyor.
- `Campaign.addCallInProgress` call id değerini `callsInProgress` set'ine ekliyor.
- `app.js` içinde `/campaignList` route'u `pages/campaignList` template'i ve `campaignCtrl as campaign` controller alias'ı ile bağlı.
- `wro.xml` içinde `services.js`, `app.js`, `campaignFactory.js` ve `campaignCtrl.js` sırası frontend açılışı için kritik.

## Riskler

- Bu testler browser içinde AngularJS çalıştırmaz; frontend için route ve WRO contract seviyesinde koruma sağlar.
- Maven bu ortamda PATH'te görünmüyor; testler yazılacak fakat yerel çalıştırma komutu raporlanacak.
- Java testi runtime counter'ların tamamını kapsamaz; sadece ilk güvenlik davranışını kilitler.

## TDD Stratejisi

- Java için `tests.campaign` domain altında karakterizasyon testi yazılacak.
- AngularJS için `tests.angular.routing` domain altında contract testi yazılacak.
- Üretim kodu değiştirilmeyecek.
- Hedefli komutlar:
  - `mvn -Dtest=CampaignRuntimeCountersCharacterizationTest test`
  - `mvn -Dtest=CampaignRoutesAndBundleContractTest test`

## Önerilen Çözüm Yönü

- `CampaignRuntimeCountersCharacterizationTest` ile negatifleşmeme, call type bazlı ringing counter'ları ve duplicate calls-in-progress davranışı korunur.
- `CampaignRoutesAndBundleContractTest` ile `/campaignList` route'u ve WRO yükleme sırası korunur.

## Execution Plan Önerisi

Geliştirici "hadi yapalım" dediği için execution plan oluşturulacak ve uygulama `docs/execution-plan/21.04.2026/FEAT-ILK-TDD-KARAKTERIZASYON-TESTLERI.md` üzerinden yürütülecek.
