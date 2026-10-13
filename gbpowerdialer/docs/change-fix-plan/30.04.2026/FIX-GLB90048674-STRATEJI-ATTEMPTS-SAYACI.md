# FIX Strateji Attempts Sayacı Change/Fix Planı

## Bağlam

PowerDialer retry stratejilerine göre camp tablolarındaki `attempts` alanının artış kuralı farklı olmalıdır. Task beklentisine göre ilk dört retry stratejisinde her arama bir attempts sayılır. `Round-robin dial with per cycle retry limit, schedule next cycle` stratejisinde ise sistemde kullanılan bütün numaralar arandıktan sonra `attempts` 1 artırılmalıdır.

## İş Etiketi

fix

## Öncelik

normal

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallOriginator.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/retry/RoundRobinPerCycle.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/retry/RetryStrategyFactory.java`
- `gbpowerdialer/src/main/java/com/powerdialer/model/Call.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java`
- `gbpowerdialer/src/test/java/com/powerdialer/engine/dialer/**`

## Mevcut Davranış

- `CallOriginator.pollCallsFromConsumerAndOriginate()` içinde `call.incrementAttempts()` her call için originate öncesi koşulsuz çalışıyor.
- `CampaignDbOperations.insertCallResultToDB()` akışı `call.getAttempts()` değerini `campaign.getColResultAttempts()` kolonuna yazıyor.
- Bu davranış ilk dört stratejide task beklentisiyle uyumlu görünüyor; her arama bir attempts sayılıyor.
- `RoundRobinPerCycle` kendi içinde cycle hesabı yapıyor, ancak bu hesap attempts artırma kararında kullanılmıyor. Bu nedenle çok numaralı kayıtta aynı cycle içindeki her numara araması `attempts` değerini artırıyor.

## Beklenen Davranış

- `REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER`: Her arama `attempts` değerini 1 artırır.
- `REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER`: Her arama `attempts` değerini 1 artırır.
- `ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER`: Her arama `attempts` değerini 1 artırır.
- `ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_CYCLE`: Her arama `attempts` değerini 1 artırır.
- `ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER`: DNC dışı kullanılabilir bütün numaralar arandığında cycle tamamlanmış kabul edilir ve `attempts` yalnız cycle sonundaki aramada 1 artırılır. Tek kullanılabilir numara varsa her arama cycle tamamlar.

## Kod Okuma Bulguları

- Attempts değerinin tek doğrudan artırıldığı ana runtime nokta `CallOriginator` içindeki `call.incrementAttempts()` çağrısıdır.
- `Call.incrementAttempts()` ilk kullanımda değeri raw data içindeki `campaign.getColResultAttempts()` kolonundan okur, sonra memory'deki `attempts` alanını artırır.
- `CampaignDbOperations` attempts için ayrı hesap yapmaz; sadece call üzerindeki mevcut değeri result/list tablo satırına yazar.
- `RoundRobinPerCycle.isRetryCycleReached()` cycle tamamlanma hesabını `retryHistory.size() + 1` ve DNC dışı numara sayısı üzerinden yapıyor.
- `RoundRobinPerCycle.isOverallRetryLimitReached()` retry limitini cycle mantığıyla `retryHistory` üzerinden değerlendiriyor; bu nedenle attempts artışının düzeltilmesi retry number seçimini doğrudan yeniden yazmayı gerektirmiyor.
- `CallerIdManager.obtainCallerId()` caller-id pool için `call.getAttempts() + 1` değerini kullanıyor. Attempts increment zamanı değişirse bu dış boundary de regression testte kontrol edilmelidir.

## Root Cause / Değişiklik Gerekçesi

Root cause, attempts sayaç artışının retry strategy'den bağımsız şekilde originate boundary'sinde yapılmasıdır. Per-cycle round robin stratejisinin cycle kavramı retry scheduling tarafında mevcut olsa da attempts writer bu kavramı bilmiyor. Bu nedenle per-cycle stratejide attempts, cycle sayısı yerine call sayısı gibi davranıyor.

## Riskler

- `CallOriginator` hot path'tedir; attempts kararı için yeni DB query, network çağrısı, geniş lock veya pahalı collection scan eklenmemelidir.
- Attempts değeri camp table status sınıflandırmasında ve caller-id pool request payload'ında kullanıldığı için off-by-one riski vardır.
- DNC dışı kullanılabilir numara sayısı ile raw data'daki boş phone number kolonları birlikte değerlendirilmelidir.
- `countAllCallTypes` açık kampanyalarda mevcut retry limit davranışı bozulmamalıdır.
- LSC timeout, no caller id ve preview/progressive gibi originate öncesi iptal akışlarında mevcut ilk dört strateji davranışı korunmalıdır.

## TDD Stratejisi

- Önce mevcut davranışı ve beklenen yeni davranışı kilitleyen hedefli regression testi yazılacak.
- Test, attempts increment kararını mümkünse küçük ve pure bir boundary üzerinden doğrulayacak; gerçek AMI, Redis veya DB bağlantısı kurulmayacak.
- İlk dört strateji için her originate denemesinde attempts artışı korunacak.
- Per-cycle strateji için iki numaralı kayıtta ilk numara aramasında attempts değişmeyecek, ikinci numara aramasında 1 artacak.
- Per-cycle strateji için tek kullanılabilir numara veya DNC sonrası tek kalan numara senaryosunda her arama cycle sayılacak.
- Caller-id pool off-by-one riski için `CallerIdManager` çağrısına giden attempts değerinin stratejiye göre tutarlı kaldığı kontrol edilecek veya mevcut mock boundary genişletilecek.

## Açık Sorular

Yok.

Plan varsayımı: Task metnindeki "sistemde kullanılan bütün numaralar" ifadesi, raw data içinde dolu olan ve DNC ile elenmemiş phone number kolonları anlamına gelir.

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [x] `CallOriginator` attempts writer davranışı için hedefli regression testi ekle.
- [x] İlk dört retry stratejisinde her arama attempts artırır davranışını testle koru.
- [x] `RoundRobinPerCycle` çok numaralı cycle davranışında attempts yalnız cycle sonunda artar testini ekle.
- [x] DNC/tek kullanılabilir numara senaryosunu testle koru.
- [x] En küçük üretim kodu değişikliğiyle attempts increment kararını strategy-aware hale getir.
- [x] Caller-id pool payload tarafında attempts off-by-one riski için mevcut veya yeni testi çalıştır.
- [x] Hedefli Maven testlerini offline tool path standardıyla çalıştır.
- [x] Plan checkbox'larını ve history kaydını uygulama sonucuna göre güncelle.

## Performans Etkisi

- Hot path durumu: `CallOriginator` originate loop'u hot path kabul edilir.
- Call/event/tick başına ek maliyet: Sadece per-cycle stratejide bounded phone number column sayısı ve retry history size hesabı kullanılmalı; ilk dört stratejide sabit enum kontrolünden fazlası olmamalıdır.
- IO/scan/lock/serialization/log etkisi: Yeni IO, DB query, network çağrısı, lock, serialization veya verbose log eklenmeyecek.
- Azaltım veya ölçüm notu: Hesap mevcut `Call` raw data ve campaign phone number metadata'sı ile memory içinde yapılacak; testlerde çok numaralı ve DNC'li kayıtlar doğrulanacak.
- Sonuç: Hot path'te yeni IO/lock/serialization yok. Per-cycle strateji dışındaki kampanyalarda ek maliyet sabit enum kontrolüyle sınırlı; per-cycle stratejide yalnız call üzerindeki mevcut phone number listesi, DNC seti ve retry history kullanılıyor.

## Doğrulama

Planlanan hedefli komut:

```powershell
mvn -o -Dtest=CallOriginatorTest,RoundRobinPerCycleTest,RetryLimitTest test
```

`.codex/TOOL-PATHS.local.properties` içinde `MAVEN_CMD`, `MAVEN_SETTINGS`, `MAVEN_REPO_LOCAL` veya `JAVA_HOME` varsa komut bu değerlere göre çalıştırılacak.

Gerçekleşen doğrulama:

- Kırmızı aşama: `CallOriginatorTest` test compile, `incrementAttemptsForOrigination(Call)` eksik olduğu için beklenen şekilde kırıldı.
- Ek kırmızı aşama: caller-id attempt regression testi, `callerIdAttemptForOrigination(Call)` eksik olduğu için beklenen şekilde kırıldı.
- Yeşil aşama: `mvn -o -Dtest=CallOriginatorTest test` başarılı; 16 test, 0 failure/error.
- Hedefli suite: `mvn -o -Dtest=CallOriginatorTest,RoundRobinPerCycleTest,RetryLimitTest test` başarılı; 24 test, 0 failure/error.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] Attempts artış kuralı strateji bazında testle kanıtlandı.
- [x] Planlanan hedefli testler çalıştırıldı ve başarılı oldu.
- [x] Kod değişikliği DB, Redis veya AMI contract adı değiştirmeden tamamlandı.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

`CallOriginator` originate boundary'sinde attempts increment kararı strategy-aware hale getirildi. İlk dört retry stratejisi mevcut her arama increment davranışını korur. `ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER` stratejisinde DNC dışı kullanılabilir numaraların tamamı aranmadan camp table attempts değeri artırılmaz; tek kullanılabilir numarada her arama cycle tamamlar. Caller-id pool için mevcut cycle attempt değeri explicit hesaplanır ve `CallerIdManager` overload'u ile iletilir.
