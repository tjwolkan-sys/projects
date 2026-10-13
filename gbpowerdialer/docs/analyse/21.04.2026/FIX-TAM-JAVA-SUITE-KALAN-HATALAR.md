# FIX Tam Java Suite Kalan Hatalar

## İş Etiketi

fix

## Bağlam

API resource test context düzeltmesinden sonra `mvn -o test` koşusu artık Spring `ObjectMapper` context hatasına takılmadan daha ileri gidiyor. Kalan hatalar legacy test fixture uyumsuzlukları, null güvenliği eksikleri ve scheduler aksiyon önceliği etrafında toplanıyor.

Son tam koşu özeti:

- `mvn -o test`: 542 test çalıştı; 13 failure, 22 error, 0 skipped.
- API resource context kaynaklı 12 error kapanmış durumda.

## Mevcut Davranış

Kalan hatalar şu kümelerde görülüyor:

- `MirrorSchedulerTest`: job log sequence sorgusu test fixture'ında campaign target JSON'u ile karışıyor; scheduler sync ve swap aşamasına ulaşamıyor.
- `AutomationCheckHandlerTest`: testler automation kurallarını doğruluyor, fakat mock `Campaign` üzerinde `scheduleAutomationEnabled` true değil.
- `ScheduledCampaignRunnerTest`: STOP ve PAUSE aynı anda true olduğunda STOP çalıştıktan sonra PAUSE da tetiklenebiliyor.
- `StopStateTest` ve `DialerFacadeTest`: `CampaignResultJobService` test fixture'ında yok, `StopState` start öncesi running job kontrolünde NPE alıyor.
- `DNCEngineTest`: üretim kodu artık DNC numaralarını `DNCPhoneNumberService.getByListId` üzerinden okuyor; testler eski `getPhoneNumbersByList` stub'ında kalmış.
- `LSCEngineTest`: bazı timeout senaryolarında `Call.rawData` null ve LSC retry öncesi called number rollback hazırlığı NPE üretiyor.
- `PredictiveGammaCallCalculatorTest` ve `PredictiveGammaCallManagerTest`: sınıflar `*Test` olarak keşfediliyor ama tüm test metodları yorum satırında kaldığı için Mockito runner initialization error veriyor.
- `InteractionNotifierPublisherTest`: publisher, testte stub'lanmayan `UserService` üzerinden user enrichment yaparken NPE alıyor.
- `UserSubscriberTest`: production subscriber async executor ile çalışıyor; test fixture'ında executor ve dialer context mock'ları eksik.
- `WebphoneSubscriberTest`: DNC identifier senaryosu campaign default DNC olmadan çalışıyor; production davranışında default DNC yoksa DNC update yapılmıyor.
- `CallInMapperTest`: `CallInDTO` içindeki `call_duration` production mapper'da saniye olarak hesaplanıyor; test beklentisi milisaniyede kalmış.

## Beklenen Davranış

Tam Java suite test runner, dependency indirmeden ve gerçek Redis/DB/AMI bağlantısı kurmadan offline Maven ile çalışmalıdır. Kalan testler ya mevcut production contract'a uygun fixture ile yeşile dönmeli ya da üretimde gerçek NPE/aksiyon önceliği riski olan yerlerde küçük ve geriye uyumlu düzeltme yapılmalıdır.

## Etkilenen Alanlar

- `MirrorScheduler`: job log toleransı ve result code mirror chunking testi.
- Kampanya scheduler zinciri: automation enabled guard ve scheduled action priority.
- Campaign lifecycle start boundary'si: `StopState` running job kontrolü test fixture'ı.
- DNC ve LSC runtime boundary'leri.
- Redis interaction notifier publisher/subscriber test izolasyonu.
- Predictive Gamma test keşfi.
- Interaction notifier DTO mapper duration contract'ı.

## Riskler

- Mirror tablosundaki `group_id` kolonu production contract olabilir; sadece testi geçirmek için kaldırılmamalıdır.
- Scheduler aksiyon önceliği değişikliği campaign lifecycle'ı etkiler; STOP, RESET, START, PAUSE sırası açık korunmalıdır.
- Redis channel adı veya payload field adı değiştirilmemelidir.
- DNC ve LSC düzeltmeleri gerçek DB/HTTP çağrısı gerektirmeden birim test seviyesinde kalmalıdır.
- Predictive Gamma testleri tamamen devre dışı bırakılırsa suite yeşil görünür ama coverage boşluğu kalır; en azından runner hatasını kapatan küçük smoke testleri eklenmelidir.

## TDD Stratejisi

1. Hedefli kırmızı doğrulama: kalan fail sınıfları tek Maven komutuyla çalıştırılır ve mevcut failure/error kümesi tekrar üretilir.
2. Fixture uyumsuzlukları önce test tarafında düzeltilir: DNC `getByListId`, StopState `CampaignResultJobService`, Redis subscriber executor/context ve publisher user enrichment mock'ları.
3. Gerçek üretim riski olan küçük düzeltmeler uygulanır: LSC null `rawData` guard, scheduler STOP önceliği, automation enabled null-safe guard, Mirror job log toleransı.
4. Predictive Gamma sınıflarına güncel constructor ve imzalarla çalışan küçük smoke regression testleri eklenir.
5. Hedefli yeşil doğrulama aynı sınıf listesiyle çalıştırılır.
6. Kapanış doğrulaması olarak `mvn -o test` tam Java suite çalıştırılır. Execution plan, hedefli testler ve tam suite başarılı olmadan tamamlandı sayılmaz.
