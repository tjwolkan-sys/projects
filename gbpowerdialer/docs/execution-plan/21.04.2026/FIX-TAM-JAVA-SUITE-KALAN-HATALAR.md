# FIX Tam Java Suite Kalan Hatalar Execution Plan

## Amaç

API resource context düzeltmesinden sonra kalan Maven test failure/error kümelerini tek plan altında, üretim davranışını genişletmeden ve offline Maven doğrulamasıyla kapatmak.

## TDD Test Planı

- Kırmızı doğrulama: kalan fail sınıfları hedefli Maven komutuyla çalıştırılır.
- Yeşil doğrulama: aynı hedefli Maven komutu başarılı olmalıdır.
- Kapanış doğrulaması: `mvn -o test` tam Java suite başarılı olmalıdır.

## Adımlar

- [x] Kalan failure/error sınıfları hedefli Maven komutuyla kırmızı olarak tekrar doğrulanacak.
- [x] `MirrorScheduler` job log başlangıcı sequence/fixture yokluğunda sync akışını bloklamayacak şekilde güvenli hâle getirilecek; `group_id` contract'ı test fixture'ında korunacak.
- [x] `AutomationCheckHandler` null-safe enabled guard ile çalışacak ve automation test fixture'ı gerçek enabled campaign davranışını temsil edecek.
- [x] `ScheduledCampaignRunner` STOP, RESET, START, PAUSE önceliğini tek aksiyon çalışacak şekilde uygulayacak.
- [x] `StopStateTest` ve `DialerFacadeTest` fixture'ları `CampaignResultJobService` running job kontrolünü karşılayacak.
- [x] `DNCEngineTest` yeni `getByListId` DNC phone number contract'ına göre güncellenecek.
- [x] `LSCEngine` null `Call.rawData` durumunda timeout/rollback hazırlığında NPE üretmeyecek.
- [x] Predictive Gamma test sınıfları test runner initialization error üretmeyecek şekilde güncel smoke testlerle kapatılacak.
- [x] Redis publisher/subscriber test fixture'ları async executor, context ve enrichment bağımlılıklarını izole edecek.
- [x] `WebphoneSubscriberTest` DNC identifier senaryosu campaign default DNC contract'ıyla uyumlu olacak.
- [x] `CallInMapperTest` duration birim beklentisi production mapper contract'ıyla uyumlu olacak.
- [x] Hedefli Maven test komutu yeşil çalıştırılacak.
- [x] `mvn -o test` tam Java suite çalıştırılacak.
- [x] Plan ve history doğrulama sonuçlarıyla güncellenecek.

## Doğrulama Sonucu

- Kırmızı doğrulama: `mvn -o -Dtest=MirrorSchedulerTest,AutomationCheckHandlerTest,ScheduledCampaignRunnerTest,DNCEngineTest,LSCEngineTest,DialerFacadeTest,StopStateTest,PredictiveGammaCallCalculatorTest,PredictiveGammaCallManagerTest,CallInMapperTest,InteractionNotifierPublisherTest,UserSubscriberTest,WebphoneSubscriberTest test` ilk koşuda 123 test çalıştırdı; 13 failure, 22 error, 0 skipped.
- Hedefli yeşil doğrulama: aynı hedefli komut 123 test çalıştırdı; 0 failure, 0 error, 0 skipped.
- Tam suite doğrulaması: `mvn -o test` 542 test çalıştırdı; 0 failure, 0 error, 0 skipped.
- Maven koşularında `.codex/TOOL-PATHS.local.properties` standardı kullanıldı ve offline mod korundu.
- Oracle JDBC/PowerMock kaynaklı MBean `LinkageError` uyarısı loglandı; Surefire sonucunu bozmadı ve build başarılı tamamlandı.

## Kapanış Kriteri

Bu plan, hedefli kalan test sınıfları ve tam Java suite offline Maven ile başarılı olmadan kapatılmış sayılmaz. Bir test toolchain/cache nedeniyle çalıştırılamazsa plan doğrulama beklemede kalır ve eksik artifact açık yazılır.
