# FIX API Resource ObjectMapper Context

## İş Etiketi

fix

## Bağlam

`mvn -o test` koşusunda API resource testlerinin bir bölümü Spring test context açılışında hata aldı. Hata, test edilen controller davranışından önce context oluşturma aşamasında oluştuğu için API contract testleri gerçek sonucu raporlayamıyor.

Etkilenen test sınıfları:

- `gbpowerdialer/src/test/java/com/powerdialer/api/CallResourceTest.java`
- `gbpowerdialer/src/test/java/com/powerdialer/api/CampaignsResourceTest.java`
- `gbpowerdialer/src/test/java/com/powerdialer/api/UserResourceTest.java`

## Mevcut Davranış

Üç test sınıfı kendi nested `@Configuration` test context'ini kuruyor ve `RedisUtil` için mock bean tanımlıyor. Buna rağmen Spring, Mockito mock bean'leri de normal bean gibi post-process ederek legacy `@Autowired` alanlarını doldurmaya çalışıyor. İlk görünen hata `RedisUtil.objectMapper` için `com.fasterxml.jackson.databind.ObjectMapper` bean'inin bulunamamasıydı.

Üretim tarafında `ObjectMapper` bean'i `WebConfig` içinde tanımlı; fakat bu dar test context'leri üretim `WebConfig` sınıfını import etmiyor.

İlk düzeltmeden sonra aynı izolasyon problemi `ACDEngine`, `DynamicBoostManager` ve `CampaignService` mock bean'lerinin iç autowire zincirlerinde de tekrarlandı. Devamında `@PostConstruct` ve `@PreDestroy` lifecycle metodları mock bean'ler üzerinde çalıştırılmaya kalktı. Kök problem, resource testlerinde mock bean'lerin gerçek legacy bağımlılık grafiği ve lifecycle bootstrap'i gibi ele alınmasıdır.

## Beklenen Davranış

API resource test context'leri üretimde mevcut olan `ObjectMapper` bean contract'ını test context içinde de sağlamalıdır. Ayrıca Mockito mock bean'ler gerçek Spring component gibi field injection veya lifecycle bootstrap işlemine sokulmamalıdır. Böylece resource endpoint testleri context hatasına takılmadan gerçek assertion aşamasına geçmelidir.

## Etkilenen Alanlar

- Spring MVC test context kurulumu.
- API resource testleri.
- `RedisUtil` autowire contract'ı.
- Mockito mock bean izolasyonu.
- Resource test context lifecycle post-processor boundary'si.

Üretim kodu, endpoint contract'ı veya Redis davranışı değiştirilmemelidir.

## Riskler

- Test context'e fazla üretim konfigürasyonu almak gereksiz bean yan etkileri yaratabilir.
- `RedisUtil` yerine farklı mock stratejisiyle geniş refactor yapmak mevcut API testlerinin kapsamını gereksiz büyütebilir.
- Sadece test context düzeltmesi yapılacağı için tam test suite'teki diğer mevcut fail'ler bu işin kapsamında kapanmayabilir.

## TDD Stratejisi

1. Hedefli kırmızı doğrulama: `CallResourceTest`, `CampaignsResourceTest` ve `UserResourceTest` offline Maven ile çalıştırılır; context hatası tekrar üretilir.
2. En küçük düzeltme: üç API test context'ine production contract ile uyumlu `ObjectMapper` bean'i eklenir.
3. Test izolasyonu: Mockito mock bean'lerin legacy `@Autowired` alanlarına Spring field injection uygulanması test context'te engellenir.
4. Lifecycle izolasyonu: Resource test context'lerinde `@PostConstruct/@PreDestroy` processor'ı kapatılır; endpoint testleri lifecycle bootstrap davranışını kapsam dışı bırakır.
5. Hedefli yeşil doğrulama: aynı üç API test sınıfı offline Maven ile tekrar çalıştırılır.
6. Kapanış: Execution plan yalnızca hedefli API testleri başarılı olursa tamamlanır; tam suite'teki kalan fail'ler ayrı analizlere bırakılır.
